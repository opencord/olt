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
