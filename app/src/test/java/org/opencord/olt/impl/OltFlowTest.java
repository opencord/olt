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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.onlab.packet.EthType;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class OltFlowTest extends TestBase {
    private OltFlowService oltFlowService;

    PortNumber uniPortNumber = PortNumber.portNumber(1);
    PortNumber nniPortNumber = PortNumber.portNumber(65535);

    UniTagInformation.Builder tagInfoBuilder = new UniTagInformation.Builder();
    UniTagInformation uniTagInfo = tagInfoBuilder.setUniTagMatch(VlanId.vlanId((short) 35))
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
    }

    @Test
    public void testDhcpFiltering() {
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId, uniTagInfo,
                true, true);
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId, uniTagInfo,
                false, true);
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber, dsMeterId, uniTagInfo,
                true, false);
        oltFlowService.processDhcpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId, uniTagInfo,
                false, false);
    }

    @Test
    public void testIgmpFiltering() {
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId, uniTagInfo,
                true, true);
        oltFlowService.processIgmpFilteringObjectives(DEVICE_ID_1, uniPortNumber, usMeterId, uniTagInfo,
                false, true);
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
        oltFlowService.enableDhcpOnProvisioning = true;
        oltFlowService.enableIgmpOnProvisioning = true;
        oltFlowService.processNniFilteringObjectives(DEVICE_ID_1, nniPortNumber, true);
        oltFlowService.processNniFilteringObjectives(DEVICE_ID_1, nniPortNumber, false);
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
                oltFlowService.createDownBuilder(nniPortNumber, uniPortNumber, dsMeterId, uniTagInfo).remove();
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
            assert  selector.getCriterion(Criterion.Type.METADATA) != null;
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
        public void clearMeters(DeviceId deviceId) {
        }
    }

    private class MockOltFlowObjectiveService implements org.onosproject.net.flowobjective.FlowObjectiveService {
        @Override
        public void filter(DeviceId deviceId, FilteringObjective filteringObjective) {

            EthTypeCriterion ethType = (EthTypeCriterion)
                    filterForCriterion(filteringObjective.conditions(), Criterion.Type.ETH_TYPE);

            Instructions.MeterInstruction meter = filteringObjective.meta().metered();
            Instruction writeMetadata = filteringObjective.meta().writeMetadata();
            VlanIdCriterion vlanIdCriterion = (VlanIdCriterion)
                    filterForCriterion(filteringObjective.conditions(), Criterion.Type.VLAN_VID);
            PortCriterion portCriterion = (PortCriterion) filteringObjective.key();


            if (ethType.ethType().equals(EthType.EtherType.LLDP.ethType()) ||
                    portCriterion.port().equals(nniPortNumber)) {
                assert meter == null;
                assert writeMetadata == null;
                assert vlanIdCriterion == null;
            } else {
                assert meter.meterId().equals(usMeterId) || meter.meterId().equals(dsMeterId);
                assert writeMetadata != null;
                assert vlanIdCriterion.vlanId() == uniTagInfo.getPonCTag();
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
            return null;
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
