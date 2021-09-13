package org.opencord.olt.impl;

import java.util.Objects;

public class OltPortStatus {
    // TODO consider adding a lastUpdated field, it may help with debugging
    public OltFlowService.OltFlowsStatus defaultEapolStatus;
    public OltFlowService.OltFlowsStatus subscriberFlowsStatus;
    // NOTE we need to keep track of the DHCP status as that is installed before the other flows
    // if macLearning is enabled (DHCP is needed to learn the MacAddress from the host)
    public OltFlowService.OltFlowsStatus dhcpStatus;

    public OltPortStatus(OltFlowService.OltFlowsStatus defaultEapolStatus,
                         OltFlowService.OltFlowsStatus subscriberFlowsStatus,
                         OltFlowService.OltFlowsStatus dhcpStatus) {
        this.defaultEapolStatus = defaultEapolStatus;
        this.subscriberFlowsStatus = subscriberFlowsStatus;
        this.dhcpStatus = dhcpStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OltPortStatus that = (OltPortStatus) o;
        return defaultEapolStatus == that.defaultEapolStatus
                && subscriberFlowsStatus == that.subscriberFlowsStatus
                && dhcpStatus == that.dhcpStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultEapolStatus, subscriberFlowsStatus, dhcpStatus);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OltPortStatus{");
        sb.append("defaultEapolStatus=").append(defaultEapolStatus);
        sb.append(", subscriberFlowsStatus=").append(subscriberFlowsStatus);
        sb.append(", dhcpStatus=").append(dhcpStatus);
        sb.append('}');
        return sb.toString();
    }
}
