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
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.opencord.olt.OltDeviceServiceInterface;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The implementation of the OltDeviceService.
 */
@Component(immediate = true)
public class OltDeviceService implements OltDeviceServiceInterface {

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.OPTIONAL,
            bind = "bindSadisService",
            unbind = "unbindSadisService",
            policy = ReferencePolicy.DYNAMIC)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Activate
    public void activate() {
        log.info("Activated");
    }

    /**
     * Returns true if the device is an OLT.
     *
     * @param device the Device to be checked
     * @return boolean
     */
    public boolean isOlt(Device device) {
        return getOltInfo(device) != null;
    }

    private SubscriberAndDeviceInformation getOltInfo(Device dev) {
        if (subsService == null) {
            return null;
        }
        String devSerialNo = dev.serialNumber();
        return subsService.get(devSerialNo);
    }


    /**
     * Returns true if the port is an NNI Port on the OLT.
     * NOTE: We can check if a port is a NNI based on the SADIS config, specifically the uplinkPort section
     *
     * @param dev  the Device this port belongs to
     * @param portNumber the PortNumber to be checked
     * @return boolean
     */
    @Override
    public boolean isNniPort(Device dev, PortNumber portNumber) {
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        return deviceInfo != null && portNumber.toLong() == deviceInfo.uplinkPort();
    }

    @Override
    public Optional<Port> getNniPort(Device device) {
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(device);
        if (deviceInfo == null) {
            return Optional.empty();
        }
        List<Port> ports = deviceService.getPorts(device.id());
        if (log.isTraceEnabled()) {
            log.trace("Get NNI Port looks for NNI in: {}", ports);
        }
        return ports.stream()
                .filter(p -> p.number().toLong() == deviceInfo.uplinkPort())
                .findFirst();
    }

    protected void bindSadisService(SadisService service) {
        this.subsService = service.getSubscriberInfoService();
        log.info("Sadis service is loaded");
    }

    protected void unbindSadisService(SadisService service) {
        this.subsService = null;
        log.info("Sadis service is unloaded");
    }

    /**
     * Checks for mastership or falls back to leadership on deviceId.
     * If the device is available use mastership,
     * otherwise fallback on leadership.
     * Leadership on the device topic is needed because the master can be NONE
     * in case the device went away, we still need to handle events
     * consistently
     *
     * @param deviceId The device ID to check.
     * @return boolean (true if the current instance is managing the device)
     */
    @Override
    public boolean isLocalLeader(DeviceId deviceId) {
        if (deviceService.isAvailable(deviceId)) {
            return mastershipService.isLocalMaster(deviceId);
        } else {
            // Fallback with Leadership service - device id is used as topic
            NodeId leader = leadershipService.runForLeadership(
                    deviceId.toString()).leaderNodeId();
            // Verify if this node is the leader
            return clusterService.getLocalNode().id().equals(leader);
        }
    }
}
