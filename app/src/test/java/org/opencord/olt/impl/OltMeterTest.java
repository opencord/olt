/*
 * Copyright 2016-present Open Networking Foundation
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.DefaultMeter;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterKey;
import org.onosproject.net.meter.MeterListener;
import org.onosproject.net.meter.MeterRequest;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class OltMeterTest extends TestBase {
    private OltMeterService oltMeterService;

    private BandwidthProfileInformation bandwidthProfileInformation = new BandwidthProfileInformation();

    @Before
    public void setUp() {
        oltMeterService = new OltMeterService();
        oltMeterService.bpInfoToMeter = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        oltMeterService.programmedMeters = Sets.newConcurrentHashSet();
        oltMeterService.meterService = new MockMeterService();
    }

    @Test
    public void testAddAndGetMeterIdToBpMapping() {
        oltMeterService.addMeterIdToBpMapping(DEVICE_ID_1, usMeterId, usBpId);
        MeterId usMeterId = oltMeterService.getMeterIdFromBpMapping(DEVICE_ID_1, usBpId);
        assert usMeterId.equals(this.usMeterId);

        oltMeterService.addMeterIdToBpMapping(DEVICE_ID_1, dsMeterId, dsBpId);
        MeterId dsMeterId = oltMeterService.getMeterIdFromBpMapping(DEVICE_ID_1, dsBpId);
        assert  dsMeterId.equals(this.dsMeterId);

        ImmutableMap<String, Collection<MeterKey>> meterMappings = oltMeterService.getBpMeterMappings();
        assert  meterMappings.size() == 2;
    }

    @Test
    public void testCreateMeter() {
        //with provided bandwidth profile information
        bandwidthProfileInformation.setId(usBpId);
        bandwidthProfileInformation.setExceededInformationRate(10000);
        bandwidthProfileInformation.setExceededBurstSize(10000L);
        bandwidthProfileInformation.setCommittedBurstSize(10000L);
        bandwidthProfileInformation.setCommittedInformationRate(10000);

        oltMeterService.addMeterIdToBpMapping(DEVICE_ID_1, usMeterId, usBpId);


        MeterId meterId =
                oltMeterService.createMeter(DEVICE_ID_1, bandwidthProfileInformation, new CompletableFuture<>());
        assert meterId != null;

        //with null bandwidth profile information
        meterId = oltMeterService.createMeter(DEVICE_ID_1, null, new CompletableFuture<>());
        assert meterId == null;
    }


    private class MockMeterService implements org.onosproject.net.meter.MeterService {
        @Override
        public Meter submit(MeterRequest meterRequest) {
            return DefaultMeter.builder()
                    .forDevice(DEVICE_ID_1)
                    .fromApp(appId)
                    .withId(usMeterId)
                    .build();
        }

        @Override
        public void withdraw(MeterRequest meterRequest, MeterId meterId) {

        }

        @Override
        public Meter getMeter(DeviceId deviceId, MeterId meterId) {
            return null;
        }

        @Override
        public Collection<Meter> getAllMeters() {
            return null;
        }

        @Override
        public Collection<Meter> getMeters(DeviceId deviceId) {
            return null;
        }

        @Override
        public MeterId allocateMeterId(DeviceId deviceId) {
            return null;
        }

        @Override
        public void freeMeterId(DeviceId deviceId, MeterId meterId) {

        }

        @Override
        public void addListener(MeterListener meterListener) {

        }

        @Override
        public void removeListener(MeterListener meterListener) {

        }
    }
}
