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
