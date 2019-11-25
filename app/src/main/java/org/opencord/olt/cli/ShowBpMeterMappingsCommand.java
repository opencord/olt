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

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.meter.MeterKey;
import org.opencord.olt.internalapi.AccessDeviceMeterService;

import java.util.Map;
import java.util.Set;

@Command(scope = "onos", name = "volt-bpmeter-mappings",
        description = "Shows information about bandwidthProfile-meterKey (device / meter) mappings")
public class ShowBpMeterMappingsCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        AccessDeviceMeterService service = AbstractShellCommand.get(AccessDeviceMeterService.class);
        Map<String, Set<MeterKey>> bpMeterMappings = service.getBpMeterMappings();
        bpMeterMappings.forEach(this::display);
    }

    private void display(String bpInfo, Set<MeterKey> meterKeyList) {
        meterKeyList.forEach(meterKey ->
                print("bpInfo=%s deviceId=%s meterId=%s",
                        bpInfo, meterKey.deviceId(), meterKey.meterId()));

    }
}
