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
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Port;

import java.util.Objects;

/**
 * OLT device port.
 */
public class AccessDevicePort {

    private ConnectPoint cp;
    private String name;

    /**
     * Creates an AccessDevicePort with given ONOS port.
     *
     * @param port ONOS port
     */
    public AccessDevicePort(Port port) {
        this.cp = ConnectPoint.deviceConnectPoint(port.element().id() + "/" + port.number().toLong());
        this.name = getPortName(port);
    }

    /**
     * Creates an AccessDevicePort with given ONOS connectPoint and name.
     *
     * @param cp ONOS connect point
     * @param name OLT port name
     */
    public AccessDevicePort(ConnectPoint cp, String name) {
        this.cp = cp;
        this.name = name;
    }

    /**
     * Get ONOS ConnectPoint object.
     *
     * @return ONOS connect point
     */
    public ConnectPoint connectPoint() {
        return this.cp;
    }

    /**
     * Get OLT port name which is combination of serial number and uni index.
     *
     * @return OLT port name (ex: BBSM00010001-1)
     */
    public String name() {
        return this.name;
    }

    @Override
    public String toString() {
        return cp.toString() + '[' + name + ']';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AccessDevicePort that = (AccessDevicePort) o;
        return Objects.equals(cp, that.cp) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cp, name);
    }

    private String getPortName(Port port) {
        String name = port.annotations().value(AnnotationKeys.PORT_NAME);
        return name == null ? "" : name;
    }
}
