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

package org.opencord.olt;

import java.util.Objects;

/**
 * OltPortStatus is used to keep track of the flow status for a subscriber service.
 */
public class OltPortStatus {
    // TODO consider adding a lastUpdated field, it may help with debugging
    public OltFlowsStatus defaultEapolStatus;
    public OltFlowsStatus subscriberEapolStatus;
    public OltFlowsStatus subscriberFlowsStatus;
    // NOTE we need to keep track of the DHCP status as that is installed before the other flows
    // if macLearning is enabled (DHCP is needed to learn the MacAddress from the host)
    public OltFlowsStatus dhcpStatus;
    public OltFlowsStatus pppoeStatus;

    /**
     * Creates a OltPortStatus from a group of OltFlowsStatus.
     *
     * @param defaultEapolStatus     the default
     * @param subscriberEapolStatus  the status
     * @param subscriberFlowsStatus  the bandwidth profile
     * @param dhcpStatus             the bandwidth profile
     * @param pppoeStatus            the bandwidth profile
     */
    public OltPortStatus(OltFlowsStatus defaultEapolStatus,
                         OltFlowsStatus subscriberEapolStatus,
                         OltFlowsStatus subscriberFlowsStatus,
                         OltFlowsStatus dhcpStatus,
                         OltFlowsStatus pppoeStatus) {
        this.defaultEapolStatus = defaultEapolStatus;
        this.subscriberEapolStatus = subscriberEapolStatus;
        this.subscriberFlowsStatus = subscriberFlowsStatus;
        this.dhcpStatus = dhcpStatus;
        this.pppoeStatus = pppoeStatus;
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
                && subscriberEapolStatus == that.subscriberEapolStatus
                && subscriberFlowsStatus == that.subscriberFlowsStatus
                && dhcpStatus == that.dhcpStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultEapolStatus, subscriberEapolStatus,
                            subscriberFlowsStatus, dhcpStatus);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OltPortStatus{");
        sb.append("defaultEapolStatus=").append(defaultEapolStatus);
        sb.append(", subscriberEapolStatus=").append(subscriberEapolStatus);
        sb.append(", subscriberFlowsStatus=").append(subscriberFlowsStatus);
        sb.append(", dhcpStatus=").append(dhcpStatus);
        sb.append('}');
        return sb.toString();
    }
}
