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

import java.util.Set;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.AdditionalVlanInfo;

/**
 * Shows provisioned (configured) uni tag information.
 */
@Command(scope = "onos", name = "volt-additional-vlans",
        description = "Shows provisioned uni tag information")
public class ShowAdditionalVlansCommand extends AbstractShellCommand {

    private static final String FORMAT = "cVlan=%s, sVlan=%s, tpId=%s";

    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = true, multiValued = false)
    private String strDeviceId = null;

    @Argument(index = 1, name = "port", description = "Subscriber port number",
            required = true, multiValued = false)
    private String strPort = null;

    @Override
    protected void execute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        ConnectPoint connectPoint = new ConnectPoint(DeviceId.deviceId(strDeviceId), PortNumber.portNumber(strPort));
        Set<AdditionalVlanInfo> additionalVlanInfos = service.getAdditionalVlans(connectPoint);
        display(additionalVlanInfos);

    }

    private void display(Set<AdditionalVlanInfo> additionalVlanInfos) {
        additionalVlanInfos.forEach(vlanInfo -> print(FORMAT, vlanInfo.getPonCTag(), vlanInfo.getPonSTag(),
                vlanInfo.getTpId()));
    }
}
