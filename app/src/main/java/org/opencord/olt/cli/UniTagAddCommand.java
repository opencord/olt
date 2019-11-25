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
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.VlanId;
import org.onosproject.cli.AbstractShellCommand;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.AccessSubscriberId;

import java.util.Optional;

/**
 * Adds a subscriber uni tag.
 */
@Service
@Command(scope = "onos", name = "volt-add-subscriber-unitag",
        description = "Adds a uni tag to an access device")
public class UniTagAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "portName", description = "Port name",
            required = true, multiValued = false)
    private String strPortName = null;

    @Option(name = "--cTag", description = "Inner vlan id",
            required = false, multiValued = false)
    private String strCtag = null;

    @Option(name = "--sTag", description = "Outer vlan id",
            required = false, multiValued = false)
    private String strStag = null;

    @Option(name = "--tpId", description = "Technology profile id",
            required = false, multiValued = false)
    private String strTpId = null;

    @Override
    protected void doExecute() {

        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        AccessSubscriberId portName = new AccessSubscriberId(strPortName);

        Optional<VlanId> cTag = strCtag == null ? Optional.empty() : Optional.of(VlanId.vlanId(strCtag));
        Optional<VlanId> sTag = strStag == null ? Optional.empty() : Optional.of(VlanId.vlanId(strStag));
        Optional<Integer> tpId = strTpId == null ? Optional.empty() : Optional.of(Integer.parseInt(strTpId));
        service.provisionSubscriber(portName, sTag, cTag, tpId);
    }
}
