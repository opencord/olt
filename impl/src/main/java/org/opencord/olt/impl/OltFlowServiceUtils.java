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

import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthTypeCriterion;
import org.onosproject.net.flow.criteria.IPProtocolCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.UdpPortCriterion;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.UniTagInformation;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.onosproject.net.flow.instructions.Instruction.Type.L2MODIFICATION;
import static org.opencord.olt.impl.OltFlowService.EAPOL_DEFAULT_VLAN;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_TP_ID_DEFAULT;

/**
 * Utility class for Flow service utility methods.
 */
public final class OltFlowServiceUtils {

    public static final int NONE_TP_ID = -1;

    private OltFlowServiceUtils() {
    }

    /**
     * Constructs and returns the metadata from cVlan, techProfileId and upstreamOltMeterId.
     *
     * @param cVlan                 the customer vlan
     * @param techProfileId         the technology profile
     * @param upstreamOltMeterId    the upstream olt meter id
     * @return Metadata
     */
    public static Long createTechProfValueForWriteMetadata(VlanId cVlan, int techProfileId,
                                                           MeterId upstreamOltMeterId) {
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

    /**
     * Converts FlowRuleEvent.Type to OltFlowService.OltFlowsStatus.
     *
     * @param type FlowRuleEvent type
     * @return OltFlowService.OltFlowsStatus
     */
    public static OltFlowService.OltFlowsStatus flowRuleStatusToOltFlowStatus(FlowRuleEvent.Type type) {
        switch (type) {
            case RULE_ADD_REQUESTED:
                return OltFlowService.OltFlowsStatus.PENDING_ADD;
            case RULE_ADDED:
                return OltFlowService.OltFlowsStatus.ADDED;
            case RULE_REMOVE_REQUESTED:
                return OltFlowService.OltFlowsStatus.PENDING_REMOVE;
            case RULE_REMOVED:
                return OltFlowService.OltFlowsStatus.REMOVED;
            default:
                return OltFlowService.OltFlowsStatus.NONE;
        }
    }

    /**
     * Checks if the configured Mac address is valid for a UniTagInformation.
     *
     * @param tagInformation UniTagInformation
     * @return true if the mac address is valid
     */
    public static boolean isMacAddressValid(UniTagInformation tagInformation) {
        return tagInformation.getConfiguredMacAddress() != null &&
                !tagInformation.getConfiguredMacAddress().trim().equals("") &&
                !MacAddress.NONE.equals(MacAddress.valueOf(tagInformation.getConfiguredMacAddress()));
    }

    /**
     * Returns true if the flow is a DHCP flow.
     * Matches both upstream and downstream flows.
     *
     * @param flowRule The FlowRule to evaluate
     * @return boolean
     */
    public static boolean isDhcpFlow(FlowRule flowRule) {
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

    /**
     * Returns true if the flow is a Pppoe flow.
     *
     * @param flowRule The FlowRule to evaluate
     * @return boolean
     */
    public static boolean isPppoeFlow(FlowRule flowRule) {
        EthTypeCriterion ethTypeCriterion = (EthTypeCriterion) flowRule.selector()
                .getCriterion(Criterion.Type.ETH_TYPE);

        if (ethTypeCriterion == null) {
            return false;
        }
        return EthType.EtherType.PPPoED.ethType().equals(ethTypeCriterion.ethType());
    }

    /**
     * Return true if the flow is a Data flow.
     * @param flowRule The FlowRule to evaluate
     * @return boolean
     */
    public static boolean isDataFlow(FlowRule flowRule) {
        // we consider subscriber flows the one that matches on VLAN_VID
        // method is valid only because it's the last check after EAPOL and DHCP.
        // this matches mcast flows as well, if we want to avoid that we can
        // filter out the elements that have groups in the treatment or
        // mcastIp in the selector
        // IPV4_DST:224.0.0.22/32
        // treatment=[immediate=[GROUP:0x1]]

        return flowRule.selector().getCriterion(Criterion.Type.VLAN_VID) != null;
    }

    /**
     * Extracts and returns inPort selector from the FlowRule.
     *
     * @param flowRule The FlowRule to evaluate
     * @return PortNumber
     */
    public static PortNumber getPortNumberFromFlowRule(FlowRule flowRule) {
        PortCriterion inPort = (PortCriterion) flowRule.selector().getCriterion(Criterion.Type.IN_PORT);
        if (inPort != null) {
            return inPort.port();
        }
        return null;
    }

    /**
     * Constructs and returns the metadata from innerVlan, techProfileId and egressPort.
     *
     * @param innerVlan         inner vlan tag
     * @param techProfileId     technology profile
     * @param egressPort        outport
     * @return Metadata
     */
    public static Long createMetadata(VlanId innerVlan, int techProfileId, PortNumber egressPort) {
        if (techProfileId == NONE_TP_ID) {
            techProfileId = DEFAULT_TP_ID_DEFAULT;
        }

        Long writeMetadata = (long) techProfileId << 32 | egressPort.toLong();

        if (innerVlan != null && !VlanId.NONE.equals(innerVlan)) {
            writeMetadata |= (long) (innerVlan.id()) << 48;
        }

        return writeMetadata;
    }

    /***
     * Checks if the FlowRule is default eapol.
     * @param flowRule FlowRule to check.
     * @return true if FlowRule is default eapol.
     */
    public static boolean isDefaultEapolFlow(FlowRule flowRule) {
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

    /***
     * Checks if the FlowRule is Subscriber eapol.
     * @param flowRule Flow Rule
     * @return true if FlowRule is Subscriber eapol.
     */
    public static boolean isSubscriberEapolFlow(FlowRule flowRule) {
        EthTypeCriterion c = (EthTypeCriterion) flowRule.selector().getCriterion(Criterion.Type.ETH_TYPE);
        if (c == null) {
            return false;
        }
        if (c.ethType().equals(EthType.EtherType.EAPOL.ethType())) {
            AtomicBoolean isSubscriber = new AtomicBoolean(false);
            flowRule.treatment().allInstructions().forEach(instruction -> {
                if (instruction.type() == L2MODIFICATION) {
                    L2ModificationInstruction modificationInstruction = (L2ModificationInstruction) instruction;
                    if (modificationInstruction.subtype() == L2ModificationInstruction.L2SubType.VLAN_ID) {
                        L2ModificationInstruction.ModVlanIdInstruction vlanInstruction =
                                (L2ModificationInstruction.ModVlanIdInstruction) modificationInstruction;
                        if (!vlanInstruction.vlanId().id().equals(EAPOL_DEFAULT_VLAN)) {
                            isSubscriber.set(true);
                            return;
                        }
                    }
                }
            });
            return isSubscriber.get();
        }
        return false;
    }
}
