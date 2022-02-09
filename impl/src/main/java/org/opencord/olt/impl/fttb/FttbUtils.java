package org.opencord.olt.impl.fttb;

import org.opencord.sadis.UniTagInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FttbUtils {

    public static final String FTTB_FLOW_DIRECTION = "fttbFlowDirection";
    public static final String FTTB_FLOW_UPSTREAM = "fttbFlowUpstream";
    public static final String FTTB_FLOW_DOWNSTREAM = "fttbFlowDownstream";

    public static final String FTTB_SERVICE_NAME = "fttbServiceName";
    public static final String FTTB_SERVICE_DPU_MGMT_TRAFFIC = "DPU_MGMT_TRAFFIC";
    public static final String FTTB_SERVICE_DPU_ANCP_TRAFFIC = "DPU_ANCP_TRAFFIC";
    public static final String FTTB_SERVICE_SUBSCRIBER_TRAFFIC = "FTTB_SUBSCRIBER_TRAFFIC";


    private static final Logger log = LoggerFactory.getLogger(FttbUtils.class);

    public static boolean isFttbService(UniTagInformation uti) {
        String serviceName = uti.getServiceName();

        if (serviceName == null) {
            log.error("Could not find service name for {}", uti);
            return false;
        }

        return isFttbService(serviceName);
    }

    public static boolean isFttbService(String serviceName) {
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

    private FttbUtils() {
    }
}
