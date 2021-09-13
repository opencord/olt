package org.opencord.olt.impl;

import org.onosproject.net.meter.MeterCellId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterState;

import java.util.Objects;

/**
 * Class containing Meter Data.
 */
public class MeterData {
    public MeterId meterId;
    public MeterCellId meterCellId;
    public MeterState meterStatus;
    public String bandwidthProfile;

    public MeterData(MeterId meterId, MeterCellId meterCellId, MeterState meterStatus, String bandwidthProfile) {
        this.meterId = meterId;
        this.meterCellId = meterCellId;
        this.meterStatus = meterStatus;
        this.bandwidthProfile = bandwidthProfile;
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
        return Objects.equals(meterId, meterData.meterId) &&
                Objects.equals(meterCellId, meterData.meterCellId) &&
                meterStatus == meterData.meterStatus &&
                Objects.equals(bandwidthProfile, meterData.bandwidthProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId, meterCellId, meterStatus, bandwidthProfile);
    }

    @Override
    public String toString() {
        return "MeterData{" +
                "meterId=" + meterId +
                ", meterCellId=" + meterCellId +
                ", meterStatus=" + meterStatus +
                ", bandwidthProfile='" + bandwidthProfile + '\'' +
                '}';
    }
}
