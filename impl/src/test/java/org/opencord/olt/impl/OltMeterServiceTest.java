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
import org.mockito.Mockito;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterServiceAdapter;
import org.onosproject.net.meter.MeterState;
import org.onosproject.store.service.StorageServiceAdapter;
import org.onosproject.store.service.TestStorageService;
import org.opencord.sadis.SadisService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_BP_ID_DEFAULT;

public class OltMeterServiceTest extends OltTestHelpers {
    OltMeterService oltMeterService;
    OltMeterService component;

    DeviceId deviceId = DeviceId.deviceId("foo");

    @Before
    public void setUp() {
        component = new OltMeterService();
        component.cfgService = new ComponentConfigAdapter();
        component.coreService = new CoreServiceAdapter();
        component.storageService = new StorageServiceAdapter();
        component.sadisService = Mockito.mock(SadisService.class);
        component.meterService = new MeterServiceAdapter();
        component.storageService = new TestStorageService();
        component.activate(null);
        oltMeterService = Mockito.spy(component);
    }

    @After
    public void tearDown() {
        component.deactivate(null);
    }

    @Test
    public void testHasMeter() {

        MeterData meterPending = new MeterData(MeterId.meterId(1),
                MeterState.PENDING_ADD, "pending");
        MeterData meterAdded = new MeterData(MeterId.meterId(2),
                MeterState.ADDED, DEFAULT_BP_ID_DEFAULT);

        Map<String, MeterData> deviceMeters = new HashMap<>();
        deviceMeters.put("pending", meterPending);
        deviceMeters.put(DEFAULT_BP_ID_DEFAULT, meterAdded);
        oltMeterService.programmedMeters.put(deviceId, deviceMeters);

        assert oltMeterService.hasMeterByBandwidthProfile(deviceId, DEFAULT_BP_ID_DEFAULT);
        assert !oltMeterService.hasMeterByBandwidthProfile(deviceId, "pending");
        assert !oltMeterService.hasMeterByBandwidthProfile(deviceId, "someBandwidthProfile");

        assert !oltMeterService.hasMeterByBandwidthProfile(DeviceId.deviceId("bar"), DEFAULT_BP_ID_DEFAULT);
    }

    @Test
    public void testGetMeterId() {

        MeterData meterAdded = new MeterData(MeterId.meterId(2),
                MeterState.ADDED, DEFAULT_BP_ID_DEFAULT);

        Map<String, MeterData> deviceMeters = new HashMap<>();
        deviceMeters.put(DEFAULT_BP_ID_DEFAULT, meterAdded);
        oltMeterService.programmedMeters.put(deviceId, deviceMeters);

        Assert.assertNull(oltMeterService.getMeterIdForBandwidthProfile(deviceId, "pending"));
        Assert.assertEquals(MeterId.meterId(2),
                oltMeterService.getMeterIdForBandwidthProfile(deviceId, DEFAULT_BP_ID_DEFAULT));
    }

    @Test
    public void testCreateMeter() {

        DeviceId deviceId = DeviceId.deviceId("foo");
        String bp = "Default";

        // if we already have a meter do nothing and return true
        doReturn(true).when(oltMeterService).hasMeterByBandwidthProfile(deviceId, bp);
        Assert.assertTrue(oltMeterService.createMeter(deviceId, bp));
        verify(oltMeterService, never()).createMeterForBp(any(), any());

        // if we have a pending meter, do nothing and return false
        doReturn(false).when(oltMeterService).hasMeterByBandwidthProfile(deviceId, bp);
        doReturn(true).when(oltMeterService).hasPendingMeterByBandwidthProfile(deviceId, bp);
        Assert.assertFalse(oltMeterService.createMeter(deviceId, bp));
        verify(oltMeterService, never()).createMeterForBp(any(), any());

        // if the meter is not present at all, create it and return false
        doReturn(false).when(oltMeterService).hasMeterByBandwidthProfile(deviceId, bp);
        doReturn(false).when(oltMeterService).hasPendingMeterByBandwidthProfile(deviceId, bp);
        Assert.assertFalse(oltMeterService.createMeter(deviceId, bp));
        verify(oltMeterService, times(1)).createMeterForBp(deviceId, bp);
    }

    @Test
    public void testConcurrentMeterCreation() throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(4);

        DeviceId deviceId = DeviceId.deviceId("foo");
        String bp = "Default";

        // try to create 4 meters at the same time, only one should be created
        for (int i = 0; i < 4; i++) {

            executor.execute(() -> {
                oltMeterService.createMeter(deviceId, bp);
            });
        }

        TimeUnit.MILLISECONDS.sleep(600);

        verify(oltMeterService, times(4)).hasMeterByBandwidthProfile(deviceId, bp);
        verify(oltMeterService, times(1)).createMeterForBp(deviceId, bp);
    }
}