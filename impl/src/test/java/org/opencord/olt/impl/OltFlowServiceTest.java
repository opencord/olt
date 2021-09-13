package org.opencord.olt.impl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.onlab.packet.ChassisId;
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.VlanId;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.DefaultHost;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flowobjective.DefaultFilteringObjective;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.store.service.TestStorageService;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opencord.olt.impl.OltFlowService.OltFlowsStatus.NONE;
import static org.opencord.olt.impl.OltFlowService.OltFlowsStatus.ADDED;
import static org.opencord.olt.impl.OltFlowService.OltFlowsStatus.PENDING_ADD;
import static org.opencord.olt.impl.OltFlowService.OltFlowsStatus.REMOVED;
import static org.opencord.olt.impl.OsgiPropertyConstants.DEFAULT_BP_ID_DEFAULT;

public class OltFlowServiceTest extends OltTestHelpers {

    private OltFlowService oltFlowService;
    OltFlowService.InternalFlowListener internalFlowListener;
    private final ApplicationId testAppId = new DefaultApplicationId(1, "org.opencord.olt.test");
    private final short eapolDefaultVlan = 4091;

    private final DeviceId deviceId = DeviceId.deviceId("test-device");
    private final Device testDevice = new DefaultDevice(ProviderId.NONE, deviceId, Device.Type.OLT,
            "testManufacturer", "1.0", "1.0", "SN", new ChassisId(1));
    Port nniPort = new OltPort(true, PortNumber.portNumber(1048576),
            DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
    Port nniPortDisabled = new OltPort(false, PortNumber.portNumber(1048576),
            DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "nni-1").build());
    Port uniUpdateEnabled = new OltPort(true, PortNumber.portNumber(16),
            DefaultAnnotations.builder().set(AnnotationKeys.PORT_NAME, "uni-1").build());

    @Before
    public void setUp() {
        oltFlowService = new OltFlowService();
        oltFlowService.cfgService = new ComponentConfigAdapter();
        oltFlowService.sadisService = Mockito.mock(SadisService.class);
        oltFlowService.coreService = Mockito.spy(new CoreServiceAdapter());
        oltFlowService.oltMeterService = Mockito.mock(OltMeterService.class);
        oltFlowService.flowObjectiveService = Mockito.mock(FlowObjectiveService.class);
        oltFlowService.hostService = Mockito.mock(HostService.class);
        oltFlowService.flowRuleService = Mockito.mock(FlowRuleService.class);
        oltFlowService.storageService = new TestStorageService();
        oltFlowService.oltDeviceService = Mockito.mock(OltDeviceService.class);
        oltFlowService.appId = testAppId;

        doReturn(Mockito.mock(BaseInformationService.class))
                .when(oltFlowService.sadisService).getSubscriberInfoService();
        doReturn(testAppId).when(oltFlowService.coreService).registerApplication("org.opencord.olt");
        oltFlowService.activate(null);

        internalFlowListener = spy(oltFlowService.internalFlowListener);
    }

    @After
    public void tearDown() {
        oltFlowService.deactivate(null);
    }

    @Test
    public void testUpdateConnectPointStatus() {
        // connect points for the test
        ConnectPoint cp1 = new ConnectPoint(DeviceId.deviceId("test1"), PortNumber.portNumber(1));
        ConnectPoint cp2 = new ConnectPoint(DeviceId.deviceId("test1"), PortNumber.portNumber(2));
        ConnectPoint cp3 = new ConnectPoint(DeviceId.deviceId("test1"), PortNumber.portNumber(3));

        // cpStatus map for the test
        oltFlowService.cpStatus = oltFlowService.storageService.
                <ConnectPoint, OltPortStatus>consistentMapBuilder().build().asJavaMap();
        OltPortStatus cp1Status = new OltPortStatus(PENDING_ADD, NONE, NONE);
        oltFlowService.cpStatus.put(cp1, cp1Status);

        //check that we only update the provided value
        oltFlowService.updateConnectPointStatus(cp1, ADDED, null, null);
        OltPortStatus updated = oltFlowService.cpStatus.get(cp1);
        Assert.assertEquals(ADDED, updated.defaultEapolStatus);
        Assert.assertEquals(NONE, updated.subscriberFlowsStatus);
        Assert.assertEquals(NONE, updated.dhcpStatus);

        // check that it creates an entry if it does not exist
        oltFlowService.updateConnectPointStatus(cp2, PENDING_ADD, NONE, NONE);
        Assert.assertNotNull(oltFlowService.cpStatus.get(cp2));

        // check that if we create a new entry with null values they're converted to NONE
        oltFlowService.updateConnectPointStatus(cp3, null, null, null);
        updated = oltFlowService.cpStatus.get(cp3);
        Assert.assertEquals(NONE, updated.defaultEapolStatus);
        Assert.assertEquals(NONE, updated.subscriberFlowsStatus);
        Assert.assertEquals(NONE, updated.dhcpStatus);
    }

    @Test
    public void testHasDefaultEapol() {
        DeviceId deviceId = DeviceId.deviceId("test-device");
        ConnectPoint cpWithStatus = new ConnectPoint(deviceId, PortNumber.portNumber(16));

        OltPortStatus portStatusAdded = new OltPortStatus(
                OltFlowService.OltFlowsStatus.ADDED,
                NONE,
                null
        );

        OltPortStatus portStatusRemoved = new OltPortStatus(
                REMOVED,
                NONE,
                null
        );

        oltFlowService.cpStatus.put(cpWithStatus, portStatusAdded);
        Assert.assertTrue(oltFlowService.hasDefaultEapol(deviceId, PortNumber.portNumber(16)));

        oltFlowService.cpStatus.put(cpWithStatus, portStatusRemoved);
        Assert.assertFalse(oltFlowService.hasDefaultEapol(deviceId, PortNumber.portNumber(16)));

        Assert.assertFalse(oltFlowService.hasDefaultEapol(deviceId, PortNumber.portNumber(17)));
    }

    @Test
    public void tesHasSubscriberFlows() {
        DeviceId deviceId = DeviceId.deviceId("test-device");
        PortNumber portNumber = PortNumber.portNumber(16);
        ConnectPoint cp = new ConnectPoint(deviceId, portNumber);

        OltPortStatus withDefaultEapol = new OltPortStatus(
                ADDED,
                NONE,
                NONE
        );

        OltPortStatus withDhcp = new OltPortStatus(
                REMOVED,
                NONE,
                ADDED
        );

        OltPortStatus withSubFlow = new OltPortStatus(
                REMOVED,
                ADDED,
                ADDED
        );

        oltFlowService.cpStatus.put(cp, withDefaultEapol);
        Assert.assertFalse(oltFlowService.hasSubscriberFlows(deviceId, portNumber));

        oltFlowService.cpStatus.put(cp, withDhcp);
        Assert.assertTrue(oltFlowService.hasDhcpFlows(deviceId, portNumber));

        oltFlowService.cpStatus.put(cp, withSubFlow);
        Assert.assertTrue(oltFlowService.hasSubscriberFlows(deviceId, portNumber));
    }

    @Test
    public void testHandleBasicPortFlowsNoEapol() throws Exception {
        oltFlowService.enableEapol = false;
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);

        final DiscoveredSubscriber addedSub =
                new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.ADDED,
                                         false, si);
        oltFlowService.handleBasicPortFlows(addedSub, DEFAULT_BP_ID_DEFAULT, DEFAULT_BP_ID_DEFAULT);
        // if eapol is not enabled there's nothing we need to do,
        // so make sure we don't even call sadis
        verify(oltFlowService.subsService, never()).get(any());
    }

    @Test
    public void testHandleBasicPortFlowsWithEapolNoMeter() throws Exception {
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        final DiscoveredSubscriber addedSub =
                new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.ADDED,
                                         false, si);
        // whether the meter is pending or not is up to the createMeter method to handle
        // we just don't proceed with the subscriber till it's ready
        doReturn(false).when(oltFlowService.oltMeterService)
                .createMeter(addedSub.device.id(), DEFAULT_BP_ID_DEFAULT);
        boolean res = oltFlowService.handleBasicPortFlows(addedSub, DEFAULT_BP_ID_DEFAULT, DEFAULT_BP_ID_DEFAULT);

        Assert.assertFalse(res);

        // we do not create flows
        verify(oltFlowService.flowObjectiveService, never())
                .filter(eq(addedSub.device.id()), any());
    }

    @Test
    public void testHandleBasicPortFlowsWithEapolAddedMeter() throws Exception {
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        final DiscoveredSubscriber addedSub =
                new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.ADDED,
                                         false, si);
        // this is the happy case, we have the meter so we check that the default EAPOL flow
        // is installed
        doReturn(true).when(oltFlowService.oltMeterService)
                .createMeter(deviceId, DEFAULT_BP_ID_DEFAULT);
        doReturn(true).when(oltFlowService.oltMeterService)
                .hasMeterByBandwidthProfile(deviceId, DEFAULT_BP_ID_DEFAULT);
        doReturn(MeterId.meterId(1)).when(oltFlowService.oltMeterService)
                .getMeterIdForBandwidthProfile(deviceId, DEFAULT_BP_ID_DEFAULT);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(uniUpdateEnabled.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(
                        DefaultTrafficTreatment.builder()
                                .meter(MeterId.meterId(1))
                                .writeMetadata(oltFlowService.createTechProfValueForWriteMetadata(
                                        VlanId.vlanId(eapolDefaultVlan),
                                        oltFlowService.defaultTechProfileId, MeterId.meterId(1)), 0)
                                .setOutput(PortNumber.CONTROLLER)
                                .pushVlan()
                                .setVlanId(VlanId.vlanId(eapolDefaultVlan)).build()
                )
                .add();


        oltFlowService.handleBasicPortFlows(addedSub, DEFAULT_BP_ID_DEFAULT, DEFAULT_BP_ID_DEFAULT);

        // we check for an existing meter (present)
        // FIXME understand why the above test invokes this call and this one doesn't
//        verify(oltFlowService.oltMeterService, times(1))
//                .hasMeterByBandwidthProfile(eq(addedSub.device.id()), eq(DEFAULT_BP_ID_DEFAULT));

        // the meter exist, no need to check for PENDING or to create it
        verify(oltFlowService.oltMeterService, never())
                .hasPendingMeterByBandwidthProfile(eq(deviceId), eq(DEFAULT_BP_ID_DEFAULT));
        verify(oltFlowService.oltMeterService, never())
                .createMeterForBp(eq(deviceId), eq(DEFAULT_BP_ID_DEFAULT));

        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
    }

    @Test
    public void testHandleBasicPortFlowsRemovedSub() throws Exception {
        // create empty service for testing
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation empty = new UniTagInformation.Builder().build();
        uniTagInformationList.add(empty);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);
        final DiscoveredSubscriber removedSub =
                new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.REMOVED,
                                         false, si);
        // we are testing that when a port goes down we remove the default EAPOL flow

        doReturn(MeterId.meterId(1)).when(oltFlowService.oltMeterService)
                .getMeterIdForBandwidthProfile(deviceId, DEFAULT_BP_ID_DEFAULT);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .deny()
                .withKey(Criteria.matchInPort(uniUpdateEnabled.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.EAPOL.ethType()))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(
                        DefaultTrafficTreatment.builder()
                                .meter(MeterId.meterId(1))
                                .writeMetadata(oltFlowService.createTechProfValueForWriteMetadata(
                                        VlanId.vlanId(eapolDefaultVlan),
                                        oltFlowService.defaultTechProfileId, MeterId.meterId(1)), 0)
                                .setOutput(PortNumber.CONTROLLER)
                                .pushVlan()
                                .setVlanId(VlanId.vlanId(eapolDefaultVlan)).build()
                )
                .add();

        oltFlowService.handleBasicPortFlows(removedSub, DEFAULT_BP_ID_DEFAULT, DEFAULT_BP_ID_DEFAULT);

        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
    }

    @Test
    public void testHandleNniFlowsOnlyLldp() {
        oltFlowService.enableDhcpOnNni = false;
        oltFlowService.handleNniFlows(testDevice, nniPort, OltFlowService.FlowOperation.ADD);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(nniPort.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.LLDP.ethType()))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(DefaultTrafficTreatment.builder().setOutput(PortNumber.CONTROLLER).build())
                .add();

        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), any());
    }

    @Test
    public void testHandleNniFlowsDhcpV4() {
        oltFlowService.enableDhcpOnNni = true;
        oltFlowService.enableDhcpV4 = true;
        oltFlowService.handleNniFlows(testDevice, nniPort, OltFlowService.FlowOperation.ADD);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(nniPort.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_UDP))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(67)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(68)))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(DefaultTrafficTreatment.builder().setOutput(PortNumber.CONTROLLER).build())
                .add();

        // invoked with the correct DHCP filtering objective
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
        // invoked only twice, LLDP and DHCP
        verify(oltFlowService.flowObjectiveService, times(2))
                .filter(eq(deviceId), any());
    }

    @Test
    public void testRemoveNniFlowsDhcpV4() {
        oltFlowService.enableDhcpOnNni = true;
        oltFlowService.enableDhcpV4 = true;
        oltFlowService.handleNniFlows(testDevice, nniPortDisabled, OltFlowService.FlowOperation.REMOVE);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .deny()
                .withKey(Criteria.matchInPort(nniPort.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_UDP))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(67)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(68)))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(DefaultTrafficTreatment.builder().setOutput(PortNumber.CONTROLLER).build())
                .add();

        // invoked with the correct DHCP filtering objective
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
        // invoked only twice, LLDP and DHCP
        verify(oltFlowService.flowObjectiveService, times(2))
                .filter(eq(deviceId), any());
    }

    @Test
    public void testHandleNniFlowsDhcpV6() {
        oltFlowService.enableDhcpOnNni = true;
        oltFlowService.enableDhcpV4 = false;
        oltFlowService.enableDhcpV6 = true;
        oltFlowService.handleNniFlows(testDevice, nniPort, OltFlowService.FlowOperation.ADD);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(nniPort.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV6.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv6.PROTOCOL_UDP))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(546)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(547)))
                .fromApp(testAppId)
                .withPriority(10000)
                .withMeta(DefaultTrafficTreatment.builder().setOutput(PortNumber.CONTROLLER).build())
                .add();

        // invoked with the correct DHCP filtering objective
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
        // invoked only twice, LLDP and DHCP
        verify(oltFlowService.flowObjectiveService, times(2))
                .filter(eq(deviceId), any());
    }

    @Test
    public void testHandleNniFlowsIgmp() {
        oltFlowService.enableDhcpOnNni = false;
        oltFlowService.enableIgmpOnNni = true;
        oltFlowService.handleNniFlows(testDevice, nniPort, OltFlowService.FlowOperation.ADD);

        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(nniPort.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_IGMP))
                .fromApp(testAppId)
                .withPriority(10000)
                .add();

        // invoked with the correct DHCP filtering objective
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(deviceId), argThat(new FilteringObjectiveMatcher(expectedFilter)));
        // invoked only twice, LLDP and DHCP
        verify(oltFlowService.flowObjectiveService, times(2))
                .filter(eq(deviceId), any());
    }

    @Test
    public void testIsMacAddressAvailableViaMacLearning() {

        // create a single service that requires macLearning to be enabled
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        VlanId hsiaCtag = VlanId.vlanId((short) 11);
        UniTagInformation hsia = new UniTagInformation.Builder()
                .setPonCTag(hsiaCtag)
                .setEnableMacLearning(true).build();
        uniTagInformationList.add(hsia);

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);

        // with no hosts discovered, return false
        boolean isMacAvailable = oltFlowService.isMacAddressAvailable(testDevice.id(), uniUpdateEnabled, si);
        Assert.assertFalse(isMacAvailable);

        // with a discovered host, return true
        Host fakeHost = new DefaultHost(ProviderId.NONE, HostId.hostId(MacAddress.NONE), MacAddress.ZERO,
                hsiaCtag, HostLocation.NONE, new HashSet<>(), DefaultAnnotations.builder().build());
        Set<Host> hosts = new HashSet<>(Arrays.asList(fakeHost));
        doReturn(hosts).when(oltFlowService.hostService).getConnectedHosts((ConnectPoint) any());

        isMacAvailable = oltFlowService.isMacAddressAvailable(testDevice.id(), uniUpdateEnabled, si);
        Assert.assertTrue(isMacAvailable);
    }

    @Test
    public void testIsMacAddressAvailableViaConfiguration() {
        // create a single service that has a macAddress configure
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        UniTagInformation hsia = new UniTagInformation.Builder()
                .setConfiguredMacAddress("2e:0a:00:01:00:00")
                .build();
        uniTagInformationList.add(hsia);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);

        boolean isMacAvailable = oltFlowService.isMacAddressAvailable(testDevice.id(), uniUpdateEnabled, si);
        Assert.assertTrue(isMacAvailable);
    }

    @Test
    public void testHandleSubscriberDhcpFlowsAdd() {

        String usBp = "usBp";
        String usOltBp = "usOltBp";
        oltFlowService.enableDhcpV4 = true;

        // create two services, one requires DHCP the other doesn't
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        VlanId hsiaCtag = VlanId.vlanId((short) 11);
        UniTagInformation hsia = new UniTagInformation.Builder()
                .setPonCTag(hsiaCtag)
                .setTechnologyProfileId(64)
                .setUniTagMatch(VlanId.vlanId(VlanId.NO_VID))
                .setUpstreamBandwidthProfile(usBp)
                .setUpstreamOltBandwidthProfile(usOltBp)
                .setIsDhcpRequired(true).build();
        UniTagInformation mc = new UniTagInformation.Builder()
                .setIsDhcpRequired(false).build();
        uniTagInformationList.add(hsia);
        uniTagInformationList.add(mc);

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        si.setUniTagList(uniTagInformationList);

        final DiscoveredSubscriber addedSub =
                new DiscoveredSubscriber(testDevice,
                                         uniUpdateEnabled, DiscoveredSubscriber.Status.ADDED,
                                         false, si);

        // return meter IDs
        doReturn(MeterId.meterId(2)).when(oltFlowService.oltMeterService)
                .getMeterIdForBandwidthProfile(addedSub.device.id(), usBp);
        doReturn(MeterId.meterId(3)).when(oltFlowService.oltMeterService)
                .getMeterIdForBandwidthProfile(addedSub.device.id(), usOltBp);

        // TODO improve the matches on the filter
        FilteringObjective expectedFilter = DefaultFilteringObjective.builder()
                .permit()
                .withKey(Criteria.matchInPort(addedSub.port.number()))
                .addCondition(Criteria.matchEthType(EthType.EtherType.IPV4.ethType()))
                .addCondition(Criteria.matchIPProtocol(IPv4.PROTOCOL_UDP))
                .addCondition(Criteria.matchUdpSrc(TpPort.tpPort(68)))
                .addCondition(Criteria.matchUdpDst(TpPort.tpPort(67)))
                .fromApp(testAppId)
                .withPriority(10000)
                .add();

        oltFlowService.handleSubscriberDhcpFlows(addedSub.device.id(), addedSub.port,
                OltFlowService.FlowOperation.ADD, si);
        verify(oltFlowService.flowObjectiveService, times(1))
                .filter(eq(addedSub.device.id()), argThat(new FilteringObjectiveMatcher(expectedFilter)));
    }

    @Test
    public void testInternalFlowListenerNotMaster() {
        doReturn(false).when(oltFlowService.oltDeviceService).isLocalLeader(any());

        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId("foo"))
                .fromApp(testAppId)
                .makePermanent()
                .withPriority(1000)
                .build();
        FlowRuleEvent event = new FlowRuleEvent(FlowRuleEvent.Type.RULE_ADDED,
                flowRule);

        internalFlowListener.event(event);

        // if we're not master of the device, we should not update
        verify(internalFlowListener, never()).updateCpStatus(any(), any(), any());
    }

    @Test
    public void testInternalFlowListenerDifferentApp() {
        ApplicationId someAppId = new DefaultApplicationId(1, "org.opencord.olt.not-test");
        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId("foo"))
                .fromApp(someAppId)
                .makePermanent()
                .withPriority(1000)
                .build();
        FlowRuleEvent event = new FlowRuleEvent(FlowRuleEvent.Type.RULE_ADDED,
                flowRule);

        internalFlowListener.event(event);

        // if we're not master of the device, we should not update
        verify(internalFlowListener, never()).updateCpStatus(any(), any(), any());
    }
}