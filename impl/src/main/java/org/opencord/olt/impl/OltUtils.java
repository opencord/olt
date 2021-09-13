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

package org.opencord.olt.impl;

import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Port;

import static org.opencord.olt.impl.OltFlowService.FlowOperation.ADD;

/**
 * Utility class for OLT app.
 */
final class OltUtils {

    private OltUtils() {
    }

    /**
     * Returns the port name if present in the annotations.
     * @param port the port
     * @return the annotated port name
     */
    static String getPortName(Port port) {
        String name = port.annotations().value(AnnotationKeys.PORT_NAME);
        return name == null ? "" : name;
    }

    /**
     * Returns a port printed as a connect point and with the name appended.
     * @param port the port
     * @return the formatted string
     */
    static String portWithName(Port port) {
        return port.element().id().toString() + '/' +
                port.number() + '[' +
                getPortName(port) + ']';
    }

    static String flowOpToString(OltFlowService.FlowOperation op) {
        return op == ADD ? "Adding" : "Removing";
    }

    static String completeFlowOpToString(OltFlowService.FlowOperation op) {
        return op == ADD ? "Added" : "Removed";
    }
}
