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

import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;

import java.util.Optional;

/**
 * Service for olt device handling.
 */
public interface OltDeviceServiceInterface {
    /**
     * Returns true if the device is a known OLT to sadis/config.
     * @param device the device
     * @return true if a configured olt
     */
    boolean isOlt(Device device);

    /**
     * Returns true if the port is an NNI port of the OLT device.
     * @param device the device
     * @param port the port
     * @return true if an NNI port of that OLT
     */
    boolean isNniPort(Device device, PortNumber port);

    /**
     * Returns the NNi port fo the OLT device if present.
     * @param device the device
     * @return the nni Port, if present
     */
    Optional<Port> getNniPort(Device device);

    /**
     * Returns true if the instance is leader for the OLT device.
     * @param deviceId the device
     * @return true if master, false otherwise.
     */
    boolean isLocalLeader(DeviceId deviceId);
}
