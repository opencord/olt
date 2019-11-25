/*
 * Copyright 2016-present Open Networking Foundation
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

import org.onlab.packet.VlanId;
import org.onosproject.event.AbstractEvent;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;

import java.util.Optional;

/**
 * Describes an access device event.
 */
public class AccessDeviceEvent extends AbstractEvent<AccessDeviceEvent.Type, DeviceId> {

    private final Optional<VlanId> sVlan;
    private final Optional<VlanId> cVlan;
    private final Optional<Integer> tpId;

    private final Optional<Port> port;

    public enum Type {
        /**
         * An access device connected.
         */
        DEVICE_CONNECTED,

        /**
         * An access device disconnected.
         */
        DEVICE_DISCONNECTED,

        /**
         * A new UNI port has been detected.
         */
        UNI_ADDED,

        /**
         * An existing UNI port was removed.
         */
        UNI_REMOVED,

        /**
         * A uniTag (one service) was registered and provisioned.
         */
        SUBSCRIBER_UNI_TAG_REGISTERED,

        /**
         * A uniTag (one service) was unregistered and deprovisioned.
         */
        SUBSCRIBER_UNI_TAG_UNREGISTERED,

        /**
         * A uniTag (one service) was failed while registration.
         */
        SUBSCRIBER_UNI_TAG_REGISTRATION_FAILED,

        /**
         * A uniTag (one service) was failed while unregistration.
         */
        SUBSCRIBER_UNI_TAG_UNREGISTRATION_FAILED
    }

    /**
     * Creates an event of a given type and for the specified device, port,
     * along with the cVlanId, sVlanId, and tpId. The vlan fields may not be provisioned
     * if the event is related to the access device (dis)connection.
     *
     * @param type     the event type
     * @param deviceId the device id
     * @param port     the device port
     * @param sVlanId  the service vlan
     * @param cVlanId  the customer vlan
     * @param tpId     the technology profile
     */
    public AccessDeviceEvent(Type type, DeviceId deviceId,
                             Port port,
                             VlanId sVlanId,
                             VlanId cVlanId,
                             Integer tpId) {
        super(type, deviceId);
        this.port = Optional.ofNullable(port);
        this.sVlan = Optional.ofNullable(sVlanId);
        this.cVlan = Optional.ofNullable(cVlanId);
        this.tpId = Optional.ofNullable(tpId);
    }

    /**
     * Creates an event of a given type and for the specified device,
     * along with the cVlanId and sVlanId. The vlan fields may not be provisioned
     * if the event is related to the access device (dis)connection.
     *
     * @param type     the event type
     * @param deviceId the device id
     * @param sVlanId  the service vlan
     * @param cVlanId  the customer vlan
     * @param tpId     the technology profile id
     */
    public AccessDeviceEvent(Type type, DeviceId deviceId,
                             VlanId sVlanId,
                             VlanId cVlanId,
                             Integer tpId) {
        super(type, deviceId);
        this.sVlan = Optional.ofNullable(sVlanId);
        this.cVlan = Optional.ofNullable(cVlanId);
        this.tpId = Optional.ofNullable(tpId);
        this.port = Optional.empty();
    }

    /**
     * Creates an event of a given type and for the specified device and port.
     *
     * @param type     the event type
     * @param deviceId the device id
     * @param port     the device port
     */
    public AccessDeviceEvent(Type type, DeviceId deviceId, Port port) {
        super(type, deviceId);
        this.sVlan = Optional.empty();
        this.cVlan = Optional.empty();
        this.tpId = Optional.empty();
        this.port = Optional.ofNullable(port);
    }

    public Optional<VlanId> sVlanId() {
        return sVlan;
    }

    public Optional<VlanId> cVlanId() {
        return cVlan;
    }

    public Optional<Port> port() {
        return port;
    }

    public Optional<Integer> tpId() {
        return tpId;
    }

}
