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

/**
 * Class representing the identifier for a subscriber.
 * It encapsulates the name of the logical port for the subscriber in
 * ONOS.
 * For e.g. if in ONOS you see
 * onos> ports
 * id=of:00000000c0a83264, available=true, local-status=connected 3m48s ago, role=MASTER, mfr=VOLTHA Projectd,...
 *   port=32, state=enabled, type=fiber, speed=0 , adminState=enabled, portMac=08:00:00:00:00:20, portName=TWSH80808082
 *
 * the subscriber id would encapsulate the string "TWSH80808082"
 */
public class AccessSubscriberId {
    // string representing the identifier
    String id;

    /**
     * Creates an AccessSubscriberId with the passed id.
     *
     * @param id identifier of the subscriber
     */
    public AccessSubscriberId(String id) {
        this.id = id;
    }

    /*
    * (non-Javadoc)
    *
    * @see java.lang.Object#hashCode()
    */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.id == null ? 0 : this.id.hashCode());

        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }

        final AccessSubscriberId other = (AccessSubscriberId) obj;
        if (this.id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!this.id.equals(other.id)) {
            return false;
        }

        return true;
    }

    /*
    * (non-Javadoc)
    *
    * @see java.lang.Object#toString()
    */
    @Override
    public String toString() {
        return id;
    }
}
