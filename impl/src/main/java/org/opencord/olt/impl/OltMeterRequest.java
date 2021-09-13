package org.opencord.olt.impl;

import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterRequest;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class containing a Meter request for a specific BandwidthProfile on a device.
 */
class OltMeterRequest {
    public MeterRequest meterRequest;
    public DeviceId deviceId;
    public String bandwidthProfile;
    public AtomicReference<MeterId> meterIdRef;

    /**
     * Build a Meter request.
     * @param meterRequest the request
     * @param deviceId the device
     * @param bandwidthProfile the bandwith profile
     * @param meterIdRef the meter id reference.
     */
    public OltMeterRequest(MeterRequest meterRequest, DeviceId deviceId,
                           String bandwidthProfile, AtomicReference<MeterId> meterIdRef) {
        this.meterRequest = meterRequest;
        this.deviceId = deviceId;
        this.bandwidthProfile = bandwidthProfile;
        this.meterIdRef = meterIdRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OltMeterRequest that = (OltMeterRequest) o;
        return Objects.equals(meterRequest, that.meterRequest) &&
                Objects.equals(deviceId, that.deviceId) &&
                Objects.equals(bandwidthProfile, that.bandwidthProfile) &&
                Objects.equals(meterIdRef, that.meterIdRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterRequest, deviceId, bandwidthProfile, meterIdRef);
    }

    @Override
    public String toString() {
        return "OltMeterRequest{" + "meterRequest=" + meterRequest +
                ", deviceId=" + deviceId +
                ", bandwidthProfile='" + bandwidthProfile + '\'' +
                ", meterIdRef=" + meterIdRef +
                '}';
    }
}
