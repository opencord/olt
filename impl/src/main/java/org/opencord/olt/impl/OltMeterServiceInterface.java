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
