package org.opencord.olt;

import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import java.util.List;

public interface OltService {
    /**
     * Provisions connectivity for a subscriber on an access device.
     * Installs flows for all uni tag information
     *
     * @param cp subscriber's connection point
     * @return true if successful false otherwise
     */
    boolean provisionSubscriber(ConnectPoint cp);
    boolean removeSubscriber(ConnectPoint cp);

    boolean provisionSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId);

    boolean removeSubscriber(ConnectPoint cp, VlanId cTag, VlanId sTag, Integer tpId);

     List<DeviceId> getConnectedOlts();

    /**
     * Finds the connect point to which a subscriber is connected.
     *
     * @param id The id of the subscriber, this is the same ID as in Sadis
     * @return Subscribers ConnectPoint if found else null
     */
    ConnectPoint findSubscriberConnectPoint(String id);

}
