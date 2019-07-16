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

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.meter.MeterKey;
import org.opencord.olt.AccessDeviceService;

import java.util.Set;

/**
 * Shows information about device-meter mappings that have been programmed in the
 * data-plane.
 */
@Service
@Command(scope = "onos", name = "volt-programmed-meters",
        description = "Shows device-meter mappings programmed in the data-plane")
public class ShowProgrammedMetersCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        Set<MeterKey> programmedMeters = service.getProgMeters();
        programmedMeters.forEach(this::display);
    }

    private void display(MeterKey meterKey) {
        print("device=%s meter=%s", meterKey.deviceId(), meterKey.meterId());
    }
}
