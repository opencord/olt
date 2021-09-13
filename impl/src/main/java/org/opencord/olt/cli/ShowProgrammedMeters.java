/*
 * Copyright 2021-present Open Networking Foundation
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
import org.onosproject.net.DeviceId;
import org.opencord.olt.impl.MeterData;
import org.opencord.olt.impl.OltMeterServiceInterface;

import java.util.Map;
/**
 * Displays the programmed meters through the OLT app.
 */
@Service
@Command(scope = "onos", name = "volt-programmed-meters",
        description = "Shows information about programmed meters, including the relation with the Bandwidth Profile")
public class ShowProgrammedMeters extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        OltMeterServiceInterface service = AbstractShellCommand.get(OltMeterServiceInterface.class);
        Map<DeviceId, Map<String, MeterData>> meters = service.getProgrammedMeters();
        if (meters.isEmpty()) {
            print("No meters programmed by the olt app");
            return;
        }
        meters.forEach(this::display);
    }

    private void display(DeviceId deviceId, Map<String, MeterData> data) {
        print("deviceId=%s, meterCount=%d", deviceId, data.size());
        data.forEach((bp, md) ->
                print("\tmeterId=%s bandwidthProfile=%s status=%s",
                        md.getMeterId(), bp, md.getMeterStatus()));

    }
}