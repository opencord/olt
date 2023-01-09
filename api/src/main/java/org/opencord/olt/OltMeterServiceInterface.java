/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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

package org.opencord.olt;

import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.Map;

/**
 * Interface for meter installation/removal methods
 * for different types of bandwidth profiles.
 */
public interface OltMeterServiceInterface {
    /**
     * Checks for a meter, if not present it will create it and return false.
     * @param deviceId DeviceId
     * @param bandwidthProfile Bandwidth Profile Id
     * @return boolean
     */
    boolean createMeter(DeviceId deviceId, String bandwidthProfile);

    /**
     * Checks for all the meters specified in the sadis uniTagList,
     * if not present it will create them and return false.
     * @param deviceId DeviceId
     * @param si SubscriberAndDeviceInformation
     * @param multicastServiceName The multicast service name
     * @return boolean
     */
    boolean createMeters(DeviceId deviceId, SubscriberAndDeviceInformation si, String multicastServiceName);

    /**
     * Checks if a meter for the specified bandwidthProfile exists
     * and is in ADDED state.
     * @param deviceId DeviceId
     * @param bandwidthProfileId bandwidth profile id
     * @return true if present and in ADDED state
     */
    boolean hasMeterByBandwidthProfile(DeviceId deviceId, String bandwidthProfileId);

    /**
     * Checks if a meter for the specified bandwidthProfile exists
     * and is in PENDING_ADD state.
     * @param deviceId DeviceId
     * @param bandwidthProfileId bandwidth profile id
     * @return true if present and in PENDING_ADD state
     */
    boolean hasPendingMeterByBandwidthProfile(DeviceId deviceId, String bandwidthProfileId);

    /**
     * Creates a meter on a device for the given BandwidthProfile Id.
     * @param deviceId the device id
     * @param bandwidthProfileId the bandwidth profile Id
     */
    void createMeterForBp(DeviceId deviceId, String bandwidthProfileId);

    /**
     * Returns the meter Id for a given bandwidth profile Id.
     * @param deviceId the device id
     * @param bandwidthProfileId the bandwidth profile Id
     * @return the meter Id
     */
    MeterId getMeterIdForBandwidthProfile(DeviceId deviceId, String bandwidthProfileId);

    /**
     * Purges all the meters on a device.
     * @param deviceId the device
     */
    void purgeDeviceMeters(DeviceId deviceId);

    /**
     * Return all programmed meters for all OLTs controlled by this ONOS cluster.
     * @return a map, with the device keys, and entry of map with bp Id and corresponding meter
     */
    Map<DeviceId, Map<String, MeterData>> getProgrammedMeters();

}
