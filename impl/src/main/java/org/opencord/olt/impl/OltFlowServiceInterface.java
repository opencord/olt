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

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.opencord.sadis.UniTagInformation;

import java.util.Map;
import java.util.Set;

public interface OltFlowServiceInterface {

    boolean handleBasicPortFlows(
            DiscoveredSubscriber sub, String defaultBpId, String oltBandwidthProfile);

    boolean handleSubscriberFlows(DiscoveredSubscriber sub, String defaultBpId, String multicastServiceName);
    void handleNniFlows(Device device, Port port, OltFlowService.FlowOperation action);

    boolean hasDefaultEapol(DeviceId deviceId, PortNumber portNumber);
    boolean hasDhcpFlows(DeviceId deviceId, PortNumber portNumber);
    boolean hasSubscriberFlows(DeviceId deviceId, PortNumber portNumber);

    void purgeDeviceFlows(DeviceId deviceId);

    Map<ConnectPoint, OltPortStatus> getConnectPointStatus();
    Map<ConnectPoint, Set<UniTagInformation>> getProgrammedSusbcribers();
    Map<ConnectPoint, Boolean> getRequestedSusbcribers();

    Boolean isSubscriberProvisioned(ConnectPoint cp);
    void updateProvisionedSubscriberStatus(ConnectPoint cp, Boolean status);
}
