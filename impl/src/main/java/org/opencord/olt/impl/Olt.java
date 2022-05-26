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
import org.onlab.util.KryoNamespace;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDevicePort;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.DiscoveredSubscriber;
import org.opencord.olt.OltDeviceServiceInterface;
import org.opencord.olt.OltFlowServiceInterface;
import org.opencord.olt.OltMeterServiceInterface;
import org.opencord.olt.ServiceKey;
import org.opencord.olt.FlowOperation;
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
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OltUtils.getPortName;
import static org.opencord.olt.impl.OltUtils.portWithName;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;

/**
 * OLT Application.
 */
@Component(immediate = true,
        property = {
                DEFAULT_BP_ID + ":String=" + DEFAULT_BP_ID_DEFAULT,
                DEFAULT_MCAST_SERVICE_NAME + ":String=" + DEFAULT_MCAST_SERVICE_NAME_DEFAULT,
                FLOW_PROCESSING_THREADS + ":Integer=" + FLOW_PROCESSING_THREADS_DEFAULT,
                FLOW_EXECUTOR_QUEUE_SIZE + ":Integer=" + FLOW_EXECUTOR_QUEUE_SIZE_DEFAULT,
                SUBSCRIBER_PROCESSING_THREADS + ":Integer=" + SUBSCRIBER_PROCESSING_THREADS_DEFAULT,
                REQUEUE_DELAY + ":Integer=" + REQUEUE_DELAY_DEFAULT
        })
public class Olt
        extends AbstractListenerManager<AccessDeviceEvent, AccessDeviceListener>
        implements AccessDeviceService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            bind = "bindSadisService",
            unbind = "unbindSadisService",
            policy = ReferencePolicy.DYNAMIC)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltDeviceServiceInterface oltDeviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltFlowServiceInterface oltFlowService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected OltMeterServiceInterface oltMeterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    protected ApplicationId appId;

    private static final String ONOS_OLT_SERVICE = "onos/olt-service";

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
    protected int flowExecutorQueueSize = FLOW_EXECUTOR_QUEUE_SIZE_DEFAULT;

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
    protected Map<ConnectPoint, LinkedBlockingQueue<DiscoveredSubscriber>> eventsQueues;

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;

    /**
     * Listener for OLT devices events.
     */
    protected OltDeviceListener deviceListener = new OltDeviceListener();
    protected ScheduledExecutorService discoveredSubscriberExecutor =
            Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                    "discovered-cp-%d", log));

    protected ScheduledExecutorService queueExecutor =
            Executors.newSingleThreadScheduledExecutor(groupedThreads("onos/olt",
                    "discovered-cp-restore-%d", log));

    /**
     * Executor used to defer flow provisioning to a different thread pool.
     */
    protected ExecutorService flowsExecutor;

    /**
     * Executor used to defer subscriber handling from API call to a different thread pool.
     */
    protected ExecutorService subscriberExecutor;

    private static final String APP_NAME = "org.opencord.olt";

    private final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock();
    private final Lock queueWriteLock = queueLock.writeLock();
    private final Lock queueReadLock = queueLock.readLock();

    @Activate
    protected void activate(ComponentContext context) {
        cfgService.registerProperties(getClass());

        modified(context);

        appId = coreService.registerApplication(APP_NAME);
        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(ConnectPoint.class)
                .register(DiscoveredSubscriber.class)
                .register(DiscoveredSubscriber.Status.class)
                .register(SubscriberAndDeviceInformation.class)
                .register(UniTagInformation.class)
                .register(LinkedBlockingQueue.class)
                .build();

        eventsQueues = storageService.<ConnectPoint, LinkedBlockingQueue<DiscoveredSubscriber>>consistentMapBuilder()
                .withName("volt-subscriber-queues")
                .withApplicationId(appId)
                .withSerializer(Serializer.using(serializer))
                .build().asJavaMap();

        deviceService.addListener(deviceListener);

        discoveredSubscriberExecutor.execute(this::processDiscoveredSubscribers);
        eventDispatcher.addSink(AccessDeviceEvent.class, listenerRegistry);
        log.info("Started");

        deviceListener.handleExistingPorts();
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        cfgService.unregisterProperties(getClass(), false);
        discoveredSubscriberExecutor.shutdown();
        deviceService.removeListener(deviceListener);
        flowsExecutor.shutdown();
        subscriberExecutor.shutdown();
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
            int oldFlowProcessingThreads = flowProcessingThreads;
            flowProcessingThreads = isNullOrEmpty(flowThreads) ?
                    oldFlowProcessingThreads : Integer.parseInt(flowThreads.trim());

            String executorQueueSize = get(properties, FLOW_EXECUTOR_QUEUE_SIZE);
            int oldExecutorQueueSize = flowExecutorQueueSize;
            flowExecutorQueueSize = isNullOrEmpty(executorQueueSize) ?
                    oldExecutorQueueSize : Integer.parseInt(executorQueueSize.trim());

            if (flowsExecutor == null || oldFlowProcessingThreads != flowProcessingThreads
                    || oldExecutorQueueSize != flowExecutorQueueSize) {
                if (flowsExecutor != null) {
                    flowsExecutor.shutdown();
                }

                flowsExecutor = new ThreadPoolExecutor(0, flowProcessingThreads, 30,
                        TimeUnit.SECONDS, new ThreadPoolQueue(flowExecutorQueueSize),
                        new ThreadPoolExecutor.DiscardPolicy());
            }

            String subscriberThreads = get(properties, SUBSCRIBER_PROCESSING_THREADS);
            int oldSubscriberProcessingThreads = subscriberProcessingThreads;
            subscriberProcessingThreads = isNullOrEmpty(subscriberThreads) ?
                    oldSubscriberProcessingThreads : Integer.parseInt(subscriberThreads.trim());

            if (subscriberExecutor == null || oldSubscriberProcessingThreads != subscriberProcessingThreads) {
                if (subscriberExecutor != null) {
                    subscriberExecutor.shutdown();
                }
                subscriberExecutor = Executors.newFixedThreadPool(subscriberProcessingThreads,
                        groupedThreads(ONOS_OLT_SERVICE,
                                "subscriber-installer-%d"));
            }

            String queueDelay = get(properties, REQUEUE_DELAY);
            requeueDelay = isNullOrEmpty(queueDelay) ?
                    REQUEUE_DELAY_DEFAULT : Integer.parseInt(queueDelay.trim());
        }
        log.info("Modified. Values = {}: {}, {}:{}, {}:{}," +
                        "{}:{}, {}:{}, {}:{}",
                DEFAULT_BP_ID, defaultBpId,
                DEFAULT_MCAST_SERVICE_NAME, multicastServiceName,
                FLOW_PROCESSING_THREADS, flowProcessingThreads,
                FLOW_EXECUTOR_QUEUE_SIZE, flowExecutorQueueSize,
                SUBSCRIBER_PROCESSING_THREADS, subscriberProcessingThreads,
                REQUEUE_DELAY, requeueDelay);
    }


    @Override
    public boolean provisionSubscriber(ConnectPoint cp) {
        subscriberExecutor.submit(() -> {
            Device device = deviceService.getDevice(cp.deviceId());
            Port port = deviceService.getPort(device.id(), cp.port());
            AccessDevicePort accessDevicePort = new AccessDevicePort(port);

            if (oltDeviceService.isNniPort(device, port.number())) {
                log.warn("will not provision a subscriber on the NNI {}", accessDevicePort);
                return false;
            }

            log.info("Provisioning subscriber on {}", accessDevicePort);

            if (oltFlowService.isSubscriberServiceProvisioned(accessDevicePort)) {
                log.error("Subscriber on {} is already provisioned", accessDevicePort);
                return false;
            }

            SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
            if (si == null) {
                log.error("Subscriber information not found in sadis for port {}", accessDevicePort);
                return false;
            }
            DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                    DiscoveredSubscriber.Status.ADDED, true, si);

            // NOTE we need to keep a list of the subscribers that are provisioned on a port,
            // regardless of the flow status
            si.uniTagList().forEach(uti -> {
                ServiceKey sk = new ServiceKey(accessDevicePort, uti);
                oltFlowService.updateProvisionedSubscriberStatus(sk, true);
            });

            addSubscriberToQueue(sub);
            return true;
        });
        //NOTE this only means we have taken the request in, nothing more.
        return true;
    }

    @Override
    public boolean removeSubscriber(ConnectPoint cp) {
        subscriberExecutor.submit(() -> {
            Device device = deviceService.getDevice(DeviceId.deviceId(cp.deviceId().toString()));
            Port port = deviceService.getPort(device.id(), cp.port());
            AccessDevicePort accessDevicePort = new AccessDevicePort(port);

            if (oltDeviceService.isNniPort(device, port.number())) {
                log.warn("will not un-provision a subscriber on the NNI {}",
                        accessDevicePort);
                return false;
            }

            log.info("Un-provisioning subscriber on {}", accessDevicePort);

            if (!oltFlowService.isSubscriberServiceProvisioned(accessDevicePort)) {
                log.error("Subscriber on {} is not provisioned", accessDevicePort);
                return false;
            }

            SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
            if (si == null) {
                log.error("Subscriber information not found in sadis for port {}",
                        accessDevicePort);
                // NOTE that we are returning true so that the subscriber is removed from the queue
                // and we can move on provisioning others
                return false;
            }
            DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                    DiscoveredSubscriber.Status.ADMIN_REMOVED, true, si);

            // NOTE we need to keep a list of the subscribers that are provisioned on a port,
            // regardless of the flow status
            si.uniTagList().forEach(uti -> {
                ServiceKey sk = new ServiceKey(accessDevicePort, uti);
                oltFlowService.updateProvisionedSubscriberStatus(sk, false);
            });

            addSubscriberToQueue(sub);
            return true;
        });
        //NOTE this only means we have taken the request in, nothing more.
        return true;
    }

    @Override
    public boolean provisionSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId) {
        log.debug("Provisioning subscriber on {}, with cTag {}, stag {}, tpId {}",
                cp, cTag, sTag, tpId);
        Device device = deviceService.getDevice(cp.deviceId());
        Port port = deviceService.getPort(device.id(), cp.port());
        AccessDevicePort accessDevicePort = new AccessDevicePort(port);

        if (oltDeviceService.isNniPort(device, port.number())) {
            log.warn("will not provision a subscriber on the NNI {}", accessDevicePort);
            return false;
        }

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        UniTagInformation specificService = getUniTagInformation(getPortName(port), cTag, sTag, tpId);
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
        ServiceKey sk = new ServiceKey(accessDevicePort, specificService);
        if (oltFlowService.isSubscriberServiceProvisioned(sk)) {
            log.error("Subscriber on {} is already provisioned", sk);
            return false;
        }
        oltFlowService.updateProvisionedSubscriberStatus(sk, true);

        addSubscriberToQueue(sub);
        return true;
    }

    @Override
    public boolean removeSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId) {
        log.debug("Un-provisioning subscriber on {} with cTag {}, stag {}, tpId {}",
                cp, cTag, sTag, tpId);
        Device device = deviceService.getDevice(cp.deviceId());
        Port port = deviceService.getPort(device.id(), cp.port());
        AccessDevicePort accessDevicePort = new AccessDevicePort(port);

        if (oltDeviceService.isNniPort(device, port.number())) {
            log.warn("will not un-provision a subscriber on the NNI {}",
                    accessDevicePort);
            return false;
        }

        SubscriberAndDeviceInformation si = new SubscriberAndDeviceInformation();
        UniTagInformation specificService = getUniTagInformation(getPortName(port), cTag, sTag, tpId);
        if (specificService == null) {
            log.error("Can't find Information for subscriber on {}, with cTag {}, " +
                    "stag {}, tpId {}", cp, cTag, sTag, tpId);
            return false;
        }
        List<UniTagInformation> uniTagInformationList = new LinkedList<>();
        uniTagInformationList.add(specificService);
        si.setUniTagList(uniTagInformationList);
        DiscoveredSubscriber sub = new DiscoveredSubscriber(device, port,
                DiscoveredSubscriber.Status.ADMIN_REMOVED, true, si);

        // NOTE we need to keep a list of the subscribers that are provisioned on a port,
        // regardless of the flow status
        ServiceKey sk = new ServiceKey(accessDevicePort, specificService);
        if (!oltFlowService.isSubscriberServiceProvisioned(sk)) {
            log.error("Subscriber on {} is not provisioned", sk);
            return false;
        }
        oltFlowService.updateProvisionedSubscriberStatus(sk, false);

        addSubscriberToQueue(sub);
        return true;
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
     * Finds the connect-point to which a subscriber is connected.
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
                    log.debug("Found on {}", portWithName(p));
                    return new ConnectPoint(d.id(), p.number());
                }
            }
        }
        return null;
    }

    protected void processDiscoveredSubscribers() {

            log.info("Started processDiscoveredSubscribers loop");
            while (true) {
                Set<ConnectPoint> discoveredCps;
                try {
                    queueReadLock.lock();
                    discoveredCps = new HashSet<>(eventsQueues.keySet());
                } catch (Exception e) {
                    log.error("Cannot read keys from queue map", e);
                    continue;
                } finally {
                    queueReadLock.unlock();
                }

                discoveredCps.forEach(cp -> {
                    LinkedBlockingQueue<DiscoveredSubscriber> eventsQueue;

                    try {
                        queueReadLock.lock();
                        eventsQueue = eventsQueues.get(cp);
                    } catch (Exception e) {
                        log.error("Cannot get key from queue map", e);
                        return;
                    } finally {
                        queueReadLock.unlock();
                    }

                    if (!oltDeviceService.isLocalLeader(cp.deviceId())) {
                        // if we're not local leader for this device, ignore this queue
                        if (log.isTraceEnabled()) {
                            log.trace("Ignoring queue on CP {} as not master of the device", cp);
                        }
                        return;
                    }

                    try {
                        flowsExecutor.execute(() -> {
                        if (!eventsQueue.isEmpty()) {
                            // we do not remove the event from the queue until it has been processed
                            // in that way we guarantee that events are processed in order
                            DiscoveredSubscriber sub = eventsQueue.peek();
                            if (sub == null) {
                                // the queue is empty
                                return;
                            }

                            if (log.isTraceEnabled()) {
                                log.trace("Processing subscriber {} on port {} with status {}, has subscriber {}",
                                     sub, portWithName(sub.port), sub.status, sub.hasSubscriber);
                            }

                            if (sub.hasSubscriber) {
                                // this is a provision subscriber call
                                if (oltFlowService.handleSubscriberFlows(sub, defaultBpId, multicastServiceName)) {
                                    removeSubscriberFromQueue(sub);
                                }
                            } else {
                                // this is a port event (ENABLED/DISABLED)
                                // means no subscriber was provisioned on that port

                                if (!deviceService.isAvailable(sub.device.id()) ||
                                        deviceService.getPort(sub.device.id(), sub.port.number()) == null) {
                                    // If the device is not connected or the port is not available do nothing
                                    // This can happen when we disable and then immediately delete the device,
                                    // the queue is populated but the meters and flows are already gone
                                    // thus there is nothing left to do
                                    return;
                                }

                                if (oltFlowService.handleBasicPortFlows(sub, defaultBpId, defaultBpId)) {
                                    if (log.isTraceEnabled()) {
                                        log.trace("Processing of port {} completed",
                                                  portWithName(sub.port));
                                    }
                                    removeSubscriberFromQueue(sub);
                                } else {
                                    log.debug("Not handling basic port flows " +
                                                      "for {}, leaving in the queue",
                                              portWithName(sub.port));
                                }
                            }
                        }
                    });
                    } catch (Exception e) {
                        log.error("Exception processing subscriber", e);
                    }
                });

                try {
                    TimeUnit.MILLISECONDS.sleep(requeueDelay);
                } catch (InterruptedException e) {
                    log.debug("Interrupted while waiting to requeue", e);
                }
            }
    }


    /**
     * Checks the subscriber uni tag list and find the uni tag information.
     * using the pon c tag, pon s tag and the technology profile id
     * May return Optional<null>
     *
     * @param portName  port of the subscriber
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

        UniTagInformation service = OltUtils.getUniTagInformation(subInfo, innerVlan, outerVlan, tpId);

        if (service == null) {
            // Try again after invalidating cache for the particular port name.
            subsService.invalidateId(portName);
            subInfo = subsService.get(portName);
            service = OltUtils.getUniTagInformation(subInfo, innerVlan, outerVlan, tpId);
        }

        if (service == null) {
            log.warn("SADIS doesn't include the service with ponCtag {} ponStag {} and tpId {} on {}",
                    innerVlan, outerVlan, tpId, portName);
            return null;
        }

        return service;
    }

    protected void bindSadisService(SadisService service) {
        sadisService = service;
        subsService = sadisService.getSubscriberInfoService();

        log.info("Sadis-service binds to onos.");
    }

    protected void unbindSadisService(SadisService service) {
        deviceListener = null;
        sadisService = null;
        subsService = null;
        log.info("Sadis-service unbinds from onos.");
    }

    protected void addSubscriberToQueue(DiscoveredSubscriber sub) {
        try {
            ConnectPoint cp = new ConnectPoint(sub.device.id(), sub.port.number());

            try {
                queueWriteLock.lock();
                eventsQueues.compute(cp, (subcp, queue) -> {
                    queue = queue == null ? new LinkedBlockingQueue<>() : queue;
                    log.info("Adding subscriber {} to queue: {} with existing {}",
                             sub, portWithName(sub.port), queue);
                    queue.add(sub);
                    return queue;
                });
            } catch (UnsupportedOperationException | ClassCastException |
                    NullPointerException | IllegalArgumentException e) {
                log.error("Cannot add subscriber {} to queue: {}", portWithName(sub.port), e.getMessage());
            } finally {
                queueWriteLock.unlock();
            }
        } catch (Exception e) {
            log.error("Can't add {} to queue", sub, e);
        }
    }

    protected void removeSubscriberFromQueue(DiscoveredSubscriber sub) {
        ConnectPoint cp = new ConnectPoint(sub.device.id(), sub.port.number());
        try {
            queueWriteLock.lock();
            eventsQueues.compute(cp, (subcp, queue) -> {
                if (log.isTraceEnabled()) {
                    log.trace("Removing subscriber {} from queue : {} " +
                                      "with existing {}", sub,
                              portWithName(sub.port), queue);
                }
                if (queue == null) {
                    log.warn("Cannot find queue for connectPoint {}", cp);
                    return queue;
                }
                boolean removed = queue.remove(sub);
                if (!removed) {
                    log.warn("Subscriber {} has not been removed from queue, " +
                                     "is it still there? {}", sub, queue);
                    return queue;
                } else {
                    log.debug("Subscriber {} has been removed from the queue {}",
                              sub, queue);
                }

                return queue;
            });
        } catch (UnsupportedOperationException | ClassCastException |
                NullPointerException | IllegalArgumentException e) {
            log.error("Cannot remove subscriber {} from queue: {}", sub, e.getMessage());
        } finally {
            queueWriteLock.unlock();
        }
    }

    protected class OltDeviceListener
            implements DeviceListener {

        private final Logger log = LoggerFactory.getLogger(getClass());
        protected ExecutorService eventExecutor;

        /**
         * Builds the listener with all the proper services and information needed.
         */
        public OltDeviceListener() {
            this.eventExecutor = Executors.newFixedThreadPool(flowProcessingThreads,
                    groupedThreads("onos/olt-device-listener-event", "event-%d", log));
        }

        public void deactivate() {
            this.eventExecutor.shutdown();
        }

        @Override
        public void event(DeviceEvent event) {
            if (log.isTraceEnabled()) {
                log.trace("OltListener receives event {} for: {}/{}", event.type(), event.subject().id(),
                         event.port() != null ? event.port().number() : null);
            }
            eventExecutor.execute(() -> {
                if (log.isTraceEnabled()) {
                    log.trace("OltListener Executor receives event {} for: {}/{}",
                             event.type(), event.subject().id(),
                              event.port() != null ? event.port().number() : null);
                }
                boolean isOlt = oltDeviceService.isOlt(event.subject());
                DeviceId deviceId = event.subject().id();
                switch (event.type()) {
                    case PORT_STATS_UPDATED:
                    case DEVICE_ADDED:
                        return;
                    case PORT_ADDED:
                    case PORT_REMOVED:
                        if (!isOlt) {
                            log.trace("Ignoring event {}, this is not an OLT device", deviceId);
                            return;
                        }
                        if (!oltDeviceService.isLocalLeader(deviceId)) {
                            log.trace("Device {} is not local to this node", deviceId);
                            return;
                        }
                        // port added, updated and removed are treated in the same way as we only care whether the port
                        // is enabled or not
                        handleOltPort(event.type(), event.subject(), event.port());
                        return;
                    case PORT_UPDATED:
                        if (!isOlt) {
                            log.trace("Ignoring event {}, this is not an OLT device", deviceId);
                            return;
                        }
                        // port updated are handled only when the device is available, makes not sense otherwise.
                        // this also solves an issue with port events and device disconnection events handled
                        // in sparse order causing failures in the ofagent disconnect test
                        // (see https://jira.opencord.org/browse/VOL-4669)
                        if (!deviceService.isAvailable(deviceId)) {
                            log.debug("Ignoring port event {} on {} as it is disconnected", event, deviceId);
                            return;
                        }
                        if (!oltDeviceService.isLocalLeader(deviceId)) {
                            log.trace("Device {} is not local to this node", deviceId);
                            return;
                        }
                        handleOltPort(event.type(), event.subject(), event.port());
                        return;
                    case DEVICE_AVAILABILITY_CHANGED:
                        if (!isOlt) {
                            log.trace("Ignoring event {}, this is not an OLT device", deviceId);
                            return;
                        }
                        if (deviceService.isAvailable(deviceId)) {
                            if (!oltDeviceService.isLocalLeader(deviceId)) {
                                if (log.isTraceEnabled()) {
                                    log.trace("Device {} is not local to this node, not handling available device",
                                            deviceId);
                                }
                            } else {
                                log.info("Handling available device: {}", deviceId);
                                handleExistingPorts();
                            }
                        } else if (!deviceService.isAvailable(deviceId) && deviceService.getPorts(deviceId).isEmpty()) {
                            // NOTE that upon disconnection there is no mastership on the device,
                            // and we should anyway clear the local cache of the flows/meters across instances.
                            // We're only clearing the device if there are no available ports,
                            // otherwise we assume it's a temporary disconnection
                            log.info("Device {} availability changed to false and ports are empty, " +
                                    "purging meters and flows", deviceId);
                            //NOTE all the instances will call these methods
                            oltFlowService.purgeDeviceFlows(deviceId);
                            oltMeterService.purgeDeviceMeters(deviceId);
                            // cpStatus is a distributed map, thus only master will update it.
                            if (oltDeviceService.isLocalLeader(deviceId)) {
                                log.debug("Master, clearing cp status for {}", deviceId);
                                clearQueueForDevice(deviceId);
                            }
                        } else {
                            log.info("Device {} availability changed to false, but ports are still available, " +
                                            "assuming temporary disconnection.",
                                    deviceId);
                            if (log.isTraceEnabled()) {
                                log.trace("Available ports: {}",  deviceService.getPorts(deviceId));
                            }
                        }
                        return;
                    case DEVICE_REMOVED:
                        if (!isOlt) {
                            log.trace("Ignoring event {}, this is not an OLT device", deviceId);
                            return;
                        }
                        log.info("Device {} Removed, purging meters and flows", deviceId);
                        oltFlowService.purgeDeviceFlows(deviceId);
                        oltMeterService.purgeDeviceMeters(deviceId);
                        if (oltDeviceService.isLocalLeader(deviceId)) {
                            log.debug("Master, clearing cp status for {}", deviceId);
                            clearQueueForDevice(deviceId);
                        }
                        return;
                    default:
                        if (log.isTraceEnabled()) {
                            log.trace("Not handling event: {}, ", event);
                        }
                }
            });
        }

        protected void clearQueueForDevice(DeviceId devId) {
            try {
                queueWriteLock.lock();
                Iterator<Map.Entry<ConnectPoint, LinkedBlockingQueue<DiscoveredSubscriber>>> iter =
                        eventsQueues.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<ConnectPoint, LinkedBlockingQueue<DiscoveredSubscriber>> entry = iter.next();
                    if (entry.getKey().deviceId().equals(devId)) {
                        eventsQueues.remove(entry.getKey());
                        log.debug("Removing key from queue {}", entry.getKey());
                    }
                }
            } finally {
                queueWriteLock.unlock();
            }
        }

        protected void handleOltPort(DeviceEvent.Type type, Device device, Port port) {
            log.info("OltDeviceListener receives event {} for port {} with status {} on device {}", type,
                    portWithName(port), port.isEnabled() ? "ENABLED" : "DISABLED", device.id());
            boolean isNni = oltDeviceService.isNniPort(device, port.number());

            if (!isNni && type == DeviceEvent.Type.PORT_ADDED) {
                log.debug("Ignoring PORT_ADD on UNI port {}", portWithName(port));
                return;
            }

            if (port.isEnabled()) {
                if (isNni) {
                    FlowOperation action = FlowOperation.ADD;
                    // NOTE the NNI is only disabled if the OLT shuts down (reboot or failure).
                    // In that case the flows are purged anyway, so there's no need to deal with them,
                    // it would actually be counter-productive as the openflow connection is severed and they won't
                    // be correctly processed
                    if (type == DeviceEvent.Type.PORT_REMOVED) {
                        log.debug("NNI port went down, " +
                                "ignoring event as flows will be removed in the generic device cleanup");
                        return;
                    }
                    oltFlowService.handleNniFlows(device, port, action);
                } else {
                    post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, device.id(), port));
                    // NOTE if the subscriber was previously provisioned,
                    // then add it back to the queue to be re-provisioned
                    boolean provisionSubscriber = oltFlowService.
                            isSubscriberServiceProvisioned(new AccessDevicePort(port));
                    SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
                    if (si == null) {
                        //NOTE this should not happen given that the subscriber was provisioned before
                        log.error("Subscriber information not found in sadis for port {}",
                                portWithName(port));
                        return;
                    }

                    DiscoveredSubscriber.Status status = DiscoveredSubscriber.Status.ADDED;
                    if (type == DeviceEvent.Type.PORT_REMOVED) {
                        status = DiscoveredSubscriber.Status.REMOVED;
                    }

                    DiscoveredSubscriber sub =
                            new DiscoveredSubscriber(device, port,
                                    status, provisionSubscriber, si);
                    addSubscriberToQueue(sub);
                }
            } else {
                if (isNni) {
                    // NOTE the NNI is only disabled if the OLT shuts down (reboot or failure).
                    // In that case the flows are purged anyway, so there's no need to deal with them,
                    // it would actually be counter-productive as the openflow connection is severed and they won't
                    // be correctly processed
                    log.debug("NNI port went down, " +
                            "ignoring event as flows will be removed in the generic device cleanup");
                } else {
                    post(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, device.id(), port));
                    // NOTE we are assuming that if a subscriber has default eapol
                    // it does not have subscriber flows
                    if (oltFlowService.hasDefaultEapol(port)) {
                        SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
                        if (si == null) {
                            //NOTE this should not happen given that the subscriber was provisioned before
                            log.error("Subscriber information not found in sadis for port {}",
                                    portWithName(port));
                            return;
                        }
                        DiscoveredSubscriber sub =
                                new DiscoveredSubscriber(device, port,
                                        DiscoveredSubscriber.Status.REMOVED, false, si);

                        addSubscriberToQueue(sub);

                    } else if (oltFlowService.isSubscriberServiceProvisioned(new AccessDevicePort(port))) {
                        SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
                        if (si == null) {
                            //NOTE this should not happen given that the subscriber was provisioned before
                            log.error("Subscriber information not found in sadis for port {}",
                                    portWithName(port));
                            return;
                        }
                        DiscoveredSubscriber sub =
                                new DiscoveredSubscriber(device, port,
                                        DiscoveredSubscriber.Status.REMOVED, true, si);
                        addSubscriberToQueue(sub);
                    }
                }
            }
        }

        /**
         * This method is invoked on app activation in order to deal
         * with devices and ports that are already existing in the system
         * and thus won't trigger an event.
         * It is also needed on instance reboot and device reconnect
         */
        protected void handleExistingPorts() {
            Iterable<DeviceId> devices = getConnectedOlts();
            for (DeviceId deviceId : devices) {
                log.info("Handling existing OLT Ports for device {}", deviceId);
                if (oltDeviceService.isLocalLeader(deviceId)) {
                    List<Port> ports = deviceService.getPorts(deviceId);
                    for (Port p : ports) {
                        if (PortNumber.LOCAL.equals(p.number()) || !p.isEnabled()) {
                            continue;
                        }
                        Device device = deviceService.getDevice(deviceId);
                        deviceListener.handleOltPort(DeviceEvent.Type.PORT_UPDATED, device, p);
                    }
                }
            }
        }
    }

    protected final class ThreadPoolQueue extends ArrayBlockingQueue<Runnable> {

        public ThreadPoolQueue(int capacity) {
            super(capacity);
        }

        @Override
        public boolean offer(Runnable runnable) {
            if (runnable == null) {
                return false;
            }
            try {
                put(runnable);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }

    }
}
