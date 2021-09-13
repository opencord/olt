package org.opencord.olt.impl;

import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@Component(immediate = true)
public class OltDeviceService implements OltDeviceServiceInterface {

    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    protected BaseInformationService<BandwidthProfileInformation> bpService;
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected volatile SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected volatile DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected volatile ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected volatile MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Activate
    public void activate() {
        subsService = sadisService.getSubscriberInfoService();
        log.info("Activated");
    }

    private boolean checkSadisRunning() {
        if (subsService == null) {
            log.warn("Sadis is not running");
            return false;
        }
        return true;
    }

    /**
     * Returns true if the device is an OLT.
     *
     * @param device the Device to be checked
     * @return boolean
     */
    public boolean isOlt(Device device) {
        if (!checkSadisRunning()) {
            return false;
        }
        String serialNumber = device.serialNumber();
        SubscriberAndDeviceInformation si = subsService.get(serialNumber);
        return si != null;
    }

    private SubscriberAndDeviceInformation getOltInfo(Device dev) {
        if (!checkSadisRunning()) {
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
     * @param port the Port to be checked
     * @return boolean
     */
    public boolean isNniPort(Device dev, Port port) {
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(dev);
        if (deviceInfo != null) {
            return port.number().toLong() == deviceInfo.uplinkPort();
        }
        return false;
    }

    @Override
    public Optional<Port> getNniPort(Device device) {
        SubscriberAndDeviceInformation deviceInfo = getOltInfo(device);
        return deviceService.getPorts(device.id()).stream()
                .filter(p -> p.number().toLong() == deviceInfo.uplinkPort())
                .findFirst();
    }

    public void bindSadisService(SadisService service) {
        this.bpService = service.getBandwidthProfileService();
        this.subsService = service.getSubscriberInfoService();
        log.info("Sadis service is loaded");
    }

    public void unbindSadisService() {
        this.bpService = null;
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
