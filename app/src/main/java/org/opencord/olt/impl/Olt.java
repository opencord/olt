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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.AbstractMap;
import java.util.Arrays;
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
import org.onlab.packet.IPv6;
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
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMultimap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.meter.MeterListener;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterEvent;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.AccessSubscriberId;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.HashMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

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
    private static final int DEFAULT_TP_ID = 64;
    private static final String DEFAULT_BP_ID = "Default";
    private static final String ADDITIONAL_VLANS = "additional-vlans";

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
    protected SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Property(name = "defaultVlan", intValue = DEFAULT_VLAN,
            label = "Default VLAN RG<->ONU traffic")
    private int defaultVlan = DEFAULT_VLAN;

    @Property(name = "enableDhcpOnProvisioning", boolValue = true,
            label = "Create the DHCP Flow rules when a subscriber is provisioned")
    protected boolean enableDhcpOnProvisioning = false;

    @Property(name = "enableDhcpV4", boolValue = true,
            label = "Enable flows for DHCP v4")
    protected boolean enableDhcpV4 = true;

    @Property(name = "enableDhcpV6", boolValue = true,
            label = "Enable flows for DHCP v6")
    protected boolean enableDhcpV6 = false;

    @Property(name = "enableIgmpOnProvisioning", boolValue = false,
            label = "Create IGMP Flow rules when a subscriber is provisioned")
    protected boolean enableIgmpOnProvisioning = false;

    @Property(name = "deleteMeters", boolValue = true,
            label = "Deleting Meters based on flow count statistics")
    protected boolean deleteMeters = true;

    @Property(name = "defaultTechProfileId", intValue = DEFAULT_TP_ID,
            label = "Default technology profile id that is used for authentication trap flows")
    protected int defaultTechProfileId = DEFAULT_TP_ID;

    @Property(name = "defaultBpId", value = DEFAULT_BP_ID,
            label = "Default bandwidth profile id that is used for authentication trap flows")
    protected String defaultBpId = DEFAULT_BP_ID;

    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final MeterListener meterListener = new InternalMeterListener();

    private ApplicationId appId;
    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private BaseInformationService<BandwidthProfileInformation> bpService;

    private Map<String, MeterId> bpInfoToMeter = new HashMap<>();

    private ExecutorService oltInstallers = Executors.newFixedThreadPool(4,
            groupedThreads("onos/olt-service",
                    "olt-installer-%d"));

    private ConsistentMultimap<ConnectPoint, Map.Entry<VlanId, VlanId>> additionalVlans;

    protected ExecutorService eventExecutor;

    private Map<ConnectPoint, SubscriberAndDeviceInformation> programmedSubs;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads("onos/olt", "events-%d", log));
        modified(context);
        appId = coreService.registerApplication(APP_NAME);
        componentConfigService.registerProperties(getClass());
        programmedSubs = Maps.newConcurrentMap();

        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);

        subsService = sadisService.getSubscriberInfoService();
        bpService = sadisService.getBandwidthProfileService();

        // look for all provisioned devices in Sadis and create EAPOL flows for the
        // UNI ports
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            checkAndCreateDeviceFlows(d);
        }

        additionalVlans = storageService.<ConnectPoint, Map.Entry<VlanId, VlanId>>consistentMultimapBuilder()
                .withName(ADDITIONAL_VLANS)
                .withSerializer(Serializer.using(Arrays.asList(KryoNamespaces.API),
                        AbstractMap.SimpleEntry.class))
                .build();

        deviceService.addListener(deviceListener);
        meterService.addListener(meterListener);

        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        componentConfigService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        meterService.removeListener(meterListener);
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

            Boolean v4 = Tools.isPropertyEnabled(properties, "enableDhcpV4");
            if (v4 != null) {
                enableDhcpV4 = v4;
            }

            Boolean v6 = Tools.isPropertyEnabled(properties, "enableDhcpV6");
            if (v6 != null) {
                enableDhcpV6 = v6;
            }

            Boolean p = Tools.isPropertyEnabled(properties, "enableIgmpOnProvisioning");
            if (p != null) {
                enableIgmpOnProvisioning = p;
            }

            log.info("DHCP Settings [enableDhcpOnProvisioning: {}, enableDhcpV4: {}, enableDhcpV6: {}]",
                    enableDhcpOnProvisioning, enableDhcpV4, enableDhcpV6);

            Boolean d = Tools.isPropertyEnabled(properties, "deleteMeters");
            if (d != null) {
                deleteMeters = d;
            }

            String tpId = get(properties, "defaultTechProfileId");
            defaultTechProfileId = isNullOrEmpty(s) ? DEFAULT_TP_ID : Integer.parseInt(tpId.trim());

            String bpId = get(properties, "defaultBpId");
            defaultBpId = bpId;

        } catch (Exception e) {
            defaultVlan = DEFAULT_VLAN;
        }
    }

    @Override
    public boolean provisionSubscriber(ConnectPoint port) {
        checkNotNull(deviceService.getPort(port.deviceId(), port.port()),
                "Invalid connect point");
        // Find the subscriber on this connect point
        SubscriberAndDeviceInformation sub = getSubscriber(port);
        if (sub == null) {
            log.warn("No subscriber found for {}", port);
            return false;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(port.deviceId()));
        if (uplinkPort == null) {
            log.warn("No uplink port found for OLT device {}", port.deviceId());
            return false;
        }

        if (enableDhcpOnProvisioning) {
            processDhcpFilteringObjectives(port.deviceId(), port.port(), true,
                    true);
        }
        log.info("Programming vlans for subscriber: {}", sub);
        Optional<VlanId> defaultVlan = Optional.empty();
        provisionVlans(port, uplinkPort.number(), defaultVlan, sub);

        if (enableIgmpOnProvisioning) {
            processIgmpFilteringObjectives(port.deviceId(), port.port(), true);
        }
        // cache subscriber info
        programmedSubs.put(port, sub);
        return true;
    }

    @Override
    public boolean removeSubscriber(ConnectPoint port) {
        // Get the subscriber connected to this port from the local cache
        // as if we don't know about the subscriber there's no need to remove it
        SubscriberAndDeviceInformation subscriber = programmedSubs.get(port);
        if (subscriber == null) {
            log.warn("Subscriber on port {} was not previously programmed, no need to remove it", port);
            return true;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(port.deviceId()));
        if (uplinkPort == null) {
            log.warn("No uplink port found for OLT device {}", port.deviceId());
            return false;
        }

        if (enableDhcpOnProvisioning) {
            processDhcpFilteringObjectives(port.deviceId(), port.port(), false,
                    true);
        }

        log.info("Removing programmed vlans for subscriber: {}", subscriber);
        Optional<VlanId> defaultVlan = Optional.empty();
        unprovisionSubscriber(port.deviceId(), uplinkPort.number(), port.port(), subscriber, defaultVlan);

        if (enableIgmpOnProvisioning) {
            processIgmpFilteringObjectives(port.deviceId(), port.port(), false);
        }

        // Remove if there are any flows for the additional Vlans
        Collection<? extends Map.Entry<VlanId, VlanId>> vlansList = additionalVlans.get(port).value();

        // Remove the flows for the additional vlans for this subscriber
        for (Map.Entry<VlanId, VlanId> vlans : vlansList) {
            unprovisionTransparentFlows(port.deviceId(), uplinkPort.number(), port.port(),
                    vlans.getValue(), vlans.getKey());

            // Remove it from the map also
            additionalVlans.remove(port, vlans);
        }

        programmedSubs.remove(port);
        return true;
    }

    @Override
    public boolean provisionSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag, Optional<VlanId> cTag) {
        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint subsPort = findSubscriberConnectPoint(subscriberId.toString());
        if (subsPort == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return provisionSubscriber(subsPort);
        } else if (sTag.isPresent() && cTag.isPresent()) {
            Port uplinkPort = getUplinkPort(deviceService.getDevice(subsPort.deviceId()));
            if (uplinkPort == null) {
                log.warn("No uplink port found for OLT device {}", subsPort.deviceId());
                return false;
            }

            provisionTransparentFlows(subsPort.deviceId(), uplinkPort.number(), subsPort.port(),
                    cTag.get(), sTag.get());
            return true;
        } else {
            log.warn("Provisioning failed for subscriber: {}", subscriberId);
            return false;
        }
    }

    @Override
    public boolean removeSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag, Optional<VlanId> cTag) {
        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint subsPort = findSubscriberConnectPoint(subscriberId.toString());
        if (subsPort == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return removeSubscriber(subsPort);
        } else if (sTag.isPresent() && cTag.isPresent()) {
            // Get the uplink port
            Port uplinkPort = getUplinkPort(deviceService.getDevice(subsPort.deviceId()));
            if (uplinkPort == null) {
                log.warn("No uplink port found for OLT device {}", subsPort.deviceId());
                return false;
            }

            unprovisionTransparentFlows(subsPort.deviceId(), uplinkPort.number(), subsPort.port(),
                    cTag.get(), sTag.get());
            return true;
        } else {
            log.warn("Removing subscriber failed for: {}", subscriberId);
            return false;
        }
    }

    @Override
    public Collection<Map.Entry<ConnectPoint, Map.Entry<VlanId, VlanId>>> getSubscribers() {
        ArrayList<Map.Entry<ConnectPoint, Map.Entry<VlanId, VlanId>>> subs = new ArrayList<>();

        // Get the subscribers for all the devices configured in sadis
        // If the port is UNI, is enabled and exists in Sadis then copy it
        for (Device d : deviceService.getDevices()) {
            if (getOltInfo(d) == null) {
                continue; // not an olt, or not configured in sadis
            }
            for (Port p : deviceService.getPorts(d.id())) {
                if (isUniPort(d, p) && p.isEnabled()) {
                    ConnectPoint cp = new ConnectPoint(d.id(), p.number());

                    SubscriberAndDeviceInformation sub = getSubscriber(cp);
                    if (sub != null) {
                        Map.Entry<VlanId, VlanId> vlans = new AbstractMap.SimpleEntry(sub.sTag(), sub.cTag());
                        subs.add(new AbstractMap.SimpleEntry(cp, vlans));
                    }
                }
            }
        }

        return subs;
    }

    @Override
    public ImmutableMap<ConnectPoint, SubscriberAndDeviceInformation> getProgSubs() {
        return ImmutableMap.copyOf(programmedSubs);
    }

    @Override
    public List<DeviceId> fetchOlts() {
        // look through all the devices and find the ones that are OLTs as per Sadis
        List<DeviceId> olts = new ArrayList<>();
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            if (getOltInfo(d) != null) {
                // So this is indeed an OLT device
                olts.add(d.id());
            }
        }
        return olts;
    }

    /**
     * Finds the connect point to which a subscriber is connected.
     *
     * @param id The id of the subscriber, this is the same ID as in Sadis
     * @return Subscribers ConnectPoint if found else null
     */
    private ConnectPoint findSubscriberConnectPoint(String id) {

        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            for (Port p : deviceService.getPorts(d.id())) {
                log.trace("Comparing {} with {}", p.annotations().value(AnnotationKeys.PORT_NAME), id);
                if (p.annotations().value(AnnotationKeys.PORT_NAME).equals(id)) {
                    log.debug("Found on device {} port {}", d.id(), p.number());
                    return new ConnectPoint(d.id(), p.number());
                }
            }
        }
        return null;
    }

    private BandwidthProfileInformation getBandwidthProfileInformation(String bandwidthProfile) {
        if (bandwidthProfile == null) {
            return null;
        }
        return bpService.get(bandwidthProfile);
    }

    private void unprovisionSubscriber(DeviceId deviceId, PortNumber uplink,
                                       PortNumber subscriberPort, SubscriberAndDeviceInformation subscriber,
                                       Optional<VlanId> defaultVlan) {

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        VlanId deviceVlan = subscriber.sTag();
        VlanId subscriberVlan = subscriber.cTag();

        MeterId upstreamMeterId = bpInfoToMeter.get(subscriber.upstreamBandwidthProfile());
        MeterId downstreamMeterId = bpInfoToMeter.get(subscriber.downstreamBandwidthProfile());

        ForwardingObjective.Builder upFwd = upBuilder(uplink, subscriberPort,
                subscriberVlan, deviceVlan,
                defaultVlan, upstreamMeterId, subscriber.technologyProfileId());
        ForwardingObjective.Builder downFwd = downBuilder(uplink, subscriberPort,
                subscriberVlan, deviceVlan,
                defaultVlan, downstreamMeterId, subscriber.technologyProfileId());

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

    private void provisionVlans(ConnectPoint port, PortNumber uplinkPort, Optional<VlanId> defaultVlan,
                                SubscriberAndDeviceInformation sub) {

        log.info("Provisioning vlans...");

        DeviceId deviceId = port.deviceId();
        PortNumber subscriberPort = port.port();
        VlanId deviceVlan = sub.sTag();
        VlanId subscriberVlan = sub.cTag();
        int techProfId = sub.technologyProfileId();

        BandwidthProfileInformation upstreamBpInfo = getBandwidthProfileInformation(sub.upstreamBandwidthProfile());
        BandwidthProfileInformation downstreamBpInfo = getBandwidthProfileInformation(sub.downstreamBandwidthProfile());

        MeterId usptreamMeterId = createMeter(deviceId, upstreamBpInfo);
        MeterId downstreamMeterId = createMeter(deviceId, downstreamBpInfo);

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        ForwardingObjective.Builder upFwd = upBuilder(uplinkPort, subscriberPort,
                subscriberVlan, deviceVlan,
                defaultVlan, usptreamMeterId, techProfId);

        ForwardingObjective.Builder downFwd = downBuilder(uplinkPort, subscriberPort,
                subscriberVlan, deviceVlan,
                defaultVlan, downstreamMeterId, techProfId);

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

    private MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo) {

        if (bpInfo == null) {
            log.warn("Bandwidth profile information is not found");
            return null;
        }

        MeterId meterId = bpInfoToMeter.get(bpInfo.id());
        if (meterId != null) {
            log.info("Meter is already added. MeterId {}", meterId);
            return meterId;
        }

        List<Band> meterBands = createMeterBands(bpInfo);

        MeterRequest meterRequest = DefaultMeterRequest.builder()
                .withBands(meterBands)
                .withUnit(Meter.Unit.KB_PER_SEC)
                .forDevice(deviceId)
                .fromApp(appId)
                .burst()
                .add();

        Meter meter = meterService.submit(meterRequest);
        bpInfoToMeter.put(bpInfo.id(), meter.id());
        log.info("Meter is created. Meter Id {}", meter.id());
        return meter.id();
    }

    private List<Band> createMeterBands(BandwidthProfileInformation bpInfo) {
        List<Band> meterBands = new ArrayList<>();

        meterBands.add(createMeterBand(bpInfo.committedInformationRate(), bpInfo.committedBurstSize()));
        meterBands.add(createMeterBand(bpInfo.exceededInformationRate(), bpInfo.exceededBurstSize()));
        meterBands.add(createMeterBand(bpInfo.assuredInformationRate(), 0L));

        return meterBands;
    }

    private Band createMeterBand(long rate, Long burst) {
        return DefaultBand.builder()
                .withRate(rate) //already Kbps
                .burstSize(burst) // already Kbits
                .ofType(Band.Type.DROP) // no matter
                .build();
    }

    private ForwardingObjective.Builder downBuilder(PortNumber uplinkPort,
                                                    PortNumber subscriberPort,
                                                    VlanId subscriberVlan,
                                                    VlanId deviceVlan,
                                                    Optional<VlanId> defaultVlan,
                                                    MeterId meterId,
                                                    int techProfId) {
        TrafficSelector downstream = DefaultTrafficSelector.builder()
                .matchVlanId(deviceVlan)
                .matchInPort(uplinkPort)
                .matchInnerVlanId(subscriberVlan)
                .build();

        TrafficTreatment.Builder downstreamTreatmentBuilder = DefaultTrafficTreatment.builder()
                .popVlan()
                .setVlanId(defaultVlan.orElse(VlanId.vlanId((short) this.defaultVlan)))
                .setOutput(subscriberPort);

        if (meterId != null) {
            downstreamTreatmentBuilder.meter(meterId);
        }

        downstreamTreatmentBuilder.writeMetadata(createMetadata(subscriberVlan, techProfId, subscriberPort), 0);

        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(1000)
                .makePermanent()
                .withSelector(downstream)
                .fromApp(appId)
                .withTreatment(downstreamTreatmentBuilder.build());
    }

    private ForwardingObjective.Builder upBuilder(PortNumber uplinkPort,
                                                  PortNumber subscriberPort,
                                                  VlanId subscriberVlan,
                                                  VlanId deviceVlan,
                                                  Optional<VlanId> defaultVlan,
                                                  MeterId meterId,
                                                  int technologyProfileId) {


        VlanId dVlan = defaultVlan.orElse(VlanId.vlanId((short) this.defaultVlan));

        if (subscriberVlan.toShort() == 4096) {
            dVlan = subscriberVlan;
        }

        TrafficSelector upstream = DefaultTrafficSelector.builder()
                .matchVlanId(dVlan)
                .matchInPort(subscriberPort)
                .build();


        TrafficTreatment.Builder upstreamTreatmentBuilder = DefaultTrafficTreatment.builder()
                .pushVlan()
                .setVlanId(subscriberVlan)
                .pushVlan()
                .setVlanId(deviceVlan)
                .setOutput(uplinkPort);

        if (meterId != null) {
            upstreamTreatmentBuilder.meter(meterId);
        }

        upstreamTreatmentBuilder.writeMetadata(createMetadata(deviceVlan, technologyProfileId, uplinkPort), 0L);

        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(1000)
                .makePermanent()
                .withSelector(upstream)
                .fromApp(appId)
                .withTreatment(upstreamTreatmentBuilder.build());
    }

    private void provisionTransparentFlows(DeviceId deviceId, PortNumber uplinkPort,
                                           PortNumber subscriberPort,
                                           VlanId innerVlan,
                                           VlanId outerVlan) {

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        ForwardingObjective.Builder upFwd = transparentUpBuilder(uplinkPort, subscriberPort,
                innerVlan, outerVlan);


        ForwardingObjective.Builder downFwd = transparentDownBuilder(uplinkPort, subscriberPort,
                innerVlan, outerVlan);

        ConnectPoint cp = new ConnectPoint(deviceId, subscriberPort);

        additionalVlans.put(cp, new AbstractMap.SimpleEntry(outerVlan, innerVlan));

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
            if (downStatus != null) {
                log.error("Flow with innervlan {} and outerVlan {} on device {} " +
                                "on port {} failed downstream installation: {}",
                        innerVlan, outerVlan, deviceId, subscriberPort, downStatus);
            } else if (upStatus != null) {
                log.error("Flow with innerVlan {} and outerVlan {} on device {} " +
                                "on port {} failed upstream installation: {}",
                        innerVlan, outerVlan, deviceId, subscriberPort, upStatus);
            }
        }, oltInstallers);

    }

    private ForwardingObjective.Builder transparentDownBuilder(PortNumber uplinkPort,
                                                               PortNumber subscriberPort,
                                                               VlanId innerVlan,
                                                               VlanId outerVlan) {
        TrafficSelector downstream = DefaultTrafficSelector.builder()
                .matchVlanId(outerVlan)
                .matchInPort(uplinkPort)
                .matchInnerVlanId(innerVlan)
                .build();

        TrafficTreatment downstreamTreatment = DefaultTrafficTreatment.builder()
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

    private ForwardingObjective.Builder transparentUpBuilder(PortNumber uplinkPort,
                                                             PortNumber subscriberPort,
                                                             VlanId innerVlan,
                                                             VlanId outerVlan) {
        TrafficSelector upstream = DefaultTrafficSelector.builder()
                .matchVlanId(outerVlan)
                .matchInPort(subscriberPort)
                .matchInnerVlanId(innerVlan)
                .build();

        TrafficTreatment upstreamTreatment = DefaultTrafficTreatment.builder()
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

    private void unprovisionTransparentFlows(DeviceId deviceId, PortNumber uplink,
                                             PortNumber subscriberPort, VlanId innerVlan,
                                             VlanId outerVlan) {

        ConnectPoint cp = new ConnectPoint(deviceId, subscriberPort);

        additionalVlans.remove(cp, new AbstractMap.SimpleEntry(outerVlan, innerVlan));

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        ForwardingObjective.Builder upFwd = transparentUpBuilder(uplink, subscriberPort,
                innerVlan, outerVlan);
        ForwardingObjective.Builder downFwd = transparentDownBuilder(uplink, subscriberPort,
                innerVlan, outerVlan);


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
            if (downStatus != null) {
                log.error("Flow with innerVlan {} and outerVlan {} on device {} " +
                                "on port {} failed downstream uninstallation: {}",
                        innerVlan, outerVlan, deviceId, subscriberPort, downStatus);
            } else if (upStatus != null) {
                log.error("Flow with innerVlan {} and outerVlan {} on device {} " +
                                "on port {} failed upstream uninstallation: {}",
                        innerVlan, outerVlan, deviceId, subscriberPort, upStatus);
            }
        }, oltInstallers);

    }

    private int getDefaultTechProfileId(DeviceId devId, PortNumber portNumber) {
        Port port = deviceService.getPort(devId, portNumber);
        SubscriberAndDeviceInformation info = subsService.get(port.annotations().value(AnnotationKeys.PORT_NAME));
        if (info != null && info.technologyProfileId() != -1) {
            return info.technologyProfileId();
        }
        return defaultTechProfileId;
    }

    private void processEapolFilteringObjectives(DeviceId devId, PortNumber portNumber, boolean install) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        BandwidthProfileInformation defaultBpInfo = getBandwidthProfileInformation(defaultBpId);
        if (defaultBpInfo != null) {
            MeterId meterId = createMeter(devId, defaultBpInfo);
            treatmentBuilder.meter(meterId);
        } else {
            log.warn("Default bandwidth profile is not found. Authentication flow will be installed without meter");
        }

        int techProfileId = getDefaultTechProfileId(devId, portNumber);
        log.debug("Eapol trap flow will be installed using the tech profile id {}", techProfileId);

        //Authentication trap flow uses only tech profile id as write metadata value
        FilteringObjective eapol = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(portNumber))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                .withMeta(treatmentBuilder
                        .writeMetadata((long) techProfileId << 32, 0)
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Eapol filter for {} on {} {}.",
                                devId, portNumber, (install) ? "installed" : "removed");
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("Eapol filter for {} on {} failed {} because {}",
                                devId, portNumber, (install) ? "installation" : "removal",
                                error);
                    }
                });

        flowObjectiveService.filter(devId, eapol);

    }

    /**
     * Installs trap filtering objectives for particular traffic types on an
     * NNI port.
     *
     * @param devId   device ID
     * @param port    port number
     * @param install true to install, false to remove
     */
    private void processNniFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        processLldpFilteringObjective(devId, port, install);
        if (enableDhcpOnProvisioning) {
            processDhcpFilteringObjectives(devId, port, install, false);
        }
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
                        log.info("LLDP filter for device {} on port {} {}.",
                                devId, port, (install) ? "installed" : "removed");
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("LLDP filter for device {} on port {} failed {} because {}",
                                devId, port, (install) ? "installation" : "removal",
                                error);
                    }
                });

        flowObjectiveService.filter(devId, lldp);

    }

    /**
     * Trap dhcp packets to the controller.
     *
     * @param devId    the device identifier
     * @param port     the port for which this trap flow is designated
     * @param install  true to install the flow, false to remove the flow
     * @param upstream true if trapped packets are flowing upstream towards
     *                 server, false if packets are flowing dowstream towards client
     */
    private void processDhcpFilteringObjectives(DeviceId devId, PortNumber port,
                                                boolean install,
                                                boolean upstream) {
        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }

        if (enableDhcpV4) {
            int udpSrc = (upstream) ? 68 : 67;
            int udpDst = (upstream) ? 67 : 68;

            EthType ethType = EthType.EtherType.IPV4.ethType();
            byte protocol = IPv4.PROTOCOL_UDP;

            this.addDhcpFilteringObjectives(devId, port, udpSrc, udpDst, ethType, protocol, install);
        }

        if (enableDhcpV6) {
            int udpSrc = (upstream) ? 547 : 546;
            int udpDst = (upstream) ? 546 : 547;

            EthType ethType = EthType.EtherType.IPV6.ethType();
            byte protocol = IPv6.PROTOCOL_UDP;

            this.addDhcpFilteringObjectives(devId, port, udpSrc, udpDst, ethType, protocol, install);
        }

    }

    private void addDhcpFilteringObjectives(DeviceId devId,
                                            PortNumber port,
                                            int udpSrc,
                                            int udpDst,
                                            EthType ethType,
                                            byte protocol,
                                            boolean install) {

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        FilteringObjective dhcpUpstream = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(ethType))
                .addCondition(Criteria.matchIPProtocol(protocol))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(udpSrc)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(udpDst)))
                .withMeta(DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(10000)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("DHCP {} filter for device {} on port {} {}.",
                                (ethType.equals(EthType.EtherType.IPV4.ethType())) ? "v4" : "v6",
                                devId, port, (install) ? "installed" : "removed");
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("DHCP {} filter for device {} on port {} failed {} because {}",
                                (ethType.equals(EthType.EtherType.IPV4.ethType())) ? "v4" : "v6",
                                devId, port, (install) ? "installation" : "removal",
                                error);
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
                        log.info("Igmp filter for {} on {} {}.",
                                devId, port, (install) ? "installed" : "removed");
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("Igmp filter for {} on {} failed {} because {}.",
                                devId, port, (install) ? "installation" : "removal",
                                error);
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
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        log.debug("checkAndCreateDeviceFlows: deviceInfo {}", deviceInfo);

        if (deviceInfo != null) {
            // This is an OLT device as per Sadis, we create flows for UNI and NNI ports
            for (Port p : deviceService.getPorts(dev.id())) {
                if (isUniPort(dev, p)) {
                    processEapolFilteringObjectives(dev.id(), p.number(), true);
                } else {
                    processNniFilteringObjectives(dev.id(), p.number(), true);
                }
            }
        }
    }


    /**
     * Get the uplink for of the OLT device.
     * <p>
     * This assumes that the OLT has a single uplink port. When more uplink ports need to be supported
     * this logic needs to be changed
     *
     * @param dev Device to look for
     * @return The uplink Port of the OLT
     */
    private Port getUplinkPort(Device dev) {
        // check if this device is provisioned in Sadis
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        log.debug("getUplinkPort: deviceInfo {}", deviceInfo);
        if (deviceInfo == null) {
            log.warn("Device {} is not configured in SADIS .. cannot fetch device"
                    + " info", dev.id());
            return null;
        }
        // Return the port that has been configured as the uplink port of this OLT in Sadis
        for (Port p : deviceService.getPorts(dev.id())) {
            if (p.number().toLong() == deviceInfo.uplinkPort()) {
                log.debug("getUplinkPort: Found port {}", p);
                return p;
            }
        }

        log.debug("getUplinkPort: No uplink port found for OLT {}", dev.id());
        return null;
    }

    /**
     * Return the subscriber on a port.
     *
     * @param cp ConnectPoint on which to find the subscriber
     * @return subscriber if found else null
     */
    SubscriberAndDeviceInformation getSubscriber(ConnectPoint cp) {
        Port port = deviceService.getPort(cp);
        checkNotNull(port, "Invalid connect point");
        String portName = port.annotations().value(AnnotationKeys.PORT_NAME);
        return subsService.get(portName);
    }

    /**
     * Write metadata instruction value (metadata) is 8 bytes.
     *
     * MS 2 bytes: C Tag
     * Next 2 bytes: Technology Profile Id
     * Next 4 bytes: Port number (uni or nni)
     */

    private Long createMetadata(VlanId innerVlan, int techProfileId, PortNumber egressPort) {

        if (techProfileId == -1) {
            techProfileId = DEFAULT_TP_ID;
        }

        return ((long) (innerVlan.id()) << 48 | (long) techProfileId << 32) | egressPort.toLong();
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
        return subsService.get(devSerialNo);
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
                                processEapolFilteringObjectives(devId, event.port().number(), true);
                            }
                        } else {
                            checkAndCreateDeviceFlows(dev);
                        }
                        break;
                    case PORT_REMOVED:
                        if (isUniPort(dev, event.port())) {
                            if (event.port().isEnabled()) {
                                processEapolFilteringObjectives(devId, event.port().number(), false);
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
                            processEapolFilteringObjectives(devId, event.port().number(), true);
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, event.port()));
                        } else {
                            processEapolFilteringObjectives(devId, event.port().number(), false);
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

    private class InternalMeterListener implements MeterListener {

        @Override
        public void event(MeterEvent meterEvent) {
            if (deleteMeters && MeterEvent.Type.METER_REFERENCE_COUNT_ZERO.equals(meterEvent.type())) {
                log.info("Zero Count Meter Event is received. Meter is {}", meterEvent.subject());
                Meter meter = meterEvent.subject();
                if (meter != null && appId.equals(meter.appId())) {
                    deleteMeter(meter.deviceId(), meter.id());
                }
            } else if (MeterEvent.Type.METER_REMOVED.equals(meterEvent.type())) {
                log.info("Meter removed event is received. Meter is {}", meterEvent.subject());
                removeMeterFromBpMap(meterEvent.subject());
            }
        }

        private void deleteMeter(DeviceId deviceId, MeterId meterId) {
            Meter meter = meterService.getMeter(deviceId, meterId);
            MeterRequest meterRequest = DefaultMeterRequest.builder()
                    .withBands(meter.bands())
                    .withUnit(meter.unit())
                    .forDevice(deviceId)
                    .fromApp(appId)
                    .burst()
                    .remove();

            meterService.withdraw(meterRequest, meterId);

        }

        private void removeMeterFromBpMap(Meter meter) {
            for (Map.Entry<String, MeterId> entry : bpInfoToMeter.entrySet()) {
                if (entry.getValue().equals(meter.id())) {
                    bpInfoToMeter.remove(entry.getKey());
                    log.info("Deleted from the internal map. Profile {} and Meter {}", entry.getKey(), meter.id());
                    break;
                }
            }
        }
    }
}