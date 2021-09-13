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
import org.onosproject.cli.net.PortNumberCompleter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.impl.OltFlowServiceInterface;
import org.opencord.olt.impl.OltPortStatus;

import java.util.HashMap;
import java.util.Map;

@Service
@Command(scope = "onos", name = "volt-port-status",
        description = "Shows information about the OLT ports (default EAPOL, subscriber flows")
public class ShowPortStatus extends AbstractShellCommand {

    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = false, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String strDeviceId = null;

    @Argument(index = 1, name = "port", description = "Subscriber port number",
            required = false, multiValued = false)
    @Completion(PortNumberCompleter.class)
    private String strPort = null;

    @Override
    protected void doExecute() {

        OltFlowServiceInterface service = AbstractShellCommand.get(OltFlowServiceInterface.class);
        Map<ConnectPoint, OltPortStatus> flowStatus = service.getConnectPointStatus();
        if (flowStatus.isEmpty()) {
            print("No ports handled by the org.opencord.olt app");
        }

        Map<DeviceId, Map<PortNumber, OltPortStatus>> sortedStatus = new HashMap<>();

        DeviceId deviceId = strDeviceId != null ? DeviceId.deviceId(strDeviceId) : null;
        PortNumber portNumber = strPort != null ? PortNumber.portNumber(strPort) : null;

        flowStatus.forEach((cp, fs) -> {
            if (deviceId != null && !deviceId.equals(cp.deviceId())) {
                return;
            }
            if (portNumber != null && !portNumber.equals(cp.port())) {
                return;
            }
            Map<PortNumber, OltPortStatus> portMap = sortedStatus.get(cp.deviceId());
            if (portMap == null) {
                portMap = new HashMap<>();
            }
            portMap.put(cp.port(), fs);

            sortedStatus.put(cp.deviceId(), portMap);
        });

        sortedStatus.forEach(this::display);
    }

    private void display(DeviceId deviceId, Map<PortNumber, OltPortStatus> portStatus) {
        print("deviceId=%s, managedPorts=%d", deviceId, portStatus.size());
        portStatus.forEach((port, status) ->
                print("\tport=%s defaultEapolStatus=%s subscriberFlowsStatus=%s dhcpStatus=%s",
                        port, status.defaultEapolStatus, status.subscriberFlowsStatus, status.dhcpStatus));
    }
}