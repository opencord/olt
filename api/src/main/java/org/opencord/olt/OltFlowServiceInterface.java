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

import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.opencord.sadis.UniTagInformation;

import java.util.Map;

/**
 * Interface for flow installation/removal methods for different types of traffic.
 */
public interface OltFlowServiceInterface {
    /**
     * Installs or removes default flows for the port to trap to controller.
     * @param sub the information about the port
     * @param defaultBpId the default bandwidth profile
     * @param oltBandwidthProfile the olt bandwidth profile.
     * @return true if successful
     */
    boolean handleBasicPortFlows(
            DiscoveredSubscriber sub, String defaultBpId, String oltBandwidthProfile);

    /**
     * Installs or removes subscriber specific flows.
     * @param sub the information about the subscriber
     * @param defaultBpId the default bandwidth profile
     * @param multicastServiceName the multicast service name.
     * @return true if successful
     */
    boolean handleSubscriberFlows(DiscoveredSubscriber sub, String defaultBpId, String multicastServiceName);

    /**
     * Installs or removes flows on the NNI port.
     * @param device the OLT
     * @param port the NNI port
     * @param action the operatio, ADD or REMOVE.
     */
    void handleNniFlows(Device device, Port port, FlowOperation action);

    /**
     * Checks if the default eapol flow is already installed.
     * @param port the port
     * @return true if installed, false otherwise.
     */
    boolean hasDefaultEapol(Port port);

    /**
     * Checks if the dhcp flows are installed.
     * @param port the port
     * @param uti the UniTagInformation to check for
     * @return true if installed, false otherwise.
     */
    boolean hasDhcpFlows(Port port, UniTagInformation uti);

    /**
     * Checks if the pppoe flows are installed.
     * @param port the port
     * @param uti the UniTagInformation to check for
     * @return true if installed, false otherwise.
     */
    boolean hasPppoeFlows(Port port, UniTagInformation uti);

    /**
     * Checks if the subscriber flows are installed.
     * @param port the port
     * @param uti the UniTagInformation to check for
     * @return true if installed, false otherwise.
     */
    boolean hasSubscriberFlows(Port port, UniTagInformation uti);

    /**
     * Removes all device flows.
     * @param deviceId the olt.
     */
    void purgeDeviceFlows(DeviceId deviceId);

    /**
     * Return the status of installation on the connect points.
     * @return the status map
     */
    Map<ServiceKey, OltPortStatus> getConnectPointStatus();

    /**
     * Returns all the programmed subscribers.
     * @return the subscribers
     */
    Map<ServiceKey, UniTagInformation> getProgrammedSubscribers();

    /**
     * Returns the list of requested subscribers to be installed with status.
     * @return the list
     */
    Map<ServiceKey, Boolean> getRequestedSubscribers();

    /**
     * Returns if a subscriber on a port is provisioned or not.
     * @param cp the port
     * @return true if any service on that port is provisioned, false otherwise
     */
    boolean isSubscriberServiceProvisioned(AccessDevicePort cp);

    /**
     * Returns if a subscriber on a port is provisioned or not.
     * @param sk the SubscriberKey
     * @return true if provisioned, false otherwise
     */
    boolean isSubscriberServiceProvisioned(ServiceKey sk);

    /**
     * Updates the subscriber provisioning status.
     * @param sk the SubscriberKey
     * @param status the next status
     */
    void updateProvisionedSubscriberStatus(ServiceKey sk, Boolean status);
}
