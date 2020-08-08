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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.onlab.packet.VlanId;
import org.onosproject.event.ListenerService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;

import com.google.common.collect.ImmutableMap;
import org.opencord.sadis.UniTagInformation;

/**
 * Service for interacting with an access device (OLT).
 */
public interface AccessDeviceService
        extends ListenerService<AccessDeviceEvent, AccessDeviceListener> {

    /**
     * Provisions connectivity for a subscriber on an access device.
     * Installs flows for all uni tag information
     *
     * @param port subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean provisionSubscriber(ConnectPoint port);

    /**
     * Removes provisioned connectivity for a subscriber from an access device.
     * Removes flows for all uni tag information
     *
     * @param port subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean removeSubscriber(ConnectPoint port);

    /**
     * Provisions a uni tag information for the specific subscriber.
     * It finds the related uni tag information from the subscriber uni tag list
     * and installs it
     *
     * @param subscriberId Identification of the subscriber
     * @param sTag         additional outer tag on this port
     * @param cTag         additional inner tag on this port
     * @param tpId         additional technology profile id
     * @return true if successful false otherwise
     */
    boolean provisionSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag,
                                Optional<VlanId> cTag, Optional<Integer> tpId);

    /**
     * Removes a uni tag information for the specific subscriber.
     * It finds the related uni tag information from the subscriber uni tag list
     * and remove it
     *
     * @param subscriberId Identification of the subscriber
     * @param sTag         additional outer tag on this port
     * @param cTag         additional inner tag on this port
     * @param tpId         additional technology profile id
     * @return true if successful false otherwise
     */
    boolean removeSubscriber(AccessSubscriberId subscriberId, Optional<VlanId> sTag,
                             Optional<VlanId> cTag, Optional<Integer> tpId);

    /**
     * Returns the list of active OLTs.
     *
     * @return a List
     */
    List<DeviceId> fetchOlts();

    /**
     * Returns information about subscribers that have been programmed in the
     * data-plane. It shows all uni tag information list of the subscribers even if
     * these have not been programmed.
     *
     * @return an immutable map of locations and subscriber information
     */
    ImmutableMap<ConnectPoint, Set<UniTagInformation>> getProgSubs();

    /**
     * Returns information about subscribers that have NOT been programmed in the
     * data-plane. It shows all uni tag information list of the subscribers even if
     * these have not been programmed, meaning no flows have been sent to the device.
     *
     * @return an immutable map of locations and subscriber information
     */
    ImmutableMap<ConnectPoint, Set<UniTagInformation>> getFailedSubs();

}
