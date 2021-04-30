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

import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;

/**
 * OLT device port.
 */
public class AccessDevicePort {

    Port port;
    Type type;

    /**
     * OLT Port Type.
     */
    public enum Type {
        NNI,
        UNI,
    }

    /**
     * Creates an AccessDevicePort with given ONOS port object and OLT port type.
     *
     * @param port ONOS port
     * @param type OLT port type
     */
    public AccessDevicePort(Port port, Type type) {
        this.port = port;
        this.type = type;
    }

    /**
     * Get ONOS Port object.
     *
     * @return ONOS port
     */
    public Port port() {
        return this.port;
    }

    /**
     * Get OLT port Type.
     *
     * @return OLT port type
     */
    public Type type() {
        return this.type;
    }

    /**
     * Check port is enabled state on ONOS.
     *
     * @return is Access Device Port enabled
     */
    public boolean isEnabled() {
        return this.port.isEnabled();
    }

    /**
     * Get Device ID of OLT which the port is connected.
     *
     * @return OLT Device ID
     */
    public DeviceId deviceId() {
        return (DeviceId) this.port.element().id();
    }

    /**
     * Get port number.
     *
     * @return port number
     */
    public PortNumber number() {
        return this.port.number();
    }

    /**
     * Get port name which is combination of serial number and uni index.
     *
     * @return port name (ex: BBSM00010001-1)
     */
    public String name() {
        return this.port.annotations().value(AnnotationKeys.PORT_NAME);
    }

    @Override
    public String toString() {
        return deviceId().toString() + '/' + number() + '[' + name() + ']';
    }

}
