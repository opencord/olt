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

import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import java.util.List;

public interface OltService {
    /**
     * Provisions connectivity for a subscriber on an access device.
     * Installs flows for all uni tag information
     *
     * @param cp subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean provisionSubscriber(ConnectPoint cp);
    boolean removeSubscriber(ConnectPoint cp);

    boolean provisionSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId);

    boolean removeSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId);

     List<DeviceId> getConnectedOlts();

    /**
     * Finds the connect point to which a subscriber is connected.
     *
     * @param id The id of the subscriber, this is the same ID as in Sadis
     * @return Subscribers ConnectPoint if found else null
     */
    ConnectPoint findSubscriberConnectPoint(String id);

}
