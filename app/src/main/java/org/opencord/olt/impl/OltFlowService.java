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

import com.google.common.collect.Sets;
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
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

import java.util.Dictionary;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions flow rules on access devices.
 */
@Component(immediate = true, property = {
        ENABLE_DHCP_ON_PROVISIONING + ":Boolean=" + ENABLE_DHCP_ON_PROVISIONING_DEFAULT,
        ENABLE_DHCP_V4 + ":Boolean=" + ENABLE_DHCP_V4_DEFAULT,
        ENABLE_DHCP_V6 + ":Boolean=" + ENABLE_DHCP_V6_DEFAULT,
        ENABLE_IGMP_ON_PROVISIONING + ":Boolean=" + ENABLE_IGMP_ON_PROVISIONING_DEFAULT,
        ENABLE_EAPOL + ":Boolean=" + ENABLE_EAPOL_DEFAULT,
        DEFAULT_TP_ID + ":Integer=" + DEFAULT_TP_ID_DEFAULT
})
public class OltFlowService implements AccessDeviceFlowService {

    private static final String APP_NAME = "org.opencord.olt";
    private static final int NONE_TP_ID = -1;
    private static final int NO_PCP = -1;
    private static final Integer MAX_PRIORITY = 10000;
    private static final Integer MIN_PRIORITY = 1000;
    private static final int DEFAULT_TP_ID = 64;
    private static final String INSTALLED = "installed";
    private static final String REMOVED = "removed";
    private static final String INSTALLATION = "installation";
    private static final String REMOVAL = "removal";
    private static final String V4 = "V4";
    private static final String V6 = "V6";
    private static final String NO_MAC = "A4:23:05:00:00:00";

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected AccessDeviceMeterService oltMeterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    /**
     * Create the DHCP Flow rules when a subscriber is provisioned.
     **/
    protected boolean enableDhcpOnProvisioning = ENABLE_DHCP_ON_PROVISIONING_DEFAULT;

    /**
     * Enable flows for DHCP v4.
     **/
    protected boolean enableDhcpV4 = ENABLE_DHCP_V4_DEFAULT;

    /**
     * Enable flows for DHCP v6.
     **/
    protected boolean enableDhcpV6 = ENABLE_DHCP_V6_DEFAULT;

    /**
     * Create IGMP Flow rules when a subscriber is provisioned.
     **/
    protected boolean enableIgmpOnProvisioning = ENABLE_IGMP_ON_PROVISIONING_DEFAULT;

    /**
     * Send EAPOL authentication trap flows before subscriber provisioning.
     **/
    protected boolean enableEapol = ENABLE_EAPOL_DEFAULT;

    /**
     * Default technology profile id that is used for authentication trap flows.
     **/
    protected int defaultTechProfileId = DEFAULT_TP_ID_DEFAULT;

    protected ApplicationId appId;
    protected BaseInformationService<BandwidthProfileInformation> bpService;
    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private Set<PortNumber> pendingAddEapol = Sets.newConcurrentHashSet();

    @Activate
    public void activate(ComponentContext context) {
        bpService = sadisService.getBandwidthProfileService();
        subsService = sadisService.getSubscriberInfoService();
        componentConfigService.registerProperties(getClass());
        appId = coreService.getAppId(APP_NAME);
        log.info("Olt Flow Service started");
    }


    @Deactivate
    public void deactivate(ComponentContext context) {
        componentConfigService.unregisterProperties(getClass(), false);
        log.info("Olt flow service stopped");
    }

    @Modified
    public void modified(ComponentContext context) {

        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

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

        Boolean eap = Tools.isPropertyEnabled(properties, "enableEapol");
        if (eap != null) {
            enableEapol = eap;
        }

        String tpId = get(properties, "defaultTechProfileId");
        defaultTechProfileId = isNullOrEmpty(tpId) ? DEFAULT_TP_ID : Integer.parseInt(tpId.trim());

    }

    @Override
    public void processDhcpFilteringObjectives(DeviceId devId, PortNumber port,
                                               MeterId upstreamMeterId,
                                               UniTagInformation tagInformation,
                                               boolean install,
                                               boolean upstream) {
        if (!enableDhcpOnProvisioning && !upstream) {
            log.debug("Dhcp provisioning is disabled.");
            return;
        }

        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }

        int techProfileId = tagInformation != null ? tagInformation.getTechnologyProfileId() : NONE_TP_ID;
        VlanId cTag = tagInformation != null ? tagInformation.getPonCTag() : VlanId.NONE;
        VlanId unitagMatch = tagInformation != null ? tagInformation.getUniTagMatch() : VlanId.ANY;

        if (enableDhcpV4) {
            int udpSrc = (upstream) ? 68 : 67;
            int udpDst = (upstream) ? 67 : 68;

            EthType ethType = EthType.EtherType.IPV4.ethType();
            byte protocol = IPv4.PROTOCOL_UDP;

            this.addDhcpFilteringObjectives(devId, port, udpSrc, udpDst, ethType,
                    upstreamMeterId, techProfileId, protocol, cTag, unitagMatch, install);
        }

        if (enableDhcpV6) {
            int udpSrc = (upstream) ? 547 : 546;
            int udpDst = (upstream) ? 546 : 547;

            EthType ethType = EthType.EtherType.IPV6.ethType();
            byte protocol = IPv6.PROTOCOL_UDP;

            this.addDhcpFilteringObjectives(devId, port, udpSrc, udpDst, ethType,
                    upstreamMeterId, techProfileId, protocol, cTag, unitagMatch, install);
        }
    }

    private void addDhcpFilteringObjectives(DeviceId devId, PortNumber port, int udpSrc, int udpDst,
                                            EthType ethType, MeterId upstreamMeterId, int techProfileId, byte protocol,
                                            VlanId cTag, VlanId unitagMatch, boolean install) {

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
        }

        if (techProfileId != NONE_TP_ID) {
            treatmentBuilder.writeMetadata(createTechProfValueForWm(unitagMatch, techProfileId), 0);
        }

        FilteringObjective.Builder dhcpUpstreamBuilder = (install ? builder.permit() : builder.deny())
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(ethType))
                .addCondition(Criteria.matchIPProtocol(protocol))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(udpSrc)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(udpDst)))
                .withMeta(treatmentBuilder
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY);

        if (!VlanId.NONE.equals(cTag)) {
            dhcpUpstreamBuilder.addCondition(Criteria.matchVlanId(cTag));
        }

        FilteringObjective dhcpUpstream = dhcpUpstreamBuilder.add(new ObjectiveContext() {
            @Override
            public void onSuccess(Objective objective) {
                log.info("DHCP {} filter for device {} on port {} {}.",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6,
                        devId, port, (install) ? INSTALLED : REMOVED);
            }

            @Override
            public void onError(Objective objective, ObjectiveError error) {
                log.info("DHCP {} filter for device {} on port {} failed {} because {}",
                        (ethType.equals(EthType.EtherType.IPV4.ethType())) ? V4 : V6,
                        devId, port, (install) ? INSTALLATION : REMOVAL,
                        error);
            }
        });

        flowObjectiveService.filter(devId, dhcpUpstream);

    }

    @Override
    public void processIgmpFilteringObjectives(DeviceId devId, PortNumber port,
                                               MeterId upstreamMeterId,
                                               UniTagInformation tagInformation,
                                               boolean install,
                                               boolean upstream) {
        if (!enableIgmpOnProvisioning && !upstream) {
            log.debug("Igmp provisioning is disabled.");
            return;
        }

        if (!mastershipService.isLocalMaster(devId)) {
            return;
        }

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        if (upstream) {

            if (tagInformation.getTechnologyProfileId() != NONE_TP_ID) {
                treatmentBuilder.writeMetadata(createTechProfValueForWm(null,
                        tagInformation.getTechnologyProfileId()), 0);
            }


            if (upstreamMeterId != null) {
                treatmentBuilder.meter(upstreamMeterId);
            }

            if (!VlanId.NONE.equals(tagInformation.getPonCTag())) {
                builder.addCondition(Criteria.matchVlanId(tagInformation.getPonCTag()));
            }

            if (tagInformation.getUsPonCTagPriority() != NO_PCP) {
                builder.addCondition(Criteria.matchVlanPcp((byte) tagInformation.getUsPonCTagPriority()));
            }
        }

        builder = install ? builder.permit() : builder.deny();

        FilteringObjective igmp = builder
                .withKey(Criteria.matchInPort(port))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_IGMP))
                .withMeta(treatmentBuilder
                        .setOutput(PortNumber.CONTROLLER).build())
                .fromApp(appId)
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("Igmp filter for {} on {} {}.",
                                devId, port, (install) ? INSTALLED : REMOVED);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("Igmp filter for {} on {} failed {} because {}.",
                                devId, port, (install) ? INSTALLATION : REMOVAL,
                                error);
                    }
                });

        flowObjectiveService.filter(devId, igmp);
    }

    @Override
    public void processEapolFilteringObjectives(DeviceId devId, PortNumber portNumber, String bpId,
                                                CompletableFuture<ObjectiveError> filterFuture,
                                                VlanId vlanId, boolean install) {

        if (!enableEapol) {
            log.debug("Eapol filtering is disabled. Completing filteringFuture immediately for the device {}", devId);
            if (filterFuture != null) {
                filterFuture.complete(null);
            }
            return;
        }

        if (!mastershipService.isLocalMaster(devId)) {
            log.warn("The master of the device {} is another instance", devId);
            if (filterFuture != null) {
                filterFuture.complete(ObjectiveError.DEVICEMISSING);
            }
            return;
        }

        BandwidthProfileInformation bpInfo = getBandwidthProfileInformation(bpId);
        if (bpInfo == null) {
            log.warn("Bandwidth profile {} is not found. Authentication flow"
                    + " will not be installed", bpId);
            if (filterFuture != null) {
                filterFuture.complete(ObjectiveError.BADPARAMS);
            }
            return;
        }

        if (install) {
            boolean added = pendingAddEapol.add(portNumber);
            if (!added) {
                if (filterFuture != null) {
                    log.warn("The eapol flow is processing for the port {}. Ignoring this request", portNumber);
                    filterFuture.complete(null);
                }
                return;
            }
            log.info("portNumber added to pendingAddEapol map {}", portNumber);
        }

        DefaultFilteringObjective.Builder builder = DefaultFilteringObjective.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        CompletableFuture<Object> meterFuture = new CompletableFuture<>();

        // check if meter exists and create it only for an install
        MeterId meterId = oltMeterService.getMeterIdFromBpMapping(devId, bpInfo.id());
        if (meterId == null) {
            if (install) {
                meterId = oltMeterService.createMeter(devId, bpInfo, meterFuture);
                treatmentBuilder.meter(meterId);
            } else {
                // this case should not happen as the request to remove an eapol
                // flow should mean that the flow points to a meter that exists.
                // Nevertheless we can still delete the flow as we only need the
                // correct 'match' to do so.
                log.warn("Unknown meter id for bp {}, still proceeding with "
                        + "delete of eapol flow for {}/{}", bpInfo.id(), devId, portNumber);
                meterFuture.complete(null);
            }
        } else {
            log.debug("Meter {} was previously created for bp {}", meterId, bpInfo.id());
            treatmentBuilder.meter(meterId);
            meterFuture.complete(null);
        }

        final MeterId mId = meterId;
        meterFuture.thenAcceptAsync(result -> {
            if (result == null) {
                log.info("Meter {} for {} on {}/{} exists. {} EAPOL trap flow",
                        mId, bpId, devId, portNumber,
                        (install) ? "Installing" : "Removing");
                int techProfileId = getDefaultTechProfileId(devId, portNumber);

                //Authentication trap flow uses only tech profile id as write metadata value
                FilteringObjective eapol = (install ? builder.permit() : builder.deny())
                        .withKey(Criteria.matchInPort(portNumber))
                        .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                        .addCondition(Criteria.matchVlanId(vlanId))
                        .withMeta(treatmentBuilder
                                .writeMetadata(createTechProfValueForWm(vlanId, techProfileId), 0)
                                .setOutput(PortNumber.CONTROLLER).build())
                        .fromApp(appId)
                        .withPriority(MAX_PRIORITY)
                        .add(new ObjectiveContext() {
                            @Override
                            public void onSuccess(Objective objective) {
                                log.info("Eapol filter for {} on {} {} with meter {}.",
                                        devId, portNumber, (install) ? INSTALLED : REMOVED, mId);
                                if (filterFuture != null) {
                                    filterFuture.complete(null);
                                }
                                pendingAddEapol.remove(portNumber);
                            }

                            @Override
                            public void onError(Objective objective, ObjectiveError error) {
                                log.info("Eapol filter for {} on {} with meter {} failed {} because {}",
                                        devId, portNumber, mId, (install) ? INSTALLATION : REMOVAL,
                                        error);
                                if (filterFuture != null) {
                                    filterFuture.complete(error);
                                }
                                pendingAddEapol.remove(portNumber);
                            }
                        });

                flowObjectiveService.filter(devId, eapol);
            } else {
                log.warn("Meter installation error while sending eapol trap flow. " +
                        "Result {} and MeterId {}", result, mId);
            }
        });
    }

    /**
     * Installs trap filtering objectives for particular traffic types on an
     * NNI port.
     *
     * @param devId   device ID
     * @param port    port number
     * @param install true to install, false to remove
     */
    @Override
    public void processNniFilteringObjectives(DeviceId devId, PortNumber port, boolean install) {
        log.info("Sending flows for NNI port {} of the device {}", port, devId);
        processLldpFilteringObjective(devId, port, install);
        processDhcpFilteringObjectives(devId, port, null, null, install, false);
        processIgmpFilteringObjectives(devId, port, null, null, install, false);
    }


    @Override
    public void processLldpFilteringObjective(DeviceId devId, PortNumber port, boolean install) {
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
                .withPriority(MAX_PRIORITY)
                .add(new ObjectiveContext() {
                    @Override
                    public void onSuccess(Objective objective) {
                        log.info("LLDP filter for device {} on port {} {}.",
                                devId, port, (install) ? INSTALLED : REMOVED);
                    }

                    @Override
                    public void onError(Objective objective, ObjectiveError error) {
                        log.info("LLDP filter for device {} on port {} failed {} because {}",
                                devId, port, (install) ? INSTALLATION : REMOVAL,
                                error);
                    }
                });

        flowObjectiveService.filter(devId, lldp);
    }

    @Override
    public ForwardingObjective.Builder createTransparentBuilder(PortNumber uplinkPort,
                                                                PortNumber subscriberPort,
                                                                MeterId meterId,
                                                                UniTagInformation tagInfo,
                                                                boolean upstream) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchVlanId(tagInfo.getPonSTag())
                .matchInPort(upstream ? subscriberPort : uplinkPort)
                .matchInnerVlanId(tagInfo.getPonCTag())
                .build();

        TrafficTreatment.Builder tBuilder = DefaultTrafficTreatment.builder();
        if (meterId != null) {
            tBuilder.meter(meterId);
        }

        TrafficTreatment treatment = tBuilder
                .setOutput(upstream ? uplinkPort : subscriberPort)
                .writeMetadata(createMetadata(upstream ? tagInfo.getPonSTag() : tagInfo.getPonCTag(),
                        tagInfo.getTechnologyProfileId(), upstream ? uplinkPort : subscriberPort), 0)
                .build();

        return createForwardingObjectiveBuilder(selector, treatment, MIN_PRIORITY);
    }

    @Override
    public ForwardingObjective.Builder createUpBuilder(PortNumber uplinkPort,
                                                       PortNumber subscriberPort,
                                                       MeterId upstreamMeterId,
                                                       UniTagInformation uniTagInformation) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(subscriberPort)
                .matchVlanId(uniTagInformation.getUniTagMatch())
                .build();

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                .pushVlan()
                .setVlanId(uniTagInformation.getPonCTag());

        if (uniTagInformation.getUsPonCTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) uniTagInformation.getUsPonCTagPriority());
        }

        treatmentBuilder.pushVlan()
                .setVlanId(uniTagInformation.getPonSTag());

        if (uniTagInformation.getUsPonSTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) uniTagInformation.getUsPonSTagPriority());
        }

        treatmentBuilder.setOutput(uplinkPort)
                .writeMetadata(createMetadata(uniTagInformation.getPonCTag(),
                        uniTagInformation.getTechnologyProfileId(), uplinkPort), 0L);

        if (upstreamMeterId != null) {
            treatmentBuilder.meter(upstreamMeterId);
        }

        return createForwardingObjectiveBuilder(selector, treatmentBuilder.build(), MIN_PRIORITY);
    }

    @Override
    public ForwardingObjective.Builder createDownBuilder(PortNumber uplinkPort,
                                                         PortNumber subscriberPort,
                                                         MeterId downstreamMeterId,
                                                         UniTagInformation tagInformation) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder()
                .matchVlanId(tagInformation.getPonSTag())
                .matchInPort(uplinkPort)
                .matchInnerVlanId(tagInformation.getPonCTag())
                .matchMetadata(tagInformation.getPonCTag().toShort());

        if (tagInformation.getDsPonSTagPriority() != NO_PCP) {
            selectorBuilder.matchVlanPcp((byte) tagInformation.getDsPonSTagPriority());
        }

        if (tagInformation.getConfiguredMacAddress() != null &&
                !NO_MAC.equals(tagInformation.getConfiguredMacAddress())) {
            selectorBuilder.matchEthDst(MacAddress.valueOf(tagInformation.getConfiguredMacAddress()));
        }

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder()
                .popVlan()
                .setOutput(subscriberPort)
                .writeMetadata(createMetadata(tagInformation.getPonCTag(), tagInformation.getTechnologyProfileId(),
                        subscriberPort), 0);

        // to remark inner vlan header
        if (tagInformation.getUsPonCTagPriority() != NO_PCP) {
            treatmentBuilder.setVlanPcp((byte) tagInformation.getUsPonCTagPriority());
        }

        if (!VlanId.NONE.equals(tagInformation.getUniTagMatch())) {
            treatmentBuilder.setVlanId(tagInformation.getUniTagMatch());
        }

        if (downstreamMeterId != null) {
            treatmentBuilder.meter(downstreamMeterId);
        }

        return createForwardingObjectiveBuilder(selectorBuilder.build(), treatmentBuilder.build(), MIN_PRIORITY);
    }

    private DefaultForwardingObjective.Builder createForwardingObjectiveBuilder(TrafficSelector selector,
                                                                                TrafficTreatment treatment,
                                                                                Integer priority) {
        return DefaultForwardingObjective.builder()
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(priority)
                .makePermanent()
                .withSelector(selector)
                .fromApp(appId)
                .withTreatment(treatment);
    }

    /**
     * Returns the write metadata value including tech profile reference and innerVlan.
     * For param cVlan, null can be sent
     *
     * @param cVlan         c (customer) tag of one subscriber
     * @param techProfileId tech profile id of one subscriber
     * @return the write metadata value including tech profile reference and innerVlan
     */
    private Long createTechProfValueForWm(VlanId cVlan, int techProfileId) {
        if (cVlan == null || VlanId.NONE.equals(cVlan)) {
            return (long) techProfileId << 32;
        }
        return ((long) (cVlan.id()) << 48 | (long) techProfileId << 32);
    }

    private BandwidthProfileInformation getBandwidthProfileInformation(String bandwidthProfile) {
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
     * @param devId
     * @param portNumber
     * @return the default technology profile id
     */
    private int getDefaultTechProfileId(DeviceId devId, PortNumber portNumber) {
        Port port = deviceService.getPort(devId, portNumber);
        if (port != null) {
            SubscriberAndDeviceInformation info = subsService.get(port.annotations().value(AnnotationKeys.PORT_NAME));
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
            techProfileId = DEFAULT_TP_ID;
        }

        return ((long) (innerVlan.id()) << 48 | (long) techProfileId << 32) | egressPort.toLong();
    }


}
