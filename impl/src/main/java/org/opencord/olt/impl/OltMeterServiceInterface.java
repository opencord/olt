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

import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.Map;

public interface OltMeterServiceInterface {
    /**
     * This method will check for a meter, if not present it will create it and return false.
     * @param deviceId DeviceId
     * @param bandwidthProfile Bandwidth Profile Id
     * @return boolean
     */
    boolean createMeter(DeviceId deviceId, String bandwidthProfile);

    /**
     * This method will check for all the meters specified in the sadis uniTagList,
     * if not present it will create them and return false.
     * @param deviceId DeviceId
     * @param si SubscriberAndDeviceInformation
     * @return boolean
     */
    boolean createMeters(DeviceId deviceId, SubscriberAndDeviceInformation si);

    boolean hasMeterByBandwidthProfile(DeviceId deviceId, String bandwidthProfile);

    boolean hasPendingMeterByBandwidthProfile(DeviceId deviceId, String bandwidthProfile);

    void createMeterForBp(DeviceId deviceId, String bp) throws Exception;

    MeterId getMeterIdForBandwidthProfile(DeviceId deviceId, String bpId);

    void purgeDeviceMeters(DeviceId deviceId);

    Map<DeviceId, Map<String, MeterData>> getProgrammedMeters();

}
