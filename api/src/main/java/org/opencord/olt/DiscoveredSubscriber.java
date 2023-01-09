/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.Objects;

/**
 * Contains a subscriber's information and status for a specific device and port.
 */
public class DiscoveredSubscriber {

    /**
     * Describe whether the subscriber needs to be added or removed.
     */
    public enum Status {
        ADDED,
        REMOVED,
        // Used for the remove subscriber calls from REST/CLI
        ADMIN_REMOVED,
    }

    public Port port;
    public Device device;
    public Enum<Status> status;
    public boolean hasSubscriber;
    public SubscriberAndDeviceInformation subscriberAndDeviceInformation;

    /**
     * Creates the class with the proper information.
     *
     * @param device        the device of the subscriber
     * @param port          the port
     * @param status        the status for this specific subscriber
     * @param hasSubscriber is the subscriber present
     * @param si            the information about the tags/dhcp and other info.
     */
    public DiscoveredSubscriber(Device device, Port port, Status status, boolean hasSubscriber,
                                SubscriberAndDeviceInformation si) {
        this.device = device;
        this.port = port;
        this.status = status;
        this.hasSubscriber = hasSubscriber;
        subscriberAndDeviceInformation = si;
    }

    /**
     * Returns the port name for the subscriber.
     *
     * @return the port name.
     */
    public String portName() {
        return getPortName(port);
    }

    @Override
    public String toString() {

        return String.format("%s (status: %s, provisionSubscriber: %s)",
                portWithName(this.port),
                this.status, this.hasSubscriber
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiscoveredSubscriber that = (DiscoveredSubscriber) o;
        return hasSubscriber == that.hasSubscriber &&
                port.equals(that.port) &&
                device.equals(that.device) &&
                status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, device, status, hasSubscriber, subscriberAndDeviceInformation);
    }

    private String portWithName(Port port) {
        return port.element().id().toString() + '/' +
                port.number() + '[' +
                getPortName(port) + ']';
    }
    private String getPortName(Port port) {
        String name = port.annotations().value(AnnotationKeys.PORT_NAME);
        return name == null ? "" : name;
    }
}
