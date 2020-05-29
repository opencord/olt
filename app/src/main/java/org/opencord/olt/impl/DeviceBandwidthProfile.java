package org.opencord.olt.impl;

import org.onosproject.net.DeviceId;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Objects;

/**
 * Class containing a mapping of DeviceId to BandwidthProfileInformation.
 */
class DeviceBandwidthProfile {
    private final DeviceId devId;
    private BandwidthProfileInformation bwInfo;

    /**
     * Creates the Mapping.
     *
     * @param devId  the device id
     * @param bwInfo the bandwidth profile information
     */
    DeviceBandwidthProfile(DeviceId devId, BandwidthProfileInformation bwInfo) {
        this.devId = devId;
        this.bwInfo = bwInfo;
    }

    /**
     * Returns the device id.
     *
     * @return device id.
     */
    DeviceId getDevId() {
        return devId;
    }

    /**
     * Returns the Bandwidth profile for this device.
     *
     * @return bandwidth profile information
     */
    BandwidthProfileInformation getBwInfo() {
        return bwInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeviceBandwidthProfile that = (DeviceBandwidthProfile) o;
        return devId.equals(that.devId)
                && bwInfo.equals(that.bwInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(devId, bwInfo);
    }

    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
                .add("devId", devId)
                .add("bwInfo", bwInfo)
                .toString();
    }
}
