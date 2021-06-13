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
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
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
import org.onosproject.net.meter.MeterId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.AccessDevicePort;
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

import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions flow rules on access devices.
 */
@Component(immediate = true, property = {
        ENABLE_DHCP_ON_NNI + ":Boolean=" + ENABLE_DHCP_ON_NNI_DEFAULT,
        ENABLE_DHCP_V4 + ":Boolean=" + ENABLE_DHCP_V4_DEFAULT,
        ENABLE_DHCP_V6 + ":Boolean=" + ENABLE_DHCP_V6_DEFAULT,
        ENABLE_IGMP_ON_NNI + ":Boolean=" + ENABLE_IGMP_ON_NNI_DEFAULT,
        ENABLE_EAPOL + ":Boolean=" + ENABLE_EAPOL_DEFAULT,
        ENABLE_PPPOE + ":Boolean=" + ENABLE_PPPOE_DEFAULT,
        DEFAULT_TP_ID + ":Integer=" + DEFAULT_TP_ID_DEFAULT
})
public class OltFlowService implements AccessDeviceFlowService {
    private static final String SADIS_NOT_RUNNING = "Sadis is not running.";
    private static final String APP_NAME = "org.opencord.olt";
    private static final int NONE_TP_ID = -1;
    private static final int NO_PCP = -1;
    private static final Integer MAX_PRIORITY = 10000;
    private static final Integer MIN_PRIORITY = 1000;
    private static final String INSTALLED = "installed";
    private static final String REMOVED = "removed";
    private static final String INSTALLATION = "installation";
    private static final String REMOVAL = "removal";
    private static final String V4 = "V4";
    private static final String V6 = "V6";

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            bind = "bindSadisService",
            unbind = "unbindSadisService",
            policy = ReferencePolicy.DYNAMIC)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected AccessDeviceMeterService oltMeterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

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

    protected ApplicationId appId;
    protected BaseInformationService<BandwidthProfileInformation> bpService;
    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    protected Map<DeviceId, BlockingQueue<SubscriberFlowInfo>> pendingEapolForDevice;

    @Activate
    public void activate(ComponentContext context) {
        if (sadisService != null) {
            bpService = sadisService.getBandwidthProfileService();
            subsService = sadisService.getSubscriberInfoService();
        } else {
            log.warn(SADIS_NOT_RUNNING);
        }
        componentConfigService.registerProperties(getClass());
        appId = coreService.getAppId(APP_NAME);
        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(UniTagInformation.class)
                .register(SubscriberFlowInfo.class)
                .register(AccessDevicePort.class)
                .register(AccessDevicePort.Type.class)
                .register(LinkedBlockingQueue.class)
                .build();
        pendingEapolForDevice = storageService.<DeviceId, BlockingQueue<SubscriberFlowInfo>>consistentMapBuilder()
                .withName("volt-pending-eapol")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .build().asJavaMap();
        log.info("started");
    }


    @Deactivate
    public void deactivate(ComponentContext context) {
        componentConfigService.unregisterProperties(getClass(), false);
        log.info("stopped");
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

        String tpId = get(properties, DEFAULT_TP_ID);
        defaultTechProfileId = isNullOrEmpty(tpId) ? DEFAULT_TP_ID_DEFAULT : Integer.parseInt(tpId.trim());

        log.info("modified. Values = enableDhcpOnNni: {}, enableDhcpV4: {}, " +
                         "enableDhcpV6:{}, enableIgmpOnNni:{}, " +
                         "enableEapol:{}, enablePppoe:{}, defaultTechProfileId:{}",
                 enableDhcpOnNni, enableDhcpV4, enableDhcpV6,
                 enableIgmpOnNni, enableEapol,  enablePppoe,
                 defaultTechProfileId);

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
    public void processDhcpFilteringObjectives(AccessDevicePort port,
                                               MeterId upstreamMeterId,
                                               MeterId upstreamOltMeterId,
                                               UniTagInformation tagInformation,
                                               boolean install,
                                               boolean upstream,
                                               Optional<CompletableFuture<ObjectiveError>> dhcpFuture) {
        if (upstream) {
            // for UNI ports
            if (tagInformation != null && !tagInformation.getIsDhcpRequired()) {
                log.debug("Dhcp provisioning is disabled for UNI port {} for service {}",
                        port, tagInformation.getServiceName());
                dhcpFuture.ifPresent(f -> f.complete(null));
                return;
            }
        } else {
            // for NNI ports
            if (!enableDhcpOnNni) {
                log.debug("Dhcp provisioning is disabled for NNI port {}", port);
                dhcpFuture.ifPresent(f -> f.complete(null));
                return;
            }
        }
        int techProfileId = tagInformation != null ? tagInformation.getTechnologyProfileId() : NONE_TP_ID;
        VlanId cTag = tagInformation != null ? tagInformation.getPonCTag() : VlanId.NONE;
        VlanId unitagMatch = tagInformation != null ? tagInformation.getUniTagMatch() : VlanId.ANY;
        Byte vlanPcp = tagInformation != null && tagInformation.getUsPonCTagPriority() != NO_PCP
                ? (byte) tagInformation.getUsPonCTagPriority() : null;


        if (enableDhcpV4) {
            int udpSrc = (upstream) ? 68 : 67;
            int udpDst = (upstream) ? 67 : 68;

            EthType ethType = EthType.EtherType.IPV4.ethType();
            byte protocol = IPv4.PROTOCOL_UDP;

            addDhcpFilteringObjectives(port, udpSrc, udpDst, ethType,
                    upstreamMeterId, upstreamOltMeterId, techProfileId, protocol, cTag, unitagMatch,
                    vlanPcp, upstream, install, dhcpFuture);
        }

        if (enableDhcpV6) {
            int udpSrc = (upstream) ? 547 : 546;
            int udpDst = (upstream) ? 546 : 547;

            EthType ethType = EthType.EtherType.IPV6.ethType();
            byte protocol = IPv6.PROTOCOL_UDP;

            addDhcpFilteringObjectives(port, udpSrc, udpDst, ethType,
                    upstreamMeterId, upstreamOltMeterId, techProfileId, protocol, cTag, unitagMatch,
                    vlanPcp, upstream, install, dhcpFuture);
        }
    }

    private void addDhcpFilteringObjectives(AccessDevicePort port, int udpSrc, int udpDst,
                                            EthType ethType, MeterId upstreamMeterId, MeterId upstreamOltMeterId,
                                            int techProfileId, byte protocol, VlanId cTag, VlanId unitagMatch,
                                            Byte vlanPcp, boolean upstream,
                                            boolean install, Optional<CompletableFuture<ObjectiveError>> dhcpFuture) {

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
        }

        if (techProfileId != NONE_TP_ID) {
            treatmentBuilder.writeMetadata(createTechProfValueForWm(unitagMatch, techProfileId, upstreamOltMeterId), 0);
        }

        FilteringObjective.Builder dhcpUpstreamBuilder = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(ethType))
                .addCondition(Criteria.matchIPProtocol(protocol))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(udpSrc)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(udpDst)))
                .fromApp(appId)
                .withPriority(MAX_PRIORITY);

        //VLAN changes and PCP matching need to happen only in the upstream directions
        if (upstream) {
            treatmentBuilder.setVlanId(cTag);
            if (!VlanId.vlanId(VlanId.NO_VID).equals(unitagMatch)) {
                dhcpUpstreamBuilder.addCondition(Criteria.matchVlanId(unitagMatch));
            }
            if (vlanPcp != null) {
                treatmentBuilder.setVlanPcp(vlanPcp);
            }
        }

        dhcpUpstreamBuilder.withMeta(treatmentBuilder
                                  .setOutput(PortNumber.CONTROLLER).build());


        FilteringObjective dhcpUpstream = dhcpUpstreamBuilder.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.info("DHCP {} filter for {} {}.",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6, port,
                        (install) ? INSTALLED : REMOVED);
                dhcpFuture.ifPresent(f -> f.complete(null));
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                log.error("DHCP {} filter for {} failed {} because {}",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6, port,
                        (install) ? INSTALLATION : REMOVAL,
                        error);
                dhcpFuture.ifPresent(f -> f.complete(error));
            }
        });
        flowObjectiveService.filter(port.deviceId(), dhcpUpstream);

    }

    @Override
    public void processPPPoEDFilteringObjectives(AccessDevicePort port,
                                                 MeterId upstreamMeterId,
                                                 MeterId upstreamOltMeterId,
                                                 UniTagInformation tagInformation,
                                                 boolean install,
                                                 boolean upstream) {
        if (!enablePppoe) {
            log.debug("PPPoED filtering is disabled. Ignoring request.");
            return;
        }

        int techProfileId = NONE_TP_ID;
        VlanId cTag = VlanId.NONE;
        VlanId unitagMatch = VlanId.ANY;
        Byte vlanPcp = null;

        if (tagInformation != null) {
            techProfileId = tagInformation.getTechnologyProfileId();
            cTag = tagInformation.getPonCTag();
            unitagMatch = tagInformation.getUniTagMatch();
            if (tagInformation.getUsPonCTagPriority() != NO_PCP) {
                vlanPcp = (byte) tagInformation.getUsPonCTagPriority();
            }
        }

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        CompletableFuture<Object> meterFuture = new CompletableFuture<>();

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
        }

        if (techProfileId != NONE_TP_ID) {
            treatmentBuilder.writeMetadata(createTechProfValueForWm(cTag, techProfileId, upstreamOltMeterId), 0);
        }

        DefaultFilteringObjective.Builder pppoedBuilder = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.PPPoED.ethType()))
                .fromApp(appId)
                .withPriority(10000);

        if (upstream) {
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
                        log.info("PPPoED filter for {} {}.", port, (install) ? INSTALLED : REMOVED);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("PPPoED filter for {} failed {} because {}", port,
                                (install) ? INSTALLATION : REMOVAL, error);
                    }
                });
        flowObjectiveService.filter(port.deviceId(), pppoed);
    }

    @Override
    public void processIgmpFilteringObjectives(AccessDevicePort port,
                                               MeterId upstreamMeterId,
                                               MeterId upstreamOltMeterId,
                                               UniTagInformation tagInformation,
                                               boolean install,
                                               boolean upstream) {
        if (upstream) {
            // for UNI ports
            if (tagInformation != null && !tagInformation.getIsIgmpRequired()) {
                log.debug("Igmp provisioning is disabled for UNI port {} for service {}",
                        port, tagInformation.getServiceName());
                return;
            }
        } else {
            // for NNI ports
            if (!enableIgmpOnNni) {
                log.debug("Igmp provisioning is disabled for NNI port {}", port);
                return;
            }
        }

        log.debug("{} IGMP flows on {}", (install) ? "Installing" : "Removing", port);
        DefaultFilteringObjective.Builder filterBuilder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        if (upstream) {

            if (tagInformation.getTechnologyProfileId() != NONE_TP_ID) {
                treatmentBuilder.writeMetadata(createTechProfValueForWm(null,
                        tagInformation.getTechnologyProfileId(), upstreamOltMeterId), 0);
            }


            if (upstreamMeterId != null) {
                treatmentBuilder.meter(upstreamMeterId);
            }

            if (!VlanId.vlanId(VlanId.NO_VID).equals(tagInformation.getUniTagMatch())) {
                filterBuilder.addCondition(Criteria.matchVlanId(tagInformation.getUniTagMatch()));
            }

            if (!VlanId.vlanId(VlanId.NO_VID).equals(tagInformation.getPonCTag())) {
                treatmentBuilder.setVlanId(tagInformation.getPonCTag());
            }

            if (tagInformation.getUsPonCTagPriority() != NO_PCP) {
                treatmentBuilder.setVlanPcp((byte) tagInformation.getUsPonCTagPriority());
            }
        }

        filterBuilder = install ? filterBuilder.permit() : filterBuilder.deny();

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
                        log.info("Igmp filter for {} {}.", port, (install) ? INSTALLED : REMOVED);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("Igmp filter for {} failed {} because {}.", port, (install) ? INSTALLATION : REMOVAL,
                                error);
                    }
                });

        flowObjectiveService.filter(port.deviceId(), igmp);
    }

    @Override
    public void processEapolFilteringObjectives(AccessDevicePort port, String bpId, Optional<String> oltBpId,
                                                CompletableFuture<ObjectiveError> filterFuture,
                                                VlanId vlanId, boolean install) {

        if (!enableEapol) {
            log.debug("Eapol filtering is disabled. Completing filteringFuture immediately for the device {}",
                    port.deviceId());
            if (filterFuture != null) {
                filterFuture.complete(null);
            }
            return;
        }
        log.info("Processing EAPOL with Bandwidth profile {} on {}", bpId, port);
        BandwidthProfileInformation bpInfo = getBandwidthProfileInformation(bpId);
        BandwidthProfileInformation oltBpInfo;
        if (oltBpId.isPresent()) {
            oltBpInfo = getBandwidthProfileInformation(oltBpId.get());
        } else {
            oltBpInfo = bpInfo;
        }
        if (bpInfo == null) {
            log.warn("Bandwidth profile {} is not found. Authentication flow"
                    + " will not be installed on {}", bpId, port);
            if (filterFuture != null) {
                filterFuture.complete(ObjectiveError.BADPARAMS);
            }
            return;
        }

        ConnectPoint cp = new ConnectPoint(port.deviceId(), port.number());
        DefaultFilteringObjective.Builder filterBuilder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        // check if meter exists and create it only for an install
        final MeterId meterId = oltMeterService.getMeterIdFromBpMapping(port.deviceId(), bpInfo.id());
        MeterId oltMeterId = null;
        if (oltBpId.isPresent()) {
            oltMeterId = oltBpId.map(id -> oltMeterService.getMeterIdFromBpMapping(port.deviceId(), id)).orElse(null);
        }
        log.info("Meter id {} for Bandwidth profile {} and OLT meter id {} for OLT Bandwidth profile {} " +
                        "associated to EAPOL on {}", meterId, bpInfo.id(), oltMeterId, oltBpId, port.deviceId());
        if (meterId == null || (oltBpId.isPresent() && oltMeterId == null)) {
            if (install) {
                log.debug("Need to install meter for EAPOL with bwp {} on {}", bpInfo.id(), port);
                SubscriberFlowInfo fi = new SubscriberFlowInfo(null, port,
                                                            new UniTagInformation.Builder()
                                                                    .setPonCTag(vlanId).build(),
                                                        null, meterId, null, oltMeterId,
                                                    null, bpInfo.id(), null, oltBpInfo.id());
                pendingEapolForDevice.compute(port.deviceId(), (id, queue) -> {
                    if (queue == null) {
                        queue = new LinkedBlockingQueue<>();
                    }
                    queue.add(fi);
                    return queue;
                });

                //If false the meter is already being installed, skipping installation
                if (!oltMeterService.checkAndAddPendingMeter(port.deviceId(), bpInfo) &&
                        !oltMeterService.checkAndAddPendingMeter(port.deviceId(), oltBpInfo)) {
                    return;
                }
                List<BandwidthProfileInformation> bwpList = Arrays.asList(bpInfo, oltBpInfo);
                bwpList.stream().distinct().filter(Objects::nonNull)
                        .forEach(bwp -> createMeterAndProceedEapol(port, bwp, filterFuture, install,
                        cp, filterBuilder, treatmentBuilder));
            } else {
                // this case should not happen as the request to remove an eapol
                // flow should mean that the flow points to a meter that exists.
                // Nevertheless we can still delete the flow as we only need the
                // correct 'match' to do so.
                log.warn("Unknown meter id for bp {}, still proceeding with "
                        + "delete of eapol flow on {}", bpInfo.id(), port);
                SubscriberFlowInfo fi = new SubscriberFlowInfo(null, port,
                        new UniTagInformation.Builder().setPonCTag(vlanId).build(),
                        null, meterId, null, oltMeterId, null,
                        bpInfo.id(), null, oltBpInfo.id());
                handleEapol(filterFuture, install, cp, filterBuilder, treatmentBuilder, fi, meterId, oltMeterId);
            }
        } else {
            log.debug("Meter {} was previously created for bp {} on {}", meterId, bpInfo.id(), port);
            SubscriberFlowInfo fi = new SubscriberFlowInfo(null, port,
                    new UniTagInformation.Builder().setPonCTag(vlanId).build(),
                    null, meterId, null, oltMeterId, null,
                    bpInfo.id(), null, oltBpInfo.id());
            handleEapol(filterFuture, install, cp, filterBuilder, treatmentBuilder, fi, meterId, oltMeterId);
            //No need for the future, meter is present.
            return;
        }
    }

    private void createMeterAndProceedEapol(AccessDevicePort port, BandwidthProfileInformation bwpInfo,
                                            CompletableFuture<ObjectiveError> filterFuture,
                                            boolean install, ConnectPoint cp,
                                            DefaultFilteringObjective.Builder filterBuilder,
                                            TrafficTreatment.Builder treatmentBuilder) {
        CompletableFuture<Object> meterFuture = new CompletableFuture<>();
        MeterId meterId = oltMeterService.createMeter(port.deviceId(), bwpInfo, meterFuture);
        DeviceId deviceId = port.deviceId();
        meterFuture.thenAccept(result -> {
            //for each pending eapol flow we check if the meter is there.
            pendingEapolForDevice.compute(deviceId, (id, queue) -> {
                if (queue != null && !queue.isEmpty()) {
                    while (true) {
                        //TODO this might return the reference and not the actual object
                        // so it can be actually swapped underneath us.
                        SubscriberFlowInfo fi = queue.peek();
                        if (fi == null) {
                            log.debug("No more subscribers eapol flows on {}", deviceId);
                            queue = new LinkedBlockingQueue<>();
                            break;
                        }
                        log.debug("handing pending eapol on {} for {}", fi.getUniPort(), fi);
                        if (result == null) {
                            MeterId mId = oltMeterService
                                    .getMeterIdFromBpMapping(port.deviceId(), fi.getUpBpInfo());
                            MeterId oltMeterId = oltMeterService
                                    .getMeterIdFromBpMapping(port.deviceId(), fi.getUpOltBpInfo());
                            if (mId != null && oltMeterId != null) {
                                log.debug("Meter installation completed for subscriber on {}, " +
                                                  "handling EAPOL trap flow", port);
                                fi.setUpMeterId(mId);
                                fi.setUpOltMeterId(oltMeterId);
                                handleEapol(filterFuture, install, cp, filterBuilder, treatmentBuilder, fi,
                                            mId, oltMeterId);
                                queue.remove(fi);
                            } else {
                                log.debug("Not all meters for {} are yet installed for EAPOL meterID {}, " +
                                                  "oltMeterId {}", fi, meterId, oltMeterId);
                            }
                        } else {
                            log.warn("Meter installation error while sending EAPOL trap flow to {}. " +
                                             "Result {} and MeterId {}", port, result, meterId);
                            queue.remove(fi);
                        }
                        oltMeterService.removeFromPendingMeters(port.deviceId(), bwpInfo);
                    }
                } else {
                    log.info("No pending EAPOLs on {}", port.deviceId());
                    queue = new LinkedBlockingQueue<>();
                }
                return queue;
            });
        });
    }

    private void handleEapol(CompletableFuture<ObjectiveError> filterFuture,
                             boolean install, ConnectPoint cp,
                             DefaultFilteringObjective.Builder filterBuilder,
                             TrafficTreatment.Builder treatmentBuilder,
                             SubscriberFlowInfo fi, MeterId mId, MeterId oltMeterId) {
        log.info("Meter {} for {} on {} exists. {} EAPOL trap flow",
                 mId, fi.getUpBpInfo(), fi.getUniPort(),
                 (install) ? "Installing" : "Removing");
        int techProfileId = getDefaultTechProfileId(fi.getUniPort());
        // can happen in case of removal
        if (mId != null) {
            treatmentBuilder.meter(mId);
        }
        //Authentication trap flow uses only tech profile id as write metadata value
        FilteringObjective eapol = (install ? filterBuilder.permit() : filterBuilder.deny())
                .withKey(Criteria.matchInPort(fi.getUniPort().number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                .withMeta(treatmentBuilder
                                  .writeMetadata(createTechProfValueForWm(
                                          fi.getTagInfo().getPonCTag(),
                                          techProfileId, oltMeterId), 0)
                                  .setOutput(PortNumber.CONTROLLER)
                                  .pushVlan()
                                  .setVlanId(fi.getTagInfo().getPonCTag())
                                  .build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Eapol filter {} for {} on {} with meter {}.",
                                 objective.id(), (install) ? INSTALLED : REMOVED, fi.getUniPort(), mId);
                        if (filterFuture != null) {
                            filterFuture.complete(null);
                        }
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("Eapol filter {} for {} with meter {} " +
                                         "failed {} because {}", objective.id(), fi.getUniPort(), mId,
                                 (install) ? INSTALLATION : REMOVAL,
                                 error);
                        if (filterFuture != null) {
                            filterFuture.complete(error);
                        }
                    }
                });
        flowObjectiveService.filter(fi.getDevId(), eapol);
    }

    /**
     * Installs trap filtering objectives for particular traffic types on an
     * NNI port.
     *
     * @param nniPort    NNI port
     * @param install true to install, false to remove
     */
    @Override
    public void processNniFilteringObjectives(AccessDevicePort nniPort, boolean install) {
        log.info("{} flows for NNI port {}",
                 install ? "Adding" : "Removing", nniPort);
        processLldpFilteringObjective(nniPort, install);
        processDhcpFilteringObjectives(nniPort, null, null, null, install, false, Optional.empty());
        processIgmpFilteringObjectives(nniPort, null, null, null, install, false);
        processPPPoEDFilteringObjectives(nniPort, null, null, null, install, false);
    }


    @Override
    public void processLldpFilteringObjective(AccessDevicePort port, boolean install) {
        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();

        FilteringObjective lldp = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.LLDP.ethType()))
                .withMeta(DefaultTrafficTreatment.builder()
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("LLDP filter for {} {}.", port, (install) ? INSTALLED : REMOVED);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.error("LLDP filter for {} failed {} because {}", port, (install) ? INSTALLATION : REMOVAL,
                                error);
                    }
                });

        flowObjectiveService.filter(port.deviceId(), lldp);
    }

    @Override
    public ForwardingObjective.Builder createTransparentBuilder(AccessDevicePort uplinkPort,
                                                                AccessDevicePort subscriberPort,
                                                                MeterId meterId,
                                                                UniTagInformation tagInfo,
                                                                boolean upstream) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchVlanId(tagInfo.getPonSTag())
                .matchInPort(upstream ? subscriberPort.number() : uplinkPort.number())
                .matchInnerVlanId(tagInfo.getPonCTag())
                .build();

        TrafficTreatment.Builder tBuilder = DefaultTrafficTreatment.builder();
        if (meterId != null) {
            tBuilder.meter(meterId);
        }

        TrafficTreatment treatment = tBuilder
                .setOutput(upstream ? uplinkPort.number() : subscriberPort.number())
                .writeMetadata(createMetadata(upstream ? tagInfo.getPonSTag() : tagInfo.getPonCTag(),
                        tagInfo.getTechnologyProfileId(),
                        upstream ? uplinkPort.number() : subscriberPort.number()), 0)
                .build();

        return createForwardingObjectiveBuilder(selector, treatment, MIN_PRIORITY,
                DefaultAnnotations.builder().build());
    }

    @Override
    public ForwardingObjective.Builder createUpBuilder(AccessDevicePort uplinkPort,
                                                       AccessDevicePort subscriberPort,
                                                       MeterId upstreamMeterId,
                                                       MeterId upstreamOltMeterId,
                                                       UniTagInformation uniTagInformation) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(subscriberPort.number())
                .matchVlanId(uniTagInformation.getUniTagMatch())
                .build();

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        //if the subscriberVlan (cTag) is different than ANY it needs to set.
        if (uniTagInformation.getPonCTag().toShort() != VlanId.ANY_VALUE) {
            treatmentBuilder.pushVlan()
                    .setVlanId(uniTagInformation.getPonCTag());
        }

        if (uniTagInformation.getUsPonCTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) uniTagInformation.getUsPonCTagPriority());
        }

        treatmentBuilder.pushVlan()
                .setVlanId(uniTagInformation.getPonSTag());

        if (uniTagInformation.getUsPonSTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) uniTagInformation.getUsPonSTagPriority());
        }

        treatmentBuilder.setOutput(uplinkPort.number())
                .writeMetadata(createMetadata(uniTagInformation.getPonCTag(),
                        uniTagInformation.getTechnologyProfileId(), uplinkPort.number()), 0L);

        DefaultAnnotations.Builder annotationBuilder = DefaultAnnotations.builder();

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
            annotationBuilder.set(UPSTREAM_ONU, upstreamMeterId.toString());
        }
        if (upstreamOltMeterId != null) {
            treatmentBuilder.meter(upstreamOltMeterId);
            annotationBuilder.set(UPSTREAM_OLT, upstreamOltMeterId.toString());
        }

        return createForwardingObjectiveBuilder(selector, treatmentBuilder.build(), MIN_PRIORITY,
                annotationBuilder.build());
    }

    @Override
    public ForwardingObjective.Builder createDownBuilder(AccessDevicePort uplinkPort,
                                                         AccessDevicePort subscriberPort,
                                                         MeterId downstreamMeterId,
                                                         MeterId downstreamOltMeterId,
                                                         UniTagInformation tagInformation,
                                                         Optional<MacAddress> macAddress) {

        //subscriberVlan can be any valid Vlan here including ANY to make sure the packet is tagged
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder()
                .matchVlanId(tagInformation.getPonSTag())
                .matchInPort(uplinkPort.number())
                .matchInnerVlanId(tagInformation.getPonCTag());


        if (tagInformation.getPonCTag().toShort() != VlanId.ANY_VALUE) {
            selectorBuilder.matchMetadata(tagInformation.getPonCTag().toShort());
        }

        if (tagInformation.getDsPonSTagPriority() != NO_PCP) {
            selectorBuilder.matchVlanPcp((byte) tagInformation.getDsPonSTagPriority());
        }

        macAddress.ifPresent(selectorBuilder::matchEthDst);

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                .popVlan()
                .setOutput(subscriberPort.number());

        treatmentBuilder.writeMetadata(createMetadata(tagInformation.getPonCTag(),
                                                      tagInformation.getTechnologyProfileId(),
                                                      subscriberPort.number()), 0);

        // Upstream pbit is used to remark inner vlan pbit.
        // Upstream is used to avoid trusting the BNG to send the packet with correct pbit.
        // this is done because ds mode 0 is used because ds mode 3 or 6 that allow for
        // all pbit acceptance are not widely supported by vendors even though present in
        // the OMCI spec.
        if (tagInformation.getUsPonCTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) tagInformation.getUsPonCTagPriority());
        }

        if (!VlanId.NONE.equals(tagInformation.getUniTagMatch()) &&
                tagInformation.getPonCTag().toShort() != VlanId.ANY_VALUE) {
            treatmentBuilder.setVlanId(tagInformation.getUniTagMatch());
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

        return createForwardingObjectiveBuilder(selectorBuilder.build(), treatmentBuilder.build(), MIN_PRIORITY,
                annotationBuilder.build());
    }

    @Override
    public void clearDeviceState(DeviceId deviceId) {
        pendingEapolForDevice.remove(deviceId);
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

    /**
     * Returns the write metadata value including tech profile reference and innerVlan.
     * For param cVlan, null can be sent
     *
     * @param cVlan                 c (customer) tag of one subscriber
     * @param techProfileId         tech profile id of one subscriber
     * @param upstreamOltMeterId    upstream meter id for OLT device.
     * @return the write metadata value including tech profile reference and innerVlan
     */
    private Long createTechProfValueForWm(VlanId cVlan, int techProfileId, MeterId upstreamOltMeterId) {
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
     * It will be used to support AT&T use case (for EAPOL flows).
     * If multiple services are found in uniServiceList, returns default tech profile id
     * If one service is found, returns the found one
     *
     * @param port uni port
     * @return the default technology profile id
     */
    private int getDefaultTechProfileId(AccessDevicePort port) {
        if (subsService == null) {
            log.warn(SADIS_NOT_RUNNING);
            return defaultTechProfileId;
        }
        if (port != null) {
            SubscriberAndDeviceInformation info = subsService.get(port.name());
            if (info != null && info.uniTagList().size() == 1) {
                return info.uniTagList().get(0).getTechnologyProfileId();
            }
        }
        return defaultTechProfileId;
    }

    /**
     * Write metadata instruction value (metadata) is 8 bytes.
     * <p>
     * MS 2 bytes: C Tag
     * Next 2 bytes: Technology Profile Id
     * Next 4 bytes: Port number (uni or nni)
     */
    private Long createMetadata(VlanId innerVlan, int techProfileId, PortNumber egressPort) {
        if (techProfileId == NONE_TP_ID) {
            techProfileId = DEFAULT_TP_ID_DEFAULT;
        }

        return ((long) (innerVlan.id()) << 48 | (long) techProfileId << 32) | egressPort.toLong();
    }


}
