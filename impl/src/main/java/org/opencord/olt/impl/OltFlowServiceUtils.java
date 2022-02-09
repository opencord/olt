package org.opencord.olt.impl;

import org.onlab.packet.VlanId;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.meter.MeterId;

public final class OltFlowServiceUtils {

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

    private OltFlowServiceUtils() {

    }
}
