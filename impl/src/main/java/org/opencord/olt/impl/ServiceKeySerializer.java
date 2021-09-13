package org.opencord.olt.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.AccessDevicePort;
import org.opencord.sadis.UniTagInformation;

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
//        output.writeString(object.getService().getServiceName());
        kryo.writeClassAndObject(output, object.getService());
    }

    @Override
    public ServiceKey read(Kryo kryo, Input input, Class<ServiceKey> type) {
        PortNumber port = (PortNumber) kryo.readClassAndObject(input);
        final String portName = input.readString();
        final String devId = input.readString();
//        final String serviceName = input.readString();
        UniTagInformation uti = (UniTagInformation) kryo.readClassAndObject(input);

        ConnectPoint cp = new ConnectPoint(DeviceId.deviceId(devId), port);

        AccessDevicePort adp = new AccessDevicePort(cp, portName);
//        UniTagInformation uti = new UniTagInformation.Builder()
//                .setServiceName(serviceName).build();
        return new ServiceKey(adp, uti);
    }
}
