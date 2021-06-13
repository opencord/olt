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
package org.opencord.olt.impl;

import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.opencord.olt.AccessDevicePort;
import org.opencord.sadis.UniTagInformation;

import java.util.Objects;

/**
 * Contains the mapping of a given port to flow information, including bandwidth profile.
 */
class SubscriberFlowInfo {
    private final DeviceId devId;
    private final AccessDevicePort nniPort;
    private final AccessDevicePort uniPort;
    private final UniTagInformation tagInfo;
    private MeterId downId;
    private MeterId upId;
    private MeterId downOltId;
    private MeterId upOltId;
    private final String downBpInfo;
    private final String upBpInfo;
    private final String downOltBpInfo;
    private final String upOltBpInfo;

    /**
     * Builds the mapper of information.
     * @param nniPort       the nni port
     * @param uniPort       the uni port
     * @param tagInfo       the tag info
     * @param downId        the downstream meter id
     * @param upId          the upstream meter id
     * @param downOltId     the downstream meter id of OLT device
     * @param upOltId       the upstream meter id of OLT device
     * @param downBpInfo    the downstream bandwidth profile
     * @param upBpInfo      the upstream bandwidth profile
     * @param downOltBpInfo the downstream bandwidth profile of OLT device
     * @param upOltBpInfo   the upstream bandwidth profile of OLT device
     */
    SubscriberFlowInfo(AccessDevicePort nniPort, AccessDevicePort uniPort,
                       UniTagInformation tagInfo, MeterId downId, MeterId upId,
                       MeterId downOltId, MeterId upOltId,
                       String downBpInfo, String upBpInfo,
                       String downOltBpInfo, String upOltBpInfo) {
        this.devId = uniPort.deviceId();
        this.nniPort = nniPort;
        this.uniPort = uniPort;
        this.tagInfo = tagInfo;
        this.downId = downId;
        this.upId = upId;
        this.downOltId = downOltId;
        this.upOltId = upOltId;
        this.downBpInfo = downBpInfo;
        this.upBpInfo = upBpInfo;
        this.downOltBpInfo = downOltBpInfo;
        this.upOltBpInfo = upOltBpInfo;
    }

    /**
     * Gets the device id of this subscriber and flow information.
     *
     * @return device id
     */
    DeviceId getDevId() {
        return devId;
    }

    /**
     * Gets the nni of this subscriber and flow information.
     *
     * @return nni port
     */
    AccessDevicePort getNniPort() {
        return nniPort;
    }

    /**
     * Gets the uni port of this subscriber and flow information.
     *
     * @return uni port
     */
    AccessDevicePort getUniPort() {
        return uniPort;
    }

    /**
     * Gets the tag of this subscriber and flow information.
     *
     * @return tag of the subscriber
     */
    UniTagInformation getTagInfo() {
        return tagInfo;
    }

    /**
     * Gets the downstream meter id of this subscriber and flow information.
     *
     * @return downstream meter id
     */
    MeterId getDownId() {
        return downId;
    }

    /**
     * Gets the upstream meter id of this subscriber and flow information.
     *
     * @return upstream meter id
     */
    MeterId getUpId() {
        return upId;
    }

    /**
     * Gets the downstream meter id of this subscriber and flow information of OLT device.
     *
     * @return downstream olt meter id
     */
    MeterId getDownOltId() {
        return downOltId;
    }

    /**
     * Gets the upstream meter id of this subscriber and flow information of OLT device.
     *
     * @return upstream olt meter id
     */
    MeterId getUpOltId() {
        return upOltId;
    }

    /**
     * Gets the downstream bandwidth profile of this subscriber and flow information.
     *
     * @return downstream bandwidth profile
     */
    String getDownBpInfo() {
        return downBpInfo;
    }

    /**
     * Gets the upstream bandwidth profile of this subscriber and flow information.
     *
     * @return upstream bandwidth profile.
     */
    String getUpBpInfo() {
        return upBpInfo;
    }

    /**
     * Gets the downstream bandwidth profile of this subscriber and flow information of OLT device.
     *
     * @return downstream OLT bandwidth profile
     */
    String getDownOltBpInfo() {
        return downOltBpInfo;
    }

    /**
     * Gets the upstream bandwidth profile of this subscriber and flow information of OLT device.
     *
     * @return upstream OLT bandwidth profile.
     */
    String getUpOltBpInfo() {
        return upOltBpInfo;
    }

    /**
     * Sets the upstream meter id.
     *
     * @param upMeterId the upstream meter id
     */
    void setUpMeterId(MeterId upMeterId) {
        this.upId = upMeterId;
    }

    /**
     * Sets the downstream meter id.
     *
     * @param downMeterId the downstream meter id
     */
    void setDownMeterId(MeterId downMeterId) {
        this.downId = downMeterId;
    }

    /**
     * Sets the upstream meter id of OLT.
     *
     * @param upOltMeterId the upstream meter id of OLT
     */
    void setUpOltMeterId(MeterId upOltMeterId) {
        this.upOltId = upOltMeterId;
    }

    /**
     * Sets the downstream meter id of OLT.
     *
     * @param downOltMeterId the downstream meter id of OLT
     */
    void setDownOltMeterId(MeterId downOltMeterId) {
        this.downOltId = downOltMeterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubscriberFlowInfo flowInfo = (SubscriberFlowInfo) o;
        return devId.equals(flowInfo.devId) &&
                nniPort.equals(flowInfo.nniPort) &&
                uniPort.equals(flowInfo.uniPort) &&
                tagInfo.equals(flowInfo.tagInfo) &&
                downBpInfo.equals(flowInfo.downBpInfo) &&
                upBpInfo.equals(flowInfo.upBpInfo) &&
                Objects.equals(downOltBpInfo, flowInfo.downOltBpInfo) &&
                Objects.equals(upOltBpInfo, flowInfo.upOltBpInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(devId, nniPort, uniPort, tagInfo, downBpInfo, upBpInfo, downOltBpInfo, upOltBpInfo);
    }

    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
                .add("devId", devId)
                .add("nniPort", nniPort)
                .add("uniPort", uniPort)
                .add("tagInfo", tagInfo)
                .add("downId", downId)
                .add("upId", upId)
                .add("downOltId", downOltId)
                .add("upOltId", upOltId)
                .add("downBpInfo", downBpInfo)
                .add("upBpInfo", upBpInfo)
                .add("downOltBpInfo", downOltBpInfo)
                .add("upOltBpInfo", upOltBpInfo)
                .toString();
    }
}
