/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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

import com.google.common.collect.Maps;
import org.mockito.ArgumentMatcher;
import org.onosproject.net.Annotations;
import org.onosproject.net.Element;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.opencord.sadis.BandwidthProfileInformation;

import java.util.Map;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class OltTestHelpers {

    protected static final String CLIENT_NAS_PORT_ID = "PON 1/1";
    protected static final String CLIENT_CIRCUIT_ID = "CIR-PON 1/1";
    protected static final String OLT_DEV_ID = "of:00000000000000aa";
    Map<String, BandwidthProfileInformation> bpInformation = Maps.newConcurrentMap();

    protected class FilteringObjectiveMatcher extends ArgumentMatcher<FilteringObjective> {

        private FilteringObjective left;

        public FilteringObjectiveMatcher(FilteringObjective left) {
            this.left = left;
        }

        @Override
        public boolean matches(Object right) {
            // NOTE this matcher can be improved
            FilteringObjective r = (FilteringObjective) right;
            boolean matches = left.type().equals(r.type()) &&
                    left.key().equals(r.key()) &&
                    left.conditions().equals(r.conditions()) &&
                    left.appId().equals(r.appId()) &&
                    left.priority() == r.priority();

            if (left.meta() != null) {
                if (left.meta().equals(r.meta())) {
                    return matches;
                } else {
                    return false;
                }
            }
            return matches;
        }
    }

    public class OltPort implements Port {

        public boolean enabled;
        public PortNumber portNumber;
        public Annotations annotations;
        public Element element;

        public OltPort(Element element, boolean enabled, PortNumber portNumber, Annotations annotations) {
            this.enabled = enabled;
            this.portNumber = portNumber;
            this.annotations = annotations;
            this.element = element;
        }

        @Override
        public Element element() {
            return element;
        }

        @Override
        public PortNumber number() {
            return portNumber;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Type type() {
            return null;
        }

        @Override
        public long portSpeed() {
            return 0;
        }

        @Override
        public Annotations annotations() {
            return annotations;
        }
    }
}
