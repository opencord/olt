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
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_BP_ID;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_BP_ID_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_MCAST_SERVICE_NAME;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_MCAST_SERVICE_NAME_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.EAPOL_DELETE_RETRY_MAX_ATTEMPS;
import static org.opencord.olt.impl.OsgiPropertyConstants.EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveContext;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.meter.MeterId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMultimap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
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
import org.slf4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Provisions rules on access devices.
 */
@Component(immediate = true,
        property = {
                DEFAULT_BP_ID + ":String=" + DEFAULT_BP_ID_DEFAULT,
                DEFAULT_MCAST_SERVICE_NAME + ":String=" + DEFAULT_MCAST_SERVICE_NAME_DEFAULT,
                EAPOL_DELETE_RETRY_MAX_ATTEMPS + ":Integer=" +
                        EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT,
        })
public class Olt
        extends AbstractListenerManager<AccessDeviceEvent, AccessDeviceListener>
        implements AccessDeviceService {
    private static final String APP_NAME = "org.opencord.olt";

    private static final short EAPOL_DEFAULT_VLAN = 4091;
    private static final String NO_UPLINK_PORT = "No uplink port found for OLT device {}";

    public static final int HASH_WEIGHT = 10;

    private final Logger log = getLogger(getClass());

    private static final String NNI = "nni-";

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected SadisService sadisService;

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

    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final ClusterEventListener clusterListener = new InternalClusterListener();

    private ConsistentHasher hasher;

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private BaseInformationService<BandwidthProfileInformation> bpService;

    private ExecutorService oltInstallers = Executors.newFixedThreadPool(4,
                                                                         groupedThreads("onos/olt-service",
                                                                                        "olt-installer-%d"));

    protected ExecutorService eventExecutor;
    protected ExecutorService retryExecutor;

    private ConsistentMultimap<ConnectPoint, UniTagInformation> programmedSubs;
    private ConsistentMultimap<ConnectPoint, UniTagInformation> failedSubs;

    private Set<SubscriberFlowInfo> pendingSubscribers;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                                                                        "events-%d", log));
        retryExecutor = Executors.newCachedThreadPool();

        modified(context);
        ApplicationId appId = coreService.registerApplication(APP_NAME);
        componentConfigService.registerProperties(getClass());

        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(UniTagInformation.class)
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

        pendingSubscribers = Sets.newConcurrentHashSet();
        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);

        subsService = sadisService.getSubscriberInfoService();
        bpService = sadisService.getBandwidthProfileService();

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
        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        componentConfigService.unregisterProperties(getClass(), false);
        clusterService.removeListener(clusterListener);
        deviceService.removeListener(deviceListener);
        eventDispatcher.removeSink(AccessDeviceEvent.class);
        eventExecutor.shutdown();
        retryExecutor.shutdown();
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

    @Override
    public boolean provisionSubscriber(ConnectPoint connectPoint) {
        log.info("Call to provision subscriber at {}", connectPoint);
        DeviceId deviceId = connectPoint.deviceId();
        PortNumber subscriberPortNo = connectPoint.port();

        checkNotNull(deviceService.getPort(deviceId, subscriberPortNo),
                     "Invalid connect point:" + connectPoint);

        // Find the subscriber on this connect point
        SubscriberAndDeviceInformation sub = getSubscriber(connectPoint);
        if (sub == null) {
            log.warn("No subscriber found for {}", connectPoint);
            return false;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(deviceId));
        if (uplinkPort == null) {
            log.warn(NO_UPLINK_PORT, deviceId);
            return false;
        }

        // delete Eapol authentication flow with default bandwidth
        // wait until Eapol rule with defaultBpId is removed to install subscriber-based rules
        // retry deletion if it fails/times-out
        retryExecutor.execute(new DeleteEapolInstallSub(connectPoint,
                                                        uplinkPort, sub, 1));
        return true;
    }

    private class DeleteEapolInstallSub implements Runnable {
        ConnectPoint cp;
        Port uplinkPort;
        SubscriberAndDeviceInformation sub;
        private int attemptNumber;

        DeleteEapolInstallSub(ConnectPoint cp, Port uplinkPort,
                              SubscriberAndDeviceInformation sub,
                              int attemptNumber) {
            this.cp = cp;
            this.uplinkPort = uplinkPort;
            this.sub = sub;
            this.attemptNumber = attemptNumber;
        }

        @Override
        public void run() {
            CompletableFuture<ObjectiveError> filterFuture = new CompletableFuture();
            oltFlowService.processEapolFilteringObjectives(cp.deviceId(), cp.port(),
                                                     defaultBpId, filterFuture,
                                                     VlanId.vlanId(EAPOL_DEFAULT_VLAN),
                                                     false);
            filterFuture.thenAcceptAsync(filterStatus -> {
                if (filterStatus == null) {
                    log.info("Default eapol flow deleted in attempt {} of {}"
                            + "... provisioning subscriber flows {}",
                             attemptNumber, eapolDeleteRetryMaxAttempts, cp);
                    provisionUniTagList(cp, uplinkPort.number(), sub);
                } else {
                    if (attemptNumber <= eapolDeleteRetryMaxAttempts) {
                        log.warn("The filtering future failed {} for subscriber {}"
                                + "... retrying {} of {} attempts",
                                 filterStatus, cp, attemptNumber, eapolDeleteRetryMaxAttempts);
                        retryExecutor.execute(
                                         new DeleteEapolInstallSub(cp, uplinkPort, sub,
                                                                   attemptNumber + 1));
                    } else {
                        log.error("The filtering future failed {} for subscriber {}"
                                + "after {} attempts. Subscriber provisioning failed",
                                  filterStatus, cp, eapolDeleteRetryMaxAttempts);
                        sub.uniTagList().forEach(ut -> failedSubs.put(cp, ut));
                    }
                }
            });
        }

    }

    @Override
    public boolean removeSubscriber(ConnectPoint connectPoint) {
        log.info("Call to un-provision subscriber at {}", connectPoint);

        // Get the subscriber connected to this port from the local cache
        // If we don't know about the subscriber there's no need to remove it
        DeviceId deviceId = connectPoint.deviceId();
        PortNumber subscriberPortNo = connectPoint.port();

        Collection<? extends UniTagInformation> uniTagInformationSet = programmedSubs.get(connectPoint).value();
        if (uniTagInformationSet == null || uniTagInformationSet.isEmpty()) {
            log.warn("Subscriber on connectionPoint {} was not previously programmed, " +
                             "no need to remove it", connectPoint);
            return true;
        }

        // Get the uplink port
        Port uplinkPort = getUplinkPort(deviceService.getDevice(deviceId));
        if (uplinkPort == null) {
            log.warn(NO_UPLINK_PORT, deviceId);
            return false;
        }

        for (UniTagInformation uniTag : uniTagInformationSet) {

            if (multicastServiceName.equals(uniTag.getServiceName())) {
                continue;
            }

            unprovisionVlans(deviceId, uplinkPort.number(), subscriberPortNo, uniTag);

            // remove eapol with subscriber bandwidth profile
            oltFlowService.processEapolFilteringObjectives(deviceId, subscriberPortNo,
                                                           uniTag.getUpstreamBandwidthProfile(),
                                                           null, uniTag.getPonCTag(), false);

            Port port = deviceService.getPort(deviceId, subscriberPortNo);
            if (port != null && port.isEnabled()) {
                // reinstall eapol with default bandwidth profile
                oltFlowService.processEapolFilteringObjectives(deviceId, subscriberPortNo, defaultBpId,
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

        log.info("Provisioning subscriber using subscriberId {}, sTag {}, cTag {}, tpId {}" +
                         "", subscriberId, sTag, cTag, tpId);

        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint subsPort = findSubscriberConnectPoint(subscriberId.toString());
        if (subsPort == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return provisionSubscriber(subsPort);
        } else if (sTag.isPresent() && cTag.isPresent() && tpId.isPresent()) {
            Port uplinkPort = getUplinkPort(deviceService.getDevice(subsPort.deviceId()));
            if (uplinkPort == null) {
                log.warn(NO_UPLINK_PORT, subsPort.deviceId());
                return false;
            }

            //delete Eapol authentication flow with default bandwidth
            //wait until Eapol rule with defaultBpId is removed to install subscriber-based rules
            //install subscriber flows
            CompletableFuture<ObjectiveError> filterFuture = new CompletableFuture();
            oltFlowService.processEapolFilteringObjectives(subsPort.deviceId(), subsPort.port(), defaultBpId,
                                                           filterFuture, VlanId.vlanId(EAPOL_DEFAULT_VLAN), false);
            filterFuture.thenAcceptAsync(filterStatus -> {
                if (filterStatus == null) {
                    provisionUniTagInformation(subsPort.deviceId(), uplinkPort.number(), subsPort.port(),
                                               cTag.get(), sTag.get(), tpId.get());
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
        ConnectPoint subsPort = findSubscriberConnectPoint(subscriberId.toString());
        if (subsPort == null) {
            log.warn("ConnectPoint for {} not found", subscriberId);
            return false;
        }

        if (!sTag.isPresent() && !cTag.isPresent()) {
            return removeSubscriber(subsPort);
        } else if (sTag.isPresent() && cTag.isPresent() && tpId.isPresent()) {
            // Get the uplink port
            Port uplinkPort = getUplinkPort(deviceService.getDevice(subsPort.deviceId()));
            if (uplinkPort == null) {
                log.warn(NO_UPLINK_PORT, subsPort.deviceId());
                return false;
            }

            Optional<UniTagInformation> tagInfo = getUniTagInformation(subsPort, cTag.get(), sTag.get(), tpId.get());
            if (!tagInfo.isPresent()) {
                log.warn("UniTagInformation does not exist for Device/Port {}, cTag {}, sTag {}, tpId {}",
                         subsPort, cTag, sTag, tpId);
                return false;
            }

            unprovisionVlans(subsPort.deviceId(), uplinkPort.number(), subsPort.port(), tagInfo.get());
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
        if (bandwidthProfile == null) {
            return null;
        }
        return bpService.get(bandwidthProfile);
    }

    /**
     * Removes subscriber vlan flows.
     *
     * @param deviceId       the device identifier
     * @param uplink         uplink port of the OLT
     * @param subscriberPort uni port
     * @param uniTag         uni tag information
     */
    private void unprovisionVlans(DeviceId deviceId, PortNumber uplink,
                                  PortNumber subscriberPort, UniTagInformation uniTag) {

        log.info("Unprovisioning vlans for {} at {}/{}", uniTag, deviceId, subscriberPort);

        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture();

        VlanId deviceVlan = uniTag.getPonSTag();
        VlanId subscriberVlan = uniTag.getPonCTag();

        MeterId upstreamMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getUpstreamBandwidthProfile());
        MeterId downstreamMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, uniTag.getDownstreamBandwidthProfile());

        ForwardingObjective.Builder upFwd =
                oltFlowService.createUpBuilder(uplink, subscriberPort, upstreamMeterId, uniTag);
        ForwardingObjective.Builder downFwd =
                oltFlowService.createDownBuilder(uplink, subscriberPort, downstreamMeterId, uniTag);

        oltFlowService.processIgmpFilteringObjectives(deviceId, subscriberPort,
                                                      upstreamMeterId, uniTag, false, true);
        oltFlowService.processDhcpFilteringObjectives(deviceId, subscriberPort,
                                                      upstreamMeterId, uniTag, false, true);

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
                log.debug("Uni tag information is unregistered successfully for cTag {}, sTag {}, tpId {}, and" +
                                  "Device/Port{}", uniTag.getPonCTag(), uniTag.getPonSTag(),
                          uniTag.getTechnologyProfileId(), subscriberPort);
                updateProgrammedSubscriber(new ConnectPoint(deviceId, subscriberPort), uniTag, false);
            } else if (downStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed downstream uninstallation: {}",
                          subscriberVlan, deviceId, subscriberPort, downStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED;
            } else if (upStatus != null) {
                log.error("Subscriber with vlan {} on device {} " +
                                  "on port {} failed upstream uninstallation: {}",
                          subscriberVlan, deviceId, subscriberPort, upStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED;
            }
            Port port = deviceService.getPort(deviceId, subscriberPort);
            post(new AccessDeviceEvent(type, deviceId, port, deviceVlan, subscriberVlan,
                                       uniTag.getTechnologyProfileId()));
        }, oltInstallers);
    }

    /**
     * Adds subscriber vlan flows, dhcp, eapol and igmp trap flows for the related uni port.
     *
     * @param connectPoint the connection point of the subscriber
     * @param uplinkPort   uplink port of the OLT (the nni port)
     * @param sub          subscriber information that includes s, c tags, tech profile and bandwidth profile references
     */
    private void provisionUniTagList(ConnectPoint connectPoint, PortNumber uplinkPort,
                                     SubscriberAndDeviceInformation sub) {

        log.debug("Provisioning vlans for subscriber {} on dev/port: {}", sub, connectPoint);

        if (sub.uniTagList() == null || sub.uniTagList().isEmpty()) {
            log.warn("Unitaglist doesn't exist for the subscriber {}", sub.id());
            return;
        }

        DeviceId deviceId = connectPoint.deviceId();
        PortNumber subscriberPort = connectPoint.port();

        for (UniTagInformation uniTag : sub.uniTagList()) {
            handleSubscriberFlows(deviceId, uplinkPort, subscriberPort, uniTag);
        }
    }

    /**
     * Finds the uni tag information and provisions the found information.
     * If the uni tag information is not found, returns
     *
     * @param deviceId       the access device id
     * @param uplinkPort     the nni port
     * @param subscriberPort the uni port
     * @param innerVlan      the pon c tag
     * @param outerVlan      the pon s tag
     * @param tpId           the technology profile id
     */
    private void provisionUniTagInformation(DeviceId deviceId, PortNumber uplinkPort,
                                            PortNumber subscriberPort,
                                            VlanId innerVlan,
                                            VlanId outerVlan,
                                            Integer tpId) {

        ConnectPoint cp = new ConnectPoint(deviceId, subscriberPort);
        Optional<UniTagInformation> gotTagInformation = getUniTagInformation(cp, innerVlan, outerVlan, tpId);
        if (!gotTagInformation.isPresent()) {
            return;
        }
        UniTagInformation tagInformation = gotTagInformation.get();
        handleSubscriberFlows(deviceId, uplinkPort, subscriberPort, tagInformation);
    }

    private void updateProgrammedSubscriber(ConnectPoint connectPoint, UniTagInformation tagInformation, Boolean add) {
        if (add) {
            programmedSubs.put(connectPoint, tagInformation);
        } else {
            programmedSubs.remove(connectPoint, tagInformation);
        }
    }

    /**
     * Installs a uni tag information flow.
     *
     * @param deviceId       the access device id
     * @param uplinkPort     the nni port
     * @param subscriberPort the uni port
     * @param tagInfo        the uni tag information
     */
    private void handleSubscriberFlows(DeviceId deviceId, PortNumber uplinkPort, PortNumber subscriberPort,
                                       UniTagInformation tagInfo) {

        log.debug("Provisioning vlan-based flows for the uniTagInformation {}", tagInfo);

        Port port = deviceService.getPort(deviceId, subscriberPort);

        if (multicastServiceName.equals(tagInfo.getServiceName())) {
            // IGMP flows are taken care of along with VOD service
            // Please note that for each service, Subscriber Registered event will be sent
            post(new AccessDeviceEvent(AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTERED,
                                       deviceId, port, tagInfo.getPonSTag(), tagInfo.getPonCTag(),
                                       tagInfo.getTechnologyProfileId()));
            return;
        }

        BandwidthProfileInformation upstreamBpInfo =
                getBandwidthProfileInformation(tagInfo.getUpstreamBandwidthProfile());
        BandwidthProfileInformation downstreamBpInfo =
                getBandwidthProfileInformation(tagInfo.getDownstreamBandwidthProfile());
        if (upstreamBpInfo == null) {
            log.warn("No meter installed since no Upstream BW Profile definition found for "
                             + "ctag {} stag {} tpId {} and Device/port: {}:{}",
                     tagInfo.getPonCTag(), tagInfo.getPonSTag(),
                     tagInfo.getTechnologyProfileId(), deviceId,
                     subscriberPort);
            return;
        }
        if (downstreamBpInfo == null) {
            log.warn("No meter installed since no Downstream BW Profile definition found for "
                             + "ctag {} stag {} tpId {} and Device/port: {}:{}",
                     tagInfo.getPonCTag(), tagInfo.getPonSTag(),
                     tagInfo.getTechnologyProfileId(), deviceId,
                     subscriberPort);
            return;
        }

        // check for meterIds for the upstream and downstream bandwidth profiles
        MeterId upMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, upstreamBpInfo.id());
        MeterId downMeterId = oltMeterService
                .getMeterIdFromBpMapping(deviceId, downstreamBpInfo.id());
        SubscriberFlowInfo fi = new SubscriberFlowInfo(deviceId, uplinkPort, subscriberPort,
                                                       tagInfo, downMeterId, upMeterId,
                                                       downstreamBpInfo.id(), upstreamBpInfo.id());

        if (upMeterId != null && downMeterId != null) {
            log.debug("Meters are existing for upstream {} and downstream {}",
                     upstreamBpInfo.id(), downstreamBpInfo.id());
            handleSubFlowsWithMeters(fi);
        } else {
            log.debug("Adding {} to pending subs", fi);
            // one or both meters are not ready. It's possible they are in the process of being
            // created for other subscribers that share the same bandwidth profile.
            pendingSubscribers.add(fi);

            // queue up the meters to be created
            if (upMeterId == null) {
                log.debug("Missing meter for upstream {}", upstreamBpInfo.id());
                checkAndCreateDevMeter(deviceId, upstreamBpInfo);
            }
            if (downMeterId == null) {
                log.debug("Missing meter for downstream {}", downstreamBpInfo.id());
                checkAndCreateDevMeter(deviceId, downstreamBpInfo);
            }
        }
    }
    private void checkAndCreateDevMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        if (oltMeterService.isMeterPending(deviceId, bwpInfo)) {
            log.debug("Meter is already pending {} on device {}", bwpInfo, deviceId);
            return;
        }
        oltMeterService.addToPendingMeters(deviceId, bwpInfo);
        createMeter(deviceId, bwpInfo);
    }

    private void createMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        log.debug("Creating Meter with {} on {} for subscriber", bwpInfo, deviceId);
        CompletableFuture<Object> meterFuture = new CompletableFuture<>();

        MeterId meterId = oltMeterService.createMeter(deviceId, bwpInfo,
                                                      meterFuture);

        meterFuture.thenAcceptAsync(result -> {
            // iterate through the subscribers on hold
            Iterator<SubscriberFlowInfo> subsIterator = pendingSubscribers.iterator();
            while (subsIterator.hasNext()) {
                SubscriberFlowInfo fi = subsIterator.next();
                if (result == null) {
                    // meter install sent to device
                    log.debug("Meter {} installed for bw {} on {}", meterId, bwpInfo, deviceId);

                    MeterId upMeterId = oltMeterService
                            .getMeterIdFromBpMapping(deviceId, fi.getUpBpInfo());
                    MeterId downMeterId = oltMeterService
                            .getMeterIdFromBpMapping(deviceId, fi.getDownBpInfo());
                    if (upMeterId != null && downMeterId != null) {
                        log.debug("Provisioning subscriber after meter {}" +
                                          "installation and both meters are present " +
                                          "upstream {} and downstream {}",
                                  meterId, upMeterId, downMeterId);
                        // put in the meterIds  because when fi was first
                        // created there may or may not have been a meterId
                        // depending on whether the meter was created or
                        // not at that time.
                        fi.setUpMeterId(upMeterId);
                        fi.setDownMeterId(downMeterId);
                        handleSubFlowsWithMeters(fi);
                        subsIterator.remove();
                    }
                    oltMeterService.removeFromPendingMeters(deviceId, bwpInfo);
                } else {
                    // meter install failed
                    log.error("Addition of subscriber {} failed due to meter " +
                                      "{} with result {}", fi, meterId, result);
                    subsIterator.remove();
                    oltMeterService.removeFromPendingMeters(deviceId, bwpInfo);
                }
            }
        });
    }
    /**
     * Add subscriber flows given meter information for both upstream and
     * downstream directions.
     *
     * @param subscriberFlowInfo relevant information for subscriber
     */
    private void handleSubFlowsWithMeters(SubscriberFlowInfo subscriberFlowInfo) {
        log.debug("Provisioning subscriber flows based on {}", subscriberFlowInfo);
        UniTagInformation tagInfo = subscriberFlowInfo.getTagInfo();
        CompletableFuture<ObjectiveError> upFuture = new CompletableFuture<>();
        CompletableFuture<ObjectiveError> downFuture = new CompletableFuture<>();

        ForwardingObjective.Builder upFwd =
                oltFlowService.createUpBuilder(subscriberFlowInfo.getNniPort(), subscriberFlowInfo.getUniPort(),
                                               subscriberFlowInfo.getUpId(), subscriberFlowInfo.getTagInfo());
        flowObjectiveService.forward(subscriberFlowInfo.getDevId(), upFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.debug("Upstream flow installed successfully {}", subscriberFlowInfo);
                upFuture.complete(null);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                upFuture.complete(error);
            }
        }));

        ForwardingObjective.Builder downFwd =
                oltFlowService.createDownBuilder(subscriberFlowInfo.getNniPort(), subscriberFlowInfo.getUniPort(),
                                                 subscriberFlowInfo.getDownId(), subscriberFlowInfo.getTagInfo());
        flowObjectiveService.forward(subscriberFlowInfo.getDevId(), downFwd.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.debug("Downstream flow installed successfully {}", subscriberFlowInfo);
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
                log.error("Flow with innervlan {} and outerVlan {} on device {} " +
                                  "on port {} failed downstream installation: {}",
                          tagInfo.getPonCTag(), tagInfo.getPonSTag(), subscriberFlowInfo.getDevId(),
                          subscriberFlowInfo.getUniPort(), downStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED;
            } else if (upStatus != null) {
                log.error("Flow with innerVlan {} and outerVlan {} on device {} " +
                                  "on port {} failed upstream installation: {}",
                          tagInfo.getPonCTag(), tagInfo.getPonSTag(), subscriberFlowInfo.getDevId(),
                          subscriberFlowInfo.getUniPort(), upStatus);
                type = AccessDeviceEvent.Type.SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED;
            } else {
                log.debug("Upstream and downstream data plane flows are installed successfully " +
                                 "for {}", subscriberFlowInfo);
                oltFlowService.processEapolFilteringObjectives(subscriberFlowInfo.getDevId(),
                                                               subscriberFlowInfo.getUniPort(),
                                                               tagInfo.getUpstreamBandwidthProfile(),
                                                               null, tagInfo.getPonCTag(), true);
                oltFlowService.processDhcpFilteringObjectives(subscriberFlowInfo.getDevId(),
                                                              subscriberFlowInfo.getUniPort(),
                                                              subscriberFlowInfo.getUpId(),
                                                              tagInfo, true, true);

                oltFlowService.processIgmpFilteringObjectives(subscriberFlowInfo.getDevId(),
                                                              subscriberFlowInfo.getUniPort(),
                                                              subscriberFlowInfo.getUpId(),
                                                              tagInfo, true, true);
                updateProgrammedSubscriber(new ConnectPoint(subscriberFlowInfo.getDevId(),
                                                            subscriberFlowInfo.getUniPort()),
                                           tagInfo, true);
            }
            post(new AccessDeviceEvent(type, subscriberFlowInfo.getDevId(),
                                       deviceService.getPort(subscriberFlowInfo.getDevId(),
                                                             subscriberFlowInfo.getUniPort()),
                                       tagInfo.getPonSTag(), tagInfo.getPonCTag(),
                                       tagInfo.getTechnologyProfileId()));
        }, oltInstallers);
    }

    /**
     * Checks the subscriber uni tag list and find the uni tag information.
     * using the pon c tag, pon s tag and the technology profile id
     * May return Optional<null>
     *
     * @param cp        the connection point of the subscriber
     * @param innerVlan pon c tag
     * @param outerVlan pon s tag
     * @param tpId      the technology profile id
     * @return the found uni tag information
     */
    private Optional<UniTagInformation> getUniTagInformation(ConnectPoint cp, VlanId innerVlan, VlanId outerVlan,
                                                             int tpId) {
        log.info("Getting uni tag information for cp: {}, innerVlan: {}, outerVlan: {}, tpId: {}", cp, innerVlan,
                 outerVlan, tpId);
        SubscriberAndDeviceInformation subInfo = getSubscriber(cp);
        if (subInfo == null) {
            log.warn("Subscriber information doesn't exist for the connect point {}", cp);
            return Optional.empty();
        }

        List<UniTagInformation> uniTagList = subInfo.uniTagList();
        if (uniTagList == null) {
            log.warn("Uni tag list is not found for the subscriber {}", subInfo.id());
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
            log.warn("SADIS doesn't include the service with ponCtag {} ponStag {} and tpId {}",
                     innerVlan, outerVlan, tpId);
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
            // This is an OLT device as per Sadis, we create flows for UNI and NNI ports
            for (Port p : deviceService.getPorts(dev.id())) {
                if (PortNumber.LOCAL.equals(p.number()) || !p.isEnabled()) {
                    continue;
                }
                if (isUniPort(dev, p)) {
                    if (!programmedSubs.containsKey(new ConnectPoint(dev.id(), p.number()))) {
                        log.info("Creating Eapol for the uni {}", p);
                        oltFlowService.processEapolFilteringObjectives(dev.id(), p.number(), defaultBpId, null,
                                                                       VlanId.vlanId(EAPOL_DEFAULT_VLAN), true);
                    } else {
                        log.debug("Subscriber Eapol for UNI port {} on device {} is already " +
                                          "provisioned, not installing default", p.number(), dev.id());
                    }
                } else {
                    oltFlowService.processNniFilteringObjectives(dev.id(), p.number(), true);
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
            return optionalPort.get();
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
    SubscriberAndDeviceInformation getSubscriber(ConnectPoint cp) {
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
        Port ulPort = getUplinkPort(d);
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
        String devSerialNo = dev.serialNumber();
        return subsService.get(devSerialNo);
    }

    // Custom-built function, when the device is not available we need a fallback mechanism
    private boolean isLocalLeader(DeviceId deviceId) {
        if (!mastershipService.isLocalMaster(deviceId)) {
            // When the device is available we just check the mastership
            if (deviceService.isAvailable(deviceId)) {
                return false;
            }
            // Fallback with Leadership service - device id is used as topic
            NodeId leader = leadershipService.runForLeadership(
                    deviceId.toString()).leaderNodeId();
            // Verify if this node is the leader
            return clusterService.getLocalNode().id().equals(leader);
        }
        return true;
    }

    private boolean isNniPort(Port port) {
        if (port.annotations().keys().contains(AnnotationKeys.PORT_NAME)) {
            return port.annotations().value(AnnotationKeys.PORT_NAME).contains(NNI);
        }
        return false;
    }

    private class InternalDeviceListener implements DeviceListener {
        private Set<DeviceId> programmedDevices = Sets.newConcurrentHashSet();

        @Override
        public void event(DeviceEvent event) {
            eventExecutor.execute(() -> {
                DeviceId devId = event.subject().id();
                Device dev = event.subject();
                Port port = event.port();
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

                log.debug("OLT got {} event for {} {}", eventType, event.subject(), event.port());

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

                switch (event.type()) {
                    //TODO: Port handling and bookkeeping should be improved once
                    // olt firmware handles correct behaviour.
                    case PORT_ADDED:
                        if (!deviceService.isAvailable(devId)) {
                            log.warn("Received {} for disconnected device {}, ignoring", event, devId);
                            return;
                        }
                        if (isUniPort(dev, port)) {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, port));

                            if (port.isEnabled() && !port.number().equals(PortNumber.LOCAL)) {
                                log.info("eapol will be sent for port added {}", port);
                                oltFlowService.processEapolFilteringObjectives(devId, port.number(), defaultBpId,
                                                                               null,
                                                                               VlanId.vlanId(EAPOL_DEFAULT_VLAN),
                                                                               true);
                            }
                        } else {
                            SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
                            if (deviceInfo != null) {
                                oltFlowService.processNniFilteringObjectives(dev.id(), port.number(), true);
                            }
                        }
                        break;
                    case PORT_REMOVED:
                        if (isUniPort(dev, port)) {
                            removeSubscriber(new ConnectPoint(devId, port.number()));
                            log.info("eapol will be send for port {} removed", port);
                            oltFlowService.processEapolFilteringObjectives(devId, port.number(), defaultBpId,
                                                                           null,
                                                                           VlanId.vlanId(EAPOL_DEFAULT_VLAN), false);

                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, port));
                        }
                        break;
                    case PORT_UPDATED:
                        if (!deviceService.isAvailable(devId)) {
                            log.warn("Received {} for disconnected device {}, ignoring", event, devId);
                            return;
                        }
                        if (!isUniPort(dev, port)) {
                            SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
                            if (deviceInfo != null && port.isEnabled()) {
                                log.debug("NNI dev/port {}/{} enabled", dev.id(),
                                          port.number());
                                oltFlowService.processNniFilteringObjectives(dev.id(),
                                                                             port.number(), true);
                            }
                            return;
                        }
                        ConnectPoint cp = new ConnectPoint(devId, port.number());
                        Collection<? extends UniTagInformation> uniTagInformationSet = programmedSubs.get(cp).value();
                        if (uniTagInformationSet == null || uniTagInformationSet.isEmpty()) {
                            if (!port.number().equals(PortNumber.LOCAL)) {
                                log.info("eapol will be {} for dev/port updated {}/{} with default vlan {}",
                                         (port.isEnabled()) ? "added" : "removed",
                                         devId, port.number(), EAPOL_DEFAULT_VLAN);
                                oltFlowService.processEapolFilteringObjectives(devId, port.number(), defaultBpId,
                                                                               null,
                                                                               VlanId.vlanId(EAPOL_DEFAULT_VLAN),
                                                                               port.isEnabled());
                            }
                        } else {
                            log.info("eapol will be {} for dev/port updated {}/{}",
                                     (port.isEnabled()) ? "added" : "removed",
                                     devId, port.number());
                            uniTagInformationSet.forEach(uniTag ->
                                oltFlowService.processEapolFilteringObjectives(devId, port.number(),
                                    uniTag.getUpstreamBandwidthProfile(), null,
                                    uniTag.getPonCTag(), port.isEnabled()));
                        }
                        if (port.isEnabled()) {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, devId, port));
                        } else {
                            post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, devId, port));
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
            //Handle case where OLT disconnects during subscriber provisioning
            pendingSubscribers.removeIf(fi -> fi.getDevId().equals(device.id()));
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
