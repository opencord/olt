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
import org.onosproject.event.ListenerService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.opencord.cordconfig.access.AccessDeviceData;

import java.util.Map;

/**
 * Service for interacting with an access device (OLT).
 */
public interface AccessDeviceService
        extends ListenerService<AccessDeviceEvent, AccessDeviceListener> {

    /**
     * Provisions connectivity for a subscriber on a given port with the given
     * VLAN tags.
     *
     * @param port subscriber connect point
     * @param sVlan s-vlan tag
     * @param cVlan c-vlan tag
     */
    SubscriberId provisionSubscriber(ConnectPoint port, VlanId sVlan, VlanId cVlan);

    /**
     * Removes provisioned connectivity for a subscriber from an access device.
     *
     * @param subscriberId subscriber ID
     */
    void removeSubscriber(SubscriberId subscriberId);

    /**
     * Gets the provisioning record for a particular subscriber ID.
     *
     * @param subscriberId subscriber ID
     * @return subscriber provisioning record
     */
    Subscriber getSubscriber(SubscriberId subscriberId);

    /**
     * Returns information about the provisioned subscribers.
     *
     * @return subscribers
     */
    Iterable<Subscriber> getSubscribers();

    /**
     * Returns the map of configured OLTs.
     *
     * @return a map
     * @deprecated per-device config record is being removed in favour of
     * getting the information through SADIS
     */
    @Deprecated
    Map<DeviceId, AccessDeviceData> fetchOlts();

}
