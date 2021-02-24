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
package org.opencord.olt.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.EthType;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.cluster.NodeId;
import org.onosproject.cluster.RoleInfo;
import org.onosproject.mastership.MastershipInfo;
import org.onosproject.mastership.MastershipListener;
import org.onosproject.net.DeviceId;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthTypeCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.VlanIdCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.flowobjective.FilteringObjQueueKey;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjQueueKey;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.NextObjQueueKey;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterKey;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.UniTagInformation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;

public class OltFlowTest extends TestBase {
    private OltFlowService oltFlowService;
    PortNumber uniPortNumber = PortNumber.portNumber(1);
    PortNumber uniPortNumber2 = PortNumber.portNumber(2);
    PortNumber nniPortNumber = PortNumber.portNumber(65535);

    MacAddress macAddress = MacAddress.valueOf("00:00:00:00:0a:0b");

    UniTagInformation.Builder tagInfoBuilder = new UniTagInformation.Builder();
    UniTagInformation uniTagInfo = tagInfoBuilder.setUniTagMatch(VlanId.vlanId((short) 35))
            .setPonCTag(VlanId.vlanId((short) 33))
            .setPonSTag(VlanId.vlanId((short) 7))
            .setDsPonCTagPriority(0)
            .setUsPonSTagPriority(0)
            .setTechnologyProfileId(64)
            .setDownstreamBandwidthProfile(dsBpId)
            .setUpstreamBandwidthProfile(usBpId)
            .setIsDhcpRequired(true)
            .setIsIgmpRequired(true)
            .build();

    UniTagInformation.Builder tagInfoBuilderNoPcp = new UniTagInformation.Builder();
    UniTagInformation uniTagInfoNoPcp = tagInfoBuilderNoPcp.setUniTagMatch(VlanId.vlanId((short) 35))
            .setPonCTag(VlanId.vlanId((short) 34))
            .setPonSTag(VlanId.vlanId((short) 7))
            .setDsPonCTagPriority(-1)
            .setUsPonSTagPriority(-1)
            .setUsPonCTagPriority(-1)
            .setDsPonSTagPriority(-1)
            .setTechnologyProfileId(64)
            .setDownstreamBandwidthProfile(dsBpId)
            .setUpstreamBandwidthProfile(usBpId)
            .setIsDhcpRequired(true)
            .setIsIgmpRequired(true)
            .build();

    UniTagInformation.Builder tagInfoBuilder2 = new UniTagInformation.Builder();
    UniTagInformation uniTagInfoNoDhcpNoIgmp = tagInfoBuilder2
            .setUniTagMatch(VlanId.vlanId((short) 35))
            .setPonCTag(VlanId.vlanId((short) 33))
            .setPonSTag(VlanId.vlanId((short) 7))
            .setDsPonCTagPriority(0)
            .setUsPonSTagPriority(0)
            .setTechnologyProfileId(64)
            .setDownstreamBandwidthProfile(dsBpId)
            .setUpstreamBandwidthProfile(usBpId)
            .build();

    @Before
    public void setUp() {
        oltFlowService = new OltFlowService();
        oltFlowService.oltMeterService = new MockOltMeterService();
        oltFlowService.flowObjectiveService = new MockOltFlowObjectiveService();
        oltFlowService.mastershipService = new MockMastershipService();
        oltFlowService.sadisService = new MockSadisService();
        oltFlowService.bpService = oltFlowService.sadisService.getBandwidthProfileService();
        oltFlowService.appId = appId;
        oltFlowService.pendingEapolForDevice = Maps.newConcurrentMap();
    }

    @Test
    public void testDhcpFiltering() {
        oltFlowService.flowObjectiveService.clearQueue();
        // ensure upstream dhcp traps can be added and removed
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 1;
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                false, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 2;

        // Ensure upstream flow has no pcp unless properly specified.
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber2,
                usMeterId, uniTagInfoNoPcp,
                true, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 3;

        // ensure upstream flows are not added if uniTagInfo is missing dhcp requirement
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfoNoDhcpNoIgmp,
                true, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 3;

        // ensure downstream traps don't succeed without global config for nni ports
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                true, false, Optional.empty());
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                false, false, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 3;
        // do global config for nni ports and now it should succeed
        oltFlowService.enableDhcpOnNni = true;
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                true, false, Optional.empty());
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                false, false, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 5;

        // turn on DHCPv6 and we should get 2 flows
        oltFlowService.enableDhcpV6 = true;
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 7;

        // turn off DHCPv4 and it's only v6
        oltFlowService.enableDhcpV4 = false;
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true, Optional.empty());
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 8;

        // cleanup
        oltFlowService.flowObjectiveService.clearQueue();
        oltFlowService.enableDhcpV4 = true;
        oltFlowService.enableDhcpV6 = false;
    }

    @Test
    public void testPppoedFiltering() {
        oltFlowService.flowObjectiveService.clearQueue();

        // ensure pppoed traps are not added if global config is off.
        oltFlowService.enablePppoe = false;
        oltFlowService.processPPPoEDFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 0;

        // ensure upstream pppoed traps can be added and removed
        oltFlowService.enablePppoe = true;
        oltFlowService.processPPPoEDFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 1;
        oltFlowService.processPPPoEDFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                false, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 2;

        // ensure downstream pppoed traps can be added and removed
        oltFlowService.processPPPoEDFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                true, false);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 3;
        oltFlowService.processPPPoEDFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                false, false);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 4;

        // cleanup
        oltFlowService.flowObjectiveService.clearQueue();
    }

    @Test
    public void testIgmpFiltering() {
        oltFlowService.flowObjectiveService.clearQueue();

        // ensure igmp flows can be added and removed
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfo,
                true, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 1;
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId,
                uniTagInfo,
                false, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 2;

        // ensure igmp flow is not added if uniTag has no igmp requirement
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                usMeterId, uniTagInfoNoDhcpNoIgmp,
                true, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 2;

        //ensure igmp flow on NNI fails without global setting
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                true, false);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 2;

        // igmp trap on NNI should succeed with global config
        oltFlowService.enableIgmpOnNni = true;
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, nniPortNumber,
                null, null,
                true, false);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives().size() == 3;
        // cleanup
        oltFlowService.flowObjectiveService.clearQueue();

    }

    @Test
    public void testEapolFiltering() {
        addBandwidthProfile(uniTagInfo.getUpstreamBandwidthProfile());
        oltFlowService.enableEapol = true;

        //will install
        oltFlowService.processEapolFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                uniTagInfo.getUpstreamBandwidthProfile(), new CompletableFuture<>(),
                uniTagInfo.getUniTagMatch(), true);

        //bp profile doesn't exist
        oltFlowService.processEapolFilteringObjectives(DEVICE_ID_1, uniPortNumber,
                uniTagInfo.getDownstreamBandwidthProfile(), new CompletableFuture<>(),
                uniTagInfo.getUniTagMatch(), true);
    }

    @Test
    public void testLldpFiltering() {
        oltFlowService.processLldpFilteringObjective(DEVICE_ID_1, nniPortNumber, true);
        oltFlowService.processLldpFilteringObjective(DEVICE_ID_1, nniPortNumber, false);
    }

    @Test
    public void testNniFiltering() {
        oltFlowService.flowObjectiveService.clearQueue();
        oltFlowService.enableDhcpOnNni = true;
        oltFlowService.enableIgmpOnNni = true;
        oltFlowService.processNniFilteringObjectives(DEVICE_ID_1, nniPortNumber, true);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives()
                .size() == 3;
        oltFlowService.processNniFilteringObjectives(DEVICE_ID_1, nniPortNumber, false);
        assert oltFlowService.flowObjectiveService.getPendingFlowObjectives()
                .size() == 6;
        oltFlowService.flowObjectiveService.clearQueue();
    }

    @Test
    public void testUpBuilder() {
        ForwardingObjective objective =
                oltFlowService.createUpBuilder(nniPortNumber, uniPortNumber, usMeterId, uniTagInfo).add();
        checkObjective(objective, true);
    }

    @Test
    public void testDownBuilder() {
        ForwardingObjective objective =
                oltFlowService.createDownBuilder(nniPortNumber, uniPortNumber, dsMeterId, uniTagInfo,
                        Optional.of(macAddress)).remove();
        checkObjective(objective, false);
    }

    private void checkObjective(ForwardingObjective fwd, boolean upstream) {
        TrafficTreatment treatment = fwd.treatment();

        //check instructions
        Set<Instructions.MeterInstruction> meters = treatment.meters();
        assert !meters.isEmpty();

        Instructions.MetadataInstruction writeMetadata = treatment.writeMetadata();
        assert writeMetadata != null;

        List<Instruction> immediateInstructions = treatment.immediate();
        Optional<Instruction> vlanInstruction = immediateInstructions.stream()
                .filter(i -> i.type() == Instruction.Type.L2MODIFICATION)
                .filter(i -> ((L2ModificationInstruction) i).subtype() ==
                        L2ModificationInstruction.L2SubType.VLAN_PUSH ||
                        ((L2ModificationInstruction) i).subtype() ==
                                L2ModificationInstruction.L2SubType.VLAN_POP)
                .findAny();

        assert vlanInstruction.isPresent();

        //check match criteria
        TrafficSelector selector = fwd.selector();
        assert selector.getCriterion(Criterion.Type.IN_PORT) != null;
        assert selector.getCriterion(Criterion.Type.VLAN_VID) != null;

        if (!upstream) {
            assert selector.getCriterion(Criterion.Type.METADATA) != null;
            assert selector.getCriterion(Criterion.Type.ETH_DST) != null;
        }
    }

    private class MockOltMeterService implements org.opencord.olt.internalapi.AccessDeviceMeterService {
        @Override
        public ImmutableMap<String, Collection<MeterKey>> getBpMeterMappings() {
            return null;
        }

        @Override
        public MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile) {
            return null;
        }


        @Override
        public ImmutableSet<MeterKey> getProgMeters() {
            return null;
        }

        @Override
        public MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                                   CompletableFuture<Object> meterFuture) {
            return usMeterId;
        }

        @Override
        public void removeFromPendingMeters(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {

        }

        @Override
        public boolean checkAndAddPendingMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
            return false;
        }


        @Override
        public void clearMeters(DeviceId deviceId) {
        }

        @Override
        public void clearDeviceState(DeviceId deviceId) {

        }
    }

    private class MockOltFlowObjectiveService implements org.onosproject.net.flowobjective.FlowObjectiveService {
        List<String> flowObjectives = new ArrayList<>();

        @Override
        public void filter(DeviceId deviceId, FilteringObjective filteringObjective) {
            flowObjectives.add(filteringObjective.toString());
            EthTypeCriterion ethType = (EthTypeCriterion)
                    filterForCriterion(filteringObjective.conditions(), Criterion.Type.ETH_TYPE);

            Instructions.MeterInstruction meter = filteringObjective.meta().metered();
            Instruction writeMetadata = filteringObjective.meta().writeMetadata();
            VlanIdCriterion vlanIdCriterion = (VlanIdCriterion)
                    filterForCriterion(filteringObjective.conditions(), Criterion.Type.VLAN_VID);
            PortCriterion portCriterion = (PortCriterion) filteringObjective.key();

            filteringObjective.meta().allInstructions().forEach(instruction -> {
                if (instruction.type().equals(Instruction.Type.L2MODIFICATION)) {
                    L2ModificationInstruction l2Instruction = (L2ModificationInstruction) instruction;
                    if (l2Instruction.subtype().equals(L2ModificationInstruction.L2SubType.VLAN_PCP)) {
                        //this, given the uniTagInfo we provide, should not be present
                        assert false;
                    }
                }
            });


            if (ethType.ethType().equals(EthType.EtherType.LLDP.ethType()) ||
                    portCriterion.port().equals(nniPortNumber)) {
                assert meter == null;
                assert writeMetadata == null;
                assert vlanIdCriterion == null;
            } else {
                assert meter.meterId().equals(usMeterId) || meter.meterId().equals(dsMeterId);
                assert writeMetadata != null;
                assert vlanIdCriterion == null || vlanIdCriterion.vlanId() == uniTagInfo.getUniTagMatch()
                        || vlanIdCriterion.vlanId() == uniTagInfoNoPcp.getUniTagMatch();
            }

        }

        @Override
        public void forward(DeviceId deviceId, ForwardingObjective forwardingObjective) {

        }

        @Override
        public void next(DeviceId deviceId, NextObjective nextObjective) {

        }

        @Override
        public int allocateNextId() {
            return 0;
        }

        @Override
        public void initPolicy(String s) {

        }

        @Override
        public void apply(DeviceId deviceId, Objective objective) {

        }

        @Override
        public Map<Pair<Integer, DeviceId>, List<String>> getNextMappingsChain() {
            return null;
        }

        @Override
        public List<String> getNextMappings() {
            return null;
        }

        @Override
        public List<String> getPendingFlowObjectives() {
            return ImmutableList.copyOf(flowObjectives);
        }

        @Override
        public ListMultimap<FilteringObjQueueKey, Objective> getFilteringObjQueue() {
            return null;
        }

        @Override
        public ListMultimap<ForwardingObjQueueKey, Objective> getForwardingObjQueue() {
            return null;
        }

        @Override
        public ListMultimap<NextObjQueueKey, Objective> getNextObjQueue() {
            return null;
        }

        @Override
        public Map<FilteringObjQueueKey, Objective> getFilteringObjQueueHead() {
            return null;
        }

        @Override
        public Map<ForwardingObjQueueKey, Objective> getForwardingObjQueueHead() {
            return null;
        }

        @Override
        public Map<NextObjQueueKey, Objective> getNextObjQueueHead() {
            return null;
        }

        @Override
        public void clearQueue() {
            flowObjectives.clear();
        }

        private Criterion filterForCriterion(Collection<Criterion> criteria, Criterion.Type type) {
            return criteria.stream()
                    .filter(c -> c.type().equals(type))
                    .limit(1)
                    .findFirst().orElse(null);
        }
    }

    private class MockMastershipService implements org.onosproject.mastership.MastershipService {
        @Override
        public MastershipRole getLocalRole(DeviceId deviceId) {
            return null;
        }

        @Override
        public boolean isLocalMaster(DeviceId deviceId) {
            return true;
        }

        @Override
        public CompletableFuture<MastershipRole> requestRoleFor(DeviceId deviceId) {
            return null;
        }

        @Override
        public CompletableFuture<Void> relinquishMastership(DeviceId deviceId) {
            return null;
        }

        @Override
        public NodeId getMasterFor(DeviceId deviceId) {
            return null;
        }

        @Override
        public RoleInfo getNodesFor(DeviceId deviceId) {
            return null;
        }

        @Override
        public MastershipInfo getMastershipFor(DeviceId deviceId) {
            return null;
        }

        @Override
        public Set<DeviceId> getDevicesOf(NodeId nodeId) {
            return null;
        }

        @Override
        public void addListener(MastershipListener mastershipListener) {

        }

        @Override
        public void removeListener(MastershipListener mastershipListener) {

        }
    }
}
