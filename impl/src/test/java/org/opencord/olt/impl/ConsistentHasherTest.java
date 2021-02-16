/*
 * Copyright 2020-present Open Networking Foundation
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
package org.opencord.olt.impl;

import org.junit.Before;
import org.junit.Test;
import org.onosproject.cluster.NodeId;
import org.onosproject.net.DeviceId;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ConsistentHasherTest {

    private static final int WEIGHT = 10;

    private static final NodeId N1 = new NodeId("10.0.0.1");
    private static final NodeId N2 = new NodeId("10.0.0.2");
    private static final NodeId N3 = new NodeId("10.0.0.3");

    private ConsistentHasher hasher;

    @Before
    public void setUp() {
        List<NodeId> servers = new ArrayList<>();
        servers.add(N1);
        servers.add(N2);

        hasher = new ConsistentHasher(servers, WEIGHT);
    }

    @Test
    public void testHasher() {
        DeviceId deviceId = DeviceId.deviceId("foo");
        NodeId server = hasher.hash(deviceId.toString());

        assertThat(server, equalTo(N1));

        deviceId = DeviceId.deviceId("bsaf");
        server = hasher.hash(deviceId.toString());

        assertThat(server, equalTo(N2));
    }

    @Test
    public void testAddServer() {
        DeviceId deviceId = DeviceId.deviceId("foo");
        NodeId server = hasher.hash(deviceId.toString());

        assertThat(server, equalTo(N1));

        hasher.addServer(N3);

        server = hasher.hash(deviceId.toString());

        assertThat(server, equalTo(N3));
    }
}
