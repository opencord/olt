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
package org.opencord.olt.internalapi;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterKey;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Olt service for meter operations.
 */
public interface AccessDeviceMeterService {

    /**
     * Returns information about bandwidthProfile-meterKey (device / meter) mappings
     * that have been programmed in the data-plane.
     *
     * @return an immutable map of bandwidthProfile-meterKey (device / meter) mappings
     */
    ImmutableMap<String, Collection<MeterKey>> getBpMeterMappings();

    /**
     * Returns the meter id for a given bandwidth profile.
     *
     * @param deviceId         the access device id
     * @param bandwidthProfile the bandwidth profile id
     * @return the meter id
     */
    MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile);

    /**
     * Returns information about device-meter relations that have been programmed in the
     * data-plane.
     *
     * @return an immutable set of device-meter mappings
     */
    ImmutableSet<MeterKey> getProgMeters();

    /**
     * Creates a meter and sends it to the device.
     *
     * @param deviceId    the access device id
     * @param bpInfo      the bandwidth profile information
     * @param meterFuture the meter future to indicate whether the meter creation is
     *                    successful or not.
     * @return meter id that is generated for the given parameters
     */
    MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                        CompletableFuture<Object> meterFuture);

    /**
     * Removes the DeviceBandwidthProfile from the pendingMeters.
     *
     * @param deviceId the device
     * @param bwpInfo the bandwidth profile info
     *
     */
    void removeFromPendingMeters(DeviceId deviceId, BandwidthProfileInformation bwpInfo);

    /**
     * Checks if DeviceBandwidthProfile is pending installation.
     * If so immediately returns false meaning that no further action is needed,
     * if not it adds the bandwidth profile do the pending list and returns true,
     * meaning that further action to install the meter is required.
     *
     * @param deviceId the device
     * @param bwpInfo the bandwidth profile info
     *
     * @return true if it was added to pending and a create meter action is needed,
     * false if it is already pending and no further action is needed.
     */
    boolean checkAndAddPendingMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo);

    /**
     * Clears out meters for the given device.
     *
     * @param deviceId device ID
     */
    void clearMeters(DeviceId deviceId);

    /**
     * Clears out local state for the given device.
     *
     * @param deviceId device ID
     */
    void clearDeviceState(DeviceId deviceId);
}
