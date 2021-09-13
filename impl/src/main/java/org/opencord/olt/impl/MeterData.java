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

import org.onosproject.net.meter.MeterCellId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterState;

import java.util.Objects;

/**
 * Class containing Meter Data.
 */
public class MeterData {
    private MeterCellId meterCellId;
    private MeterState meterStatus;
    private String bandwidthProfileName;

    public MeterData(MeterCellId meterCellId, MeterState meterStatus, String bandwidthProfile) {
        this.meterCellId = meterCellId;
        this.meterStatus = meterStatus;
        this.bandwidthProfileName = bandwidthProfile;
    }

    public void setMeterCellId(MeterCellId meterCellId) {
        this.meterCellId = meterCellId;
    }

    public void setMeterStatus(MeterState meterStatus) {
        this.meterStatus = meterStatus;
    }

    public MeterId getMeterId() {
        return (MeterId) meterCellId;
    }

    public MeterCellId getMeterCellId() {
        return meterCellId;
    }

    public MeterState getMeterStatus() {
        return meterStatus;
    }

    public String getBandwidthProfileName() {
        return bandwidthProfileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MeterData meterData = (MeterData) o;
        return Objects.equals(meterCellId, meterData.meterCellId) &&
                meterStatus == meterData.meterStatus &&
                Objects.equals(bandwidthProfileName, meterData.bandwidthProfileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterCellId, meterStatus, bandwidthProfileName);
    }

    @Override
    public String toString() {
        return "MeterData{" +
                "meterCellId=" + meterCellId +
                ", meterStatus=" + meterStatus +
                ", bandwidthProfile='" + bandwidthProfileName + '\'' +
                '}';
    }
}
