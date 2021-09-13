/*
 * Copyright 2021-present Open Networking Foundation
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
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthTypeCriterion;
import org.onosproject.net.flow.criteria.IPProtocolCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.UdpPortCriterion;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.flowobjective.DefaultFilteringObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.flowobjective.ObjectiveContext;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.host.HostService;
import org.onosproject.net.meter.MeterId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.AccessDevicePort;
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
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onosproject.net.flow.instructions.Instruction.Type.L2MODIFICATION;
import static org.opencord.olt.impl.OltUtils.getPortName;
import static org.opencord.olt.impl.OltUtils.portWithName;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_TP_ID;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_TP_ID_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.DOWNSTREAM_OLT;
import static org.opencord.olt.impl.OsgiPropertyConstants.DOWNSTREAM_ONU;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_ON_NNI;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_ON_NNI_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_V4;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_V4_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_V6;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_DHCP_V6_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_EAPOL;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_EAPOL_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_IGMP_ON_NNI;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_IGMP_ON_NNI_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_PPPOE;
import static org.opencord.olt.impl.OsgiPropertyConstants.ENABLE_PPPOE_DEFAULT;
import static org.opencord.olt.impl.OsgiPropertyConstants.UPSTREAM_OLT;
import static org.opencord.olt.impl.OsgiPropertyConstants.UPSTREAM_ONU;
import static org.opencord.olt.impl.OsgiPropertyConstants.WAIT_FOR_REMOVAL;
import static org.opencord.olt.impl.OsgiPropertyConstants.WAIT_FOR_REMOVAL_DEFAULT;

@Component(immediate = true, property = {
        ENABLE_DHCP_ON_NNI + ":Boolean=" + ENABLE_DHCP_ON_NNI_DEFAULT,
        ENABLE_DHCP_V4 + ":Boolean=" + ENABLE_DHCP_V4_DEFAULT,
        ENABLE_DHCP_V6 + ":Boolean=" + ENABLE_DHCP_V6_DEFAULT,
        ENABLE_IGMP_ON_NNI + ":Boolean=" + ENABLE_IGMP_ON_NNI_DEFAULT,
        ENABLE_EAPOL + ":Boolean=" + ENABLE_EAPOL_DEFAULT,
        ENABLE_PPPOE + ":Boolean=" + ENABLE_PPPOE_DEFAULT,
        DEFAULT_TP_ID + ":Integer=" + DEFAULT_TP_ID_DEFAULT,
        // FIXME remove this option as potentially dangerous in production
        WAIT_FOR_REMOVAL + ":Boolean=" + WAIT_FOR_REMOVAL_DEFAULT
})
public class OltFlowService implements OltFlowServiceInterface {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            bind = "bindSadisService",
            unbind = "unbindSadisService",
            policy = ReferencePolicy.DYNAMIC)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltMeterServiceInterface oltMeterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltDeviceServiceInterface oltDeviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    protected BaseInformationService<BandwidthProfileInformation> bpService;

    private static final String APP_NAME = "org.opencord.olt";
    protected ApplicationId appId;
    private static final Integer MAX_PRIORITY = 10000;
    private static final Integer MIN_PRIORITY = 1000;
    private static final short EAPOL_DEFAULT_VLAN = 4091;
    private static final int NONE_TP_ID = -1;
    private static final String V4 = "V4";
    private static final String V6 = "V6";
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Connect Point status map.
     * Used to keep track of which cp has flows that needs to be removed when the status changes.
     */
    protected Map<AccessDevicePort, OltPortStatus> cpStatus;
    private final ReentrantReadWriteLock cpStatusLock = new ReentrantReadWriteLock();
    private final Lock cpStatusWriteLock = cpStatusLock.writeLock();
    private final Lock cpStatusReadLock = cpStatusLock.readLock();

    /**
     * This map contains the subscriber that have been provisioned by the operator.
     * They may or may not have flows, depending on the port status.
     * The map is used to define whether flows need to be provisioned when a port comes up.
     */
    protected Map<AccessDevicePort, Boolean> provisionedSubscribers;
    private final ReentrantReadWriteLock provisionedSubscribersLock = new ReentrantReadWriteLock();
    private final Lock provisionedSubscribersWriteLock = provisionedSubscribersLock.writeLock();
    private final Lock provisionedSubscribersReadLock = provisionedSubscribersLock.readLock();

    /**
     * Create DHCP trap flow on NNI port(s).
     */
    protected boolean enableDhcpOnNni = ENABLE_DHCP_ON_NNI_DEFAULT;

    /**
     * Enable flows for DHCP v4 if dhcp is required in sadis config.
     **/
    protected boolean enableDhcpV4 = ENABLE_DHCP_V4_DEFAULT;

    /**
     * Enable flows for DHCP v6 if dhcp is required in sadis config.
     **/
    protected boolean enableDhcpV6 = ENABLE_DHCP_V6_DEFAULT;

    /**
     * Create IGMP trap flow on NNI port(s).
     **/
    protected boolean enableIgmpOnNni = ENABLE_IGMP_ON_NNI_DEFAULT;

    /**
     * Send EAPOL authentication trap flows before subscriber provisioning.
     **/
    protected boolean enableEapol = ENABLE_EAPOL_DEFAULT;

    /**
     * Send PPPoED authentication trap flows before subscriber provisioning.
     **/
    protected boolean enablePppoe = ENABLE_PPPOE_DEFAULT;

    /**
     * Default technology profile id that is used for authentication trap flows.
     **/
    protected int defaultTechProfileId = DEFAULT_TP_ID_DEFAULT;

    protected boolean waitForRemoval = WAIT_FOR_REMOVAL_DEFAULT;

    public enum FlowOperation {
        ADD,
        REMOVE,
    }

    public enum FlowDirection {
        UPSTREAM,
        DOWNSTREAM,
    }

    public enum OltFlowsStatus {
        NONE,
        PENDING_ADD,
        ADDED,
        PENDING_REMOVE,
        REMOVED,
        ERROR
    }

    protected InternalFlowListener internalFlowListener;

    @Activate
    public void activate(ComponentContext context) {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication(APP_NAME);
        internalFlowListener = new InternalFlowListener();

        modified(context);

        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(OltFlowsStatus.class)
                .register(FlowDirection.class)
                .register(OltPortStatus.class)
                .register(OltFlowsStatus.class)
                .register(AccessDevicePort.class)
                .build();

        cpStatus = storageService.<AccessDevicePort, OltPortStatus>consistentMapBuilder()
                .withName("volt-cp-status")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer))
                .build().asJavaMap();

        provisionedSubscribers = storageService.<AccessDevicePort, Boolean>consistentMapBuilder()
                .withName("volt-provisioned-subscriber")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer))
                .build().asJavaMap();

        flowRuleService.addListener(internalFlowListener);

        log.info("Started");
    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
        flowRuleService.removeListener(internalFlowListener);
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {

        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

        Boolean o = Tools.isPropertyEnabled(properties, ENABLE_DHCP_ON_NNI);
        if (o != null) {
            enableDhcpOnNni = o;
        }

        Boolean v4 = Tools.isPropertyEnabled(properties, ENABLE_DHCP_V4);
        if (v4 != null) {
            enableDhcpV4 = v4;
        }

        Boolean v6 = Tools.isPropertyEnabled(properties, ENABLE_DHCP_V6);
        if (v6 != null) {
            enableDhcpV6 = v6;
        }

        Boolean p = Tools.isPropertyEnabled(properties, ENABLE_IGMP_ON_NNI);
        if (p != null) {
            enableIgmpOnNni = p;
        }

        Boolean eap = Tools.isPropertyEnabled(properties, ENABLE_EAPOL);
        if (eap != null) {
            enableEapol = eap;
        }

        Boolean pppoe = Tools.isPropertyEnabled(properties, ENABLE_PPPOE);
        if (pppoe != null) {
            enablePppoe = pppoe;
        }

        Boolean wait = Tools.isPropertyEnabled(properties, WAIT_FOR_REMOVAL);
        if (wait != null) {
            waitForRemoval = wait;
        }

        String tpId = get(properties, DEFAULT_TP_ID);
        defaultTechProfileId = isNullOrEmpty(tpId) ? DEFAULT_TP_ID_DEFAULT : Integer.parseInt(tpId.trim());

        log.info("Modified. Values = enableDhcpOnNni: {}, enableDhcpV4: {}, " +
                        "enableDhcpV6:{}, enableIgmpOnNni:{}, " +
                        "enableEapol:{}, enablePppoe:{}, defaultTechProfileId:{}," +
                        "waitForRemoval:{}",
                enableDhcpOnNni, enableDhcpV4, enableDhcpV6,
                enableIgmpOnNni, enableEapol, enablePppoe,
                defaultTechProfileId, waitForRemoval);

    }

    @Override
    public ImmutableMap<AccessDevicePort, OltPortStatus> getConnectPointStatus() {
        try {
            cpStatusReadLock.lock();
            return ImmutableMap.copyOf(cpStatus);
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    @Override
    public ImmutableMap<AccessDevicePort, Set<UniTagInformation>> getProgrammedSubscribers() {

        Map<AccessDevicePort, Set<UniTagInformation>> subscribers =
                new HashMap<>();
        try {
            cpStatusReadLock.lock();
            cpStatus.forEach((accessPort, status) -> {
                Set<UniTagInformation> uniTags = subscribers.getOrDefault(accessPort, new HashSet<>());
                if (hasSubscriberFlows(accessPort.getPort())) {
                    Port port = deviceService.getPort(accessPort.connectPoint());
                    if (port == null) {
                        log.warn("Cannot find port for cp {} on device {}", accessPort.connectPoint(),
                                 accessPort.connectPoint().deviceId());
                        return;
                    }
                    String portName = accessPort.name();
                    SubscriberAndDeviceInformation info = subsService.get(portName);
                    if (info == null) {
                        log.error("Cannot find information for port {}", portWithName(port));
                        return;
                    }
                    log.info("adding to programmed {} ", uniTags);
                    uniTags.addAll(info.uniTagList());
//                    info.uniTagList().addAll(uniTags);
                }
                log.info("adding to subscribers {} ", uniTags);
                subscribers.put(accessPort, uniTags);
            });
            log.info("returning {} ", subscribers);
            return ImmutableMap.copyOf(subscribers);
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    @Override
    public Map<AccessDevicePort, Boolean> getRequestedSubscribers() {
        try {
            provisionedSubscribersReadLock.lock();
            return ImmutableMap.copyOf(provisionedSubscribers);
        } finally {
            provisionedSubscribersReadLock.unlock();
        }
    }

    @Override
    public void handleNniFlows(Device device, Port port, FlowOperation action) {

        // always handle the LLDP flow
        processLldpFilteringObjective(device.id(), port, action);

        if (enableDhcpOnNni) {
            if (enableDhcpV4) {
                log.debug("Installing DHCPv4 trap flow on NNI {} for device {}", portWithName(port), device.id());
                processDhcpFilteringObjectives(device.id(), port, action, FlowDirection.DOWNSTREAM,
                        67, 68, EthType.EtherType.IPV4.ethType(), IPv4.PROTOCOL_UDP,
                        null, null, NONE_TP_ID, VlanId.NONE, VlanId.ANY, null);
            }
            if (enableDhcpV6) {
                log.debug("Installing DHCPv6 trap flow on NNI {} for device {}", portWithName(port), device.id());
                processDhcpFilteringObjectives(device.id(), port, action, FlowDirection.DOWNSTREAM,
                        546, 547, EthType.EtherType.IPV6.ethType(), IPv6.PROTOCOL_UDP,
                        null, null, NONE_TP_ID, VlanId.NONE, VlanId.ANY, null);
            }
        } else {
            log.info("DHCP is not required on NNI {} for device {}", portWithName(port), device.id());
        }

        if (enableIgmpOnNni) {
            log.debug("Installing IGMP flow on NNI {} for device {}", portWithName(port), device.id());
            processIgmpFilteringObjectives(device.id(), port, action, FlowDirection.DOWNSTREAM,
                    null, null, NONE_TP_ID, VlanId.NONE, VlanId.ANY, -1);
        }

        if (enablePppoe) {
            log.debug("Installing PPPoE flow on NNI {} for device {}", port.number(), device.id());
            processPPPoEDFilteringObjectives(device.id(), port, action, FlowDirection.DOWNSTREAM,
                    null, null, NONE_TP_ID, VlanId.NONE, VlanId.ANY, null);
        }
    }

    @Override
    public boolean handleBasicPortFlows(DiscoveredSubscriber sub, String bandwidthProfileId,
                                        String oltBandwidthProfileId) {

        // we only need to something if EAPOL is enabled
        if (!enableEapol) {
            return true;
        }

        if (sub.status == DiscoveredSubscriber.Status.ADDED) {
            return addDefaultFlows(sub, bandwidthProfileId, oltBandwidthProfileId);
        } else if (sub.status == DiscoveredSubscriber.Status.REMOVED) {
            return removeDefaultFlows(sub, bandwidthProfileId, oltBandwidthProfileId);
        } else {
            log.error("don't know how to handle {}", sub);
            return false;
        }

    }

    private boolean addDefaultFlows(DiscoveredSubscriber sub, String bandwidthProfileId, String oltBandwidthProfileId) {

        if (!oltMeterService.createMeter(sub.device.id(), bandwidthProfileId)) {
            if (log.isTraceEnabled()) {
                log.trace("waiting on meter for bp {} and sub {}", bandwidthProfileId, sub);
            }
            return false;
        }
        if (hasDefaultEapol(sub.port)) {
            return true;
        }
        return handleEapolFlow(sub, bandwidthProfileId,
                               oltBandwidthProfileId, FlowOperation.ADD, VlanId.vlanId(EAPOL_DEFAULT_VLAN));

    }

    private boolean removeDefaultFlows(DiscoveredSubscriber sub, String bandwidthProfile, String oltBandwidthProfile) {
        // NOTE that we are not checking for meters as they must have been created to install the flow in first place
        return handleEapolFlow(sub, bandwidthProfile, oltBandwidthProfile,
                FlowOperation.REMOVE, VlanId.vlanId(EAPOL_DEFAULT_VLAN));
    }

    @Override
    public boolean handleSubscriberFlows(DiscoveredSubscriber sub, String defaultBandwidthProfile,
                                         String multicastServiceName) {
        // NOTE that we are taking defaultBandwithProfile as a parameter as that can be configured in the Olt component
        if (sub.status == DiscoveredSubscriber.Status.ADDED) {
            return addSubscriberFlows(sub, defaultBandwidthProfile, multicastServiceName);
        } else if (sub.status == DiscoveredSubscriber.Status.REMOVED) {
            return removeSubscriberFlows(sub, defaultBandwidthProfile, multicastServiceName);
        } else {
            log.error("don't know how to handle {}", sub);
            return false;
        }
    }

    private boolean addSubscriberFlows(DiscoveredSubscriber sub, String defaultBandwithProfile,
                                       String multicastServiceName) {
        if (log.isTraceEnabled()) {
            log.trace("Provisioning of subscriber on {} started", portWithName(sub.port));
        }
        if (enableEapol) {
            if (hasDefaultEapol(sub.port)) {
                // remove EAPOL flow and throw exception so that we'll retry later
                if (!isDefaultEapolPendingRemoval(sub.port)) {
                    removeDefaultFlows(sub, defaultBandwithProfile, defaultBandwithProfile);
                }

                if (waitForRemoval) {
                    // NOTE wait for removal is a flag only needed to make sure VOLTHA
                    // does not explode with the flows remove/add in the same batch
                    log.debug("Awaiting for default flows removal for {}", portWithName(sub.port));
                    return false;
                } else {
                    log.warn("continuing provisioning on {}", portWithName(sub.port));
                }
            }

        }

        // NOTE createMeters will return if the meters are not installed
        if (!oltMeterService.createMeters(sub.device.id(),
                sub.subscriberAndDeviceInformation)) {
            return false;
        }

        // NOTE we need to add the DHCP flow regardless so that the host can be discovered and the MacAddress added
        handleSubscriberDhcpFlows(sub.device.id(), sub.port, FlowOperation.ADD,
                sub.subscriberAndDeviceInformation);

        if (isMacLearningEnabled(sub.subscriberAndDeviceInformation)
                && !isMacAddressAvailable(sub.device.id(), sub.port,
                sub.subscriberAndDeviceInformation)) {
            log.debug("Awaiting for macAddress on {}", portWithName(sub.port));
            return false;
        }

        handleSubscriberDataFlows(sub.device, sub.port, FlowOperation.ADD,
                sub.subscriberAndDeviceInformation, multicastServiceName);

        handleSubscriberEapolFlows(sub, FlowOperation.ADD, sub.subscriberAndDeviceInformation);

        handleSubscriberIgmpFlows(sub, FlowOperation.ADD);

        log.info("Provisioning of subscriber on {} completed", portWithName(sub.port));
        return true;
    }

    private boolean removeSubscriberFlows(DiscoveredSubscriber sub, String defaultBandwithProfile,
                                          String multicastServiceName) {

        if (log.isTraceEnabled()) {
            log.trace("Removal of subscriber on {} started",
                      portWithName(sub.port));
        }
        SubscriberAndDeviceInformation si = subsService.get(sub.portName());
        if (si == null) {
            log.error("Subscriber information not found in sadis for port {} during subscriber removal",
                      portWithName(sub.port));
            // NOTE that we are returning true so that the subscriber is removed from the queue
            // and we can move on provisioning others
            return true;
        }

        if (hasDhcpFlows(sub.port)) {
            handleSubscriberDhcpFlows(sub.device.id(), sub.port, FlowOperation.REMOVE, si);
        }

        if (hasSubscriberFlows(sub.port)) {
            if (enableEapol) {
                // remove the tagged eapol
                handleSubscriberEapolFlows(sub, FlowOperation.REMOVE, si);

                // and add the default one back
                if (sub.port.isEnabled()) {
                    // NOTE we remove the subscriber when the port goes down
                    // but in that case we don't need to add default eapol
                    handleEapolFlow(sub, defaultBandwithProfile, defaultBandwithProfile,
                            FlowOperation.ADD, VlanId.vlanId(EAPOL_DEFAULT_VLAN));
                }
            }
            handleSubscriberDataFlows(sub.device, sub.port, FlowOperation.REMOVE, si, multicastServiceName);
        }

        handleSubscriberIgmpFlows(sub, FlowOperation.REMOVE);

        log.info("Removal of subscriber on {} completed", portWithName(sub.port));
        return true;
    }

    @Override
    public boolean hasDefaultEapol(Port port) {
        try {
            cpStatusReadLock.lock();
            OltPortStatus status = getOltPortStatus(port);
            // NOTE we consider ERROR as a present EAPOL flow as ONOS
            // will keep trying to add it
            return status != null && (status.defaultEapolStatus == OltFlowsStatus.ADDED ||
                    status.defaultEapolStatus == OltFlowsStatus.PENDING_ADD ||
                    status.defaultEapolStatus == OltFlowsStatus.ERROR);
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    private OltPortStatus getOltPortStatus(Port port) {
        return cpStatus.get(new AccessDevicePort(port));
    }

    public boolean isDefaultEapolPendingRemoval(Port port) {
        try {
            cpStatusReadLock.lock();
            OltPortStatus status = getOltPortStatus(port);
            if (log.isTraceEnabled()) {
                log.trace("Status during EAPOL flow check {}", status);
            }
            return status != null && status.defaultEapolStatus == OltFlowsStatus.PENDING_REMOVE;
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    @Override
    public boolean hasDhcpFlows(Port port) {
        try {
            cpStatusReadLock.lock();
            OltPortStatus status = getOltPortStatus(port);
            if (log.isTraceEnabled()) {
                log.trace("Status during DHCP flow check {}", status);
            }
            return status != null &&
                    (status.dhcpStatus == OltFlowsStatus.ADDED ||
                            status.dhcpStatus == OltFlowsStatus.PENDING_ADD);
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    @Override
    public boolean hasSubscriberFlows(Port port) {
        try {
            cpStatusReadLock.lock();
            OltPortStatus status = getOltPortStatus(port);
            if (log.isTraceEnabled()) {
                log.trace("Status during subscriber flow check {}", status);
            }
            return status != null && (status.subscriberFlowsStatus == OltFlowsStatus.ADDED ||
                    status.subscriberFlowsStatus == OltFlowsStatus.PENDING_ADD);
        } finally {
            cpStatusReadLock.unlock();
        }
    }

    @Override
    public void purgeDeviceFlows(DeviceId deviceId) {
        log.debug("Purging flows on device {}", deviceId);
        flowRuleService.purgeFlowRules(deviceId);

        // removing the status from the cpStatus map
        try {
            cpStatusWriteLock.lock();
            Iterator<Map.Entry<AccessDevicePort, OltPortStatus>> iter = cpStatus.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<AccessDevicePort, OltPortStatus> entry = iter.next();
                if (entry.getKey().connectPoint().deviceId().equals(deviceId)) {
                    cpStatus.remove(entry.getKey());
                }
            }
        } finally {
            cpStatusWriteLock.unlock();
        }

        // removing subscribers from the provisioned map
        try {
            provisionedSubscribersWriteLock.lock();
            Iterator<Map.Entry<AccessDevicePort, Boolean>> iter = provisionedSubscribers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<AccessDevicePort, Boolean> entry = iter.next();
                if (entry.getKey().connectPoint().deviceId().equals(deviceId)) {
                    log.info("processing subscriber {} for removal for device {}", entry.getKey(), deviceId);
                    provisionedSubscribers.remove(entry.getKey());
                }
            }
        } finally {
            provisionedSubscribersWriteLock.unlock();
        }
    }

    @Override
    public boolean isSubscriberProvisioned(AccessDevicePort cp) {
        try {
            provisionedSubscribersReadLock.lock();
            Boolean provisioned = provisionedSubscribers.get(cp);
            if (provisioned == null || !provisioned) {
                return false;
            }
        } finally {
            provisionedSubscribersReadLock.unlock();
        }
        return true;
    }

    @Override
    public void updateProvisionedSubscriberStatus(AccessDevicePort cp, Boolean status) {
        try {
            provisionedSubscribersWriteLock.lock();
            provisionedSubscribers.put(cp, status);
        } finally {
            provisionedSubscribersWriteLock.unlock();
        }
    }

    private boolean handleEapolFlow(DiscoveredSubscriber sub, String bandwidthProfile,
                                    String oltBandwidthProfile, FlowOperation action, VlanId vlanId) {

        // NOTE we only need to keep track of the default EAPOL flow in the
        // connectpoint status map
        if (vlanId.id().equals(EAPOL_DEFAULT_VLAN)) {
            OltFlowsStatus status = action == FlowOperation.ADD ?
                    OltFlowsStatus.PENDING_ADD : OltFlowsStatus.PENDING_REMOVE;
            updateConnectPointStatus(new AccessDevicePort(sub.port), status, OltFlowsStatus.NONE, OltFlowsStatus.NONE);

        }

        DefaultFilteringObjective.Builder filterBuilder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        int techProfileId = getDefaultTechProfileId(sub.port);
        MeterId meterId = oltMeterService.getMeterIdForBandwidthProfile(sub.device.id(), bandwidthProfile);

        // in the delete case the meter should still be there as we remove
        // the meters only if no flows are pointing to them
        if (meterId == null) {
            log.debug("MeterId is null for BandwidthProfile {} on device {}",
                    bandwidthProfile, sub.device.id());
            return false;
        }

        MeterId oltMeterId = oltMeterService.getMeterIdForBandwidthProfile(sub.device.id(), oltBandwidthProfile);
        if (oltMeterId == null) {
            log.debug("MeterId is null for OltBandwidthProfile {} on device {}",
                    oltBandwidthProfile, sub.device.id());
            return false;
        }

        log.info("Preforming {} on EAPOL flow for {} with vlanId {} and meterId {}",
                action, portWithName(sub.port), vlanId, meterId);

        FilteringObjective.Builder eapolAction;

        if (action == FlowOperation.ADD) {
            eapolAction = filterBuilder.permit();
        } else if (action == FlowOperation.REMOVE) {
            eapolAction = filterBuilder.deny();
        } else {
            log.error("Operation {} not supported", action);
            return false;
        }

        FilteringObjective.Builder baseEapol = eapolAction
                .withKey(Criteria.matchInPort(sub.port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()));

        // NOTE we only need to add the treatment to install the flow,
        // we can remove it based in the match
        FilteringObjective.Builder eapol;

        TrafficTreatment treatment = treatmentBuilder
                .meter(meterId)
                .writeMetadata(createTechProfValueForWriteMetadata(
                        vlanId,
                        techProfileId, oltMeterId), 0)
                .setOutput(PortNumber.CONTROLLER)
                .pushVlan()
                .setVlanId(vlanId)
                .build();
        eapol = baseEapol
                .withMeta(treatment);

        FilteringObjective eapolObjective = eapol
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("EAPOL flow {} for {} with details {}",
                                action, portWithName(sub.port), objective);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("Cannot {} eapol flow for {} : {}", action,
                                  portWithName(sub.port), error);

                        if (vlanId.id().equals(EAPOL_DEFAULT_VLAN)) {
                            updateConnectPointStatus(new AccessDevicePort(sub.port),
                                                     OltFlowsStatus.ERROR, null, null);
                        }
                    }
                });

        flowObjectiveService.filter(sub.device.id(), eapolObjective);

        log.info("Created EAPOL filter to {} for {}: {}", action, portWithName(sub.port), eapol);
        return true;
    }

    // FIXME it's confusing that si is not necessarily inside the DiscoveredSubscriber
    private boolean handleSubscriberEapolFlows(DiscoveredSubscriber sub, FlowOperation action,
                                               SubscriberAndDeviceInformation si) {
        if (!enableEapol) {
            return true;
        }
        // TODO verify we need an EAPOL flow for EACH service
        AtomicBoolean success = new AtomicBoolean(true);
        si.uniTagList().forEach(u -> {
            if (!handleEapolFlow(sub, u.getUpstreamBandwidthProfile(),
                    u.getUpstreamOltBandwidthProfile(),
                    action, u.getPonCTag())) {
                //
                log.error("Failed to install EAPOL with suscriber tags");
                //TODO this sets it for all services, maybe some services succeeded.
                success.set(false);
            }
        });
        return success.get();
    }

    private void handleSubscriberIgmpFlows(DiscoveredSubscriber sub, FlowOperation action) {
        sub.subscriberAndDeviceInformation.uniTagList().forEach(uti -> {
            if (uti.getIsIgmpRequired()) {
                DeviceId deviceId = sub.device.id();
                // if we reached here a meter already exists
                MeterId meterId = oltMeterService
                        .getMeterIdForBandwidthProfile(deviceId, uti.getUpstreamBandwidthProfile());
                MeterId oltMeterId = oltMeterService
                        .getMeterIdForBandwidthProfile(deviceId, uti.getUpstreamOltBandwidthProfile());

                processIgmpFilteringObjectives(deviceId, sub.port,
                        action, FlowDirection.UPSTREAM, meterId, oltMeterId, uti.getTechnologyProfileId(),
                        uti.getPonCTag(), uti.getUniTagMatch(), uti.getUsPonCTagPriority());
            }
        });
    }

    private boolean checkSadisRunning() {
        if (bpService == null) {
            log.warn("Sadis is not running");
            return false;
        }
        return true;
    }

    private int getDefaultTechProfileId(Port port) {
        if (!checkSadisRunning()) {
            return defaultTechProfileId;
        }
        if (port != null) {
            SubscriberAndDeviceInformation info = subsService.get(getPortName(port));
            if (info != null && info.uniTagList().size() == 1) {
                return info.uniTagList().get(0).getTechnologyProfileId();
            }
        }
        return defaultTechProfileId;
    }

    protected Long createTechProfValueForWriteMetadata(VlanId cVlan, int techProfileId, MeterId upstreamOltMeterId) {
        Long writeMetadata;

        if (cVlan == null || VlanId.NONE.equals(cVlan)) {
            writeMetadata = (long) techProfileId << 32;
        } else {
            writeMetadata = ((long) (cVlan.id()) << 48 | (long) techProfileId << 32);
        }
        if (upstreamOltMeterId == null) {
            return writeMetadata;
        } else {
            return writeMetadata | upstreamOltMeterId.id();
        }
    }

    private void processLldpFilteringObjective(DeviceId deviceId, Port port, FlowOperation action) {
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        FilteringObjective lldp = (action == FlowOperation.ADD ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.LLDP.ethType()))
                .withMeta(DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("LLDP filter for {} {}.", portWithName(port), action);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("LLDP filter for {} failed {} because {}", portWithName(port), action,
                                error);
                    }
                });

        flowObjectiveService.filter(deviceId, lldp);
    }

    protected void handleSubscriberDhcpFlows(DeviceId deviceId, Port port,
                                             FlowOperation action,
                                             SubscriberAndDeviceInformation si) {
        si.uniTagList().forEach(uti -> {

            if (!uti.getIsDhcpRequired()) {
                return;
            }

            log.info("{} DHCP flows for subscriber {} on {} and service {}",
                    action, si.id(), portWithName(port), uti.getServiceName());

            // if we reached here a meter already exists
            MeterId meterId = oltMeterService
                    .getMeterIdForBandwidthProfile(deviceId, uti.getUpstreamBandwidthProfile());
            MeterId oltMeterId = oltMeterService
                    .getMeterIdForBandwidthProfile(deviceId, uti.getUpstreamOltBandwidthProfile());

            if (enableDhcpV4) {
                processDhcpFilteringObjectives(deviceId, port, action, FlowDirection.UPSTREAM, 68, 67,
                        EthType.EtherType.IPV4.ethType(), IPv4.PROTOCOL_UDP, meterId, oltMeterId,
                        uti.getTechnologyProfileId(), uti.getPonCTag(), uti.getUniTagMatch(),
                        (byte) uti.getUsPonCTagPriority());
            }
            if (enableDhcpV6) {
                log.error("DHCP V6 not supported for subscribers");
            }
        });
    }

    protected void handleSubscriberDataFlows(Device device, Port port,
                                             FlowOperation action,
                                             SubscriberAndDeviceInformation si, String multicastServiceName) {

        Optional<Port> nniPort = oltDeviceService.getNniPort(device);
        if (nniPort.isEmpty()) {
            log.error("Cannot configure DP flows as upstream port is not configuredfor subscriber {} on {}",
                    si.id(), portWithName(port));
            return;
        }
        si.uniTagList().forEach(uti -> {

            if (multicastServiceName.equals(uti.getServiceName())) {
                log.debug("This is the multicast service ({}) for subscriber {} on {}, " +
                                "dataplane flows are not needed",
                        uti.getServiceName(), si.id(), portWithName(port));
                return;
            }

            log.info("{} Data plane flows for subscriber {} on {} and service {}",
                    action, si.id(), portWithName(port), uti.getServiceName());

            // upstream flows
            MeterId usMeterId = oltMeterService
                    .getMeterIdForBandwidthProfile(device.id(), uti.getUpstreamBandwidthProfile());
            MeterId oltUsMeterId = oltMeterService
                    .getMeterIdForBandwidthProfile(device.id(), uti.getUpstreamOltBandwidthProfile());
            processUpstreamDataFilteringObjects(device.id(), port, nniPort.get(), action, usMeterId,
                    oltUsMeterId, uti.getTechnologyProfileId(), uti.getPonSTag(), uti.getPonCTag(),
                    uti.getUniTagMatch(), uti.getUsPonCTagPriority(), uti.getUsPonSTagPriority());

            // downstream flows
            MeterId dsMeterId = oltMeterService
                    .getMeterIdForBandwidthProfile(device.id(), uti.getDownstreamBandwidthProfile());
            MeterId oltDsMeterId = oltMeterService
                    .getMeterIdForBandwidthProfile(device.id(), uti.getDownstreamOltBandwidthProfile());
            processDownstreamDataFilteringObjects(device.id(), port, nniPort.get(), action, dsMeterId,
                    oltDsMeterId, uti.getTechnologyProfileId(), uti.getPonSTag(), uti.getPonCTag(),
                    uti.getUniTagMatch(), uti.getDsPonCTagPriority(), uti.getUsPonCTagPriority(),
                    getMacAddress(device.id(), port, uti));
        });
    }

    private void processDhcpFilteringObjectives(DeviceId deviceId, Port port,
                                                FlowOperation action, FlowDirection direction,
                                                int udpSrc, int udpDst, EthType ethType, byte protocol,
                                                MeterId meterId, MeterId oltMeterId, int techProfileId,
                                                VlanId cTag, VlanId unitagMatch, Byte vlanPcp) {
        log.debug("{} DHCP filtering objectives on {}", action, portWithName(port));

        OltFlowsStatus status = action.equals(FlowOperation.ADD) ?
                OltFlowsStatus.PENDING_ADD : OltFlowsStatus.PENDING_REMOVE;
        updateConnectPointStatus(new AccessDevicePort(port), null, null, status);

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        if (meterId != null) {
            treatmentBuilder.meter(meterId);
        }

        if (techProfileId != NONE_TP_ID) {
            treatmentBuilder.writeMetadata(
                    createTechProfValueForWriteMetadata(unitagMatch, techProfileId, oltMeterId), 0);
        }

        FilteringObjective.Builder dhcpBuilder = (action == FlowOperation.ADD ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(ethType))
                .addCondition(Criteria.matchIPProtocol(protocol))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(udpSrc)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(udpDst)))
                .fromApp(appId)
                .withPriority(MAX_PRIORITY);

        //VLAN changes and PCP matching need to happen only in the upstream directions
        if (direction == FlowDirection.UPSTREAM) {
            treatmentBuilder.setVlanId(cTag);
            if (!VlanId.vlanId(VlanId.NO_VID).equals(unitagMatch)) {
                dhcpBuilder.addCondition(Criteria.matchVlanId(unitagMatch));
            }
            if (vlanPcp != null) {
                treatmentBuilder.setVlanPcp(vlanPcp);
            }
        }

        dhcpBuilder.withMeta(treatmentBuilder
                .setOutput(PortNumber.CONTROLLER).build());


        FilteringObjective dhcpUpstream = dhcpBuilder.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.info("DHCP {} filter for {} {}.",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6,
                         portWithName(port),
                        action);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                log.error("DHCP {} filter for {} failed {} because {}",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6,
                          portWithName(port),
                        action,
                        error);
                ConnectPoint cp = new ConnectPoint(deviceId, port.number());
                updateConnectPointStatus(new AccessDevicePort(port), null, null, OltFlowsStatus.ERROR);
            }
        });
        flowObjectiveService.filter(deviceId, dhcpUpstream);
    }

    private void processIgmpFilteringObjectives(DeviceId deviceId, Port port,
                                                FlowOperation action, FlowDirection direction,
                                                MeterId meterId, MeterId oltMeterId, int techProfileId,
                                                VlanId cTag, VlanId unitagMatch, int vlanPcp) {

        DefaultFilteringObjective.Builder filterBuilder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        if (direction == FlowDirection.UPSTREAM) {

            if (techProfileId != NONE_TP_ID) {
                treatmentBuilder.writeMetadata(createTechProfValueForWriteMetadata(null,
                        techProfileId, oltMeterId), 0);
            }


            if (meterId != null) {
                treatmentBuilder.meter(meterId);
            }

            if (!VlanId.vlanId(VlanId.NO_VID).equals(unitagMatch)) {
                filterBuilder.addCondition(Criteria.matchVlanId(unitagMatch));
            }

            if (!VlanId.vlanId(VlanId.NO_VID).equals(cTag)) {
                treatmentBuilder.setVlanId(cTag);
            }

            if (vlanPcp != -1) {
                treatmentBuilder.setVlanPcp((byte) vlanPcp);
            }
        }

        filterBuilder = (action == FlowOperation.ADD) ? filterBuilder.permit() : filterBuilder.deny();

        FilteringObjective igmp = filterBuilder
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_IGMP))
                .withMeta(treatmentBuilder
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Igmp filter for {} {}.", portWithName(port), action);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("Igmp filter for {} failed {} because {}.", portWithName(port), action,
                                error);
                    }
                });

        flowObjectiveService.filter(deviceId, igmp);

    }

    private void processPPPoEDFilteringObjectives(DeviceId deviceId, Port port,
                                                  FlowOperation action, FlowDirection direction,
                                                  MeterId meterId, MeterId oltMeterId, int techProfileId,
                                                  VlanId cTag, VlanId unitagMatch, Byte vlanPcp) {

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        if (meterId != null) {
            treatmentBuilder.meter(meterId);
        }

        if (techProfileId != NONE_TP_ID) {
            treatmentBuilder.writeMetadata(createTechProfValueForWriteMetadata(cTag, techProfileId, oltMeterId), 0);
        }

        DefaultFilteringObjective.Builder pppoedBuilder = ((action == FlowOperation.ADD)
                ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.PPPoED.ethType()))
                .fromApp(appId)
                .withPriority(10000);

        if (direction == FlowDirection.UPSTREAM) {
            treatmentBuilder.setVlanId(cTag);
            if (!VlanId.vlanId(VlanId.NO_VID).equals(unitagMatch)) {
                pppoedBuilder.addCondition(Criteria.matchVlanId(unitagMatch));
            }
            if (vlanPcp != null) {
                treatmentBuilder.setVlanPcp(vlanPcp);
            }
        }
        pppoedBuilder = pppoedBuilder.withMeta(treatmentBuilder.setOutput(PortNumber.CONTROLLER).build());

        FilteringObjective pppoed = pppoedBuilder
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("PPPoED filter for {} {}.", portWithName(port), action);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("PPPoED filter for {} failed {} because {}", portWithName(port),
                                action, error);
                    }
                });
        flowObjectiveService.filter(deviceId, pppoed);
    }

    private void processUpstreamDataFilteringObjects(DeviceId deviceId, Port port, Port nniPort,
                                                     FlowOperation action,
                                                     MeterId upstreamMeterId,
                                                     MeterId upstreamOltMeterId,
                                                     int techProfileId, VlanId sTag,
                                                     VlanId cTag, VlanId unitagMatch, int cTagPcp, int sTagPcp) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(port.number())
                .matchVlanId(unitagMatch)
                .build();

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        //if the subscriberVlan (cTag) is different than ANY it needs to set.
        if (cTag.toShort() != VlanId.ANY_VALUE) {
            treatmentBuilder.pushVlan()
                    .setVlanId(cTag);
        }

        if (cTagPcp != -1) {
            treatmentBuilder.setVlanPcp((byte) cTagPcp);
        }

        treatmentBuilder.pushVlan()
                .setVlanId(sTag);

        if (sTagPcp != -1) {
            treatmentBuilder.setVlanPcp((byte) sTagPcp);
        }

        treatmentBuilder.setOutput(nniPort.number())
                .writeMetadata(createMetadata(cTag,
                        techProfileId, nniPort.number()), 0L);

        DefaultAnnotations.Builder annotationBuilder = DefaultAnnotations.builder();

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
            annotationBuilder.set(UPSTREAM_ONU, upstreamMeterId.toString());
        }
        if (upstreamOltMeterId != null) {
            treatmentBuilder.meter(upstreamOltMeterId);
            annotationBuilder.set(UPSTREAM_OLT, upstreamOltMeterId.toString());
        }

        DefaultForwardingObjective.Builder flowBuilder = createForwardingObjectiveBuilder(selector,
                treatmentBuilder.build(), MIN_PRIORITY,
                annotationBuilder.build());

        ObjectiveContext context = new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.info("Upstream Data plane filter installed for {} {}.",
                         portWithName(port), action);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                log.error("Upstream Data plane filter for {} failed {} because {}.",
                         portWithName(port), action, error);
                updateConnectPointStatus(new AccessDevicePort(port), null, OltFlowsStatus.ERROR, null);
            }
        };

        ForwardingObjective flow = null;
        if (action == FlowOperation.ADD) {
            flow = flowBuilder.add(context);
        } else if (action == FlowOperation.REMOVE) {
            flow = flowBuilder.remove(context);
        } else {
            log.error("Flow action not supported: {}", action);
        }

        if (flow != null) {
            flowObjectiveService.forward(deviceId, flow);
        }
    }

    private void processDownstreamDataFilteringObjects(DeviceId deviceId, Port port, Port nniPort,
                                                       FlowOperation action,
                                                       MeterId downstreamMeterId,
                                                       MeterId downstreamOltMeterId,
                                                       int techProfileId, VlanId sTag,
                                                       VlanId cTag, VlanId unitagMatch, int vlanPcpDs, int vlanPcpUs,
                                                       MacAddress macAddress) {
        //subscriberVlan can be any valid Vlan here including ANY to make sure the packet is tagged
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder()
                .matchVlanId(sTag)
                .matchInPort(nniPort.number())
                .matchInnerVlanId(cTag);

        if (cTag.toShort() != VlanId.ANY_VALUE) {
            selectorBuilder.matchMetadata(cTag.toShort());
        }

        if (vlanPcpDs != -1) {
            selectorBuilder.matchVlanPcp((byte) vlanPcpDs);
        }

        if (macAddress != null) {
            selectorBuilder.matchEthDst(macAddress);
        }

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                .popVlan()
                .setOutput(port.number());

        treatmentBuilder.writeMetadata(createMetadata(cTag,
                techProfileId,
                port.number()), 0);

        // Upstream pbit is used to remark inner vlan pbit.
        // Upstream is used to avoid trusting the BNG to send the packet with correct pbit.
        // this is done because ds mode 0 is used because ds mode 3 or 6 that allow for
        // all pbit acceptance are not widely supported by vendors even though present in
        // the OMCI spec.
        if (vlanPcpUs != -1) {
            treatmentBuilder.setVlanPcp((byte) vlanPcpUs);
        }

        if (!VlanId.NONE.equals(unitagMatch) &&
                cTag.toShort() != VlanId.ANY_VALUE) {
            treatmentBuilder.setVlanId(unitagMatch);
        }

        DefaultAnnotations.Builder annotationBuilder = DefaultAnnotations.builder();

        if (downstreamMeterId != null) {
            treatmentBuilder.meter(downstreamMeterId);
            annotationBuilder.set(DOWNSTREAM_ONU, downstreamMeterId.toString());
        }

        if (downstreamOltMeterId != null) {
            treatmentBuilder.meter(downstreamOltMeterId);
            annotationBuilder.set(DOWNSTREAM_OLT, downstreamOltMeterId.toString());
        }

        DefaultForwardingObjective.Builder flowBuilder = createForwardingObjectiveBuilder(selectorBuilder.build(),
                treatmentBuilder.build(), MIN_PRIORITY, annotationBuilder.build());

        ObjectiveContext context = new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.info("Downstream Data plane filter for {} {}.",
                         portWithName(port), action);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                log.info("Downstream Data plane filter for {} failed {} because {}.",
                         portWithName(port), action, error);
                updateConnectPointStatus(new AccessDevicePort(port), null, OltFlowsStatus.ERROR, null);
            }
        };

        ForwardingObjective flow = null;
        if (action == FlowOperation.ADD) {
            flow = flowBuilder.add(context);
        } else if (action == FlowOperation.REMOVE) {
            flow = flowBuilder.remove(context);
        } else {
            log.error("Flow action not supported: {}", action);
        }

        if (flow != null) {
            if (log.isTraceEnabled()) {
                log.trace("Forwarding rule {}", flow);
            }
            flowObjectiveService.forward(deviceId, flow);
        }
    }

    private DefaultForwardingObjective.Builder createForwardingObjectiveBuilder(TrafficSelector selector,
                                                                                TrafficTreatment treatment,
                                                                                Integer priority,
                                                                                Annotations annotations) {
        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(priority)
                .makePermanent()
                .withSelector(selector)
                .withAnnotations(annotations)
                .fromApp(appId)
                .withTreatment(treatment);
    }

    private Long createMetadata(VlanId innerVlan, int techProfileId, PortNumber egressPort) {
        if (techProfileId == NONE_TP_ID) {
            techProfileId = DEFAULT_TP_ID_DEFAULT;
        }

        return ((long) (innerVlan.id()) << 48 | (long) techProfileId << 32) | egressPort.toLong();
    }

    private boolean isMacLearningEnabled(SubscriberAndDeviceInformation si) {
        AtomicBoolean requiresMacLearning = new AtomicBoolean();
        requiresMacLearning.set(false);

        si.uniTagList().stream().forEach(uniTagInfo -> {
            if (uniTagInfo.getEnableMacLearning()) {
                requiresMacLearning.set(true);
            }
        });

        return requiresMacLearning.get();
    }

    /**
     * Checks whether the subscriber has the MacAddress configured or discovered.
     *
     * @param deviceId DeviceId for this subscriber
     * @param port     Port for this subscriber
     * @param si       SubscriberAndDeviceInformation
     * @return boolean
     */
    protected boolean isMacAddressAvailable(DeviceId deviceId, Port port, SubscriberAndDeviceInformation si) {
        AtomicBoolean isConfigured = new AtomicBoolean();
        isConfigured.set(true);

        si.uniTagList().forEach(uniTagInfo -> {
            boolean configureMac = isMacAddressValid(uniTagInfo);
            boolean discoveredMac = false;
            Optional<Host> optHost = hostService.getConnectedHosts(new ConnectPoint(deviceId, port.number()))
                    .stream().filter(host -> host.vlan().equals(uniTagInfo.getPonCTag())).findFirst();
            if (optHost.isPresent() && optHost.get().mac() != null) {
                discoveredMac = true;
            }
            if (!configureMac && !discoveredMac) {
                isConfigured.set(false);
            }
        });

        return isConfigured.get();
    }

    protected MacAddress getMacAddress(DeviceId deviceId, Port port, UniTagInformation uniTagInfo) {
        boolean configuredMac = isMacAddressValid(uniTagInfo);
        if (configuredMac) {
            return MacAddress.valueOf(uniTagInfo.getConfiguredMacAddress());
        } else if (uniTagInfo.getEnableMacLearning()) {
            Optional<Host> optHost = hostService.getConnectedHosts(new ConnectPoint(deviceId, port.number()))
                    .stream().filter(host -> host.vlan().equals(uniTagInfo.getPonCTag())).findFirst();
            if (optHost.isPresent() && optHost.get().mac() != null) {
                return optHost.get().mac();
            }
        }
        return null;
    }

    private boolean isMacAddressValid(UniTagInformation tagInformation) {
        return tagInformation.getConfiguredMacAddress() != null &&
                !tagInformation.getConfiguredMacAddress().trim().equals("") &&
                !MacAddress.NONE.equals(MacAddress.valueOf(tagInformation.getConfiguredMacAddress()));
    }

    protected void updateConnectPointStatus(AccessDevicePort cp, OltFlowsStatus eapolStatus,
                                            OltFlowsStatus subscriberFlowsStatus, OltFlowsStatus dhcpStatus) {
        try {
            cpStatusWriteLock.lock();
            OltPortStatus status = cpStatus.get(cp);

            if (status == null) {
                status = new OltPortStatus(
                        eapolStatus != null ? eapolStatus : OltFlowsStatus.NONE,
                        subscriberFlowsStatus != null ? subscriberFlowsStatus : OltFlowsStatus.NONE,
                        dhcpStatus != null ? dhcpStatus : OltFlowsStatus.NONE
                );
            } else {
                if (eapolStatus != null) {
                    status.defaultEapolStatus = eapolStatus;
                }
                if (subscriberFlowsStatus != null) {
                    status.subscriberFlowsStatus = subscriberFlowsStatus;
                }
                if (dhcpStatus != null) {
                    status.dhcpStatus = dhcpStatus;
                }
            }

            cpStatus.put(cp, status);
        } finally {
            cpStatusWriteLock.unlock();
        }
    }

    protected class InternalFlowListener implements FlowRuleListener {
        @Override
        public void event(FlowRuleEvent event) {
            if (appId.id() != (event.subject().appId())) {
                return;
            }

            if (!oltDeviceService.isLocalLeader(event.subject().deviceId())) {
                if (log.isTraceEnabled()) {
                    log.trace("ignoring flow event {} " +
                            "as not leader for {}", event, event.subject().deviceId());
                }
                return;
            }

            switch (event.type()) {
                case RULE_ADDED:
                case RULE_REMOVED:
                    Port port = getCpFromFlowRule(event.subject());
                    if (port == null) {
                        log.error("Can't find port in flow {}", event.subject());
                        return;
                    }
                    if (log.isTraceEnabled()) {
                        log.trace("flow event {} on cp {}: {}", event.type(),
                                  portWithName(port), event.subject());
                    }
                    updateCpStatus(event.type(), port, event.subject());
                    return;
                case RULE_ADD_REQUESTED:
                case RULE_REMOVE_REQUESTED:
                    // NOTE that PENDING_ADD/REMOVE is set when we create the flowObjective
                    return;
                default:
                    return;
            }
        }

        protected void updateCpStatus(FlowRuleEvent.Type type, Port port, FlowRule flowRule) {
            ConnectPoint cp = new ConnectPoint(port.element().id(), port.number());
            OltFlowsStatus status = flowRuleStatusToOltFlowStatus(type);
            if (isDefaultEapolFlow(flowRule)) {
                if (log.isTraceEnabled()) {
                    log.trace("update defaultEapolStatus {} on {}", status, portWithName(port));
                }
                updateConnectPointStatus(new AccessDevicePort(port), status, null, null);
            } else if (isDhcpFlow(flowRule)) {
                if (log.isTraceEnabled()) {
                    log.trace("update dhcpStatus {} on {}", status, portWithName(port));
                }
                updateConnectPointStatus(new AccessDevicePort(port), null, null, status);
            } else if (isDataFlow(flowRule)) {
                if (log.isTraceEnabled()) {
                    log.trace("update dataplaneStatus {} on {}", status, portWithName(port));
                }
                updateConnectPointStatus(new AccessDevicePort(port), null, status, null);
            }
        }

        private boolean isDefaultEapolFlow(FlowRule flowRule) {
            EthTypeCriterion c = (EthTypeCriterion) flowRule.selector().getCriterion(Criterion.Type.ETH_TYPE);
            if (c == null) {
                return false;
            }
            if (c.ethType().equals(EthType.EtherType.EAPOL.ethType())) {
                AtomicBoolean isDefault = new AtomicBoolean(false);
                flowRule.treatment().allInstructions().forEach(instruction -> {
                    if (instruction.type() == L2MODIFICATION) {
                        L2ModificationInstruction modificationInstruction = (L2ModificationInstruction) instruction;
                        if (modificationInstruction.subtype() == L2ModificationInstruction.L2SubType.VLAN_ID) {
                            L2ModificationInstruction.ModVlanIdInstruction vlanInstruction =
                                    (L2ModificationInstruction.ModVlanIdInstruction) modificationInstruction;
                            if (vlanInstruction.vlanId().id().equals(EAPOL_DEFAULT_VLAN)) {
                                isDefault.set(true);
                                return;
                            }
                        }
                    }
                });
                return isDefault.get();
            }
            return false;
        }

        /**
         * Returns true if the flow is a DHCP flow.
         * Matches both upstream and downstream flows.
         * @param flowRule The FlowRule to evaluate
         * @return boolean
         */
        private boolean isDhcpFlow(FlowRule flowRule) {
            IPProtocolCriterion ipCriterion = (IPProtocolCriterion) flowRule.selector()
                    .getCriterion(Criterion.Type.IP_PROTO);
            if (ipCriterion == null) {
                return false;
            }

            UdpPortCriterion src = (UdpPortCriterion) flowRule.selector().getCriterion(Criterion.Type.UDP_SRC);

            if (src == null) {
                return false;
            }
            return ipCriterion.protocol() == IPv4.PROTOCOL_UDP &&
                    (src.udpPort().toInt() == 68 || src.udpPort().toInt() == 67);
        }

        private boolean isDataFlow(FlowRule flowRule) {
            // we consider subscriber flows the one that matches on VLAN_VID
            // method is valid only because it's the last check after EAPOL and DHCP.
            // this matches mcast flows as well, if we want to avoid that we can
            // filter out the elements that have groups in the treatment or
            // mcastIp in the selector
            // IPV4_DST:224.0.0.22/32
            // treatment=[immediate=[GROUP:0x1]]

            return flowRule.selector().getCriterion(Criterion.Type.VLAN_VID) != null;
        }

        private Port getCpFromFlowRule(FlowRule flowRule) {
            DeviceId deviceId = flowRule.deviceId();
            PortCriterion inPort = (PortCriterion) flowRule.selector().getCriterion(Criterion.Type.IN_PORT);
            if (inPort != null) {
                PortNumber port = inPort.port();
                return deviceService.getPort(deviceId, port);
            }
            return null;
        }

        private OltFlowsStatus flowRuleStatusToOltFlowStatus(FlowRuleEvent.Type type) {
            switch (type) {
                case RULE_ADD_REQUESTED:
                    return OltFlowsStatus.PENDING_ADD;
                case RULE_ADDED:
                    return OltFlowsStatus.ADDED;
                case RULE_REMOVE_REQUESTED:
                    return OltFlowsStatus.PENDING_REMOVE;
                case RULE_REMOVED:
                    return OltFlowsStatus.REMOVED;
                default:
                    return OltFlowsStatus.NONE;
            }
        }
    }

    protected void bindSadisService(SadisService service) {
        this.subsService = service.getSubscriberInfoService();
        this.bpService = service.getBandwidthProfileService();
        log.info("Sadis service is loaded");
    }

    protected void unbindSadisService(SadisService service) {
        this.subsService = null;
        this.bpService = null;
        log.info("Sadis service is unloaded");
    }
}
