/*
 * Copyright 2016-present Open Networking Foundation
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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.opencord.olt.AccessDeviceService;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provisions all subscribers on an access device.
 */
@Service
@Command(scope = "onos", name = "volt-add-all-subscriber-access",
        description = "Provisions all subscribers on an access device")
public class SubscriberAddAllCommand extends AbstractShellCommand {
    private final Logger log = getLogger(getClass());
    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String strDeviceId = null;

    @Override
    protected void doExecute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);

        DeviceId deviceId = DeviceId.deviceId(strDeviceId);
        DeviceService deviceService = (DeviceService) DefaultServiceDirectory.getService(DeviceService.class);
        deviceService.getPorts(deviceId).stream().forEach(port -> {
            if (port.isEnabled()) {
                ConnectPoint connectPoint = new ConnectPoint(deviceId, port.number());
                log.info("Provisioning subscriber on {}", connectPoint);
                service.provisionSubscriber(connectPoint);
            }
        });

    }
}
