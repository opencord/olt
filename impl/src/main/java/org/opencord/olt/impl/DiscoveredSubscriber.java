package org.opencord.olt.impl;

import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.Objects;

public class DiscoveredSubscriber {

    public enum Status {
        ADDED,
        REMOVED,
    }

    public Port port;
    public Device device;
    public Enum<Status> status;
    public boolean hasSubscriber;
    public SubscriberAndDeviceInformation subscriberAndDeviceInformation;

    public DiscoveredSubscriber(Device device, Port port, Status status, boolean hasSubscriber,
                                SubscriberAndDeviceInformation si) {
        this.device = device;
        this.port = port;
        this.status = status;
        this.hasSubscriber = hasSubscriber;
        subscriberAndDeviceInformation = si;
    }

    //TODO check this and throw error
    public String portName() {
        return port.annotations().value(AnnotationKeys.PORT_NAME);
    }

    @Override
    public String toString() {
        return String.format("%s{device:%s, port: %s, status: %s, provisionSubscriber: %s, " +
                                     "subscriberAndDeviceInformation: %s}",
                this.getClass().getName(),
                this.device.id().toString(),
                this.port.number().toString(),
                this.status.toString(),
                this.hasSubscriber,
                this.subscriberAndDeviceInformation
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
                Objects.equals(port, that.port) &&
                Objects.equals(device, that.device) &&
                Objects.equals(status, that.status) &&
                Objects.equals(subscriberAndDeviceInformation, that.subscriberAndDeviceInformation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, device, status, hasSubscriber, subscriberAndDeviceInformation);
    }
}
