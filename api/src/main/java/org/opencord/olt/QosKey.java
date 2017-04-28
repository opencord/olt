package org.opencord.olt;

import org.onosproject.net.ConnectPoint;

/**
 * Created by onos on 16-11-15.
 */
public class QosKey {
    public ConnectPoint port;
    public Integer vlanPcp;

    public QosKey(ConnectPoint cp, Integer pcp) {
        port = cp;
        vlanPcp = pcp;
    }
}
