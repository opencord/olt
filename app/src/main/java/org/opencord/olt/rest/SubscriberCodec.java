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

package org.opencord.olt.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.packet.VlanId;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.onosproject.net.ConnectPoint;
import org.opencord.olt.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes and decodes Subscribers to/from JSON.
 */
public class SubscriberCodec extends JsonCodec<Subscriber> {

    private static final String DEVICE_ID = "deviceId";
    private static final String PORT = "port";
    private static final String S_VLAN = "sVlan";
    private static final String C_VLAN = "cVlan";

    @Override
    public ObjectNode encode(Subscriber entity, CodecContext context) {
        ObjectNode subscriberNode = context.mapper().createObjectNode();
        subscriberNode.put(DEVICE_ID, entity.uniPort().deviceId().toString());
        subscriberNode.put(PORT, entity.uniPort().port().toString());
        subscriberNode.put(S_VLAN, entity.sVlan().toString());
        subscriberNode.put(C_VLAN, entity.cVlan().toString());
        return subscriberNode;
    }

    @Override
    public Subscriber decode(ObjectNode json, CodecContext context) {
        String s = json.path(DEVICE_ID).asText() + "/" + json.path(PORT).asText();
        Logger log = LoggerFactory.getLogger("foo");
        log.info("string {}", s);

        ConnectPoint cp = ConnectPoint.deviceConnectPoint(
                json.path(DEVICE_ID).asText() + "/" + json.path(PORT).asText());
        VlanId sVlanId = VlanId.vlanId(json.path(S_VLAN).asText());
        VlanId cVlanId = VlanId.vlanId(json.path(C_VLAN).asText());
        return new Subscriber(cp, sVlanId, cVlanId);
    }
}
