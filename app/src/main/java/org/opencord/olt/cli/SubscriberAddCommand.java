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
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.AccessDeviceService;

/**
 * Adds a subscriber to an access device.
 */
@Service
@Command(scope = "onos", name = "volt-add-subscriber-access",
        description = "Adds a subscriber to an access device")
public class SubscriberAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String strDeviceId = null;

    @Argument(index = 1, name = "port", description = "Subscriber port number",
            required = true, multiValued = false)
    private String strPort = null;

    @Override
    protected void doExecute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);

        DeviceId deviceId = DeviceId.deviceId(strDeviceId);
        PortNumber port = PortNumber.portNumber(strPort);
        ConnectPoint connectPoint = new ConnectPoint(deviceId, port);

        service.provisionSubscriber(connectPoint);
    }
}
