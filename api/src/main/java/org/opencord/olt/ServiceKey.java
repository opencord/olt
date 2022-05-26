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

package org.opencord.olt;

import org.opencord.sadis.UniTagInformation;

import java.util.Objects;

/**
 * SubscriberKey is used to identify the combination of a subscriber and a service.
 */
public class ServiceKey {
    private AccessDevicePort port;
    private UniTagInformation service;


    public ServiceKey(AccessDevicePort port, UniTagInformation service) {
        this.port = port;
        this.service = service;
    }

    public AccessDevicePort getPort() {
        return port;
    }

    public void setPort(AccessDevicePort port) {
        this.port = port;
    }

    public UniTagInformation getService() {
        return service;
    }

    public void setService(UniTagInformation service) {
        this.service = service;
    }

    @Override
    public String toString() {
        return this.port.toString() + " - " + this.service.getServiceName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceKey that = (ServiceKey) o;
        boolean isPortEqual = Objects.equals(port, that.port);
        boolean isServiceEqual = Objects.equals(service, that.service);

        return isPortEqual && isServiceEqual;
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, service);
    }
}
