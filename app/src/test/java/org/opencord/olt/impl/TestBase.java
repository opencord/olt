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

import com.google.common.collect.Maps;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.Map;

public class TestBase {

    protected static final String CLIENT_NAS_PORT_ID = "PON 1/1";
    protected static final String CLIENT_CIRCUIT_ID = "CIR-PON 1/1";
    protected static final String OLT_DEV_ID = "of:00000000000000aa";
    protected static final DeviceId DEVICE_ID_1 = DeviceId.deviceId(OLT_DEV_ID);
    protected MeterId usMeterId = MeterId.meterId(1);
    protected MeterId dsMeterId = MeterId.meterId(2);
    protected String usBpId = "HSIA-US";
    protected String dsBpId = "HSIA-DS";
    protected DefaultApplicationId appId = new DefaultApplicationId(1, "OltServices");

    Map<String, BandwidthProfileInformation> bpInformation = Maps.newConcurrentMap();

    protected void addBandwidthProfile(String id) {
        BandwidthProfileInformation bpInfo = new BandwidthProfileInformation();
        bpInfo.setAssuredInformationRate(0);
        bpInfo.setCommittedInformationRate(10000);
        bpInfo.setCommittedBurstSize(1000L);
        bpInfo.setExceededBurstSize(2000L);
        bpInfo.setExceededInformationRate(20000);
        bpInformation.put(id, bpInfo);
    }

    protected class MockSadisService implements SadisService {

        @Override
        public BaseInformationService<SubscriberAndDeviceInformation> getSubscriberInfoService() {
            return new MockSubService();
        }

        @Override
        public BaseInformationService<BandwidthProfileInformation> getBandwidthProfileService() {
            return new MockBpService();
        }
    }

    private class MockBpService implements BaseInformationService<BandwidthProfileInformation> {

        @Override
        public void invalidateAll() {

        }

        @Override
        public void invalidateId(String id) {

        }

        @Override
        public BandwidthProfileInformation get(String id) {
            return bpInformation.get(id);
        }

        @Override
        public BandwidthProfileInformation getfromCache(String id) {
            return null;
        }
    }

    private class MockSubService implements BaseInformationService<SubscriberAndDeviceInformation> {
        MockSubscriberAndDeviceInformation sub =
                new MockSubscriberAndDeviceInformation(CLIENT_NAS_PORT_ID,
                        CLIENT_NAS_PORT_ID, CLIENT_CIRCUIT_ID, null, null);

        @Override
        public SubscriberAndDeviceInformation get(String id) {
            return sub;
        }

        @Override
        public void invalidateAll() {
        }

        @Override
        public void invalidateId(String id) {
        }

        @Override
        public SubscriberAndDeviceInformation getfromCache(String id) {
            return null;
        }
    }

    private class MockSubscriberAndDeviceInformation extends SubscriberAndDeviceInformation {

        MockSubscriberAndDeviceInformation(String id, String nasPortId,
                                           String circuitId, MacAddress hardId,
                                           Ip4Address ipAddress) {
            this.setHardwareIdentifier(hardId);
            this.setId(id);
            this.setIPAddress(ipAddress);
            this.setNasPortId(nasPortId);
            this.setCircuitId(circuitId);
        }
    }
}
