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

import org.onlab.packet.VlanId;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Port;
import org.opencord.olt.AccessDevicePort;
import org.opencord.olt.FlowOperation;
import org.opencord.olt.OltFlowServiceInterface;
import org.opencord.olt.ServiceKey;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for OLT app.
 */
public final class OltUtils {

    private OltUtils() {
    }

    /**
     * Returns the port name if present in the annotations.
     * @param port the port
     * @return the annotated port name
     */
    public static String getPortName(Port port) {
        String name = port.annotations().value(AnnotationKeys.PORT_NAME);
        return name == null ? "" : name;
    }

    /**
     * Returns a port printed as a connect point and with the name appended.
     * @param port the port
     * @return the formatted string
     */
    public static String portWithName(Port port) {
        return port.element().id().toString() + '/' +
                port.number() + '[' +
                getPortName(port) + ']';
    }

    public static String flowOpToString(FlowOperation op) {
        return op == FlowOperation.ADD ? "Adding" : "Removing";
    }

    public static String completeFlowOpToString(FlowOperation op) {
        return op == FlowOperation.ADD ? "Added" : "Removed";
    }

    /**
     * Search and return the matching UniTagInfomation from the list of UniTagInfomation in the
     * SubscriberAndDeviceInformation.
     * For the match : cvlan, svlan and tpId are used.
     *
     * @param subInfo       Subscriber information.
     * @param innerVlan     cTag
     * @param outerVlan     sTag
     * @param tpId          Techprofile Id
     * @return UniTagInformation
     */
    public static UniTagInformation getUniTagInformation(SubscriberAndDeviceInformation subInfo, VlanId innerVlan,
                                                  VlanId outerVlan, int tpId) {
        UniTagInformation service = null;
        for (UniTagInformation tagInfo : subInfo.uniTagList()) {
            if (innerVlan.equals(tagInfo.getPonCTag()) && outerVlan.equals(tagInfo.getPonSTag())
                    && tpId == tagInfo.getTechnologyProfileId()) {
                service = tagInfo;
                break;
            }
        }
        return service;
    }

    public static SubscriberAndDeviceInformation getProgrammedSubscriber(
            OltFlowServiceInterface service, AccessDevicePort accessDevicePort) {
        List<Map.Entry<ServiceKey, UniTagInformation>> entries =
                service.getProgrammedSubscribers().entrySet().stream()
                        .filter(entry -> entry.getKey().getPort().equals(accessDevicePort))
                        .collect(Collectors.toList());
        if (!entries.isEmpty()) {
            List<UniTagInformation> programmedList = entries.stream()
                    .map(entry -> entry.getKey().getService())
                    .collect(Collectors.toList());
            SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
            si.setUniTagList(programmedList);
            return si;
        }
        return null;
    }
}
