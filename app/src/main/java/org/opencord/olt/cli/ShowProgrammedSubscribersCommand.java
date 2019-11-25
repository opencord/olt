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

import java.util.Map;
import java.util.Set;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.opencord.olt.AccessDeviceService;
import org.opencord.sadis.UniTagInformation;

/**
 * Shows subscriber information for those subscriber which have been programmed
 * in the data-plane.
 */
@Command(scope = "onos", name = "volt-programmed-subscribers",
        description = "Shows subscribers programmed in the dataplane")
public class ShowProgrammedSubscribersCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        Map<ConnectPoint, Set<UniTagInformation>> info = service.getProgSubs();
        info.forEach(this::display);
    }

    private void display(ConnectPoint cp, Set<UniTagInformation> uniTagInformation) {
        uniTagInformation.forEach(uniTag ->
                print("location=%s tagInformation=%s", cp, uniTag));
    }
}
