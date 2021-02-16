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

package org.opencord.olt.driver;

import org.onosproject.net.driver.AbstractDriverLoader;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Loader for olt device drivers.
 */
@Component(immediate = true)
public class OltDriversLoader extends AbstractDriverLoader {

    private final Logger log = getLogger(getClass());

    public OltDriversLoader() {
        super("/olt-drivers.xml");
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }
}
