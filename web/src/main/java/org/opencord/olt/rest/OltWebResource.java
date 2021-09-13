package org.opencord.olt.rest;

import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.olt.OltService;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Optional;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * OLT REST APIs.
 */

@Path("oltapp")
public class OltWebResource extends AbstractWebResource {
    private final Logger log = getLogger(getClass());


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("status")
    public Response status() {
        return Response.ok().build();
    }
    /**
     * Provision a subscriber.
     *
     * @param device device id
     * @param port port number
     * @return 200 OK or 500 Internal Server Error
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{device}/{port}")
    public Response provisionSubscriber(
            @PathParam("device") String device,
            @PathParam("port") long port) {
        OltService service = get(OltService.class);
        DeviceId deviceId = DeviceId.deviceId(device);
        PortNumber portNumber = PortNumber.portNumber(port);
        ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);

        try {
            service.provisionSubscriber(connectPoint);
        } catch (Exception e) {
            log.error("Can't provision subscriber {} due to exception", connectPoint, e);
            return Response.status(INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        return ok("").build();
    }

    /**
     * Remove the provisioning for a subscriber.
     *
     * @param device device id
     * @param port port number
     * @return 204 NO CONTENT
     */
    @DELETE
    @Path("{device}/{port}")
    public Response removeSubscriber(
            @PathParam("device")String device,
            @PathParam("port")long port) {
        OltService service = get(OltService.class);
        DeviceId deviceId = DeviceId.deviceId(device);
        PortNumber portNumber = PortNumber.portNumber(port);
        ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);
        try {
            service.removeSubscriber(connectPoint);
        } catch (Exception e) {
            log.error("Can't remove subscriber {} due to exception", connectPoint, e);
            return Response.status(INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        return Response.noContent().build();
    }

    /**
     * Provision service for a subscriber.
     *
     * @param portName Name of the port on which the subscriber is connected
     * @return 200 OK or 204 NO CONTENT
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("services/{portName}")
    public Response provisionServices(
            @PathParam("portName") String portName) {
        OltService service = get(OltService.class);

        Optional<VlanId> emptyVlan = Optional.empty();
        Optional<Integer> emptyTpId = Optional.empty();
        // Check if we can find the connect point to which this subscriber is connected
        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("ConnectPoint not found for " + portName).build();
        }
        if (service.provisionSubscriber(cp)) {
            return ok("").build();
        }
        return Response.noContent().build();
    }

    /**
     * Removes services for a subscriber.
     *
     * @param portName Name of the port on which the subscriber is connected
     * @return 200 OK or 204 NO CONTENT
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("services/{portName}")
    public Response deleteServices(
            @PathParam("portName") String portName) {
        OltService service = get(OltService.class);

        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("ConnectPoint not found for " + portName).build();
        }
        if (service.removeSubscriber(cp)) {
            return ok("").build();
        }
        return Response.noContent().build();
    }

    /**
     * Provision service with particular tags for a subscriber.
     *
     * @param portName Name of the port on which the subscriber is connected
     * @param sTagVal  additional outer tag on this port
     * @param cTagVal  additional innter tag on this port
     * @param tpIdVal  technology profile id
     * @return 200 OK or 204 NO CONTENT
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("services/{portName}/{sTag}/{cTag}/{tpId}")
    public Response provisionAdditionalVlans(
            @PathParam("portName") String portName,
            @PathParam("sTag") String sTagVal,
            @PathParam("cTag") String cTagVal,
            @PathParam("tpId") String tpIdVal) {
        OltService service = get(OltService.class);
        VlanId cTag = VlanId.vlanId(cTagVal);
        VlanId sTag = VlanId.vlanId(sTagVal);
        Integer tpId = Integer.valueOf(tpIdVal);
        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("ConnectPoint not found for " + portName).build();
        }
        //TODO create methods and pass elements
        if (service.provisionSubscriber(cp, cTag, sTag, tpId)) {
            return ok("").build();
        }
        return Response.noContent().build();
    }

    /**
     * Removes additional vlans of a particular subscriber.
     *
     * @param portName Name of the port on which the subscriber is connected
     * @param sTagVal  additional outer tag on this port which needs to be removed
     * @param cTagVal  additional inner tag on this port which needs to be removed
     * @param tpIdVal  additional technology profile id
     * @return 200 OK or 204 NO CONTENT
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("services/{portName}/{sTag}/{cTag}/{tpId}")
    public Response removeAdditionalVlans(
            @PathParam("portName") String portName,
            @PathParam("sTag") String sTagVal,
            @PathParam("cTag") String cTagVal,
            @PathParam("tpId") String tpIdVal) {
        OltService service = get(OltService.class);
        VlanId cTag = VlanId.vlanId(cTagVal);
        VlanId sTag = VlanId.vlanId(sTagVal);
        Integer tpId = Integer.valueOf(tpIdVal);
        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("ConnectPoint not found for " + portName).build();
        }
        if (service.removeSubscriber(cp, cTag, sTag, tpId)) {
            return ok("").build();
        }
        return Response.noContent().build();
    }
}
