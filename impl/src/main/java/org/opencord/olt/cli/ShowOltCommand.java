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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.opencord.olt.AccessDeviceService;

import java.util.List;

/**
 * Shows configured OLTs.
 */
@Service
@Command(scope = "onos", name = "volt-olts",
        description = "Shows vOLTs connected to ONOS")
public class ShowOltCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        AccessDeviceService service = AbstractShellCommand.get(AccessDeviceService.class);
        if (outputJson()) {
            print("%s", json(service.fetchOlts()));
        } else {
            service.fetchOlts().forEach(did -> print("OLT %s", did));
        }

    }

    /**
     * Returns JSON node representing the specified olts.
     *
     * @param olts collection of olts
     * @return JSON node
     */
    private JsonNode json(List<DeviceId> olts) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        ArrayNode result = node.putArray("olts");
        for (DeviceId olt : olts) {
            result.add(olt.toString());
        }
        return node;
    }
}
