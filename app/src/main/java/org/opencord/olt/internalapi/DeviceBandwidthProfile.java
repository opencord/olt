/*
 * Copyright 2020-present Open Networking Foundation
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

import org.onosproject.net.DeviceId;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Objects;

/**
 * Class containing a mapping of DeviceId to BandwidthProfileInformation.
 */
public class DeviceBandwidthProfile {
    private final DeviceId devId;
    private BandwidthProfileInformation bwInfo;

    /**
     * Creates the Mapping.
     *
     * @param devId  the device id
     * @param bwInfo the bandwidth profile information
     */
    public DeviceBandwidthProfile(DeviceId devId, BandwidthProfileInformation bwInfo) {
        this.devId = devId;
        this.bwInfo = bwInfo;
    }

    /**
     * Returns the device id.
     *
     * @return device id.
     */
    public DeviceId getDevId() {
        return devId;
    }

    /**
     * Returns the Bandwidth profile for this device.
     *
     * @return bandwidth profile information
     */
    public BandwidthProfileInformation getBwInfo() {
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
