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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class OltDeviceListenerTest extends OltTestHelpers {
    private OltDeviceListener oltDeviceListener;

    protected BlockingQueue<DiscoveredSubscriber> discoveredSubscribersQueue =
            new LinkedBlockingQueue<>();

    private final DeviceId deviceId = DeviceId.deviceId("test-device");
    private final Device testDevice = new DefaultDevice(ProviderId.NONE, deviceId, Device.Type.OLT,
            "testManufacturer", "1.0", "1.0", "SN", new ChassisId(1));

    @Before
    public void setUp() {
        MastershipService mastershipService = Mockito.mock(MastershipService.class);
        OltDeviceServiceInterface oltDeviceService = Mockito.mock(OltDeviceService.class);
        OltFlowServiceInterface oltFlowService = Mockito.mock(OltFlowService.class);
        OltMeterServiceInterface oltMeterService = Mockito.mock(OltMeterService.class);
        DeviceService deviceService = Mockito.mock(DeviceService.class);
        LeadershipService leadershipService = Mockito.mock(LeadershipService.class);
        ClusterService clusterService = Mockito.mock(ClusterService.class);
        BaseInformationService<SubscriberAndDeviceInformation> subsService = Mockito.mock(BaseInformationService.class);

        OltDeviceListener baseClass = new OltDeviceListener(clusterService, mastershipService,
                                                            leadershipService, deviceService,
                                                            oltDeviceService, oltFlowService,
                                                            oltMeterService, discoveredSubscribersQueue,
                                                            subsService);
        baseClass.portExecutor = Mockito.mock(ExecutorService.class);
        oltDeviceListener = Mockito.spy(baseClass);

        // mock the executor so it immediately invokes the method
        doAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Exception {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        }).when(baseClass.portExecutor).execute(any(Runnable.class));


        discoveredSubscribersQueue.clear();
    }

    @Test
    public void testDeviceDisconnection() {
        doReturn(true).when(oltDeviceListener.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(oltDeviceListener.deviceService).isAvailable(any());
        doReturn(new LinkedList<Port>()).when(oltDeviceListener.deviceService).getPorts(any());

        DeviceEvent disconnect = new DeviceEvent(DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED, testDevice, null);
        oltDeviceListener.event(disconnect);

        verify(oltDeviceListener.oltFlowService, times(1)).purgeDeviceFlows(testDevice.id());
        verify(oltDeviceListener.oltMeterService, times(1)).purgeDeviceMeters(testDevice.id());
    }

    @Test
    public void testPortEventOwnership() {
        // make sure that we ignore events for devices that are not local to this node

        // make sure the device is recognized as an OLT and the port is not an NNI
        doReturn(true).when(oltDeviceListener.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(oltDeviceListener.oltDeviceService).isNniPort(eq(testDevice), any());

        // make sure we're not leaders of the device
        doReturn(false).when(oltDeviceListener.oltDeviceService).isLocalLeader(any());

        // this is a new port, should create an entry in the queue
        Port uniUpdateEnabled = new OltPort(true, PortNumber.portNumber(16),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniUpdateEnabledEvent =
                new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, uniUpdateEnabled);
        oltDeviceListener.event(uniUpdateEnabledEvent);

        assert discoveredSubscribersQueue.isEmpty();
    }

    @Test
    public void testNniEvent() throws InterruptedException {
        // make sure the device is recognized as an OLT and the port is recognized as an NNI,
        // and we're local leaders
        doReturn(true).when(oltDeviceListener.oltDeviceService).isOlt(testDevice);
        doReturn(true).when(oltDeviceListener.oltDeviceService).isNniPort(eq(testDevice), any());
        doReturn(true).when(oltDeviceListener.oltDeviceService).isLocalLeader(any());

        Port enabledNniPort = new OltPort(true, PortNumber.portNumber(1048576),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
        DeviceEvent nniEnabledEvent = new DeviceEvent(DeviceEvent.Type.PORT_ADDED, testDevice, enabledNniPort);
        oltDeviceListener.event(nniEnabledEvent);

        // NNI events are straight forward, we can provision the flows directly
        assert discoveredSubscribersQueue.isEmpty();
        verify(oltDeviceListener.oltFlowService, times(1))
                .handleNniFlows(testDevice, enabledNniPort, OltFlowService.FlowOperation.ADD);

        Port disabledNniPort = new OltPort(false, PortNumber.portNumber(1048576),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
        DeviceEvent nniDisabledEvent = new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, disabledNniPort);
        oltDeviceListener.event(nniDisabledEvent);

        assert discoveredSubscribersQueue.isEmpty();
        verify(oltDeviceListener.oltFlowService, times(1))
                .handleNniFlows(testDevice, disabledNniPort, OltFlowService.FlowOperation.REMOVE);

        // when we disable the device we receive a PORT_REMOVED event with status ENABLED
        // make sure we're removing the flows correctly
        DeviceEvent nniRemoveEvent = new DeviceEvent(DeviceEvent.Type.PORT_REMOVED, testDevice, enabledNniPort);
        oltDeviceListener.event(nniRemoveEvent);

        assert discoveredSubscribersQueue.isEmpty();
        verify(oltDeviceListener.oltFlowService, times(1))
                .handleNniFlows(testDevice, enabledNniPort, OltFlowService.FlowOperation.REMOVE);
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
        doReturn(true).when(oltDeviceListener.oltDeviceService).isOlt(testDevice);
        doReturn(false).when(oltDeviceListener.oltDeviceService).isNniPort(eq(testDevice), any());
        doReturn(true).when(oltDeviceListener.oltDeviceService).isLocalLeader(any());

        PortNumber uniPortNumber = PortNumber.portNumber(16);
        Port uniAddedDisabled = new OltPort(false, uniPortNumber,
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniAddedDisabledEvent = new DeviceEvent(DeviceEvent.Type.PORT_ADDED, testDevice, uniAddedDisabled);

        // if the port does not have default EAPOL we should not generate an event
        oltDeviceListener.event(uniAddedDisabledEvent);
        assert discoveredSubscribersQueue.isEmpty();

        // if the port has default EAPOL then create an entry in the queue to remove it
        doReturn(true).when(oltDeviceListener.oltFlowService)
                .hasDefaultEapol(testDevice.id(), uniPortNumber);
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        doReturn(si).when(oltDeviceListener.subsService).get("uni-1");

        oltDeviceListener.event(uniAddedDisabledEvent);
        assert !discoveredSubscribersQueue.isEmpty();
        sub = discoveredSubscribersQueue.poll();
        assert !sub.hasSubscriber; // this is not a provision subscriber call
        assert sub.device.equals(testDevice);
        assert sub.port.equals(uniAddedDisabled);
        assert sub.status.equals(DiscoveredSubscriber.Status.REMOVED); // we need to remove flows for this port (if any)
        assert discoveredSubscribersQueue.isEmpty(); // the queue is now empty

        Port uniUpdateEnabled = new OltPort(true, PortNumber.portNumber(16),
                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());
        DeviceEvent uniUpdateEnabledEvent =
                new DeviceEvent(DeviceEvent.Type.PORT_UPDATED, testDevice, uniUpdateEnabled);
        oltDeviceListener.event(uniUpdateEnabledEvent);

        assert !discoveredSubscribersQueue.isEmpty();
        sub = discoveredSubscribersQueue.poll();
        assert !sub.hasSubscriber; // this is not a provision subscriber call
        assert sub.device.equals(testDevice);
        assert sub.port.equals(uniUpdateEnabled);
        assert sub.status.equals(DiscoveredSubscriber.Status.ADDED); // we need to remove flows for this port (if any)
        assert discoveredSubscribersQueue.isEmpty(); // the queue is now empty
    }
}
