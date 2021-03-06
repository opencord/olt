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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterEvent;
import org.onosproject.cluster.ClusterEventListener;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.ControllerNode;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveContext;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.meter.MeterId;
import org.onosproject.store.primitives.DefaultConsistentMap;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMultimap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDevicePort;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.AccessSubscriberId;
import org.opencord.olt.internalapi.AccessDeviceFlowService;
import org.opencord.olt.internalapi.AccessDeviceMeterService;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.stream.Collectors.*;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions rules on access devices.
 */
@Component(immediate = true,
        property = {
                DEFAULT_BP_ID + ":String=" + DEFAULT_BP_ID_DEFAULT,
                DEFAULT_MCAST_SERVICE_NAME + ":String=" + DEFAULT_MCAST_SERVICE_NAME_DEFAULT,
                EAPOL_DELETE_RETRY_MAX_ATTEMPS + ":Integer=" +
                        EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT,
                PROVISION_DELAY + ":Integer=" + PROVISION_DELAY_DEFAULT,
        })
public class Olt
        extends AbstractListenerManager<AccessDeviceEvent, AccessDeviceListener>
        implements AccessDeviceService {
    private static final String SADIS_NOT_RUNNING = "Sadis is not running.";
    private static final String APP_NAME = "org.opencord.olt";

    private static final short EAPOL_DEFAULT_VLAN = 4091;
    private static final String NO_UPLINK_PORT = "No uplink port found for OLT device {}";

    public static final int HASH_WEIGHT = 10;
    public static final long PENDING_SUBS_MAP_TIMEOUT_MILLIS = 30000L;

    private final Logger log = getLogger(getClass());

    private static final String NNI = "nni-";

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    //Dependency on driver service is to ensure correct startup order
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            bind = "bindSadisService",
            unbind = "unbindSadisService",
            policy = ReferencePolicy.DYNAMIC)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected AccessDeviceFlowService oltFlowService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected AccessDeviceMeterService oltMeterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    /**
     * Default bandwidth profile id that is used for authentication trap flows.
     **/
    protected String defaultBpId = DEFAULT_BP_ID_DEFAULT;

    /**
     * Default multicast service name.
     **/
    protected String multicastServiceName = DEFAULT_MCAST_SERVICE_NAME_DEFAULT;

    /**
     * Default amounts of eapol retry.
     **/
    protected int eapolDeleteRetryMaxAttempts = EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT;

    /**
     * Delay between EAPOL removal and data plane flows provisioning.
     */
    protected int provisionDelay = PROVISION_DELAY_DEFAULT;

    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final ClusterEventListener clusterListener = new InternalClusterListener();
    private final HostListener hostListener = new InternalHostListener();

    private ConsistentHasher hasher;

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private BaseInformationService<BandwidthProfileInformation> bpService;

    private ExecutorService oltInstallers = Executors.newFixedThreadPool(4,
                                                                         groupedThreads("onos/olt-service",
                                                                                        "olt-installer-%d"));

    protected ExecutorService eventExecutor;
    protected ExecutorService hostEventExecutor;
    protected ExecutorService retryExecutor;
    protected ScheduledExecutorService provisionExecutor;

    private ConsistentMultimap<ConnectPoint, UniTagInformation> programmedSubs;
    private ConsistentMultimap<ConnectPoint, UniTagInformation> failedSubs;

    protected Map<DeviceId, BlockingQueue<SubscriberFlowInfo>> pendingSubscribersForDevice;
    private ConsistentMultimap<ConnectPoint, SubscriberFlowInfo> waitingMacSubscribers;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                                                                        "events-%d", log));
        hostEventExecutor = Executors.newFixedThreadPool(8, groupedThreads("onos/olt", "mac-events-%d", log));
        retryExecutor = Executors.newCachedThreadPool();
        provisionExecutor = Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                "provision-%d", log));

        modified(context);
        ApplicationId appId = coreService.registerApplication(APP_NAME);
        componentConfigService.registerProperties(getClass());

        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(UniTagInformation.class)
                .register(SubscriberFlowInfo.class)
                .register(AccessDevicePort.class)
                .register(AccessDevicePort.Type.class)
                .register(LinkedBlockingQueue.class)
                .build();

        programmedSubs = storageService.<ConnectPoint, UniTagInformation>consistentMultimapBuilder()
                .withName("volt-programmed-subs")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .build();

        failedSubs = storageService.<ConnectPoint, UniTagInformation>consistentMultimapBuilder()
                .withName("volt-failed-subs")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .build();

        KryoNamespace macSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(SubscriberFlowInfo.class)
                .register(AccessDevicePort.class)
                .register(AccessDevicePort.Type.class)
                .register(UniTagInformation.class)
                .build();

        waitingMacSubscribers = storageService.<ConnectPoint, SubscriberFlowInfo>consistentMultimapBuilder()
                .withName("volt-waiting-mac-subs")
                .withSerializer(Serializer.using(macSerializer))
                .withApplicationId(appId)
                .build();
        //TODO possibly use consistent multimap with compute on key and element to avoid
        // lock on all objects of the map, while instead obtaining and releasing the lock
        // on a per subscriber basis.
        pendingSubscribersForDevice = new DefaultConsistentMap<>(
                storageService.<DeviceId, BlockingQueue<SubscriberFlowInfo>>consistentMapBuilder()
                .withName("volt-pending-subs")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .buildAsyncMap(), PENDING_SUBS_MAP_TIMEOUT_MILLIS).asJavaMap();
        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);

        if (sadisService != null) {
            subsService = sadisService.getSubscriberInfoService();
            bpService = sadisService.getBandwidthProfileService();
        } else {
            log.warn(SADIS_NOT_RUNNING);
        }

        List<NodeId> readyNodes = clusterService.getNodes().stream()
                .filter(c -> clusterService.getState(c.id()) == ControllerNode.State.READY)
                .map(ControllerNode::id)
                .collect(toList());
        hasher = new ConsistentHasher(readyNodes, HASH_WEIGHT);
        clusterService.addListener(clusterListener);

        // look for all provisioned devices in Sadis and create EAPOL flows for the
        // UNI ports
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            if (isLocalLeader(d.id())) {
                checkAndCreateDeviceFlows(d);
            }
        }

        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        componentConfigService.unregisterProperties(getClass(), false);
        clusterService.removeListener(clusterListener);
        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);
        eventDispatcher.removeSink(AccessDeviceEvent.class);
        eventExecutor.shutdown();
        hostEventExecutor.shutdown();
        retryExecutor.shutdown();
        provisionExecutor.shutdown();
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

        try {
            String bpId = get(properties, DEFAULT_BP_ID);
            defaultBpId = isNullOrEmpty(bpId) ? defaultBpId : bpId;

            String mcastSN = get(properties, DEFAULT_MCAST_SERVICE_NAME);
            multicastServiceName = isNullOrEmpty(mcastSN) ? multicastServiceName : mcastSN;

            String eapolDeleteRetryNew = get(properties, EAPOL_DELETE_RETRY_MAX_ATTEMPS);
            eapolDeleteRetryMaxAttempts = isNullOrEmpty(eapolDeleteRetryNew) ? EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT :
                    Integer.parseInt(eapolDeleteRetryNew.trim());

            log.debug("OLT properties: DefaultBpId: {}, MulticastServiceName: {}, EapolDeleteRetryMaxAttempts: {}",
                      defaultBpId, multicastServiceName, eapolDeleteRetryMaxAttempts);

        } catch (Exception e) {
            log.error("Error while modifying the properties", e);
            defaultBpId = DEFAULT_BP_ID_DEFAULT;
            multicastServiceName = DEFAULT_MCAST_SERVICE_NAME_DEFAULT;
        }
    }

    protected void bindSadisService(SadisService service) {
        sadisService = service;
        bpService = sadisService.getBandwidthProfileService();
        subsService = sadisService.getSubscriberInfoService();
        log.info("Sadis-service binds to onos.");
    }

    protected void unbindSadisService(SadisService service) {
        sadisService = null;
        bpService = null;
        subsService = null;
        log.info("Sadis-service unbinds from onos.");
    }

    @Override
    public boolean provisionSubscriber(ConnectPoint connectPoint) {
        log.info("Call to provision subscriber at {}", connectPoint);
        DeviceId deviceId = connectPoint.deviceId();
        Port subscriberPortOnos = deviceService.getPort(deviceId, connectPoint.port());
        checkNotNull(subscriberPortOnos, "Invalid connect point:" + connectPoint);
        AccessDevicePort subscriberPort = new AccessDevicePort(subscriberPortOnos, AccessDevicePort.Type.UNI);

        if (isSubscriberInstalled(connectPoint)) {
            log.warn("Subscriber at {} already provisioned or in the process .."
                    + " not taking any more action", connectPoint);
            return true;
        }

        // Find the subscriber config at this connect point
        SubscriberAndDeviceInformation sub = getSubscriber(connectPoint);
        if (sub == null) {
            log.warn("No subscriber found for {}", connectPoint);
            return false;
        }

        // Get the uplink port
        AccessDevicePort uplinkPort = getUplinkPort(deviceService.getDevice(deviceId));
        if (uplinkPort == null) {
            log.warn(NO_UPLINK_PORT, deviceId);
            return false;
        }

        // delete Eapol authentication flow with default bandwidth
        // wait until Eapol rule with defaultBpId is removed to install subscriber-based rules
        // retry deletion if it fails/times-out
        retryExecutor.execute(new DeleteEapolInstallSub(subscriberPort, uplinkPort, sub, 1));
        return true;
    }

    // returns true if subscriber is programmed or in the process of being programmed
    private boolean isSubscriberInstalled(ConnectPoint connectPoint) {
        Collection<? extends UniTagInformation> uniTagInformationSet =
                programmedSubs.get(connectPoint).value();
        if (!uniTagInformationSet.isEmpty()) {
            return true;
        }
        //Check if the subscriber is already getting provisioned
        // so we do not provision twice
        AtomicBoolean isPending = new AtomicBoolean(false);
        pendingSubscribersForDevice.computeIfPresent(connectPoint.deviceId(), (id, infos) -> {
            for (SubscriberFlowInfo fi : infos) {
                if (fi.getUniPort().equals(connectPoint.port())) {
                    log.debug("Subscriber is already pending, {}", fi);
                    isPending.set(true);
                    break;
                }
            }
            return infos;
        });

        return isPending.get();
    }

    private class DeleteEapolInstallSub implements Runnable {
        AccessDevicePort subscriberPort;
        AccessDevicePort uplinkPort;
        SubscriberAndDeviceInformation sub;
        private int attemptNumber;

        DeleteEapolInstallSub(AccessDevicePort subscriberPort, AccessDevicePort uplinkPort,
                              SubscriberAndDeviceInformation sub,
                              int attemptNumber) {
            this.subscriberPort = subscriberPort;
            this.uplinkPort = uplinkPort;
            this.sub = sub;
            this.attemptNumber = attemptNumber;
        }

        @Override
        public void run() {
            CompletableFuture<ObjectiveError> filterFuture = new CompletableFuture<>();
            oltFlowService.processEapolFilteringObjectives(subscriberPort,
                                                     defaultBpId, Optional.empty(), filterFuture,
                                                     VlanId.vlanId(EAPOL_DEFAULT_VLAN),
                                                     false);
            filterFuture.thenAcceptAsync(filterStatus -> {
                if (filterStatus == null) {
                    log.info("Default eapol flow deleted in attempt {} of {}"
                            + "... provisioning subscriber flows on {}",
                            attemptNumber, eapolDeleteRetryMaxAttempts, subscriberPort);

                    // FIXME this is needed to prevent that default EAPOL flow removal and
                    // data plane flows install are received by the device at the same time
                    provisionExecutor.schedule(
                            () -> provisionUniTagList(subscriberPort, uplinkPort, sub),
                            provisionDelay, TimeUnit.MILLISECONDS);
                } else {
                    if (attemptNumber <= eapolDeleteRetryMaxAttempts) {
                        log.warn("The filtering future failed {} for subscriber on {}"
                                + "... retrying {} of {} attempts",
                                 filterStatus, subscriberPort, attemptNumber, eapolDeleteRetryMaxAttempts);
                        retryExecutor.execute(
                                         new DeleteEapolInstallSub(subscriberPort, uplinkPort, sub,
                                                                   attemptNumber + 1));
                    } else {
                        log.error("The filtering future failed {} for subscriber on {}"
                                + "after {} attempts. Subscriber provisioning failed",
                                  filterStatus, subscriberPort, eapolDeleteRetryMaxAttempts);
                        sub.uniTagList().forEach(ut ->
                                failedSubs.put(
                                        new ConnectPoint(subscriberPort.deviceId(), subscriberPort.number()), ut));
                    }
                }
            });
        }

    }

    @Override
    public boolean removeSubscriber(ConnectPoint connectPoint) {
        Port subscriberPort = deviceService.getPort(connectPoint);
        if (subscriberPort == null) {
            log.error("Subscriber port not found at: {}", connectPoint);
            return false;
        }
        return removeSubscriber(new AccessDevicePort(subscriberPort, AccessDevicePort.Type.UNI));
    }

    private boolean removeSubscriber(AccessDevicePort subscriberPort) {
        log.info("Call to un-provision subscriber at {}", subscriberPort);
        //TODO we need to check if the subscriber is pending
        // Get the subscriber connected to this port from the local cache
        // If we don't know about the subscriber there's no need to remove it
        DeviceId deviceId = subscriberPort.deviceId();

        ConnectPoint connectPoint = new ConnectPoint(deviceId, subscriberPort.number());
        Collection<? extends UniTagInformation> uniTagInformationSet = programmedSubs.get(connectPoint).value();
        if (uniTagInformationSet == null || uniTagInformationSet.isEmpty()) {
            log.warn("Subscriber on connectionPoint {} was not previously programmed, " +
                             "no need to remove it", connectPoint);
            return true;
        }

        // Get the uplink port
        AccessDevicePort uplinkPort = getUplinkPort(deviceService.getDevice(deviceId));
        if (uplinkPort == null) {
            log.warn(NO_UPLINK_PORT, deviceId);
            return false;
        }

        for (UniTagInformation uniTag : uniTagInformationSet) {

            if (multicastServiceName.equals(uniTag.getServiceName())) {
                continue;
            }

            unprovisionVlans(uplinkPort, subscriberPort, uniTag);

            // remove eapol with subscriber bandwidth profile
            Optional<String> upstreamOltBw = uniTag.getUpstreamOltBandwidthProfile() == null ?
                    Optional.empty() : Optional.of(uniTag.getUpstreamOltBandwidthProfile());
            oltFlowService.processEapolFilteringObjectives(subscriberPort,
                                                           uniTag.getUpstreamBandwidthProfile(),
                                                           upstreamOltBw,
                                                           null, uniTag.getPonCTag(), false);

            if (subscriberPort.port() != null && subscriberPort.isEnabled()) {
                // reinstall eapol with default bandwidth profile
                oltFlowService.processEapolFilteringObjectives(subscriberPort, defaultBpId, Optional.empty(),
                        null, VlanId.vlanId(EAPOL_DEFAULT_VLAN), true);
            } else {
                log.debug("Port {} is no longer enabled or it's unavailable. Not "
                                  + "reprogramming default eapol flow", connectPoint);
            }
        }
        return true;
    }


    @Override
    public boolean provisionSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag,
                                       Optional<VlanId> cTag, Optional<Integer> tpId) {

        log.info("Provisioning subscriber using subscriberId {}, sTag {}, cTag {}, tpId {}",
                subscriberId, sTag, cTag, tpId);

        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint cp = findSubscriberConnectPoint(subscriberId.toString());
        if (cp == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }
        AccessDevicePort subscriberPort = new AccessDevicePort(deviceService.getPort(cp), AccessDevicePort.Type.UNI);

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return provisionSubscriber(cp);
        } else if (sTag.isPresent() && cTag.isPresent() && tpId.isPresent()) {
            AccessDevicePort uplinkPort = getUplinkPort(deviceService.getDevice(cp.deviceId()));
            if (uplinkPort == null) {
                log.warn(NO_UPLINK_PORT, cp.deviceId());
                return false;
            }

            //delete Eapol authentication flow with default bandwidth
            //wait until Eapol rule with defaultBpId is removed to install subscriber-based rules
            //install subscriber flows
            CompletableFuture<ObjectiveError> filterFuture = new CompletableFuture<>();
            oltFlowService.processEapolFilteringObjectives(subscriberPort, defaultBpId, Optional.empty(),
                    filterFuture, VlanId.vlanId(EAPOL_DEFAULT_VLAN), false);
            filterFuture.thenAcceptAsync(filterStatus -> {
                if (filterStatus == null) {
                    provisionUniTagInformation(uplinkPort, subscriberPort, cTag.get(), sTag.get(), tpId.get());
                }
            });
            return true;
        } else {
            log.warn("Provisioning failed for subscriber: {}", subscriberId);
            return false;
        }
    }

    @Override
    public boolean removeSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag,
                                    Optional<VlanId> cTag, Optional<Integer> tpId) {
        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint cp = findSubscriberConnectPoint(subscriberId.toString());
        if (cp == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }
        AccessDevicePort subscriberPort = new AccessDevicePort(deviceService.getPort(cp), AccessDevicePort.Type.UNI);

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return removeSubscriber(cp);
        } else if (sTag.isPresent() && cTag.isPresent() && tpId.isPresent()) {
            // Get the uplink port
            AccessDevicePort uplinkPort = getUplinkPort(deviceService.getDevice(cp.deviceId()));
            if (uplinkPort == null) {
                log.warn(NO_UPLINK_PORT, cp.deviceId());
                return false;
            }

            Optional<UniTagInformation> tagInfo = getUniTagInformation(subscriberPort, cTag.get(),
                    sTag.get(), tpId.get());
            if (!tagInfo.isPresent()) {
                log.warn("UniTagInformation does not exist for {}, cTag {}, sTag {}, tpId {}",
                        subscriberPort, cTag, sTag, tpId);
                return false;
            }

            unprovisionVlans(uplinkPort, subscriberPort, tagInfo.get());
            return true;
        } else {
            log.warn("Removing subscriber is not possible - please check the provided information" +
                             "for the subscriber: {}", subscriberId);
            return false;
        }
    }

    @Override
    public ImmutableMap<ConnectPoint, Set<UniTagInformation>> getProgSubs() {
        return programmedSubs.stream()
                .collect(collectingAndThen(
                        groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toSet())),
                        ImmutableMap::copyOf));
    }

    @Override
    public ImmutableMap<ConnectPoint, Set<UniTagInformation>> getFailedSubs() {
        return failedSubs.stream()
                .collect(collectingAndThen(
                        groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toSet())),
                        ImmutableMap::copyOf));
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

    /**
     * Gets the context of the bandwidth profile information for the given parameter.
     *
     * @param bandwidthProfile the bandwidth profile id
     * @return the context of the bandwidth profile information
     */
    private BandwidthProfileInformation getBandwidthProfileInformation(String bandwidthProfile) {
        if (bpService == null) {
            log.warn(SADIS_NOT_RUNNING);
            return null;
        }
        if (bandwidthProfile == null) {
            return null;
        }
        return bpService.get(bandwidthProfile);
    }

    /**
     * Removes subscriber vlan flows.
     *
     * @param uplink         uplink port of the OLT
     * @param subscriberPort uni port
     * @param uniTag         uni tag information
     */
    private void unprovisionVlans(AccessDevicePort uplink, AccessDevicePort subscriberPort, UniTagInformation uniTag) {
        log.info("Unprovisioning vlans for {} at {}", uniTag, subscriberPort);
        DeviceId deviceId = subscriberPort.deviceId();

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture<>();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture<>();

        VlanId deviceVlan = uniTag.getPonSTag();
        VlanId subscriberVlan = uniTag.getPonCTag();

        MeterId upstreamMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getUpstreamBandwidthProfile());
        MeterId downstreamMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getDownstreamBandwidthProfile());
        MeterId upstreamOltMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getUpstreamOltBandwidthProfile());
        MeterId downstreamOltMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getDownstreamOltBandwidthProfile());

        Optional<SubscriberFlowInfo> waitingMacSubFlowInfo =
                getAndRemoveWaitingMacSubFlowInfoForCTag(new ConnectPoint(deviceId, subscriberPort.number()),
                        subscriberVlan);
        if (waitingMacSubFlowInfo.isPresent()) {
            // only dhcp objectives applied previously, so only dhcp uninstallation objective will be processed
            log.debug("Waiting MAC service removed and dhcp uninstallation objective will be processed. " +
                    "waitingMacSubFlowInfo:{}", waitingMacSubFlowInfo.get());
            CompletableFuture<ObjectiveError> dhcpFuture = new CompletableFuture<>();
            oltFlowService.processDhcpFilteringObjectives(subscriberPort,
                    upstreamMeterId, upstreamOltMeterId, uniTag, false, true, Optional.of(dhcpFuture));
            dhcpFuture.thenAcceptAsync(dhcpStatus -> {
                AccessDeviceEvent.Type type;
                if (dhcpStatus == null) {
                    type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTERED;
                    log.debug("Dhcp uninstallation objective was processed successfully for cTag {}, sTag {}, " +
                                    "tpId {} and Device/Port:{}", uniTag.getPonCTag(), uniTag.getPonSTag(),
                            uniTag.getTechnologyProfileId(), subscriberPort);
                    updateProgrammedSubscriber(subscriberPort, uniTag, false);
                } else {
                    type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED;
                    log.error("Dhcp uninstallation objective was failed for cTag {}, sTag {}, " +
                                    "tpId {} and Device/Port:{} :{}", uniTag.getPonCTag(), uniTag.getPonSTag(),
                            uniTag.getTechnologyProfileId(), subscriberPort, dhcpStatus);
                }
                post(new AccessDeviceEvent(type, deviceId, subscriberPort.port(),
                        deviceVlan, subscriberVlan, uniTag.getTechnologyProfileId()));
            });
            return;
        } else {
            log.debug("There is no waiting MAC service for {} and subscriberVlan: {}", subscriberPort, subscriberVlan);
        }

        ForwardingObjective.Builder upFwd =
                oltFlowService.createUpBuilder(uplink, subscriberPort, upstreamMeterId, upstreamOltMeterId, uniTag);

        Optional<MacAddress> macAddress = getMacAddress(deviceId, subscriberPort, uniTag);
        ForwardingObjective.Builder downFwd =
                oltFlowService.createDownBuilder(uplink, subscriberPort, downstreamMeterId, downstreamOltMeterId,
                        uniTag, macAddress);

        oltFlowService.processIgmpFilteringObjectives(subscriberPort, upstreamMeterId, upstreamOltMeterId, uniTag,
                false, true);
        oltFlowService.processDhcpFilteringObjectives(subscriberPort,
                upstreamMeterId, upstreamOltMeterId, uniTag, false, true, Optional.empty());
        oltFlowService.processPPPoEDFilteringObjectives(subscriberPort, upstreamMeterId, upstreamOltMeterId, uniTag,
                false, true);

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
            AccessDeviceEvent.Type type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTERED;
            if (upStatus == null && downStatus == null) {
                log.debug("Uni tag information is unregistered successfully for cTag {}, sTag {}, tpId {} on {}",
                        uniTag.getPonCTag(), uniTag.getPonSTag(), uniTag.getTechnologyProfileId(), subscriberPort);
                updateProgrammedSubscriber(subscriberPort, uniTag, false);
            } else if (downStatus != null) {
                log.error("Subscriber with vlan {} on {} failed downstream uninstallation: {}",
                          subscriberVlan, subscriberPort, downStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED;
            } else if (upStatus != null) {
                log.error("Subscriber with vlan {} on {} failed upstream uninstallation: {}",
                          subscriberVlan, subscriberPort, upStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED;
            }
            post(new AccessDeviceEvent(type, deviceId, subscriberPort.port(), deviceVlan, subscriberVlan,
                                       uniTag.getTechnologyProfileId()));
        }, oltInstallers);
    }

    private Optional<SubscriberFlowInfo> getAndRemoveWaitingMacSubFlowInfoForCTag(ConnectPoint cp, VlanId cTag) {
        SubscriberFlowInfo returnSubFlowInfo = null;
        Collection<? extends SubscriberFlowInfo> subFlowInfoSet = waitingMacSubscribers.get(cp).value();
        for (SubscriberFlowInfo subFlowInfo : subFlowInfoSet) {
            if (subFlowInfo.getTagInfo().getPonCTag().equals(cTag)) {
                returnSubFlowInfo = subFlowInfo;
                break;
            }
        }
        if (returnSubFlowInfo != null) {
            waitingMacSubscribers.remove(cp, returnSubFlowInfo);
            return Optional.of(returnSubFlowInfo);
        }
        return Optional.empty();
    }

    /**
     * Adds subscriber vlan flows, dhcp, eapol and igmp trap flows for the related uni port.
     *
     * @param subPort      the connection point of the subscriber
     * @param uplinkPort   uplink port of the OLT (the nni port)
     * @param sub          subscriber information that includes s, c tags, tech profile and bandwidth profile references
     */
    private void provisionUniTagList(AccessDevicePort subPort, AccessDevicePort uplinkPort,
                                     SubscriberAndDeviceInformation sub) {

        log.debug("Provisioning vlans for subscriber on {}", subPort);
        if (log.isTraceEnabled()) {
            log.trace("Subscriber informations {}", sub);
        }

        if (sub.uniTagList() == null || sub.uniTagList().isEmpty()) {
            log.warn("Unitaglist doesn't exist for the subscriber {} on {}", sub.id(), subPort);
            return;
        }

        for (UniTagInformation uniTag : sub.uniTagList()) {
            handleSubscriberFlows(uplinkPort, subPort, uniTag);
        }
    }

    /**
     * Finds the uni tag information and provisions the found information.
     * If the uni tag information is not found, returns
     *
     * @param uplinkPort     the nni port
     * @param subscriberPort the uni port
     * @param innerVlan      the pon c tag
     * @param outerVlan      the pon s tag
     * @param tpId           the technology profile id
     */
    private void provisionUniTagInformation(AccessDevicePort uplinkPort,
                                            AccessDevicePort subscriberPort,
                                            VlanId innerVlan,
                                            VlanId outerVlan,
                                            Integer tpId) {

        Optional<UniTagInformation> gotTagInformation = getUniTagInformation(subscriberPort, innerVlan,
                outerVlan, tpId);
        if (!gotTagInformation.isPresent()) {
            return;
        }
        UniTagInformation tagInformation = gotTagInformation.get();
        handleSubscriberFlows(uplinkPort, subscriberPort, tagInformation);
    }

    private void updateProgrammedSubscriber(AccessDevicePort port, UniTagInformation tagInformation, boolean add) {
        if (add) {
            programmedSubs.put(new ConnectPoint(port.deviceId(), port.number()), tagInformation);
        } else {
            programmedSubs.remove(new ConnectPoint(port.deviceId(), port.number()), tagInformation);
        }
    }

    /**
     * Installs a uni tag information flow.
     *
     * @param uplinkPort     the nni port
     * @param subscriberPort the uni port
     * @param tagInfo        the uni tag information
     */
    private void handleSubscriberFlows(AccessDevicePort uplinkPort, AccessDevicePort subscriberPort,
                                       UniTagInformation tagInfo) {
        log.debug("Provisioning vlan-based flows for the uniTagInformation {} on {}", tagInfo, subscriberPort);
        DeviceId deviceId = subscriberPort.deviceId();

        if (multicastServiceName.equals(tagInfo.getServiceName())) {
            // IGMP flows are taken care of along with VOD service
            // Please note that for each service, Subscriber Registered event will be sent
            post(new AccessDeviceEvent(AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTERED, deviceId,
                    subscriberPort.port(), tagInfo.getPonSTag(), tagInfo.getPonCTag(),
                    tagInfo.getTechnologyProfileId()));
            return;
        }

        BandwidthProfileInformation upstreamBpInfo =
                getBandwidthProfileInformation(tagInfo.getUpstreamBandwidthProfile());
        BandwidthProfileInformation downstreamBpInfo =
                getBandwidthProfileInformation(tagInfo.getDownstreamBandwidthProfile());
        BandwidthProfileInformation upstreamOltBpInfo =
                getBandwidthProfileInformation(tagInfo.getUpstreamOltBandwidthProfile());
        BandwidthProfileInformation downstreamOltBpInfo =
                getBandwidthProfileInformation(tagInfo.getDownstreamOltBandwidthProfile());
        if (upstreamBpInfo == null) {
            log.warn("No meter installed since no Upstream BW Profile definition found for "
                             + "ctag {} stag {} tpId {} on {}",
                     tagInfo.getPonCTag(), tagInfo.getPonSTag(), tagInfo.getTechnologyProfileId(), subscriberPort);
            return;
        }
        if (downstreamBpInfo == null) {
            log.warn("No meter installed since no Downstream BW Profile definition found for "
                             + "ctag {} stag {} tpId {} on {}",
                     tagInfo.getPonCTag(), tagInfo.getPonSTag(),
                     tagInfo.getTechnologyProfileId(), subscriberPort);
            return;
        }
        if ((upstreamOltBpInfo != null && downstreamOltBpInfo == null) ||
                (upstreamOltBpInfo == null && downstreamOltBpInfo != null)) {
            log.warn("No meter installed since only one olt BW Profile definition found for "
                            + "ctag {} stag {} tpId {} and Device/port: {}:{}",
                    tagInfo.getPonCTag(), tagInfo.getPonSTag(),
                    tagInfo.getTechnologyProfileId(), deviceId,
                    subscriberPort);
            return;
        }

        MeterId upOltMeterId = null;
        MeterId downOltMeterId = null;

        // check for meterIds for the upstream and downstream bandwidth profiles
        MeterId upMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, upstreamBpInfo.id());
        MeterId downMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, downstreamBpInfo.id());

        if (upstreamOltBpInfo != null) {
            // Multi UNI service
            upOltMeterId = oltMeterService
                    .getMeterIdFromBpMapping(deviceId, upstreamOltBpInfo.id());
            downOltMeterId = oltMeterService
                    .getMeterIdFromBpMapping(deviceId, downstreamOltBpInfo.id());
        } else {
            // NOT Multi UNI service
            log.debug("OLT bandwidth profiles fields are set to ONU bandwidth profiles");
            upstreamOltBpInfo = upstreamBpInfo;
            downstreamOltBpInfo = downstreamBpInfo;
            upOltMeterId = upMeterId;
            downOltMeterId = downMeterId;
        }
        SubscriberFlowInfo fi = new SubscriberFlowInfo(uplinkPort, subscriberPort,
                                                       tagInfo, downMeterId, upMeterId, downOltMeterId, upOltMeterId,
                                                       downstreamBpInfo.id(), upstreamBpInfo.id(),
                                                       downstreamOltBpInfo.id(), upstreamOltBpInfo.id());

        if (upMeterId != null && downMeterId != null && upOltMeterId != null && downOltMeterId != null) {
            log.debug("Meters are existing for upstream {} and downstream {} on {}",
                    upstreamBpInfo.id(), downstreamBpInfo.id(), subscriberPort);
            handleSubFlowsWithMeters(fi);
        } else {
            log.debug("Adding {} on {} to pending subs", fi, subscriberPort);
            // one or both meters are not ready. It's possible they are in the process of being
            // created for other subscribers that share the same bandwidth profile.
            pendingSubscribersForDevice.compute(deviceId, (id, queue) -> {
                if (queue == null) {
                    queue = new LinkedBlockingQueue<>();
                }
                queue.add(fi);
                log.info("Added {} to pending subscribers on {}", fi, subscriberPort);
                return queue;
            });

            List<BandwidthProfileInformation> bws = new ArrayList<>();
            // queue up the meters to be created
            if (upMeterId == null) {
                log.debug("Missing meter for upstream {} on {}", upstreamBpInfo.id(), subscriberPort);
                bws.add(upstreamBpInfo);
            }
            if (downMeterId == null) {
                log.debug("Missing meter for downstream {} on {}", downstreamBpInfo.id(), subscriberPort);
                bws.add(downstreamBpInfo);
            }
            if (upOltMeterId == null) {
                log.debug("Missing meter for upstreamOlt {} on {}", upstreamOltBpInfo.id(), subscriberPort);
                bws.add(upstreamOltBpInfo);
            }
            if (downOltMeterId == null) {
                log.debug("Missing meter for downstreamOlt {} on {}", downstreamOltBpInfo.id(), subscriberPort);
                bws.add(downstreamOltBpInfo);
            }
            bws.stream().distinct().forEach(bw -> checkAndCreateDevMeter(deviceId, bw));
        }
    }

    private void checkAndCreateDevMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        log.debug("Checking and Creating Meter with {} on {}", bwpInfo, deviceId);
        if (bwpInfo == null) {
            log.error("Can't create meter. Bandwidth profile is null for device : {}", deviceId);
            return;
        }
        //If false the meter is already being installed, skipping installation
        if (!oltMeterService.checkAndAddPendingMeter(deviceId, bwpInfo)) {
            log.debug("Meter is already being installed on {} for {}", deviceId, bwpInfo);
            return;
        }
        createMeter(deviceId, bwpInfo);
    }

    private void createMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        log.info("Creating Meter with {} on {}", bwpInfo, deviceId);
        CompletableFuture<Object> meterFuture = new CompletableFuture<>();

        MeterId meterId = oltMeterService.createMeter(deviceId, bwpInfo,
                                                      meterFuture);

        meterFuture.thenAcceptAsync(result -> {
            log.info("Meter Future for {} has completed", meterId);
            pendingSubscribersForDevice.compute(deviceId, (id, queue) -> {
                // iterate through the subscribers on hold
                if (queue != null && !queue.isEmpty()) {
                    while (true) {
                        //TODO this might return the reference and not the actual object so
                        // it can be actually swapped underneath us.
                        SubscriberFlowInfo fi = queue.peek();
                        if (fi == null) {
                            log.info("No more subscribers pending on {}", deviceId);
                            queue = new LinkedBlockingQueue<>();
                            break;
                        }
                        if (result == null) {
                            // meter install sent to device
                            log.debug("Meter {} installed for bw {} on {}", meterId, bwpInfo, deviceId);

                            MeterId upMeterId = oltMeterService
                                    .getMeterIdFromBpMapping(deviceId, fi.getUpBpInfo());
                            MeterId downMeterId = oltMeterService
                                    .getMeterIdFromBpMapping(deviceId, fi.getDownBpInfo());
                            MeterId upOltMeterId = oltMeterService
                                    .getMeterIdFromBpMapping(deviceId, fi.getUpOltBpInfo());
                            MeterId downOltMeterId = oltMeterService
                                    .getMeterIdFromBpMapping(deviceId, fi.getDownOltBpInfo());
                            if (upMeterId != null && downMeterId != null &&
                                    upOltMeterId != null && downOltMeterId != null) {
                                log.debug("Provisioning subscriber after meter {} " +
                                                  "installation and all meters are present " +
                                                  "upstream {} , downstream {} , oltUpstream {} " +
                                                  "and oltDownstream {} on {}",
                                          meterId, upMeterId, downMeterId, upOltMeterId,
                                          downOltMeterId, fi.getUniPort());
                                // put in the meterIds  because when fi was first
                                // created there may or may not have been a meterId
                                // depending on whether the meter was created or
                                // not at that time.
                                //TODO possibly spawn this in a separate thread.
                                fi.setUpMeterId(upMeterId);
                                fi.setDownMeterId(downMeterId);
                                fi.setUpOltMeterId(upOltMeterId);
                                fi.setDownOltMeterId(downOltMeterId);
                                handleSubFlowsWithMeters(fi);
                                queue.remove(fi);
                            } else {
                                log.debug("Not all meters for {} are yet installed up {}, " +
                                                 "down {}, oltUp {}, oltDown {}", fi, upMeterId,
                                         downMeterId, upOltMeterId, downOltMeterId);
                            }
                            oltMeterService.removeFromPendingMeters(deviceId, bwpInfo);
                        } else {
                            // meter install failed
                            log.error("Addition of subscriber {} on {} failed due to meter " +
                                              "{} with result {}", fi, fi.getUniPort(), meterId, result);
                            oltMeterService.removeFromPendingMeters(deviceId, bwpInfo);
                            queue.remove(fi);
                        }
                    }
                } else {
                    log.info("No pending subscribers on {}", deviceId);
                    queue = new LinkedBlockingQueue<>();
                }
                return queue;
            });
        });

    }

    /**
     * Add subscriber flows given meter information for both upstream and
     * downstream directions.
     *
     * @param subscriberFlowInfo relevant information for subscriber
     */
    private void handleSubFlowsWithMeters(SubscriberFlowInfo subscriberFlowInfo) {
        log.info("Provisioning subscriber flows based on {}", subscriberFlowInfo);
        UniTagInformation tagInfo = subscriberFlowInfo.getTagInfo();
        if (tagInfo.getIsDhcpRequired()) {
            Optional<MacAddress> macAddress =
                    getMacAddress(subscriberFlowInfo.getDevId(), subscriberFlowInfo.getUniPort(), tagInfo);
            if (subscriberFlowInfo.getTagInfo().getEnableMacLearning()) {
                ConnectPoint cp = new ConnectPoint(subscriberFlowInfo.getDevId(),
                        subscriberFlowInfo.getUniPort().number());
                if (macAddress.isPresent()) {
                    log.debug("MAC Address {} obtained for {}", macAddress.get(), subscriberFlowInfo);
                } else {
                    waitingMacSubscribers.put(cp, subscriberFlowInfo);
                    log.debug("Adding sub to waiting mac map: {}", subscriberFlowInfo);
                }

                CompletableFuture<ObjectiveError> dhcpFuture = new CompletableFuture<>();
                oltFlowService.processDhcpFilteringObjectives(subscriberFlowInfo.getUniPort(),
                        subscriberFlowInfo.getUpId(), subscriberFlowInfo.getUpOltId(),
                        tagInfo, true, true, Optional.of(dhcpFuture));
                dhcpFuture.thenAcceptAsync(dhcpStatus -> {
                    if (dhcpStatus != null) {
                        log.error("Dhcp Objective failed for {}: {}", subscriberFlowInfo, dhcpStatus);
                        if (macAddress.isEmpty()) {
                            waitingMacSubscribers.remove(cp, subscriberFlowInfo);
                        }
                        post(new AccessDeviceEvent(AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED,
                                subscriberFlowInfo.getDevId(), subscriberFlowInfo.getUniPort().port(),
                                tagInfo.getPonSTag(), tagInfo.getPonCTag(), tagInfo.getTechnologyProfileId()));
                    } else {
                        log.debug("Dhcp Objective success for: {}", subscriberFlowInfo);
                        if (macAddress.isPresent()) {
                            continueProvisioningSubs(subscriberFlowInfo, macAddress);
                        }
                    }
                });
            } else {
                log.debug("Dynamic MAC Learning disabled, so will not learn for: {}", subscriberFlowInfo);
                // dhcp flows will handle after data plane flows
                continueProvisioningSubs(subscriberFlowInfo, macAddress);
            }
        } else {
            // dhcp not required for this service
            continueProvisioningSubs(subscriberFlowInfo, Optional.empty());
        }
    }

    private void continueProvisioningSubs(SubscriberFlowInfo subscriberFlowInfo, Optional<MacAddress> macAddress) {
        AccessDevicePort uniPort = subscriberFlowInfo.getUniPort();
        log.debug("Provisioning subscriber flows on {} based on {}", uniPort, subscriberFlowInfo);
        UniTagInformation tagInfo = subscriberFlowInfo.getTagInfo();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture<>();
        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture<>();

        ForwardingObjective.Builder upFwd =
                oltFlowService.createUpBuilder(subscriberFlowInfo.getNniPort(), uniPort, subscriberFlowInfo.getUpId(),
                        subscriberFlowInfo.getUpOltId(), subscriberFlowInfo.getTagInfo());
        flowObjectiveService.forward(subscriberFlowInfo.getDevId(), upFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.debug("Upstream HSIA flow {} installed successfully on {}", subscriberFlowInfo, uniPort);
                upFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                upFuture.complete(error);
            }
        }));

        ForwardingObjective.Builder downFwd =
                oltFlowService.createDownBuilder(subscriberFlowInfo.getNniPort(), uniPort,
                        subscriberFlowInfo.getDownId(), subscriberFlowInfo.getDownOltId(),
                        subscriberFlowInfo.getTagInfo(), macAddress);
        flowObjectiveService.forward(subscriberFlowInfo.getDevId(), downFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.debug("Downstream HSIA flow {} installed successfully on {}", subscriberFlowInfo, uniPort);
                downFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                downFuture.complete(error);
            }
        }));

        upFuture.thenAcceptBothAsync(downFuture, (upStatus, downStatus) -> {
            AccessDeviceEvent.Type type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTERED;
            if (downStatus != null) {
                log.error("Flow with innervlan {} and outerVlan {} on {} failed downstream installation: {}",
                        tagInfo.getPonCTag(), tagInfo.getPonSTag(), uniPort, downStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED;
            } else if (upStatus != null) {
                log.error("Flow with innervlan {} and outerVlan {} on {} failed upstream installation: {}",
                        tagInfo.getPonCTag(), tagInfo.getPonSTag(), uniPort, upStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED;
            } else {
                log.debug("Upstream and downstream data plane flows are installed successfully on {}", uniPort);
                Optional<String> upstreamOltBw = tagInfo.getUpstreamOltBandwidthProfile() == null ?
                        Optional.empty() : Optional.of(tagInfo.getUpstreamOltBandwidthProfile());
                oltFlowService.processEapolFilteringObjectives(uniPort, tagInfo.getUpstreamBandwidthProfile(),
                                                               upstreamOltBw, null,
                        tagInfo.getPonCTag(), true);

                if (!tagInfo.getEnableMacLearning()) {
                    oltFlowService.processDhcpFilteringObjectives(uniPort, subscriberFlowInfo.getUpId(),
                            subscriberFlowInfo.getUpOltId(), tagInfo, true, true, Optional.empty());
                }

                oltFlowService.processIgmpFilteringObjectives(uniPort, subscriberFlowInfo.getUpId(),
                        subscriberFlowInfo.getUpOltId(), tagInfo, true, true);

                oltFlowService.processPPPoEDFilteringObjectives(uniPort, subscriberFlowInfo.getUpId(),
                        subscriberFlowInfo.getUpOltId(), tagInfo, true, true);

                updateProgrammedSubscriber(uniPort, tagInfo, true);
            }
            post(new AccessDeviceEvent(type, subscriberFlowInfo.getDevId(), uniPort.port(),
                                       tagInfo.getPonSTag(), tagInfo.getPonCTag(),
                                       tagInfo.getTechnologyProfileId()));
        }, oltInstallers);
    }

    /**
     * Gets mac address from tag info if present, else checks the host service.
     *
     * @param deviceId device ID
     * @param port uni port
     * @param tagInformation tag info
     * @return MAC Address of subscriber
     */
    private Optional<MacAddress> getMacAddress(DeviceId deviceId, AccessDevicePort port,
                                               UniTagInformation tagInformation) {
        if (isMacAddressValid(tagInformation)) {
            log.debug("Got MAC Address {} from the uniTagInformation for {} and cTag {}",
                    tagInformation.getConfiguredMacAddress(), port, tagInformation.getPonCTag());
            return Optional.of(MacAddress.valueOf(tagInformation.getConfiguredMacAddress()));
        } else if (tagInformation.getEnableMacLearning()) {
            Optional<Host> optHost = hostService.getConnectedHosts(new ConnectPoint(deviceId, port.number()))
                    .stream().filter(host -> host.vlan().equals(tagInformation.getPonCTag())).findFirst();
            if (optHost.isPresent()) {
                log.debug("Got MAC Address {} from the hostService for {} and cTag {}",
                        optHost.get().mac(), port, tagInformation.getPonCTag());
                return Optional.of(optHost.get().mac());
            }
        }
        log.debug("Could not obtain MAC Address for {} and cTag {}", port, tagInformation.getPonCTag());
        return Optional.empty();
    }

    private boolean isMacAddressValid(UniTagInformation tagInformation) {
        return tagInformation.getConfiguredMacAddress() != null &&
                !tagInformation.getConfiguredMacAddress().trim().equals("") &&
                !MacAddress.NONE.equals(MacAddress.valueOf(tagInformation.getConfiguredMacAddress()));
    }

    /**
     * Checks the subscriber uni tag list and find the uni tag information.
     * using the pon c tag, pon s tag and the technology profile id
     * May return Optional<null>
     *
     * @param port        port of the subscriber
     * @param innerVlan pon c tag
     * @param outerVlan pon s tag
     * @param tpId      the technology profile id
     * @return the found uni tag information
     */
    private Optional<UniTagInformation> getUniTagInformation(AccessDevicePort port, VlanId innerVlan,
                                                             VlanId outerVlan, int tpId) {
        log.debug("Getting uni tag information for {}, innerVlan: {}, outerVlan: {}, tpId: {}",
                port, innerVlan, outerVlan, tpId);
        SubscriberAndDeviceInformation subInfo = getSubscriber(new ConnectPoint(port.deviceId(), port.number()));
        if (subInfo == null) {
            log.warn("Subscriber information doesn't exist for {}", port);
            return Optional.empty();
        }

        List<UniTagInformation> uniTagList = subInfo.uniTagList();
        if (uniTagList == null) {
            log.warn("Uni tag list is not found for the subscriber {} on {}", subInfo.id(), port);
            return Optional.empty();
        }

        UniTagInformation service = null;
        for (UniTagInformation tagInfo : subInfo.uniTagList()) {
            if (innerVlan.equals(tagInfo.getPonCTag()) && outerVlan.equals(tagInfo.getPonSTag())
                    && tpId == tagInfo.getTechnologyProfileId()) {
                service = tagInfo;
                break;
            }
        }

        if (service == null) {
            log.warn("SADIS doesn't include the service with ponCtag {} ponStag {} and tpId {} on {}",
                     innerVlan, outerVlan, tpId, port);
            return Optional.empty();
        }

        return Optional.of(service);
    }

    /**
     * Creates trap flows for device, including DHCP and LLDP trap on NNI and
     * EAPOL trap on the UNIs, if device is present in Sadis config.
     *
     * @param dev Device to look for
     */
    private void checkAndCreateDeviceFlows(Device dev) {
        // check if this device is provisioned in Sadis
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        log.info("checkAndCreateDeviceFlows: deviceInfo {}", deviceInfo);

        if (deviceInfo != null) {
            log.debug("Driver for device {} is {}", dev.id(),
                     driverService.getDriver(dev.id()));
            for (Port p : deviceService.getPorts(dev.id())) {
                if (PortNumber.LOCAL.equals(p.number()) || !p.isEnabled()) {
                    continue;
                }
                if (isUniPort(dev, p)) {
                    AccessDevicePort port = new AccessDevicePort(p, AccessDevicePort.Type.UNI);
                    if (!programmedSubs.containsKey(new ConnectPoint(dev.id(), p.number()))) {
                        log.info("Creating Eapol on {}", port);
                        oltFlowService.processEapolFilteringObjectives(port, defaultBpId, Optional.empty(),
                                null, VlanId.vlanId(EAPOL_DEFAULT_VLAN), true);
                    } else {
                        log.debug("Subscriber Eapol on {} is already provisioned, not installing default", port);
                    }
                } else {
                    AccessDevicePort port = new AccessDevicePort(p, AccessDevicePort.Type.NNI);
                    oltFlowService.processNniFilteringObjectives(port, true);
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
    private AccessDevicePort getUplinkPort(Device dev) {
        // check if this device is provisioned in Sadis
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        log.trace("getUplinkPort: deviceInfo {}", deviceInfo);
        if (deviceInfo == null) {
            log.warn("Device {} is not configured in SADIS .. cannot fetch device"
                             + " info", dev.id());
            return null;
        }
        // Return the port that has been configured as the uplink port of this OLT in Sadis
        Optional<Port> optionalPort = deviceService.getPorts(dev.id()).stream()
                .filter(port -> isNniPort(port) ||
                        (port.number().toLong() == deviceInfo.uplinkPort()))
                .findFirst();
        if (optionalPort.isPresent()) {
            log.trace("getUplinkPort: Found port {}", optionalPort.get());
            return new AccessDevicePort(optionalPort.get(), AccessDevicePort.Type.NNI);
        }

        log.warn("getUplinkPort: " + NO_UPLINK_PORT, dev.id());
        return null;
    }

    /**
     * Return the subscriber on a port.
     *
     * @param cp ConnectPoint on which to find the subscriber
     * @return subscriber if found else null
     */
    protected SubscriberAndDeviceInformation getSubscriber(ConnectPoint cp) {
        if (subsService == null) {
            log.warn(SADIS_NOT_RUNNING);
            return null;
        }
        Port port = deviceService.getPort(cp);
        checkNotNull(port, "Invalid connect point");
        String portName = port.annotations().value(AnnotationKeys.PORT_NAME);
        return subsService.get(portName);
    }

    /**
     * Checks whether the given port of the device is a uni port or not.
     *
     * @param d the access device
     * @param p the port of the device
     * @return true if the given port is a uni port
     */
    private boolean isUniPort(Device d, Port p) {
        AccessDevicePort ulPort = getUplinkPort(d);
        if (ulPort != null) {
            return (ulPort.number().toLong() != p.number().toLong());
        }
        //handles a special case where NNI port is misconfigured in SADIS and getUplinkPort(d) returns null
        //checks whether the port name starts with nni- which is the signature of an NNI Port
        if (p.annotations().value(AnnotationKeys.PORT_NAME) != null &&
                p.annotations().value(AnnotationKeys.PORT_NAME).startsWith(NNI)) {
            log.error("NNI port number {} is not matching with configured value", p.number().toLong());
            return false;
        }
        return true;
    }

    /**
     * Gets the given device details from SADIS.
     * If the device is not found, returns null
     *
     * @param dev the access device
     * @return the olt information
     */
    private SubscriberAndDeviceInformation getOltInfo(Device dev) {
        if (subsService == null) {
            log.warn(SADIS_NOT_RUNNING);
            return null;
        }
        String devSerialNo = dev.serialNumber();
        return subsService.get(devSerialNo);
    }

    /**
     * Checks for mastership or falls back to leadership on deviceId.
     * If the device is available use mastership,
     * otherwise fallback on leadership.
     * Leadership on the device topic is needed because the master can be NONE
     * in case the device went away, we still need to handle events
     * consistently
     */
    private boolean isLocalLeader(DeviceId deviceId) {
        if (deviceService.isAvailable(deviceId)) {
            return mastershipService.isLocalMaster(deviceId);
        } else {
            // Fallback with Leadership service - device id is used as topic
            NodeId leader = leadershipService.runForLeadership(
                    deviceId.toString()).leaderNodeId();
            // Verify if this node is the leader
            return clusterService.getLocalNode().id().equals(leader);
        }
    }

    private boolean isNniPort(Port port) {
        if (port.annotations().keys().contains(AnnotationKeys.PORT_NAME)) {
            return port.annotations().value(AnnotationKeys.PORT_NAME).contains(NNI);
        }
        return false;
    }

    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            hostEventExecutor.execute(() -> {
                Host host = event.subject();
                switch (event.type()) {
                    case HOST_ADDED:
                        ConnectPoint cp = new ConnectPoint(host.location().deviceId(), host.location().port());
                        Optional<SubscriberFlowInfo> optSubFlowInfo =
                                getAndRemoveWaitingMacSubFlowInfoForCTag(cp, host.vlan());
                        if (optSubFlowInfo.isPresent()) {
                            log.debug("Continuing provisioning for waiting mac service. event: {}", event);
                            continueProvisioningSubs(optSubFlowInfo.get(), Optional.of(host.mac()));
                        } else {
                            log.debug("There is no waiting mac sub. event: {}", event);
                        }
                        break;
                    case HOST_UPDATED:
                        if (event.prevSubject() != null && !event.prevSubject().mac().equals(event.subject().mac())) {
                            log.debug("Subscriber's MAC address changed from {} to {}. " +
                                            "devId/portNumber: {}/{} vlan: {}", event.prevSubject().mac(),
                                    event.subject().mac(), host.location().deviceId(), host.location().port(),
                                    host.vlan());
                            // TODO handle subscriber MAC Address changed
                        } else {
                            log.debug("Unhandled HOST_UPDATED event: {}", event);
                        }
                        break;
                    default:
                        log.debug("Unhandled host event received. event: {}", event);
                }
            });
        }

        @Override
        public boolean isRelevant(HostEvent event) {
            return isLocalLeader(event.subject().location().deviceId());
        }
    }

    private class InternalDeviceListener implements DeviceListener {
        private Set<DeviceId> programmedDevices = Sets.newConcurrentHashSet();

        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> {
                DeviceId devId = event.subject().id();
                Device dev = event.subject();
                Port p = event.port();
                DeviceEvent.Type eventType = event.type();

                if (DeviceEvent.Type.PORT_STATS_UPDATED.equals(eventType) ||
                        DeviceEvent.Type.DEVICE_SUSPENDED.equals(eventType) ||
                        DeviceEvent.Type.DEVICE_UPDATED.equals(eventType)) {
                    return;
                }

                boolean isLocalLeader = isLocalLeader(devId);
                // Only handle the event if the device belongs to us
                if (!isLocalLeader && event.type().equals(DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED)
                        && !deviceService.isAvailable(devId) && deviceService.getPorts(devId).isEmpty()) {
                    log.info("Cleaning local state for non master instance upon " +
                                     "device disconnection {}", devId);
                    // Since no mastership of the device is present upon disconnection
                    // the method in the FlowRuleManager only empties the local copy
                    // of the DeviceFlowTable thus this method needs to get called
                    // on every instance, see how it's done in the InternalDeviceListener
                    // in FlowRuleManager: no mastership check for purgeOnDisconnection
                    handleDeviceDisconnection(dev, false, false);
                    return;
                } else if (!isLocalLeader) {
                    log.debug("Not handling event because instance is not leader for {}", devId);
                    return;
                }

                log.debug("OLT got {} event for {}/{}", eventType, event.subject(), event.port());

                if (getOltInfo(dev) == null) {
                    // it's possible that we got an event for a previously
                    // programmed OLT that is no longer available in SADIS
                    // we let such events go through
                    if (!programmedDevices.contains(devId)) {
                        log.warn("No device info found for {}, this is either "
                                         + "not an OLT or not known to sadis", dev);
                        return;
                    }
                }
                AccessDevicePort port = null;
                if (p != null) {
                    if (isUniPort(dev, p)) {
                        port = new AccessDevicePort(p, AccessDevicePort.Type.UNI);
                    } else {
                        port = new AccessDevicePort(p, AccessDevicePort.Type.NNI);
                    }
                }

                switch (event.type()) {
                    //TODO: Port handling and bookkeeping should be improved once
                    // olt firmware handles correct behaviour.
                    case PORT_ADDED:
                        if (!deviceService.isAvailable(devId)) {
                            log.warn("Received {} for disconnected device {}, ignoring", event, devId);
                            return;
                        }
                        if (port.type().equals(AccessDevicePort.Type.UNI)) {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, port.port()));

                            if (port.isEnabled() && !port.number().equals(PortNumber.LOCAL)) {
                                log.info("eapol will be sent for port added {}", port);
                                oltFlowService.processEapolFilteringObjectives(port, defaultBpId, Optional.empty(),
                                                                               null,
                                                                               VlanId.vlanId(EAPOL_DEFAULT_VLAN),
                                                                               true);
                            }
                        } else {
                            SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
                            if (deviceInfo != null) {
                                oltFlowService.processNniFilteringObjectives(port, true);
                            }
                        }
                        break;
                    case PORT_REMOVED:
                        if (port.type().equals(AccessDevicePort.Type.UNI)) {
                            // if no subscriber is provisioned we need to remove the default EAPOL
                            // if a subscriber was provisioned the default EAPOL will not be there and we can skip.
                            // The EAPOL with subscriber tag will be removed by removeSubscriber call.
                            Collection<? extends UniTagInformation> uniTagInformationSet =
                                    programmedSubs.get(new ConnectPoint(port.deviceId(), port.number())).value();
                            if (uniTagInformationSet == null || uniTagInformationSet.isEmpty()) {
                                log.info("No subscriber provisioned on port {} in PORT_REMOVED event, " +
                                                 "removing default EAPOL flow", port);
                                oltFlowService.processEapolFilteringObjectives(port, defaultBpId, Optional.empty(),
                                        null, VlanId.vlanId(EAPOL_DEFAULT_VLAN), false);
                            } else {
                                removeSubscriber(port);
                            }

                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, port.port()));
                        }
                        break;
                    case PORT_UPDATED:
                        if (!deviceService.isAvailable(devId)) {
                            log.warn("Received {} for disconnected device {}, ignoring", event, devId);
                            return;
                        }
                        if (port.type().equals(AccessDevicePort.Type.NNI)) {
                            SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
                            if (deviceInfo != null && port.isEnabled()) {
                                log.debug("NNI {} enabled", port);
                                oltFlowService.processNniFilteringObjectives(port, true);
                            }
                            return;
                        }
                        ConnectPoint cp = new ConnectPoint(devId, port.number());
                        Collection<? extends UniTagInformation> uniTagInformationSet = programmedSubs.get(cp).value();
                        if (uniTagInformationSet == null || uniTagInformationSet.isEmpty()) {
                            if (!port.number().equals(PortNumber.LOCAL)) {
                                log.info("eapol will be {} updated for {} with default vlan {}",
                                         (port.isEnabled()) ? "added" : "removed", port, EAPOL_DEFAULT_VLAN);
                                oltFlowService.processEapolFilteringObjectives(port, defaultBpId, Optional.empty(),
                                        null, VlanId.vlanId(EAPOL_DEFAULT_VLAN), port.isEnabled());
                            }
                        } else {
                            log.info("eapol will be {} updated for {}", (port.isEnabled()) ? "added" : "removed",
                                     port);
                            for (UniTagInformation uniTag : uniTagInformationSet) {
                                oltFlowService.processEapolFilteringObjectives(port,
                                        uniTag.getUpstreamBandwidthProfile(),
                                        Optional.of(uniTag.getUpstreamOltBandwidthProfile()),
                                        null, uniTag.getPonCTag(), port.isEnabled());
                            }
                        }
                        if (port.isEnabled()) {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, port.port()));
                        } else {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, port.port()));
                        }
                        break;
                    case DEVICE_ADDED:
                        handleDeviceConnection(dev, true);
                        break;
                    case DEVICE_REMOVED:
                        handleDeviceDisconnection(dev, true, true);
                        break;
                    case DEVICE_AVAILABILITY_CHANGED:
                        if (deviceService.isAvailable(devId)) {
                            log.info("Handling available device: {}", dev.id());
                            handleDeviceConnection(dev, false);
                        } else {
                            if (deviceService.getPorts(devId).isEmpty()) {
                                log.info("Handling controlled device disconnection .. "
                                                 + "flushing all state for dev:{}", devId);
                                handleDeviceDisconnection(dev, true, false);
                            } else {
                                log.info("Disconnected device has available ports .. "
                                                 + "assuming temporary disconnection, "
                                                 + "retaining state for device {}", devId);
                            }
                        }
                        break;
                    default:
                        log.debug("Not handling event {}", event);
                        return;
                }
            });
        }

        private void sendUniEvent(Device device, AccessDeviceEvent.Type eventType) {
            deviceService.getPorts(device.id()).stream()
                    .filter(p -> !PortNumber.LOCAL.equals(p.number()))
                    .filter(p -> isUniPort(device, p))
                    .forEach(p -> post(new AccessDeviceEvent(eventType, device.id(), p)));
        }

        private void handleDeviceDisconnection(Device device, boolean sendDisconnectedEvent, boolean sendUniEvent) {
            programmedDevices.remove(device.id());
            removeAllSubscribers(device.id());
            removeWaitingMacSubs(device.id());
            //Handle case where OLT disconnects during subscriber provisioning
            pendingSubscribersForDevice.remove(device.id());
            oltFlowService.clearDeviceState(device.id());

            //Complete meter and flow purge
            flowRuleService.purgeFlowRules(device.id());
            oltMeterService.clearMeters(device.id());
            if (sendDisconnectedEvent) {
                post(new AccessDeviceEvent(
                        AccessDeviceEvent.Type.DEVICE_DISCONNECTED, device.id(),
                        null, null, null));
            }
            if (sendUniEvent) {
                sendUniEvent(device, AccessDeviceEvent.Type.UNI_REMOVED);
            }
        }

        private void handleDeviceConnection(Device dev, boolean sendUniEvent) {
            post(new AccessDeviceEvent(
                    AccessDeviceEvent.Type.DEVICE_CONNECTED, dev.id(),
                    null, null, null));
            programmedDevices.add(dev.id());
            checkAndCreateDeviceFlows(dev);
            if (sendUniEvent) {
                sendUniEvent(dev, AccessDeviceEvent.Type.UNI_ADDED);
            }
        }

        private void removeAllSubscribers(DeviceId deviceId) {
            List<Map.Entry<ConnectPoint, UniTagInformation>> subs = programmedSubs.stream()
                    .filter(e -> e.getKey().deviceId().equals(deviceId))
                    .collect(toList());

            subs.forEach(e -> programmedSubs.remove(e.getKey(), e.getValue()));
        }

        private void removeWaitingMacSubs(DeviceId deviceId) {
            List<ConnectPoint> waitingMacKeys = waitingMacSubscribers.stream()
                    .filter(cp -> cp.getKey().deviceId().equals(deviceId))
                    .map(Map.Entry::getKey)
                    .collect(toList());
            waitingMacKeys.forEach(cp -> waitingMacSubscribers.removeAll(cp));
        }

    }

    private class InternalClusterListener implements ClusterEventListener {

        @Override
        public void event(ClusterEvent event) {
            if (event.type() == ClusterEvent.Type.INSTANCE_READY) {
                hasher.addServer(event.subject().id());
            }
            if (event.type() == ClusterEvent.Type.INSTANCE_DEACTIVATED) {
                hasher.removeServer(event.subject().id());
            }
        }
    }

}
