/*
 * Copyright 2018-present Open Networking Foundation
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

package org.opencord.olt.xos;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class XosEndpointConfig extends Config<ApplicationId> {

    private static final String ENDPOINT = "endpoint";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    @Override
    public boolean isValid() {
        return hasOnlyFields(ENDPOINT, USERNAME, PASSWORD) &&
                hasFields(ENDPOINT, USERNAME, PASSWORD);
    }

    public String getEndpoint() {
        return object.path(ENDPOINT).asText();
    }

    public String getUsername() {
        return object.path(USERNAME).asText();
    }

    public String getPassword() {
        return object.path(PASSWORD).asText();
    }
}
