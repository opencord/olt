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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.onlab.packet.ChassisId;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.store.service.TestStorageService;
import org.opencord.olt.DiscoveredSubscriber;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_BP_ID_DEFAULT;

/**
 * Set of tests of the ONOS application component.
 */

@RunWith(MockitoJUnitRunner.class)
public class OltTest extends OltTestHelpers {

    private Olt component;
    private final ApplicationId testAppId = new DefaultApplicationId(1, "org.opencord.olt.test");
    private final Logger log = LoggerFactory.getLogger(getClass());

    private DeviceId deviceId = DeviceId.deviceId("test-device");
    private Device testDevice = new DefaultDevice(ProviderId.NONE, deviceId, Device.Type.OLT,
            "testManufacturer", "1.0", "1.0", "SN", new ChassisId(1));
    private Port uniUpdateEnabled = new OltPort(testDevice, true, PortNumber.portNumber(16),
                                                DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1")
                                                        .build());
    private ConnectPoint cp = new ConnectPoint(deviceId, uniUpdateEnabled.number());
    private DiscoveredSubscriber sub;

    @Before
    public void setUp() {
        component = new Olt();
        component.requeueDelay = 0; // avoid delays in the queue add to make things easier in testing
        component.cfgService = new ComponentConfigAdapter();
        component.deviceService = Mockito.mock(DeviceService.class);
        component.storageService = new TestStorageService();
        component.coreService = Mockito.spy(new CoreServiceAdapter());
        component.oltDeviceService = Mockito.mock(OltDeviceService.class);

        doReturn(testAppId).when(component.coreService).registerApplication("org.opencord.olt");

        component.discoveredSubscriberExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        groupedThreads("onos/olt",
                "discovered-cp-%d", log));
        component.flowsExecutor =
                Executors.newFixedThreadPool(component.flowProcessingThreads,
                                             groupedThreads("onos/olt-service",
                                                            "flows-installer-%d"));

        component.subscriberExecutor = Executors.newFixedThreadPool(component.subscriberProcessingThreads,
                                                          groupedThreads("onos/olt-service",
                                                                         "subscriber-installer-%d"));
        SadisService sadisService = Mockito.mock(SadisService.class);
        component.oltFlowService = Mockito.mock(OltFlowService.class);
        component.sadisService = sadisService;

        // reset the spy on oltFlowService
        reset(component.oltFlowService);

        component.bindSadisService(sadisService);
        component.eventsQueues = new HashMap<>();
        component.eventsQueues.put(cp, new LinkedBlockingQueue<>());

        component.discoveredSubscriberExecutor.execute(() -> {
            component.processDiscoveredSubscribers();
        });
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        sub = new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.ADDED,
                                         false, si);
    }

    @After
    public void tearDown() {
        component.deactivate(null);
    }

    @Test
    public void testProcessDiscoveredSubscribersBasicPortSuccess() throws Exception {
        doReturn(true).when(component.deviceService).isAvailable(any());
        doReturn(sub.port).when(component.deviceService).getPort(any(), any());
        doReturn(true).when(component.oltFlowService).handleBasicPortFlows(eq(sub), eq(DEFAULT_BP_ID_DEFAULT),
                eq(DEFAULT_BP_ID_DEFAULT));
        doReturn(true).when(component.oltDeviceService).isLocalLeader(cp.deviceId());

        // adding the discovered subscriber to the queue
        LinkedBlockingQueue<DiscoveredSubscriber> q = component.eventsQueues.get(cp);
        q.add(sub);
        component.eventsQueues.put(cp, q);

        // check that we're calling the correct method
        TimeUnit.MILLISECONDS.sleep(600);
        verify(component.oltFlowService, atLeastOnce()).handleBasicPortFlows(eq(sub), eq(DEFAULT_BP_ID_DEFAULT),
                eq(DEFAULT_BP_ID_DEFAULT));

        // check if the method doesn't throw an exception we're removing the subscriber from the queue
        LinkedBlockingQueue<DiscoveredSubscriber> updatedQueue = component.eventsQueues.get(cp);
        assert updatedQueue.isEmpty();
    }

    @Test
    public void testProcessDiscoveredSubscribersBasicPortException() throws Exception {
        doReturn(true).when(component.deviceService).isAvailable(any());
        doReturn(sub.port).when(component.deviceService).getPort(any(), any());
        doReturn(false).when(component.oltFlowService).handleBasicPortFlows(any(), eq(DEFAULT_BP_ID_DEFAULT),
                eq(DEFAULT_BP_ID_DEFAULT));
        doReturn(true).when(component.oltDeviceService).isLocalLeader(cp.deviceId());
        // replace the queue with a spy
        LinkedBlockingQueue<DiscoveredSubscriber> q = component.eventsQueues.get(cp);
        LinkedBlockingQueue<DiscoveredSubscriber> spiedQueue = spy(q);
        // adding the discovered subscriber to the queue
        spiedQueue.add(sub);
        component.eventsQueues.put(cp, spiedQueue);

        TimeUnit.MILLISECONDS.sleep(600);

        // check that we're calling the correct method,
        // since the subscriber is not removed from the queue we're calling the method multiple times
        verify(component.oltFlowService, atLeastOnce()).handleBasicPortFlows(eq(sub), eq(DEFAULT_BP_ID_DEFAULT),
                eq(DEFAULT_BP_ID_DEFAULT));

        // check if the method throws an exception we are not removing the subscriber from the queue
        verify(spiedQueue, never()).remove(sub);
    }

    @Test
    public void testAddSubscriberToQueue() {

        // replace the queue with a spy
        LinkedBlockingQueue<DiscoveredSubscriber> q = component.eventsQueues.get(cp);
        LinkedBlockingQueue<DiscoveredSubscriber> spiedQueue = spy(q);
        component.eventsQueues.put(cp, spiedQueue);

        component.addSubscriberToQueue(sub);
        component.addSubscriberToQueue(sub);

        verify(spiedQueue, times(2)).add(sub);
        Assert.assertEquals(2, spiedQueue.size());



    }

}
