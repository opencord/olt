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

package org.opencord.olt.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.ControllerNode;
import org.onosproject.cluster.DefaultControllerNode;
import org.onosproject.cluster.Leader;
import org.onosproject.cluster.Leadership;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.opencord.sadis.SadisService;

import java.util.LinkedList;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class OltDeviceServiceTest {
    OltDeviceService component;
    private OltDeviceService oltDeviceService;

    @Before
    public void setUp() {
        component = new OltDeviceService();
        component.mastershipService = Mockito.mock(MastershipService.class);
        component.deviceService = Mockito.mock(DeviceService.class);
        component.leadershipService = Mockito.mock(LeadershipService.class);
        component.clusterService = Mockito.mock(ClusterService.class);
        component.sadisService = Mockito.mock(SadisService.class);
        component.activate();

        oltDeviceService = Mockito.spy(component);


    }

    @Test
    public void testIsLocalLeader() {

        NodeId nodeId = NodeId.nodeId("node1");
        ControllerNode localNode = new DefaultControllerNode(nodeId, "host1");
        DeviceId deviceId1 = DeviceId.deviceId("availableNotLocal");
        DeviceId deviceId2 = DeviceId.deviceId("notAvailableButLocal");
        Leadership leadership = new Leadership(deviceId2.toString(), new Leader(nodeId, 0, 0), new LinkedList<>());

        doReturn(true).when(oltDeviceService.deviceService).isAvailable(eq(deviceId1));
        doReturn(false).when(oltDeviceService.mastershipService).isLocalMaster(eq(deviceId1));
        Assert.assertFalse(oltDeviceService.isLocalLeader(deviceId1));

        doReturn(false).when(oltDeviceService.deviceService).isAvailable(eq(deviceId1));
        doReturn(localNode).when(oltDeviceService.clusterService).getLocalNode();
        doReturn(leadership).when(oltDeviceService.leadershipService).runForLeadership(eq(deviceId2.toString()));
        Assert.assertTrue(oltDeviceService.isLocalLeader(deviceId2));

    }
}
