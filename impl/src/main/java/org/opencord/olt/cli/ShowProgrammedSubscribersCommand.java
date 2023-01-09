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
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.OltFlowServiceInterface;
import org.opencord.olt.ServiceKey;
import org.opencord.sadis.UniTagInformation;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shows programmed subscribers.
 */
@Service
@Command(scope = "onos", name = "volt-programmed-subscribers",
        description = "Shows subscribers programmed in the dataplane")
public class ShowProgrammedSubscribersCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = false, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String strDeviceId = null;

    @Argument(index = 1, name = "port", description = "Subscriber port number",
            required = false, multiValued = false)
    @Completion(OltUniPortCompleter.class)
    private String strPort = null;

    @Override
    protected void doExecute() {
        OltFlowServiceInterface service = AbstractShellCommand.get(OltFlowServiceInterface.class);
        Map<ServiceKey, UniTagInformation> info = service.getProgrammedSubscribers();
        Set<Map.Entry<ServiceKey, UniTagInformation>> entries = info.entrySet();
        if (strDeviceId != null && !strDeviceId.isEmpty()) {
            entries = entries.stream().filter(entry -> entry.getKey().getPort().connectPoint().deviceId()
                    .equals(DeviceId.deviceId(strDeviceId))).collect(Collectors.toSet());
        }

        if (strPort != null && !strPort.isEmpty()) {
            PortNumber portNumber = PortNumber.portNumber(strPort);
            entries = entries.stream().filter(entry -> entry.getKey().getPort().connectPoint().port()
                    .equals(portNumber)).collect(Collectors.toSet());
        }

        entries.forEach(entry -> display(entry.getKey(), entry.getValue()));
    }

    private void display(ServiceKey sk, UniTagInformation uniTag) {
        print("location=%s tagInformation=%s", sk.getPort().connectPoint(), uniTag);
    }
}