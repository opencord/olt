/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.AccessDevicePort;
import org.opencord.olt.ServiceKey;
import org.opencord.sadis.UniTagInformation;

/**
 * ServiceKeySerializer is a custom serializer to store a ServiceKey in an Atomix ditributed map.
 */
class ServiceKeySerializer extends Serializer<ServiceKey> {

    ServiceKeySerializer() {
        // non-null, immutable
        super(false, true);
    }

    @Override
    public void write(Kryo kryo, Output output, ServiceKey object) {
        kryo.writeClassAndObject(output, object.getPort().connectPoint().port());
        output.writeString(object.getPort().name());
        output.writeString(object.getPort().connectPoint().deviceId().toString());
        kryo.writeClassAndObject(output, object.getService());
    }

    @Override
    public ServiceKey read(Kryo kryo, Input input, Class<ServiceKey> type) {
        PortNumber port = (PortNumber) kryo.readClassAndObject(input);
        final String portName = input.readString();
        final String devId = input.readString();

        UniTagInformation uti = (UniTagInformation) kryo.readClassAndObject(input);
        ConnectPoint cp = new ConnectPoint(DeviceId.deviceId(devId), port);
        AccessDevicePort adp = new AccessDevicePort(cp, portName);

        return new ServiceKey(adp, uti);
    }
}
