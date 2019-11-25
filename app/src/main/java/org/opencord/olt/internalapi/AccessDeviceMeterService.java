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
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterKey;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
    ImmutableMap<String, Set<MeterKey>> getBpMeterMappings();

    /**
     * Adds a bandwidthProfile-meterKey (device / meter) mapping that have been programmed
     * in the data plane.
     *
     * @param deviceId the access device id
     * @param meterId the meter id that is mapped to the bandwidth profile
     * @param bandwidthProfile the bandwidth profile id
     */
    void addMeterIdToBpMapping(DeviceId deviceId, MeterId meterId, String bandwidthProfile);

    /**
     * Returns the meter id for a given bandwidth profile.
     *
     * @param deviceId the access device id
     * @param bandwidthProfile the bandwidth profile id
     * @return the meter id
     */
    MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile);

    /**
     * For upstream and downstream data-plane flows, decrement (decrementCount must be 2)
     * twice - there are two ont / olt flows. For DHCP, EAPOL, and IGMP, decrement once (decrementCount
     * must be 1).
     *
     * @param deviceId       the access device id
     * @param meterId        meter id that is related either upstream or downstream bandwidth profile
     * @param decrementCount decrement value
     */
    void decrementMeterCount(DeviceId deviceId, MeterId meterId, int decrementCount);

    /**
     * For upstream and downstream data-plane flows, increment twice (incrementCount must be 2)
     * For DHCP, EAPOL and IGMP, increment once (incrementCount must be 1).
     *
     * @param deviceId the access device id
     * @param meterId meter id that is related either upstream or downstream bandwidth profile
     * @param incrementCount increment value
     */
    void incrementMeterCount(DeviceId deviceId, MeterId meterId, Integer incrementCount);

    /**
     * Returns information about device-meter mappings that have been programmed in the
     * data-plane.
     *
     * @return an immutable map of device-meter mappings
     */
    ImmutableMap<MeterKey, AtomicInteger> getProgMeters();

    /**
     * Creates a meter and sends it to the device.
     *
     * @param deviceId the access device id
     * @param bpInfo the bandwidth profile information
     * @param meterFuture the meter future to indicate whether the meter creation is
     *                    successful or not.
     * @return meter id that is generated for the given parameters
     */
    MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                        CompletableFuture<Object> meterFuture);












}
