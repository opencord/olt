/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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

package org.opencord.olt.impl.fttb;

import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Utility class for holding FTTB constants and utility methods.
 */
public final class FttbUtils {

    public static final String FTTB_FLOW_DIRECTION = "fttbFlowDirection";
    public static final String FTTB_FLOW_UPSTREAM = "fttbFlowUpstream";
    public static final String FTTB_FLOW_DOWNSTREAM = "fttbFlowDownstream";

    public static final String FTTB_SERVICE_NAME = "fttbServiceName";
    public static final String FTTB_SERVICE_DPU_MGMT_TRAFFIC = "DPU_MGMT_TRAFFIC";
    public static final String FTTB_SERVICE_DPU_ANCP_TRAFFIC = "DPU_ANCP_TRAFFIC";
    public static final String FTTB_SERVICE_SUBSCRIBER_TRAFFIC = "FTTB_SUBSCRIBER_TRAFFIC";

    private static final Logger log = LoggerFactory.getLogger(FttbUtils.class);

    private FttbUtils() {
    }

    /**
     * Checks if the FlowObjective qualifies as FTTB rule.
     *
     * @param fwd ForwardingObjective rule.
     * @return true if the fwd is FTTB rule.
     */
    public static boolean isFttbRule(ForwardingObjective fwd) {
        String serviceName = fwd.annotations().value(FTTB_SERVICE_NAME);

        if (serviceName == null) {
            if (log.isTraceEnabled()) {
                log.trace("Service name not found for : {} ", fwd);
            }
            return false;
        }

        return isFttbService(serviceName);
    }

    /**
     * Checks if the UniTagInformation is a FTTB subscriber.
     *
     * @param uti The UniTagInformation to check for.
     * @return true if the uti is FTTB subscriber.
     */
    public static boolean isFttbService(UniTagInformation uti) {
        String serviceName = uti.getServiceName();

        if (serviceName == null) {
            log.warn("Could not find service name for {}", uti);
            return false;
        }

        return isFttbService(serviceName);
    }

    /**
     * Checks if the UniTagInformation is FTTB DPU or ANCP service.
     *
     * @param uti The UniTagInformation to check for.
     * @return true if the uti is FTTB DPU or ANCP service.
     */
    public static boolean isFttbDpuOrAncpService(UniTagInformation uti) {
        String serviceName = uti.getServiceName();

        if (serviceName == null) {
            log.trace("Could not find service name for {}", uti);
            return false;
        }

        switch (serviceName) {
            case FTTB_SERVICE_DPU_MGMT_TRAFFIC:
            case FTTB_SERVICE_DPU_ANCP_TRAFFIC:
                return true;
            default:
                return false;
        }
    }

    /**
     * Adds match conditions to FilteringObjective.Builder for FTTB.
     * @param dhcpBuilder FilteringObjective.Builder
     * @param uti UniTagInformation
     */
    public static void addUpstreamDhcpCondition(FilteringObjective.Builder dhcpBuilder,
                                                UniTagInformation uti) {
        dhcpBuilder.addCondition(Criteria.matchVlanId(uti.getPonCTag()));
        if (uti.getUsPonCTagPriority() != -1) {
            dhcpBuilder.addCondition(Criteria.matchVlanPcp((byte) uti.getUsPonCTagPriority()));
        }
    }

    /**
     * Adds Instructions to TrafficTreatment.Builder for FTTB.
     * @param treatmentBuilder TrafficTreatment.Builder
     * @param uti UniTagInformation
     */
    public static void addUpstreamDhcpTreatment(TrafficTreatment.Builder treatmentBuilder, UniTagInformation uti) {
        treatmentBuilder.setVlanId(uti.getPonSTag());

        if (uti.getUsPonSTagPriority() != -1) {
            treatmentBuilder.setVlanPcp((byte) uti.getUsPonSTagPriority());
        }
    }

    private static boolean isFttbService(String serviceName) {
        if (serviceName == null) {
            return false;
        }

        switch (serviceName) {
            case FTTB_SERVICE_DPU_MGMT_TRAFFIC:
            case FTTB_SERVICE_DPU_ANCP_TRAFFIC:
            case FTTB_SERVICE_SUBSCRIBER_TRAFFIC:
                return true;
            default:
                if (log.isTraceEnabled()) {
                    log.trace("Service name {} is not one for FTTB", serviceName);
                }
                return false;
        }
    }

    /**
     * Returns mac address from the Dhcp Enabled UniTagInformation for a FTTB service.
     *
     * @param hostService    Service for interacting with the inventory of end-station hosts
     * @param si             Information about a subscriber
     * @param deviceId       Device id for mac lookup.
     * @param port           Uni port on the device for mac lookup.
     * @param localAddresses Map of the addresses that this app has locally
     * @return Mac address of the subscriber.
     */
    public static MacAddress getMacAddressFromDhcpEnabledUti(HostService hostService,
                                                             SubscriberAndDeviceInformation si,
                                                             DeviceId deviceId,
                                                             Port port,
                                                             Map<ConnectPoint, MacAddress> localAddresses) {
        for (UniTagInformation uniTagInfo : si.uniTagList()) {
            boolean isMacLearningEnabled = uniTagInfo.getEnableMacLearning();
            if (isMacLearningEnabled) {
                ConnectPoint cp = new ConnectPoint(deviceId, port.number());
                Optional<Host> optHost = hostService.getConnectedHosts(cp)
                        .stream().filter(host -> host.vlan().equals(uniTagInfo.getPonSTag())).findFirst();
                if (optHost.isPresent()) {
                    MacAddress learntMac = optHost.get().mac();
                    if (learntMac != null) {
                        localAddresses.put(cp, learntMac);
                        log.info("Stored mac {} locally for connectPoint {}", learntMac, cp);
                        return learntMac;
                    }
                } else {
                    MacAddress localMac = localAddresses.get(new ConnectPoint(deviceId, port.number()));
                    if (localMac != null) {
                        log.debug("Returning local mac {} for connectPoint {}", localMac, cp);
                        return localMac;
                    }
                }
            } else if (uniTagInfo.getConfiguredMacAddress() != null &&
                    !uniTagInfo.getConfiguredMacAddress().isEmpty()) {
                log.info("Using configured mac address for FTTB {}", uniTagInfo.getConfiguredMacAddress());
                return MacAddress.valueOf(uniTagInfo.getConfiguredMacAddress());
            }
        }
        return null;
    }
}
