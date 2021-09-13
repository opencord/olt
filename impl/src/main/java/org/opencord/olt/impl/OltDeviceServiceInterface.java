package org.opencord.olt.impl;

import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;

import java.util.Optional;

public interface OltDeviceServiceInterface {
    boolean isOlt(Device device);
    boolean isNniPort(Device device, Port port);
    Optional<Port> getNniPort(Device device);

    boolean isLocalLeader(DeviceId deviceId);
}
