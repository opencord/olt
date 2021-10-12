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

import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDevicePort;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OltUtils.getPortName;
import static org.opencord.olt.impl.OltUtils.portWithName;

/**
 * Device listener for actions related to OLT.
 */
public class OltDeviceListener
        implements DeviceListener {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected ClusterService clusterService;

    protected MastershipService mastershipService;

    protected LeadershipService leadershipService;

    protected DeviceService deviceService;

    protected OltDeviceServiceInterface oltDeviceService;

    protected OltFlowServiceInterface oltFlowService;

    protected OltMeterServiceInterface oltMeterService;

    private BlockingQueue<DiscoveredSubscriber> discoveredSubscribersQueue;

    protected ExecutorService eventExecutor;

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;

    protected Consumer<AccessDeviceEvent> post;

    /**
     * Builds the listener with all the proper services and information needed.
     * @param clusterService the cluster service
     * @param mastershipService the mastership service
     * @param leadershipService the leadership service
     * @param deviceService the device service
     * @param oltDeviceService the olt device service
     * @param oltFlowService the olt flow service
     * @param oltMeterService the olt meter service
     * @param discoveredSubscribersQueue the discovered subscribers queue
     * @param subsService the subscriber service
     * @param post a callable function (used to emit events)
     */
    public OltDeviceListener(ClusterService clusterService, MastershipService mastershipService,
                             LeadershipService leadershipService, DeviceService deviceService,
                             OltDeviceServiceInterface oltDeviceService, OltFlowServiceInterface oltFlowService,
                             OltMeterServiceInterface oltMeterService,
                             BlockingQueue<DiscoveredSubscriber> discoveredSubscribersQueue,
                             BaseInformationService<SubscriberAndDeviceInformation> subsService,
                             Consumer<AccessDeviceEvent> post) {
        this.clusterService = clusterService;
        this.mastershipService = mastershipService;
        this.leadershipService = leadershipService;
        this.deviceService = deviceService;
        this.oltDeviceService = oltDeviceService;
        this.oltFlowService = oltFlowService;
        this.oltMeterService = oltMeterService;
        this.discoveredSubscribersQueue = discoveredSubscribersQueue;
        this.subsService = subsService;
        this.post = post;
        this.eventExecutor = Executors.newSingleThreadExecutor(
                groupedThreads("onos/olt-device-listener-event", "event-%d", log));
    }

    public void deactivate() {
        this.eventExecutor.shutdown();
    }

    @Override
    public void event(DeviceEvent event) {
        eventExecutor.execute(() -> {
            // TODO handle events for existing items when app is installed/removed
            // this method has two problems:
            // 1) You are doing potential blocking operations inline to the core event dispatcher
            // 2) order of the events can be messed up by the portExecutor.
            if (!oltDeviceService.isOlt(event.subject())) {
                // if the device is not an OLT recognized in sadis
                // then we don't care about the events it is emitting
                log.debug("Ignoring event {}, subject is not an OLT device", event);
                return;
            }
            DeviceId deviceId = event.subject().id();
            switch (event.type()) {
                case PORT_STATS_UPDATED:
                case DEVICE_ADDED:
                    return;
                case PORT_ADDED:
                case PORT_UPDATED:
                case PORT_REMOVED:
                    if (!oltDeviceService.isLocalLeader(deviceId)) {
                        log.trace("Device {} is not local to this node", deviceId);
                        return;
                    }
                    // port added, updated and removed are treated in the same way as we only care whether the port
                    // is enabled or not
                    handleOltPort(event.type(), event.subject(), event.port());
                    return;
                case DEVICE_AVAILABILITY_CHANGED:
                    // NOTE that upon disconnection there is no mastership on the device,
                    // and we should anyway clear the local cache of the flows/meters across instances
                    if (!deviceService.isAvailable(deviceId) && deviceService.getPorts(deviceId).isEmpty()) {
                        // we're only clearing the device if there are no available ports,
                        // otherwise we assume it's a temporary disconnection
                        log.info("Device {} availability changed to false and ports are empty, " +
                                         "purging meters and flows", deviceId);
                        //NOTE all the instances will call these methods
                        oltFlowService.purgeDeviceFlows(deviceId);
                        oltMeterService.purgeDeviceMeters(deviceId);
                    } else {
                        log.info("Device {} availability changed to false, but ports are still available, " +
                                         "assuming temporary disconnection", deviceId);
                    }
                    return;
                case DEVICE_REMOVED:
                    log.info("Device {} Removed, purging meters and flows", deviceId);
                    oltFlowService.purgeDeviceFlows(deviceId);
                    oltMeterService.purgeDeviceMeters(event.subject().id());
                    return;
                default:
                    log.debug("OltDeviceListener receives event: {}, not handling", event);
            }
        });
    }

    protected void handleOltPort(DeviceEvent.Type type, Device device, Port port) {
        log.info("OltDeviceListener receives event {} for port {} with status {} on device {}", type, port.number(),
                port.isEnabled() ? "ENABLED" : "DISABLED", device.id());

        if (port.isEnabled()) {
            if (oltDeviceService.isNniPort(device, port)) {
                // NOTE in the NNI case we receive a PORT_REMOVED event with status ENABLED, thus we need to
                // pass the floeAction to the handleNniFlows method
                OltFlowService.FlowOperation action = OltFlowService.FlowOperation.ADD;
                if (type == DeviceEvent.Type.PORT_REMOVED) {
                    action = OltFlowService.FlowOperation.REMOVE;
                }
                oltFlowService.handleNniFlows(device, port, action);
            } else {
                post.accept(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_ADDED, device.id(), port));
                // NOTE if the subscriber was previously provisioned,
                // then add it back to the queue to be re-provisioned
                boolean provisionSubscriber = oltFlowService.isSubscriberProvisioned(new AccessDevicePort(port));
                SubscriberAndDeviceInformation si = subsService.get(getPortName(port));
                if (si == null) {
                    //NOTE this should not happen given that the subscriber was provisioned before
                    log.error("Subscriber information not found in sadis for port {}",
                              portWithName(port));
                    return;
                }
                DiscoveredSubscriber sub =
                        new DiscoveredSubscriber(device, port,
                                                 DiscoveredSubscriber.Status.ADDED, provisionSubscriber, si);
                if (!discoveredSubscribersQueue.contains(sub)) {
                    log.info("Adding subscriber to queue: {} with status {}",
                            portWithName(sub.port), sub.status);
                    discoveredSubscribersQueue.add(sub);
                }
            }
        } else {
            if (oltDeviceService.isNniPort(device, port)) {
                // NOTE this may need to be handled on DEVICE_REMOVE as we don't disable the NNI
                oltFlowService.handleNniFlows(device, port, OltFlowService.FlowOperation.REMOVE);
            } else {
                post.accept(new AccessDeviceEvent(AccessDeviceEvent.Type.UNI_REMOVED, device.id(), port));
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

                    if (!discoveredSubscribersQueue.contains(sub)) {
                        log.info("Adding subscriber to queue: {} with status {}",
                                portWithName(sub.port), sub.status);
                        discoveredSubscribersQueue.add(sub);
                    }
                } else if (oltFlowService.isSubscriberProvisioned(new AccessDevicePort(port))) {
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
                    if (!discoveredSubscribersQueue.contains(sub)) {
                        log.info("Adding provisioned subscriber to queue: {}/{} with status {}",
                                sub.device.id(), sub.port.number(), sub.status);
                        discoveredSubscribersQueue.add(sub);
                    }
                }
            }
        }
    }
}
