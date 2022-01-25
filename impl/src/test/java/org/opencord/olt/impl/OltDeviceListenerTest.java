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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.onlab.packet.ChassisId;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.ProviderId;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OltDeviceListenerTest extends OltTestHelpers {
    private Olt olt;
    private Olt.OltDeviceListener oltDeviceListener;

    private final DeviceId deviceId = DeviceId.deviceId("test-device");
    private final Device testDevice = new DefaultDevice(ProviderId.NONE, deviceId, Device.Type.OLT,
            "testManufacturer", "1.0", "1.0", "SN", new ChassisId(1));

    @Before
    public void setUp() {
        olt = new Olt();
        olt.eventsQueues = new HashMap<>();
        olt.mastershipService = Mockito.mock(MastershipService.class);
        olt.oltDeviceService = Mockito.mock(OltDeviceService.class);
        olt.oltFlowService = Mockito.mock(OltFlowService.class);
        olt.oltMeterService = Mockito.mock(OltMeterService.class);
        olt.deviceService = Mockito.mock(DeviceService.class);
        olt.leadershipService = Mockito.mock(LeadershipService.class);
        olt.clusterService = Mockito.mock(ClusterService.class);
        olt.subsService = Mockito.mock(BaseInformationService.class);

        Olt.OltDeviceListener baseClass = olt.deviceListener;
        baseClass.eventExecutor = Mockito.mock(ExecutorService.class);
        oltDeviceListener = Mockito.spy(baseClass);

        // mock the executor so it immediately invokes the method
        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Exception {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(baseClass.eventExecutor).execute(any(Runnable.class));

        olt.eventsQueues.forEach((cp, q) -> q.clear());
    }

    @Test
    public void testDeviceDisconnection() {
        doReturn(true).when(olt.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(olt.deviceService).isAvailable(any());
        doReturn(new LinkedList<Port>()).when(olt.deviceService).getPorts(any());
        doReturn(true).when(olt.oltDeviceService).isLocalLeader(any());

        DeviceEvent disconnect = new DeviceEvent(DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED, testDevice, null);
        oltDeviceListener.event(disconnect);

        verify(olt.oltFlowService, times(1)).purgeDeviceFlows(testDevice.id());
        verify(olt.oltMeterService, times(1)).purgeDeviceMeters(testDevice.id());
    }

    @Test
    public void testPortEventOwnership() {
        // make sure that we ignore events for devices that are not local to this node

        // make sure the device is recognized as an OLT and the port is not an NNI
        doReturn(true).when(olt.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(olt.oltDeviceService).isNniPort(eq(testDevice), any());

        // make sure we're not leaders of the device
        doReturn(false).when(olt.oltDeviceService).isLocalLeader(any());

        // this is a new port, should not create an entry in the queue
        // we're not owners of the device
        Port uniUpdateEnabled = new OltPort(testDevice, true, PortNumber.portNumber(16),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniUpdateEnabledEvent =
                new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, uniUpdateEnabled);
        oltDeviceListener.event(uniUpdateEnabledEvent);

        // the queue won't even be created
        assert olt.eventsQueues.isEmpty();
    }

    @Test
    public void testNniEvent() throws InterruptedException {
        // make sure the device is recognized as an OLT and the port is recognized as an NNI,
        // and we're local leaders
        doReturn(true).when(olt.oltDeviceService).isOlt(testDevice);
        doReturn(true).when(olt.oltDeviceService).isNniPort(eq(testDevice), any());
        doReturn(true).when(olt.oltDeviceService).isLocalLeader(any());

        Port enabledNniPort = new OltPort(testDevice, true, PortNumber.portNumber(1048576),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
        DeviceEvent nniEnabledEvent = new DeviceEvent(DeviceEvent.Type.PORT_ADDED, testDevice, enabledNniPort);
        oltDeviceListener.event(nniEnabledEvent);

        // NNI events are straight forward, we can provision the flows directly
        assert olt.eventsQueues.isEmpty();
        verify(olt.oltFlowService, times(1))
                .handleNniFlows(testDevice, enabledNniPort, OltFlowService.FlowOperation.ADD);

        Port disabledNniPort = new OltPort(testDevice, false, PortNumber.portNumber(1048576),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
        DeviceEvent nniDisabledEvent = new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, disabledNniPort);
        oltDeviceListener.event(nniDisabledEvent);

        // when the NNI goes down we ignore the event
        assert olt.eventsQueues.isEmpty();
        verify(olt.oltFlowService, never())
                .handleNniFlows(testDevice, disabledNniPort, OltFlowService.FlowOperation.REMOVE);

        // if the NNI is removed we ignore the event
        Port removedNniPort = new OltPort(testDevice, true, PortNumber.portNumber(1048576),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
        DeviceEvent nniRemovedEvent = new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, removedNniPort);
        oltDeviceListener.event(nniRemovedEvent);

        assert olt.eventsQueues.isEmpty();
        verify(olt.oltFlowService, never())
                .handleNniFlows(testDevice, removedNniPort, OltFlowService.FlowOperation.REMOVE);
    }

    @Test
    public void testUniEvents() {
        DiscoveredSubscriber sub;
        // there are few cases we need to test in the UNI port case:
        // - [X] UNI port added in disabled state
        // - [X] UNI port added in disabled state (with default EAPOL installed)
        // - UNI port added in enabled state
        // - [X] UNI port updated to enabled state
        // - UNI port updated to disabled state
        // - UNI port removed (assumes it's disabled state)

        // make sure the device is recognized as an OLT, the port is not an NNI,
        // and we're local masters
        doReturn(true).when(olt.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(olt.oltDeviceService).isNniPort(eq(testDevice), any());
        doReturn(true).when(olt.oltDeviceService).isLocalLeader(any());

        PortNumber uniPortNumber = PortNumber.portNumber(16);
        Port uniAddedDisabled = new OltPort(testDevice, false, uniPortNumber,
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniAddedDisabledEvent =
                new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, uniAddedDisabled);
        ConnectPoint cp = new ConnectPoint(testDevice.id(), uniPortNumber);

        // if the port does not have default EAPOL we should not generate an event
        oltDeviceListener.event(uniAddedDisabledEvent);

        // no event == no queue is created
        assert olt.eventsQueues.isEmpty();

        // if the port has default EAPOL then create an entry in the queue to remove it
        doReturn(true).when(olt.oltFlowService)
                .hasDefaultEapol(uniAddedDisabled);
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        doReturn(si).when(olt.subsService).get("uni-1");

        oltDeviceListener.event(uniAddedDisabledEvent);
        LinkedBlockingQueue<DiscoveredSubscriber> q = olt.eventsQueues.get(cp);
        assert !q.isEmpty();
        sub = q.poll();
        assert !sub.hasSubscriber; // this is not a provision subscriber call
        assert sub.device.equals(testDevice);
        assert sub.port.equals(uniAddedDisabled);
        assert sub.status.equals(DiscoveredSubscriber.Status.REMOVED); // we need to remove flows for this port (if any)
        assert q.isEmpty(); // the queue is now empty

        Port uniUpdateEnabled = new OltPort(testDevice, true, PortNumber.portNumber(16),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniUpdateEnabledEvent =
                new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, uniUpdateEnabled);
        oltDeviceListener.event(uniUpdateEnabledEvent);

        assert !q.isEmpty();
        sub = q.poll();
        assert !sub.hasSubscriber; // this is not a provision subscriber call
        assert sub.device.equals(testDevice);
        assert sub.port.equals(uniUpdateEnabled);
        assert sub.status.equals(DiscoveredSubscriber.Status.ADDED); // we need to remove flows for this port (if any)
        assert q.isEmpty(); // the queue is now empty
    }
}
