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
/**
 * Shows failed subscribers on an OLTs.
 */
@Service
@Command(scope = "onos", name = "volt-failed-subscribers",
        description = "Shows subscribers that failed provisioning")
public class ShowFailedSubscribersCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        // NOTE
        // this command is still available purely for backward compatibility but whilst the old implementation
        // had a limited number of retries available the new implementation will keep retrying.
        print("Unimplemented");
    }

}