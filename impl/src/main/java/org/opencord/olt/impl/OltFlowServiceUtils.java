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

import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.UniTagInformation;

/**
 * Utility class for Flow service utility methods.
 */
public final class OltFlowServiceUtils {


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
}
