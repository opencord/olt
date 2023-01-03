/*
 * Copyright 2016-2023 Open Networking Foundation (ONF) and the ONF Contributors
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
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.VlanId;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.opencord.olt.AccessDeviceService;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Adds a subscriber uni tag.
 */
@Service
@Command(scope = "onos", name = "volt-add-subscriber-unitag",
        description = "Adds a uni tag to an access device")
public class UniTagAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "portName", description = "Port name",
            required = true, multiValued = false)
    private String portName = null;

    @Option(name = "--cTag", description = "Inner vlan id",
            required = true, multiValued = false)
    private String strCtag = null;

    @Option(name = "--sTag", description = "Outer vlan id",
            required = true, multiValued = false)
    private String strStag = null;

    @Option(name = "--tpId", description = "Technology profile id",
            required = true, multiValued = false)
    private String strTpId = null;

    @Override
    protected void doExecute() {

        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            print("ConnectPoint not found for %s", portName);
            return;
        }
        if (isNullOrEmpty(strCtag) || isNullOrEmpty(strStag) || isNullOrEmpty(strTpId)) {
            print("Values for c-tag (%s), s-tag (%s) and technology profile Id (%s) " +
                          "are required", strCtag, strStag, strTpId);
            return;
        }

        VlanId cTag = VlanId.vlanId(strCtag);
        VlanId sTag = VlanId.vlanId(strStag);
        Integer tpId = Integer.valueOf(strTpId);
        service.provisionSubscriber(cp, cTag, sTag, tpId);
    }
}
