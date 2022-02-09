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

package org.opencord.olt.impl.fttb;

import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.opencord.sadis.UniTagInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        if (uti.getUsPonCTagPriority() != -1) {
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
                log.warn("Unknown service name {}, for FTTB", serviceName);
                return false;
        }
    }
}
