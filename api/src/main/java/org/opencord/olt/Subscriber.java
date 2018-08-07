/*
 * Copyright 2018-present Open Networking Foundation
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

public class Subscriber {

    private final ConnectPoint uniPort;
    private final VlanId sVlan;
    private final VlanId cVlan;

    public Subscriber(ConnectPoint uniPort, VlanId sVlan, VlanId cVlan) {
        this.uniPort = uniPort;
        this.sVlan = sVlan;
        this.cVlan = cVlan;
    }

    public SubscriberId id() {

    }

    public ConnectPoint uniPort() {
        return uniPort;
    }

    public VlanId sVlan() {
        return sVlan;
    }

    public VlanId cVlan() {
        return cVlan;
    }
}
