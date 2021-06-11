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

package org.opencord.olt.cli;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.cli.net.PortNumberCompleter;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class OltUniPortCompleter extends PortNumberCompleter {
    private static final String NNI = "nni-";

    public OltUniPortCompleter() {
    }

    protected List<String> choices() {
        DeviceId deviceId = this.lookForDeviceId();
        if (deviceId == null) {
            return Collections.emptyList();
        } else {
            DeviceService deviceService = (DeviceService) DefaultServiceDirectory.getService(DeviceService.class);
            return (List) StreamSupport.stream(deviceService.getPorts(deviceId).spliterator(), false)
                    .filter((port) -> {
                        return port.isEnabled() && !port.annotations().value(AnnotationKeys.PORT_NAME).startsWith(NNI);
                    })
                    .map((port) -> {
                        return port.number().toString();
                    }).collect(Collectors.toList());
        }
    }
}
