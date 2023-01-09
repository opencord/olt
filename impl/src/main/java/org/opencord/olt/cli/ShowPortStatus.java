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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.cli.net.PortNumberCompleter;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.OltFlowServiceInterface;
import org.opencord.olt.OltPortStatus;
import org.opencord.olt.ServiceKey;
import org.opencord.sadis.UniTagInformation;

import java.util.HashMap;
import java.util.Map;

/**
 * Shows ports' status of an OLT.
 */
@Service
@Command(scope = "onos", name = "volt-port-status",
        description = "Shows information about the OLT ports (default EAPOL, subscriber flows)")
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
        Map<ServiceKey, OltPortStatus> connectPointStatus = service.getConnectPointStatus();
        if (connectPointStatus.isEmpty()) {
            print("No ports handled by the olt app");
            return;
        }

        Map<DeviceId, Map<PortNumber, Map<UniTagInformation, OltPortStatus>>> sortedStatus = new HashMap<>();

        DeviceId deviceId = strDeviceId != null ? DeviceId.deviceId(strDeviceId) : null;
        PortNumber portNumber = strPort != null ? PortNumber.portNumber(strPort) : null;

        connectPointStatus.forEach((sk, fs) -> {
            if (deviceId != null && !deviceId.equals(sk.getPort().connectPoint()
                    .deviceId())) {
                return;
            }
            if (portNumber != null && !portNumber.equals(sk.getPort().connectPoint().port())) {
                return;
            }
            sortedStatus.compute(sk.getPort().connectPoint().deviceId(), (id, portMap) -> {
                if (portMap == null) {
                    portMap = new HashMap<>();
                }
                portMap.compute(sk.getPort().connectPoint().port(), (id2, ps) -> {
                    if (ps == null) {
                        ps = new HashMap<>();
                    }
                    ps.put(sk.getService(), fs);
                    return ps;
                });

                return portMap;
            });
        });

        sortedStatus.forEach(this::display);
    }

    private void display(DeviceId deviceId, Map<PortNumber, Map<UniTagInformation, OltPortStatus>> ports) {
        print("deviceId=%s, managedPorts=%d", deviceId, ports.size());

        ports.forEach((port, subscribers) -> {
            print("\tport=%s", port);
            subscribers.forEach((uti, status) -> {
                print("\t\tservice=%s defaultEapolStatus=%s subscriberFlowsStatus=%s dhcpStatus=%s",
                        uti.getServiceName(), status.defaultEapolStatus,
                        status.subscriberFlowsStatus, status.dhcpStatus);
            });
        });
    }
}