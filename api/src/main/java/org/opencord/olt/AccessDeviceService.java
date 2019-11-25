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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.onlab.packet.VlanId;
import org.onosproject.event.ListenerService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import com.google.common.collect.ImmutableMap;

/**
 * Service for interacting with an access device (OLT).
 */
public interface AccessDeviceService
        extends ListenerService<AccessDeviceEvent, AccessDeviceListener> {

    /**
     * Provisions connectivity for a subscriber on an access device.
     *
     * @param port subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean provisionSubscriber(ConnectPoint port);

    /**
     * Removes provisioned connectivity for a subscriber from an access device.
     *
     * @param port subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean removeSubscriber(ConnectPoint port);

    /**
     * Provisions a uni tag information for the specific subscriber.
     * It can be used for two different purposes:
     * It can find the related uni tag information from the subscriber uni tag list
     * and install it
     * OR
     * It can create a transparent flow (double tagged with no action)
     * If the enableTransparentFlows property of OLT is disabled, the first case takes place.
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
     * It can be used for two different purposes:
     * It can find the related uni tag information from the subscriber uni tag list
     * and remove it
     * OR
     * It can remove a transparent flow
     * If the enableTransparentFlows property of OLT is enabled, the first case takes place.
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
     * Returns information about the provisioned subscribers.
     *
     * @return provisioned uni tag information
     */
    Collection<Map.Entry<ConnectPoint, Set<AdditionalVlanInfo>>> getSubscribers();

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
    ImmutableMap<ConnectPoint, SubscriberAndDeviceInformation> getProgSubs();

    /**
     * Gets additional vlans for a given connect point.
     *
     * @param connectPoint the connection point (device-port)
     * @return a set of additional vlan info
     */
    Set<AdditionalVlanInfo> getAdditionalVlans(ConnectPoint connectPoint);

}
