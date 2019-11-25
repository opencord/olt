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
     * @param deviceId         the access device id
     * @param meterId          the meter id that is mapped to the bandwidth profile
     * @param bandwidthProfile the bandwidth profile id
     */
    void addMeterIdToBpMapping(DeviceId deviceId, MeterId meterId, String bandwidthProfile);

    /**
     * Returns the meter id for a given bandwidth profile.
     *
     * @param deviceId         the access device id
     * @param bandwidthProfile the bandwidth profile id
     * @return the meter id
     */
    MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile);

    /**
     * The olt application keeps a counter for meters that are used by subscriber flows.
     * It is required to increment the meter count when it is associated with a flow
     * Similarly, when a flow is removed, the meter count must be decremented
     * This counter is used for removing the unused flows
     * It is not sufficient to use the METER_REFERENCE_COUNT_ZERO event - when a flow is created, we can receive
     * METER_REFERENCE_COUNT_ZERO event from the ONOS a few times, this counter saves the flow from the wrong removal.
     * <p>
     * <p>
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
     * The olt application keeps a counter for meters that are used by subscriber flows.
     * It is required to increment the meter count when it is associated with a flow
     * Similarly, when a flow is removed, the meter count must be decremented
     * This counter is used for removing the unused flows
     * It is not sufficient to use the METER_REFERENCE_COUNT_ZERO event - when a flow is created, we can receive
     * METER_REFERENCE_COUNT_ZERO event from the ONOS a few times, this counter saves the flow from the wrong removal.
     * <p>
     * <p>
     * For upstream and downstream data-plane flows, increment twice (incrementCount must be 2)
     * For DHCP, EAPOL and IGMP, increment once (incrementCount must be 1).
     *
     * @param deviceId       the access device id
     * @param meterId        meter id that is related either upstream or downstream bandwidth profile
     * @param incrementCount increment value
     */
    void incrementMeterCount(DeviceId deviceId, MeterId meterId, Integer incrementCount);


    /**
     * The olt application keeps a counter for meters that are used by subscriber flows.
     * It is required to increment the meter count when it is associated with a flow
     * Similarly, when a flow is removed, the meter count must be decremented
     * This counter is used for removing the unused flows
     * It is not sufficient to use the METER_REFERENCE_COUNT_ZERO event - when a flow is created, we can receive
     * METER_REFERENCE_COUNT_ZERO event from the ONOS a few times, this counter saves the flow from the wrong removal.
     * If the install is true, increments the related meter by count.
     * Otherwise, decrements the meter by count
     * <p>
     * <p>
     * @param deviceId the access device id
     * @param bpId     the bandwidth profile id
     * @param count    the value used by increment or decrement process
     * @param install  true indicates an installation process
     */
    void updateMeterCount(DeviceId deviceId, String bpId, int count, boolean install);

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
     * @param deviceId    the access device id
     * @param bpInfo      the bandwidth profile information
     * @param meterFuture the meter future to indicate whether the meter creation is
     *                    successful or not.
     * @return meter id that is generated for the given parameters
     */
    MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                        CompletableFuture<Object> meterFuture);

    /**
     * Deletes all meter counters for a particular device.
     * @param deviceId the access device_id
     */
    void deleteAllMeterCounters(DeviceId deviceId);


}
