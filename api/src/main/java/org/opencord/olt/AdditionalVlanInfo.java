/*
 * Copyright 2017-present Open Networking Foundation
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
package org.opencord.olt;

import org.onlab.packet.VlanId;

public class AdditionalVlanInfo {

    private VlanId ponCTag;
    private VlanId ponSTag;
    private Integer tpId;

    public AdditionalVlanInfo(VlanId ponCTag, VlanId ponSTag, Integer tpId) {
        this.ponCTag = ponCTag;
        this.ponSTag = ponSTag;
        this.tpId = tpId;
    }

    public VlanId getPonCTag() {
        return ponCTag;
    }

    public void setPonCTag(VlanId ponCTag) {
        this.ponCTag = ponCTag;
    }

    public VlanId getPonSTag() {
        return ponSTag;
    }

    public void setPonSTag(VlanId ponSTag) {
        this.ponSTag = ponSTag;
    }

    public Integer getTpId() {
        return tpId;
    }

    public void setTpId(Integer tpId) {
        this.tpId = tpId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AdditionalVlanInfo that = (AdditionalVlanInfo) o;

        if (ponCTag != null ? !ponCTag.equals(that.ponCTag) : that.ponCTag != null) {
            return false;
        }
        if (ponSTag != null ? !ponSTag.equals(that.ponSTag) : that.ponSTag != null) {
            return false;
        }
        return tpId != null ? tpId.equals(that.tpId) : that.tpId == null;

    }

    @Override
    public int hashCode() {
        int result = ponCTag != null ? ponCTag.hashCode() : 0;
        result = 31 * result + (ponSTag != null ? ponSTag.hashCode() : 0);
        result = 31 * result + (tpId != null ? tpId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AdditionalVlanInfo{");
        sb.append("ponCTag=").append(ponCTag);
        sb.append(", ponSTag=").append(ponSTag);
        sb.append(", tpId=").append(tpId);
        sb.append('}');
        return sb.toString();
    }
}


