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

import com.google.common.collect.Maps;
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
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
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
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.cordconfig.access.AccessDeviceConfig;
import org.opencord.cordconfig.access.AccessDeviceData;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDeviceService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
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
    private static final String SUBSCRIBERS = "existing-subscribers";

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
    protected NetworkConfigRegistry networkConfig;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Property(name = "defaultVlan", intValue = DEFAULT_VLAN,
            label = "Default VLAN RG<->ONU traffic")
    private int defaultVlan = DEFAULT_VLAN;

    @Property(name = "enableDhcpIgmpOnProvisioning", boolValue = false,
            label = "Create the DHCP and IGMP Flow rules when a subscriber is provisioned")
    protected boolean enableDhcpIgmpOnProvisioning = false;

    private final DeviceListener deviceListener = new InternalDeviceListener();

    private ApplicationId appId;

    private ExecutorService oltInstallers = Executors.newFixedThreadPool(4,
                                                                         groupedThreads("onos/olt-service",
                                                                                        "olt-installer-%d"));

    private Map<DeviceId, AccessDeviceData> oltData = new ConcurrentHashMap<>();

    private Map<ConnectPoint, VlanId> subscribers;

    private InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();
    private static final Class<AccessDeviceConfig> CONFIG_CLASS =
            AccessDeviceConfig.class;

    private ConfigFactory<DeviceId, AccessDeviceConfig> configFactory =
            new ConfigFactory<DeviceId, AccessDeviceConfig>(
                    SubjectFactories.DEVICE_SUBJECT_FACTORY, CONFIG_CLASS, "accessDevice") {
                @Override
                public AccessDeviceConfig createConfig() {
                    return new AccessDeviceConfig();
                }
            };


    @Activate
    public void activate(ComponentContext context) {
        modified(context);
        appId = coreService.registerApplication(APP_NAME);
        componentConfigService.registerProperties(getClass());

        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);

        networkConfig.registerConfigFactory(configFactory);
        networkConfig.addListener(configListener);


        networkConfig.getSubjects(DeviceId.class, AccessDeviceConfig.class).forEach(
                subject -> {
                    AccessDeviceConfig config = networkConfig.getConfig(subject, AccessDeviceConfig.class);
                    if (config != null) {
                        AccessDeviceData data = config.getOlt();
                        oltData.put(data.deviceId(), data);
                    }
                }
        );

        oltData.keySet().stream()
                .flatMap(did -> deviceService.getPorts(did).stream())
                .filter(this::isUni)
                .forEach(this::initializeUni);

        subscribers = storageService.<ConnectPoint, VlanId>consistentMapBuilder()
                .withName(SUBSCRIBERS)
                .withSerializer(Serializer.using(KryoNamespaces.API))
                .build().asJavaMap();

        deviceService.addListener(deviceListener);

        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        componentConfigService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        networkConfig.removeListener(configListener);
        networkConfig.unregisterConfigFactory(configFactory);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

        try {
            String s = get(properties, "defaultVlan");
            defaultVlan = isNullOrEmpty(s) ? DEFAULT_VLAN : Integer.parseInt(s.trim());

            Boolean o = Tools.isPropertyEnabled(properties, "enableDhcpIgmpOnProvisioning");
            if (o != null) {
                enableDhcpIgmpOnProvisioning = o;
            }
        } catch (Exception e) {
            defaultVlan = DEFAULT_VLAN;
        }
    }

    @Override
    public void provisionSubscriber(ConnectPoint port, VlanId vlan) {
        checkNotNull(deviceService.getPort(port.deviceId(), port.port()),
                "Invalid connect point");
        AccessDeviceData olt = oltData.get(port.deviceId());

        if (olt == null) {
            log.warn("No data found for OLT device {}", port.deviceId());
            throw new IllegalArgumentException("Missing OLT configuration for device");
        }

        if (enableDhcpIgmpOnProvisioning) {
            processDhcpFilteringObjectives(olt.deviceId(), port.port(), true);
        }

        provisionVlans(olt.deviceId(), olt.uplink(), port.port(), vlan, olt.vlan(),
                olt.defaultVlan());

        if (enableDhcpIgmpOnProvisioning) {
            processIgmpFilteringObjectives(olt.deviceId(), port.port(), true);
        }
    }

    @Override
    public void removeSubscriber(ConnectPoint port) {
        AccessDeviceData olt = oltData.get(port.deviceId());

        if (olt == null) {
            log.warn("No data found for OLT device {}", port.deviceId());
            return;
        }

        VlanId subscriberVlan = subscribers.remove(port);

        if (subscriberVlan == null) {
            log.warn("Unknown subscriber at location {}", port);
            return;
        }

        if (enableDhcpIgmpOnProvisioning) {
            processDhcpFilteringObjectives(olt.deviceId(), port.port(), false);
        }

        unprovisionSubscriber(olt.deviceId(), olt.uplink(), port.port(), subscriberVlan,
                              olt.vlan(), olt.defaultVlan());

        if (enableDhcpIgmpOnProvisioning) {
            processIgmpFilteringObjectives(olt.deviceId(), port.port(), false);
        }
    }


    @Override
    public Collection<Map.Entry<ConnectPoint, VlanId>> getSubscribers() {
        return subscribers.entrySet();
    }

    @Override
    public Map<DeviceId, AccessDeviceData> fetchOlts() {
        return Maps.newHashMap(oltData);
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

        ConnectPoint cp = new ConnectPoint(deviceId, subscriberPort);
        subscribers.put(cp, subscriberVlan);

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
                .withPriority(1000)
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
                .withPriority(1000)
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

    private boolean isUni(Port port) {
        return !oltData.get(port.element().id()).uplink().equals(port.number());
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId devId = event.subject().id();
            if (!oltData.containsKey(devId)) {
                return;
            }
            switch (event.type()) {
                //TODO: Port handling and bookkeeping should be improved once
                // olt firmware handles correct behaviour.
                case PORT_ADDED:
                    if (!oltData.get(devId).uplink().equals(event.port().number())) {
                        post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, event.port()));

                        if (event.port().isEnabled()) {
                            processFilteringObjectives(devId, event.port().number(), true);
                        }
                    }
                    break;
                case PORT_REMOVED:
                    AccessDeviceData olt = oltData.get(devId);
                    VlanId vlan = subscribers.get(new ConnectPoint(devId,
                                                                   event.port().number()));
                    if (vlan != null) {
                        unprovisionSubscriber(devId, olt.uplink(),
                                event.port().number(),
                                vlan, olt.vlan(), olt.defaultVlan());
                    }
                    if (!oltData.get(devId).uplink().equals(event.port().number())) {
                        if (event.port().isEnabled()) {
                            processFilteringObjectives(devId, event.port().number(), false);
                        }

                        post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, event.port()));
                    }

                    break;
                case PORT_UPDATED:
                    if (oltData.get(devId).uplink().equals(event.port().number())) {
                        break;
                    }
                    if (event.port().isEnabled()) {
                        processFilteringObjectives(devId, event.port().number(), true);
                    } else {
                        processFilteringObjectives(devId, event.port().number(), false);
                    }
                    break;
                case DEVICE_ADDED:
                    post(new AccessDeviceEvent(
                            AccessDeviceEvent.Type.DEVICE_CONNECTED, devId,
                            null, null));

                    // Send UNI_ADDED events for all existing ports
                    deviceService.getPorts(devId).stream()
                            .filter(Olt.this::isUni)
                            .filter(Port::isEnabled)
                            .forEach(p -> post(new AccessDeviceEvent(
                                    AccessDeviceEvent.Type.UNI_ADDED, devId, p)));

                    provisionDefaultFlows(devId);
                    break;
                case DEVICE_REMOVED:
                    deviceService.getPorts(devId).stream()
                            .filter(Olt.this::isUni)
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
        }
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {

                case CONFIG_ADDED:
                case CONFIG_UPDATED:

                    AccessDeviceConfig config =
                            networkConfig.getConfig((DeviceId) event.subject(), CONFIG_CLASS);
                    if (config != null) {
                        oltData.put(config.getOlt().deviceId(), config.getOlt());
                        provisionDefaultFlows((DeviceId) event.subject());
                    }

                    break;
                case CONFIG_REGISTERED:
                case CONFIG_UNREGISTERED:
                    break;
                case CONFIG_REMOVED:
                    oltData.remove(event.subject());
                default:
                    break;
            }
        }

        @Override
        public boolean isRelevant(NetworkConfigEvent event) {
            return event.configClass().equals(CONFIG_CLASS);
        }
    }

    private void provisionDefaultFlows(DeviceId deviceId) {
        if (!mastershipService.isLocalMaster(deviceId)) {
            return;
        }
        List<Port> ports = deviceService.getPorts(deviceId);

        ports.stream()
                .filter(p -> !oltData.get(p.element().id()).uplink().equals(p.number()))
                .filter(p -> p.isEnabled())
                .forEach(p -> processFilteringObjectives((DeviceId) p.element().id(),
                                                         p.number(), true));

    }

}
