/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencord.olt.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flowobjective.DefaultFilteringObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveContext;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDeviceService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.SubscriberAndDeviceInformationService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions rules on access devices.
 */
@Service
@Component(immediate = true)
public class Olt
        extends AbstractListenerManager<AccessDeviceEvent, AccessDeviceListener>
        implements AccessDeviceService {
    private static final String APP_NAME = "org.opencord.olt";

    private static final short DEFAULT_VLAN = 0;

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SubscriberAndDeviceInformationService subsService;

    @Property(name = "defaultVlan", intValue = DEFAULT_VLAN,
            label = "Default VLAN RG<->ONU traffic")
    private int defaultVlan = DEFAULT_VLAN;

    @Property(name = "enableDhcpOnProvisioning", boolValue = true,
            label = "Create the DHCP Flow rules when a subscriber is provisioned")
    protected boolean enableDhcpOnProvisioning = false;

    @Property(name = "enableIgmpOnProvisioning", boolValue = false,
            label = "Create IGMP Flow rules when a subscriber is provisioned")
    protected boolean enableIgmpOnProvisioning = false;

    private final DeviceListener deviceListener = new InternalDeviceListener();

    private ApplicationId appId;

    private ExecutorService oltInstallers = Executors.newFixedThreadPool(4,
                                                                         groupedThreads("onos/olt-service",
                                                                                        "olt-installer-%d"));

    protected ExecutorService eventExecutor;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads("onos/olt", "events-%d", log));
        modified(context);
        appId = coreService.registerApplication(APP_NAME);
        componentConfigService.registerProperties(getClass());

        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);

        // look for all provisioned devices in Sadis and create EAPOL flows for the
        // UNI ports
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            checkAndCreateDeviceFlows(d);
        }

        deviceService.addListener(deviceListener);

        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        componentConfigService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        eventDispatcher.removeSink(AccessDeviceEvent.class);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

        try {
            String s = get(properties, "defaultVlan");
            defaultVlan = isNullOrEmpty(s) ? DEFAULT_VLAN : Integer.parseInt(s.trim());

            Boolean o = Tools.isPropertyEnabled(properties, "enableDhcpOnProvisioning");
            if (o != null) {
                enableDhcpOnProvisioning = o;
            }

            Boolean p = Tools.isPropertyEnabled(properties, "enableIgmpOnProvisioning");
            if (p != null) {
                enableIgmpOnProvisioning = p;
            }

        } catch (Exception e) {
            defaultVlan = DEFAULT_VLAN;
        }
    }

    @Override
    public void provisionSubscriber(ConnectPoint port) {
        checkNotNull(deviceService.getPort(port.deviceId(), port.port()),
                "Invalid connect point");
        // Find the subscriber on this connect point
        SubscriberAndDeviceInformation sub = getSubscriber(port);
        if (sub == null) {
            log.warn("No subscriber found for {}", port);
            return;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(port.deviceId()));
        if (uplinkPort == null) {
            log.warn("No uplink port found for OLT device {}", port.deviceId());
            return;
        }

        if (enableDhcpOnProvisioning) {
            processDhcpFilteringObjectives(port.deviceId(), port.port(), true);
        }

        Optional<VlanId> defaultVlan = Optional.empty();
        provisionVlans(port.deviceId(), uplinkPort.number(), port.port(), sub.cTag(), sub.sTag(),
                defaultVlan);

        if (enableIgmpOnProvisioning) {
            processIgmpFilteringObjectives(port.deviceId(), port.port(), true);
        }
    }

    @Override
    public void removeSubscriber(ConnectPoint port) {
        // Get the subscriber connected to this port from Sadis
        SubscriberAndDeviceInformation subscriber = getSubscriber(port);
        if (subscriber == null) {
            log.warn("Subscriber on port {} not found", port);
            return;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(port.deviceId()));
        if (uplinkPort == null) {
            log.warn("No uplink port found for OLT device {}", port.deviceId());
            return;
        }

        if (enableDhcpOnProvisioning) {
            processDhcpFilteringObjectives(port.deviceId(), port.port(), false);
        }

        Optional<VlanId> defaultVlan = Optional.empty();
        unprovisionSubscriber(port.deviceId(), uplinkPort.number(), port.port(), subscriber.cTag(),
                              subscriber.sTag(), defaultVlan);

        if (enableIgmpOnProvisioning) {
            processIgmpFilteringObjectives(port.deviceId(), port.port(), false);
        }
    }


    @Override
    public Collection<Map.Entry<ConnectPoint, VlanId>> getSubscribers() {
        ArrayList<Map.Entry<ConnectPoint, VlanId>> subs = new ArrayList<>();

        // Get the subscribers for all the devices
        // If the port is UNI, is enabled and exists in Sadis then copy it
        for (Device d : deviceService.getDevices()) {
            for (Port p: deviceService.getPorts(d.id())) {
                if (isUniPort(d, p) && p.isEnabled()) {
                    ConnectPoint cp = new ConnectPoint(d.id(), p.number());

                    SubscriberAndDeviceInformation sub = getSubscriber(cp);
                    if (sub != null) {
                        subs.add(new AbstractMap.SimpleEntry(cp, sub.cTag()));
                    }
                }
            }
        }

        return subs;
    }

    @Override
    public List<DeviceId> fetchOlts() {
        // look through all the devices and find the ones that are OLTs as per Sadis
        List<DeviceId> olts = new ArrayList<>();
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            String devSerialNo = d.serialNumber();
            SubscriberAndDeviceInformation deviceInfo = subsService.get(devSerialNo);

            if (deviceInfo != null) {
                // So this is indeed a OLT device
                olts.add(d.id());
            }
        }
        return olts;
    }

    private void initializeUni(Port port) {
        DeviceId deviceId = (DeviceId) port.element().id();

        post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, deviceId, port));

        if (port.isEnabled()) {
            processFilteringObjectives(deviceId, port.number(), true);
        }
    }

    private void unprovisionSubscriber(DeviceId deviceId, PortNumber uplink,
                                       PortNumber subscriberPort, VlanId subscriberVlan,
                                       VlanId deviceVlan, Optional<VlanId> defaultVlan) {

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        ForwardingObjective.Builder upFwd = upBuilder(uplink, subscriberPort,
                                                      subscriberVlan, deviceVlan,
                                                      defaultVlan);
        ForwardingObjective.Builder downFwd = downBuilder(uplink, subscriberPort,
                                                          subscriberVlan, deviceVlan,
                                                          defaultVlan);


        flowObjectiveService.forward(deviceId, upFwd.remove(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                upFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                upFuture.complete(error);
            }
        }));

        flowObjectiveService.forward(deviceId, downFwd.remove(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                downFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                downFuture.complete(error);
            }
        }));

        upFuture.thenAcceptBothAsync(downFuture, (upStatus, downStatus) -> {
            if (upStatus == null && downStatus == null) {
                post(new AccessDeviceEvent(AccessDeviceEvent.Type.SUBSCRIBER_UNREGISTERED,
                                           deviceId,
                                           deviceVlan,
                                           subscriberVlan));
            } else if (downStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed downstream uninstallation: {}",
                          subscriberVlan, deviceId, subscriberPort, downStatus);
            } else if (upStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed upstream uninstallation: {}",
                          subscriberVlan, deviceId, subscriberPort, upStatus);
            }
        }, oltInstallers);

    }

    private void provisionVlans(DeviceId deviceId, PortNumber uplinkPort,
                                PortNumber subscriberPort,
                                VlanId subscriberVlan, VlanId deviceVlan,
                                Optional<VlanId> defaultVlan) {

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        ForwardingObjective.Builder upFwd = upBuilder(uplinkPort, subscriberPort,
                                                      subscriberVlan, deviceVlan,
                                                      defaultVlan);


        ForwardingObjective.Builder downFwd = downBuilder(uplinkPort, subscriberPort,
                                                          subscriberVlan, deviceVlan,
                                                          defaultVlan);

        flowObjectiveService.forward(deviceId, upFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                upFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                upFuture.complete(error);
            }
        }));

        flowObjectiveService.forward(deviceId, downFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                downFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                downFuture.complete(error);
            }
        }));

        upFuture.thenAcceptBothAsync(downFuture, (upStatus, downStatus) -> {
            if (upStatus == null && downStatus == null) {
                post(new AccessDeviceEvent(AccessDeviceEvent.Type.SUBSCRIBER_REGISTERED,
                                           deviceId,
                                           deviceVlan,
                                           subscriberVlan));

            } else if (downStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed downstream installation: {}",
                          subscriberVlan, deviceId, subscriberPort, downStatus);
            } else if (upStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed upstream installation: {}",
                          subscriberVlan, deviceId, subscriberPort, upStatus);
            }
        }, oltInstallers);

    }

    private ForwardingObjective.Builder downBuilder(PortNumber uplinkPort,
                                                    PortNumber subscriberPort,
                                                    VlanId subscriberVlan,
                                                    VlanId deviceVlan,
                                                    Optional<VlanId> defaultVlan) {
        TrafficSelector downstream = DefaultTrafficSelector.builder()
                .matchVlanId(deviceVlan)
                .matchInPort(uplinkPort)
                .matchInnerVlanId(subscriberVlan)
                .build();

        TrafficTreatment downstreamTreatment = DefaultTrafficTreatment.builder()
                .popVlan()
                .setVlanId(defaultVlan.orElse(VlanId.vlanId((short) this.defaultVlan)))
                .setOutput(subscriberPort)
                .build();

        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(1000)
                .makePermanent()
                .withSelector(downstream)
                .fromApp(appId)
                .withTreatment(downstreamTreatment);
    }

    private ForwardingObjective.Builder upBuilder(PortNumber uplinkPort,
                                                  PortNumber subscriberPort,
                                                  VlanId subscriberVlan,
                                                  VlanId deviceVlan,
                                                  Optional<VlanId> defaultVlan) {
        TrafficSelector upstream = DefaultTrafficSelector.builder()
                .matchVlanId(defaultVlan.orElse(VlanId.vlanId((short) this.defaultVlan)))
                .matchInPort(subscriberPort)
                .build();


        TrafficTreatment upstreamTreatment = DefaultTrafficTreatment.builder()
                .pushVlan()
                .setVlanId(subscriberVlan)
                .pushVlan()
                .setVlanId(deviceVlan)
                .setOutput(uplinkPort)
                .build();

        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(1000)
                .makePermanent()
                .withSelector(upstream)
                .fromApp(appId)
                .withTreatment(upstreamTreatment);
    }

    private void processFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        FilteringObjective eapol = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                .withMeta(DefaultTrafficTreatment.builder()
                                  .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Eapol filter for {} on {} installed.",
                                 devId, port);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("Eapol filter for {} on {} failed because {}",
                                 devId, port, error);
                    }
                });

        flowObjectiveService.filter(devId, eapol);

    }

    /**
     * Installs trap filtering objectives for particular traffic types on an
     * NNI port.
     *
     * @param devId device ID
     * @param port port number
     * @param install true to install, false to remove
     */
    private void processNniFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        processLldpFilteringObjective(devId, port, install);
        processDhcpFilteringObjectives(devId, port, install);
    }

    private void processLldpFilteringObjective(DeviceId devId, PortNumber port, boolean install) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        FilteringObjective lldp = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(EthType.EtherType.LLDP.ethType()))
                .withMeta(DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("LLDP filter for {} on {} installed.",
                                devId, port);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("LLDP filter for {} on {} failed because {}",
                                devId, port, error);
                    }
                });

        flowObjectiveService.filter(devId, lldp);

    }

    private void processDhcpFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        FilteringObjective dhcpUpstream = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_UDP))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(68)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(67)))
                .withMeta(DefaultTrafficTreatment.builder()
                                  .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("DHCP filter for {} on {} installed.",
                                devId, port);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("DHCP filter for {} on {} failed because {}",
                                devId, port, error);
                    }
                });

        flowObjectiveService.filter(devId, dhcpUpstream);
    }

    private void processIgmpFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }

       DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        builder = install ? builder.permit() : builder.deny();

        FilteringObjective igmp = builder
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_IGMP))
                .withMeta(DefaultTrafficTreatment.builder()
                                  .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Igmp filter for {} on {} installed.",
                                 devId, port);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("Igmp filter for {} on {} failed because {}.",
                                 devId, port, error);
                    }
                });

        flowObjectiveService.filter(devId, igmp);
    }

    /**
     * Creates trap flows for device, including DHCP and LLDP trap on NNI and
     * EAPOL trap on the UNIs, if device is present in Sadis config.
     *
     * @param dev Device to look for
     */
    private void checkAndCreateDeviceFlows(Device dev) {
        // we create only for the ones we are master of
        if (!mastershipService.isLocalMaster(dev.id())) {
                return;
        }
        // check if this device is provisioned in Sadis
        String devSerialNo = dev.serialNumber();
        SubscriberAndDeviceInformation deviceInfo = subsService.get(devSerialNo);
        log.debug("checkAndCreateDeviceFlows: deviceInfo {}", deviceInfo);

        if (deviceInfo != null) {
            // This is an OLT device as per Sadis, we create flows for UNI and NNI ports
            for (Port p : deviceService.getPorts(dev.id())) {
                if (isUniPort(dev, p)) {
                    processFilteringObjectives(dev.id(), p.number(), true);
                } else {
                    processNniFilteringObjectives(dev.id(), p.number(), true);
                }
            }
        }
    }


    /**
     * Get the uplink for of the OLT device.
     *
     * This assumes that the OLT has a single uplink port. When more uplink ports need to be supported
     * this logic needs to be changed
     *
     * @param dev Device to look for
     * @return The uplink Port of the OLT
     */
    private Port getUplinkPort(Device dev) {
        // check if this device is provisioned in Sadis
        String devSerialNo = dev.serialNumber();
        SubscriberAndDeviceInformation deviceInfo = subsService.get(devSerialNo);
        log.debug("getUplinkPort: deviceInfo {}", deviceInfo);

        if (deviceInfo != null) {
            // Return the port that has been configured as the uplink port of this OLT in Sadis
            for (Port p: deviceService.getPorts(dev.id())) {
                if (p.number().toLong() == deviceInfo.uplinkPort()) {
                    log.debug("getUplinkPort: Found port {}", p);
                    return p;
                }
            }
        }

        log.debug("getUplinkPort: No uplink port found for OLT {}", dev);
        return null;
    }

    /**
     * Return the subscriber on a port.
     *
     * @param port On which to find the subscriber
     * @return subscriber if found else null
     */
    private SubscriberAndDeviceInformation getSubscriber(ConnectPoint port) {
        String portName = deviceService.getPort(port).annotations()
                .value(AnnotationKeys.PORT_NAME);

        return subsService.get(portName);
    }

    private boolean isUniPort(Device d, Port p) {
        Port ulPort = getUplinkPort(d);
        if (ulPort != null) {
            return (ulPort.number().toLong() != p.number().toLong());
        }
        return false;
    }

    private SubscriberAndDeviceInformation getOltInfo(Device dev) {
        String devSerialNo = dev.serialNumber();
        SubscriberAndDeviceInformation deviceInfo = subsService.get(devSerialNo);
        return deviceInfo;
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> {
                DeviceId devId = event.subject().id();
                Device dev = event.subject();

                if (event.type() == DeviceEvent.Type.PORT_STATS_UPDATED) {
                    return;
                }

                if (getOltInfo(dev) == null) {
                    log.debug("No device info found, this is not an OLT");
                    return;
                }

                log.debug("OLT got {} event for {}", event.type(), event.subject());

                switch (event.type()) {
                    //TODO: Port handling and bookkeeping should be improved once
                    // olt firmware handles correct behaviour.
                    case PORT_ADDED:
                        if (isUniPort(dev, event.port())) {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, event.port()));

                            if (event.port().isEnabled()) {
                                processFilteringObjectives(devId, event.port().number(), true);
                            }
                        } else {
                            checkAndCreateDeviceFlows(dev);
                        }
                        break;
                    case PORT_REMOVED:
                        if (isUniPort(dev, event.port())) {
                            if (event.port().isEnabled()) {
                                processFilteringObjectives(devId, event.port().number(), false);
                                removeSubscriber(new ConnectPoint(devId, event.port().number()));
                            }

                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, event.port()));
                        }

                        break;
                    case PORT_UPDATED:
                        if (!isUniPort(dev, event.port())) {
                            break;
                        }

                        if (event.port().isEnabled()) {
                            processFilteringObjectives(devId, event.port().number(), true);
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, event.port()));
                        } else {
                            processFilteringObjectives(devId, event.port().number(), false);
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, event.port()));
                        }
                        break;
                    case DEVICE_ADDED:
                        post(new AccessDeviceEvent(
                                AccessDeviceEvent.Type.DEVICE_CONNECTED, devId,
                                null, null));

                        // Send UNI_ADDED events for all existing ports
                        deviceService.getPorts(devId).stream()
                                .filter(p -> isUniPort(dev, p))
                                .filter(Port::isEnabled)
                                .forEach(p -> post(new AccessDeviceEvent(
                                        AccessDeviceEvent.Type.UNI_ADDED, devId, p)));

                        checkAndCreateDeviceFlows(dev);
                        break;
                    case DEVICE_REMOVED:
                        deviceService.getPorts(devId).stream()
                                .filter(p -> isUniPort(dev, p))
                                .forEach(p -> post(new AccessDeviceEvent(
                                        AccessDeviceEvent.Type.UNI_REMOVED, devId, p)));

                        post(new AccessDeviceEvent(
                                AccessDeviceEvent.Type.DEVICE_DISCONNECTED, devId,
                                null, null));
                        break;
                    case DEVICE_AVAILABILITY_CHANGED:
                        if (deviceService.isAvailable(devId)) {
                            post(new AccessDeviceEvent(
                                    AccessDeviceEvent.Type.DEVICE_CONNECTED, devId,
                                    null, null));
                            checkAndCreateDeviceFlows(dev);
                        } else {
                            post(new AccessDeviceEvent(
                                    AccessDeviceEvent.Type.DEVICE_DISCONNECTED, devId,
                                    null, null));
                        }
                        break;
                    case DEVICE_UPDATED:
                    case DEVICE_SUSPENDED:
                    case PORT_STATS_UPDATED:
                    default:
                        return;
                }
            });
        }
    }
}
