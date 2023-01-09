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

package org.opencord.olt.cli;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.cli.net.PortNumberCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.opencord.olt.OltDeviceServiceInterface;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OltUniPortCompleter extends PortNumberCompleter {

    public OltUniPortCompleter() {
    }

    protected List<String> choices() {
        DeviceService deviceService = DefaultServiceDirectory.getService(DeviceService.class);
        DeviceId deviceId = this.lookForDeviceId();
        if (deviceId == null) {
            return Collections.emptyList();
        } else {
            Device device = deviceService.getDevice(DeviceId.deviceId(deviceId.toString()));
            OltDeviceServiceInterface oltDeviceService =
                    DefaultServiceDirectory.getService(OltDeviceServiceInterface.class);
            return deviceService.getPorts(deviceId).stream()
                    .filter((port) -> port.isEnabled() && !oltDeviceService.isNniPort(device, port.number()))
                    .map((port) -> port.number().toString())
                    .collect(Collectors.toList());
        }
    }
}
