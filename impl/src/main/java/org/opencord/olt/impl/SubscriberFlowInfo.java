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
import org.onosproject.net.PortNumber;
import org.onosproject.net.meter.MeterId;
import org.opencord.sadis.UniTagInformation;

import java.util.Objects;

/**
 * Contains the mapping of a given port to flow information, including bandwidth profile.
 */
class SubscriberFlowInfo {
    private final DeviceId devId;
    private final PortNumber nniPort;
    private final PortNumber uniPort;
    private final UniTagInformation tagInfo;
    private MeterId downId;
    private MeterId upId;
    private final String downBpInfo;
    private final String upBpInfo;

    /**
     * Builds the mapper of information.
     * @param devId the device id
     * @param nniPort the nni port
     * @param uniPort the uni port
     * @param tagInfo the tag info
     * @param downId the downstream meter id
     * @param upId the upstream meter id
     * @param downBpInfo the downstream bandwidth profile
     * @param upBpInfo the upstream bandwidth profile
     */
    SubscriberFlowInfo(DeviceId devId, PortNumber nniPort, PortNumber uniPort,
                       UniTagInformation tagInfo, MeterId downId, MeterId upId,
                       String downBpInfo, String upBpInfo) {
        this.devId = devId;
        this.nniPort = nniPort;
        this.uniPort = uniPort;
        this.tagInfo = tagInfo;
        this.downId = downId;
        this.upId = upId;
        this.downBpInfo = downBpInfo;
        this.upBpInfo = upBpInfo;
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
    PortNumber getNniPort() {
        return nniPort;
    }

    /**
     * Gets the uni port of this subscriber and flow information.
     *
     * @return uni port
     */
    PortNumber getUniPort() {
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
     * Sets the upstream meter id.
     * @param upMeterId the upstream meter id
     */
    void setUpMeterId(MeterId upMeterId) {
        this.upId = upMeterId;
    }

    /**
     * Sets the downstream meter id.
     * @param downMeterId the downstream meter id
     */
    void setDownMeterId(MeterId downMeterId) {
        this.downId = downMeterId;
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
                upBpInfo.equals(flowInfo.upBpInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(devId, nniPort, uniPort, tagInfo, downBpInfo, upBpInfo);
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
                .add("downBpInfo", downBpInfo)
                .add("upBpInfo", upBpInfo)
                .toString();
    }
}
