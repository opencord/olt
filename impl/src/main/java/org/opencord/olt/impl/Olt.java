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

import org.onlab.packet.VlanId;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceStore;
import org.opencord.olt.OltService;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;

/**
 * OLT Application.
 */
@Component(immediate = true,
        property = {
                DEFAULT_BP_ID + ":String=" + DEFAULT_BP_ID_DEFAULT,
                DEFAULT_MCAST_SERVICE_NAME + ":String=" + DEFAULT_MCAST_SERVICE_NAME_DEFAULT,
                FLOW_PROCESSING_THREADS + ":Integer=" + FLOW_PROCESSING_THREADS_DEFAULT,
                SUBSCRIBER_PROCESSING_THREADS + ":Integer=" + SUBSCRIBER_PROCESSING_THREADS_DEFAULT,
                REQUEUE_DELAY + ":Integer=" + REQUEUE_DELAY_DEFAULT
        })
public class Olt implements OltService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceStore deviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltDeviceServiceInterface oltDeviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltFlowServiceInterface oltFlowService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltMeterServiceInterface oltMeterService;

    /**
     * Default bandwidth profile id that is used for authentication trap flows.
     **/
    protected String defaultBpId = DEFAULT_BP_ID_DEFAULT;

    /**
     * Default multicast service name.
     **/
    protected String multicastServiceName = DEFAULT_MCAST_SERVICE_NAME_DEFAULT;

    /**
     * Number of threads used to process flows.
     **/
    protected int flowProcessingThreads = FLOW_PROCESSING_THREADS_DEFAULT;

    /**
     * Number of threads used to process flows.
     **/
    protected int subscriberProcessingThreads = SUBSCRIBER_PROCESSING_THREADS_DEFAULT;

    /**
     * Delay in ms to put an event back in the queue, used to avoid retrying things to often if conditions are not met.
     **/
    protected int requeueDelay = REQUEUE_DELAY_DEFAULT;

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * A queue to asynchronously process events.
     */
    protected BlockingQueue<DiscoveredSubscriber> eventsQueue =
            new LinkedBlockingQueue<>();

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;

    /**
     * Listener for OLT devices events.
     */
    private OltDeviceListener deviceListener;
    protected ScheduledExecutorService discoveredSubscriberExecutor =
            Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                    "discovered-cp-%d", log));

    protected ScheduledExecutorService queueExecutor =
            Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                    "discovered-cp-restore-%d", log));

    /**
     * Executor used to defer flow provisioning to a different thread pool.
     */
    private ExecutorService flowsExecutor;

    /**
     * Executor used to defer subscriber handling from API call to a different thread pool.
     */
    private ExecutorService subscriberExecutor;

    @Activate
    protected void activate(ComponentContext context) {
        cfgService.registerProperties(getClass());
        modified(context);
        subsService = sadisService.getSubscriberInfoService();
        deviceListener = new OltDeviceListener(clusterService, mastershipService,
                leadershipService, deviceService, oltDeviceService, oltFlowService,
                oltMeterService, eventsQueue, subsService);
        deviceService.addListener(deviceListener);
        discoveredSubscriberExecutor.execute(this::processDiscoveredSubscribers);

        flowsExecutor = Executors.newFixedThreadPool(flowProcessingThreads,
                                                     groupedThreads("onos/olt-service",
                        "flows-installer-%d"));

        subscriberExecutor = Executors.newFixedThreadPool(subscriberProcessingThreads,
                                                     groupedThreads("onos/olt-service",
                                                                    "flows-installer-%d"));

        log.info("Started");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        discoveredSubscriberExecutor.shutdown();
        flowsExecutor.shutdown();
        deviceListener.deactivate();
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            String bpId = get(properties, DEFAULT_BP_ID);
            defaultBpId = isNullOrEmpty(bpId) ? defaultBpId : bpId;

            String mcastSN = get(properties, DEFAULT_MCAST_SERVICE_NAME);
            multicastServiceName = isNullOrEmpty(mcastSN) ? multicastServiceName : mcastSN;

            String flowThreads = get(properties, FLOW_PROCESSING_THREADS);
            flowProcessingThreads = isNullOrEmpty(flowThreads) ?
                    FLOW_PROCESSING_THREADS_DEFAULT : Integer.parseInt(flowThreads.trim());

            String subscriberThreads = get(properties, SUBSCRIBER_PROCESSING_THREADS);
            subscriberProcessingThreads = isNullOrEmpty(subscriberThreads) ?
                    SUBSCRIBER_PROCESSING_THREADS_DEFAULT : Integer.parseInt(subscriberThreads.trim());

            String queueDelay = get(properties, REQUEUE_DELAY);
            requeueDelay = isNullOrEmpty(queueDelay) ?
                    REQUEUE_DELAY_DEFAULT : Integer.parseInt(queueDelay.trim());
        }
        log.info("Modified. Values = {}: {}, {}: {}, " +
                         "{}:{}, {}:{}, {}:{}",
                 DEFAULT_BP_ID, defaultBpId,
                 DEFAULT_MCAST_SERVICE_NAME, multicastServiceName,
                 FLOW_PROCESSING_THREADS, flowProcessingThreads,
                 SUBSCRIBER_PROCESSING_THREADS, subscriberProcessingThreads,
                 REQUEUE_DELAY, requeueDelay);
    }


    @Override
    public boolean provisionSubscriber(ConnectPoint cp) {
        subscriberExecutor.submit(() -> {
            log.debug("Provisioning subscriber on {}", cp);
            Device device = deviceService.getDevice(cp.deviceId());
            Port port = deviceStore.getPort(device.id(), cp.port());

            if (oltDeviceService.isNniPort(device, port)) {
                log.warn("will not provision a subscriber on the NNI");
                return false;
            }

            if (oltFlowService.isSubscriberProvisioned(cp)) {
                log.error("Subscriber on {} is already provisioned", cp);
                return false;
            }

            String portName = port.annotations().value(AnnotationKeys.PORT_NAME);
            SubscriberAndDeviceInformation si = subsService.get(portName);
            if (si == null) {
                log.error("Subscriber information not found in sadis for port {}/{} ({})",
                          device.id(), port.number(), portName);
                return false;
            }
            DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                                                                DiscoveredSubscriber.Status.ADDED, true, si);

            // NOTE we need to keep a list of the subscribers that are provisioned on a port,
            // regardless of the flow status
            oltFlowService.updateProvisionedSubscriberStatus(cp, true);

            if (!eventsQueue.contains(sub)) {
                log.info("Adding subscriber to queue: {}/{} with status {} for provisioning",
                         sub.device.id(), sub.port.number(), sub.status);
                eventsQueue.add(sub);
                return true;
            } else {
                log.debug("Subscriber queue already contains subscriber {}, " +
                                  "not adding for provisioning", sub);
                return false;
            }
        });
        //NOTE this only means we have taken the request in, nothing more.
        return true;
    }

    @Override
    public boolean removeSubscriber(ConnectPoint cp) {
        subscriberExecutor.submit(() -> {
            log.debug("Un-provisioning subscriber on {}", cp);
            Device device = deviceService.getDevice(DeviceId.deviceId(cp.deviceId().toString()));
            Port port = deviceStore.getPort(device.id(), cp.port());

            if (oltDeviceService.isNniPort(device, port)) {
                log.warn("will not un-provision a subscriber on the NNI");
                return false;
            }

            if (!oltFlowService.isSubscriberProvisioned(cp)) {
                log.error("Subscriber on {} is not provisioned", cp);
                return false;
            }

            String portName = port.annotations().value(AnnotationKeys.PORT_NAME);
            SubscriberAndDeviceInformation si = subsService.get(portName);
            if (si == null) {
                log.error("Subscriber information not found in sadis for port {}/{} ({})",
                          device.id(), port.number(), portName);
                // NOTE that we are returning true so that the subscriber is removed from the queue
                // and we can move on provisioning others
                return false;
            }
            DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                                                                DiscoveredSubscriber.Status.REMOVED, true, si);

            // NOTE we need to keep a list of the subscribers that are provisioned on a port,
            // regardless of the flow status
            oltFlowService.updateProvisionedSubscriberStatus(cp, false);

            if (!eventsQueue.contains(sub)) {
                log.info("Adding subscriber to queue: {}/{} with status {} for removal",
                         sub.device.id(), sub.port.number(), sub.status);
                eventsQueue.add(sub);
                return true;
            } else {
                log.debug("Subscriber Queue already contains subscriber {}, " +
                                  "not adding for removal", sub);
                return false;
            }
        });
        //NOTE this only means we have taken the request in, nothing more.
        return true;
    }

    @Override
    public boolean provisionSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId) {
        log.debug("Provisioning subscriber on {}, with cTag {}, stag {}, tpId {}",
                  cp, cTag, sTag, tpId);
        Device device = deviceService.getDevice(cp.deviceId());
        Port port = deviceStore.getPort(device.id(), cp.port());

        if (oltDeviceService.isNniPort(device, port)) {
            log.warn("will not provision a subscriber on the NNI");
            return false;
        }

        if (oltFlowService.isSubscriberProvisioned(cp)) {
            log.error("Subscriber on {} is already provisioned", cp);
            return false;
        }

        String portName = port.annotations().value(AnnotationKeys.PORT_NAME);
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        UniTagInformation specificService = getUniTagInformation(portName, cTag, sTag, tpId);
        if (specificService == null) {
            log.error("Can't find Information for subscriber on {}, with cTag {}, " +
                              "stag {}, tpId {}", cp, cTag, sTag, tpId);
            return false;
        }
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        uniTagInformationList.add(specificService);
        si.setUniTagList(uniTagInformationList);
        DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                                                            DiscoveredSubscriber.Status.ADDED, true, si);

        // NOTE we need to keep a list of the subscribers that are provisioned on a port,
        // regardless of the flow status
        oltFlowService.updateProvisionedSubscriberStatus(cp, true);

        if (!eventsQueue.contains(sub)) {
            log.info("Adding subscriber to queue: {}/{} with status {} for provisioning",
                     sub.device.id(), sub.port.number(), sub.status);
            eventsQueue.add(sub);
            return true;
        } else {
            log.debug("Subscriber queue already contains subscriber {}, " +
                              "not adding for provisioning", sub);
            return false;
        }
    }

    @Override
    public boolean removeSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId) {
        log.debug("Un-provisioning subscriber on {} with cTag {}, stag {}, tpId {}",
                  cp, cTag, sTag, tpId);
        Device device = deviceService.getDevice(DeviceId.deviceId(cp.deviceId().toString()));
        Port port = deviceStore.getPort(device.id(), cp.port());
        String portName = port.annotations().value(AnnotationKeys.PORT_NAME);

        if (oltDeviceService.isNniPort(device, port)) {
            log.warn("will not un-provision a subscriber on the NNI");
            return false;
        }

        if (!oltFlowService.isSubscriberProvisioned(cp)) {
            log.error("Subscriber on {} is not provisioned", cp);
            return false;
        }
        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        UniTagInformation specificService = getUniTagInformation(portName, cTag, sTag, tpId);
        if (specificService == null) {
            log.error("Can't find Information for subscriber on {}, with cTag {}, " +
                              "stag {}, tpId {}", cp, cTag, sTag, tpId);
            return false;
        }
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        uniTagInformationList.add(specificService);
        si.setUniTagList(uniTagInformationList);
        DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                                                            DiscoveredSubscriber.Status.ADDED, true, si);

        // NOTE we need to keep a list of the subscribers that are provisioned on a port,
        // regardless of the flow status
        oltFlowService.updateProvisionedSubscriberStatus(cp, false);

        if (!eventsQueue.contains(sub)) {
            log.info("Adding subscriber to queue: {}/{} with status {} for removal",
                     sub.device.id(), sub.port.number(), sub.status);
            eventsQueue.add(sub);
            return true;
        } else {
            log.debug("Subscriber Queue already contains subscriber {}, " +
                              "not adding for removal", sub);
            return false;
        }
    }

    @Override
    public List<DeviceId> getConnectedOlts() {
        List<DeviceId> olts = new ArrayList<>();
        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            if (oltDeviceService.isOlt(d)) {
                // So this is indeed an OLT device
                olts.add(d.id());
            }
        }
        return olts;
    }

    /**
     * Finds the connect point to which a subscriber is connected.
     *
     * @param id The id of the subscriber, this is the same ID as in Sadis
     * @return Subscribers ConnectPoint if found else null
     */
    @Override
    public ConnectPoint findSubscriberConnectPoint(String id) {

        Iterable<Device> devices = deviceService.getDevices();
        for (Device d : devices) {
            for (Port p : deviceService.getPorts(d.id())) {
                log.trace("Comparing {} with {}", p.annotations().value(AnnotationKeys.PORT_NAME), id);
                if (p.annotations().value(AnnotationKeys.PORT_NAME).equals(id)) {
                    log.debug("Found on device {} port {}", d.id(), p.number());
                    return new ConnectPoint(d.id(), p.number());
                }
            }
        }
        return null;
    }

    private void processDiscoveredSubscribers() {
        log.info("Started processDiscoveredSubscribers loop");
        while (true) {
            if (!eventsQueue.isEmpty()) {
                DiscoveredSubscriber sub = eventsQueue.poll();
                if (sub == null) {
                    // the queue is empty
                    continue;
                }
                if (log.isTraceEnabled()) {
                    log.debug("Processing subscriber on port {}/{} with status {}",
                              sub.device.id(), sub.port.number(), sub.status);
                }

                if (sub.hasSubscriber) {
                    // this is a provision subscriber call
                    flowsExecutor.execute(() -> {
                        if (!oltFlowService.handleSubscriberFlows(sub, defaultBpId, multicastServiceName)) {
                            if (log.isTraceEnabled()) {
                                log.trace("Provisioning of subscriber on {}/{} ({}) postponed",
                                          sub.device.id(), sub.port.number(), sub.portName());
                            }
                            addBackInQueue(eventsQueue, sub);
                        }
                    });
                } else {
                    // this is a port event (ENABLED/DISABLED)
                    // means no subscriber was provisioned on that port


                    if (!deviceService.isAvailable(sub.device.id()) ||
                            deviceService.getPort(sub.device.id(), sub.port.number()) == null) {
                        // If the device is not connected or the port is not available do nothig
                        // This can happen when we disable and then immediately delete the device,
                        // the queue is populated but the meters and flows are already gone
                        // thus there is nothing left to do
                        continue;
                    }

                    flowsExecutor.execute(() -> {
                        if (!oltFlowService.handleBasicPortFlows(sub, defaultBpId, defaultBpId)) {
                            if (log.isTraceEnabled()) {
                                log.trace("Processing of port {}/{} postponed",
                                          sub.device.id(), sub.port.number());
                            }
                            addBackInQueue(eventsQueue, sub);
                        }
                    });
                }
            }
        }
    }

    /**
     * Adds an event back into the queue after 500 milliseconds.
     * We add the delay as if something failed it's because some condition is not met yet,
     * thus there's no reason to immediately retry.
     *
     * TODO: the addBackInQueue will have to become smarter and decide wether or not we need to put the subscriber back.
     * see TST conversation and mailing list discussion titled: OLT App rewrite
     *
     * @param queue
     * @param subscriber
     */
    private void addBackInQueue(BlockingQueue queue, DiscoveredSubscriber subscriber) {
        queueExecutor.schedule(() -> {
            queue.add(subscriber);
        }, requeueDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Checks the subscriber uni tag list and find the uni tag information.
     * using the pon c tag, pon s tag and the technology profile id
     * May return Optional<null>
     *
     * @param portName        port of the subscriber
     * @param innerVlan pon c tag
     * @param outerVlan pon s tag
     * @param tpId      the technology profile id
     * @return the found uni tag information
     */
    private UniTagInformation getUniTagInformation(String portName, VlanId innerVlan,
                                                             VlanId outerVlan, int tpId) {
        log.debug("Getting uni tag information for {}, innerVlan: {}, outerVlan: {}, tpId: {}",
                  portName, innerVlan, outerVlan, tpId);
        SubscriberAndDeviceInformation subInfo = subsService.get(portName);
        if (subInfo == null) {
            log.warn("Subscriber information doesn't exist for {}", portName);
            return null;
        }

        List<UniTagInformation> uniTagList = subInfo.uniTagList();
        if (uniTagList == null) {
            log.warn("Uni tag list is not found for the subscriber {} on {}", subInfo.id(), portName);
            return null;
        }

        UniTagInformation service = null;
        for (UniTagInformation tagInfo : subInfo.uniTagList()) {
            if (innerVlan.equals(tagInfo.getPonCTag()) && outerVlan.equals(tagInfo.getPonSTag())
                    && tpId == tagInfo.getTechnologyProfileId()) {
                service = tagInfo;
                break;
            }
        }

        if (service == null) {
            log.warn("SADIS doesn't include the service with ponCtag {} ponStag {} and tpId {} on {}",
                     innerVlan, outerVlan, tpId, portName);
            return null;
        }

        return service;
    }

}
