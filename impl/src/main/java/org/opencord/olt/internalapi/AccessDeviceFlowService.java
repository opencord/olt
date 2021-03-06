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
package org.opencord.olt.internalapi;

import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.meter.MeterId;
import org.opencord.olt.AccessDevicePort;
import org.opencord.sadis.UniTagInformation;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Olt service for flow operations.
 */
public interface AccessDeviceFlowService {

    /**
     * Provisions or removes trap-to-controller DHCP packets.
     *
     * @param port               the uni port for which this trap flow is designated
     * @param upstreamMeterId    the upstream meter id that includes the upstream
     *                           bandwidth profile values such as PIR,CIR. If no meter id needs to be referenced,
     *                           null can be sent
     * @param upstreamOltMeterId the upstream meter id of OLT device that includes the upstream
     *                           bandwidth profile values such as PIR,CIR. If no meter id needs to be referenced,
     *                           null can be sent
     * @param tagInformation     the uni tag (ctag, stag) information
     * @param install            true to install the flow, false to remove the flow
     * @param upstream           true if trapped packets are flowing upstream towards
     *                           server, false if packets are flowing downstream towards client
     * @param dhcpFuture         gets result of dhcp objective when complete
     */
    void processDhcpFilteringObjectives(AccessDevicePort port,
                                        MeterId upstreamMeterId,
                                        MeterId upstreamOltMeterId,
                                        UniTagInformation tagInformation,
                                        boolean install,
                                        boolean upstream,
                                        Optional<CompletableFuture<ObjectiveError>> dhcpFuture);

    /**
     * Trap igmp packets to the controller.
     *
     * @param port                  Uni Port number
     * @param upstreamMeterId       upstream meter id that represents the upstream bandwidth profile
     * @param upstreamOltMeterId    upstream meter id of OLT device that represents the upstream bandwidth profile
     * @param tagInformation        the uni tag information of the subscriber
     * @param install               the indicator to install or to remove the flow
     * @param upstream              determines the direction of the flow
     */
    void processIgmpFilteringObjectives(AccessDevicePort port,
                                        MeterId upstreamMeterId,
                                        MeterId upstreamOltMeterId,
                                        UniTagInformation tagInformation,
                                        boolean install,
                                        boolean upstream);

    /**
     * Trap eapol authentication packets to the controller.
     *
     * @param port         the port for which this trap flow is designated
     * @param bpId         bandwidth profile id to add the related meter to the flow
     * @param oltBpId      bandwidth profile id of OLT device to add the related meter to the flow
     * @param filterFuture completable future for this filtering objective operation
     * @param vlanId       the default or customer tag for a subscriber
     * @param install      true to install the flow, false to remove the flow
     */
    void processEapolFilteringObjectives(AccessDevicePort port,
                                         String bpId,
                                         Optional<String> oltBpId,
                                         CompletableFuture<ObjectiveError> filterFuture,
                                         VlanId vlanId,
                                         boolean install);

    /**
     * Trap PPPoE discovery packets to the controller.
     *
     * @param port               the uni port for which this trap flow is designated
     * @param upstreamMeterId    the upstream meter id that includes the upstream
     *                           bandwidth profile values such as PIR,CIR. If no meter id needs to be referenced,
     *                           null can be sent
     * @param upstreamOltMeterId the upstream meter id of OLT device that includes the upstream
     *                           bandwidth profile values such as PIR,CIR. If no meter id needs to be referenced,
     *                           null can be sent
     * @param tagInformation     the uni tag (ctag, stag) information
     * @param install            true to install the flow, false to remove the flow
     * @param upstream           true if trapped packets are flowing upstream towards
     *                           server, false if packets are flowing downstream towards client
     **/
    void processPPPoEDFilteringObjectives(AccessDevicePort port,
                                          MeterId upstreamMeterId,
                                          MeterId upstreamOltMeterId,
                                          UniTagInformation tagInformation,
                                          boolean install,
                                          boolean upstream);

    /**
     * Trap lldp packets to the controller.
     *
     * @param port    the port for which this trap flow is designated
     * @param install true to install the flow, false to remove the flow
     */
    void processLldpFilteringObjective(AccessDevicePort port, boolean install);

    /**
     * Installs trap filtering objectives for particular traffic types (LLDP, IGMP and DHCP) on an
     * NNI port.
     *
     * @param port    port number
     * @param install true to install, false to remove
     */
    void processNniFilteringObjectives(AccessDevicePort port, boolean install);

    /**
     * Creates a ForwardingObjective builder with double-tag match criteria and output
     * action. The treatment will not contain pop or push actions.
     * If the last parameter is true, use the upstream meter id and vice versa.
     *
     * @param uplinkPort      the nni port
     * @param subscriberPort  the uni port
     * @param meterId         the meter id that is assigned to upstream or downstream flows
     * @param tagInfo         the uni tag information
     * @param upstream        true to create upstream, false to create downstream builder
     * @return ForwardingObjective.Builder
     */
    ForwardingObjective.Builder createTransparentBuilder(AccessDevicePort uplinkPort,
                                                         AccessDevicePort subscriberPort,
                                                         MeterId meterId,
                                                         UniTagInformation tagInfo,
                                                         boolean upstream);

    /**
     * Creates a ForwardingObjective builder for the upstream flows.
     * The treatment will contain push action
     *
     * @param uplinkPort         the nni port
     * @param subscriberPort     the uni port
     * @param upstreamMeterId    the meter id that is assigned to upstream flows
     * @param upstreamOltMeterId the meter id that is assigned to upstream flows for OLT device
     * @param uniTagInformation  the uni tag information
     * @return ForwardingObjective.Builder
     */
    ForwardingObjective.Builder createUpBuilder(AccessDevicePort uplinkPort,
                                                AccessDevicePort subscriberPort,
                                                MeterId upstreamMeterId,
                                                MeterId upstreamOltMeterId,
                                                UniTagInformation uniTagInformation);

    /**
     * Creates a ForwardingObjective builder for the downstream flows.
     * The treatment will contain pop action
     *
     * @param uplinkPort           the nni port
     * @param subscriberPort       the uni port
     * @param downstreamMeterId    the meter id that is assigned to downstream flows
     * @param downstreamOltMeterId the meter id that is assigned to downstream flows
     * @param tagInformation       the uni tag information
     * @param macAddress           the mac address
     * @return ForwardingObjective.Builder
     */
    ForwardingObjective.Builder createDownBuilder(AccessDevicePort uplinkPort,
                                                  AccessDevicePort subscriberPort,
                                                  MeterId downstreamMeterId,
                                                  MeterId downstreamOltMeterId,
                                                  UniTagInformation tagInformation,
                                                  Optional<MacAddress> macAddress);

    /**
     * Clears pending mappings and state for device.
     * @param deviceId the device id
     */
    void clearDeviceState(DeviceId deviceId);
}
