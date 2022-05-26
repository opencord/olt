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
package org.opencord.olt.rest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.OltFlowServiceInterface;
import org.opencord.olt.ServiceKey;
import org.opencord.sadis.UniTagInformation;

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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * OLT REST APIs.
 */

@Path("oltapp")
public class OltWebResource extends AbstractWebResource {
    private final ObjectNode root = mapper().createObjectNode();
    private final ArrayNode node = root.putArray("entries");
    private final Logger log = getLogger(getClass());
    private static final String LOCATION = "location";
    private static final String TAG_INFO = "tagInfo";

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
        AccessDeviceService service = get(AccessDeviceService.class);
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
        AccessDeviceService service = get(AccessDeviceService.class);
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
        AccessDeviceService service = get(AccessDeviceService.class);

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
        AccessDeviceService service = get(AccessDeviceService.class);

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
        AccessDeviceService service = get(AccessDeviceService.class);
        VlanId cTag = VlanId.vlanId(cTagVal);
        VlanId sTag = VlanId.vlanId(sTagVal);
        Integer tpId = Integer.valueOf(tpIdVal);
        // TODO this is not optimal, because we call device service 2 times here and
        // 2 times in the provisionSubscriber call, optimize byu having 2 more methods
        // in the OltService that allow provisioning with portName.
        ConnectPoint cp = service.findSubscriberConnectPoint(portName);
        if (cp == null) {
            log.warn("ConnectPoint not found for {}", portName);
            return Response.status(INTERNAL_SERVER_ERROR)
                    .entity("ConnectPoint not found for " + portName).build();
        }
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
        AccessDeviceService service = get(AccessDeviceService.class);
        VlanId cTag = VlanId.vlanId(cTagVal);
        VlanId sTag = VlanId.vlanId(sTagVal);
        Integer tpId = Integer.valueOf(tpIdVal);
        // TODO this is not optimal, because we call device service 2 times here and
        // 2 times in the provisionSubscriber call, optimize byu having 2 more methods
        // in the OltService that allow provisioning with portName.
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

    /**
     * Gets subscribers programmed in the dataplane.
     *
     * @return 200 OK
     */
    @GET
    @Path("programmed-subscribers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgrammedSubscribers() {
        return getProgrammedSubscribers(null, null);
    }

    /**
     * Gets subscribers programmed in the dataplane.
     *
     * @param deviceId  device-id to filter the results.
     *
     * @return 200 OK
     */
    @GET
    @Path("programmed-subscribers/{deviceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgrammedSubscribersByDeviceId(@PathParam("deviceId") String deviceId) {
        return getProgrammedSubscribers(deviceId, null);
    }

    /*
     * Gets subscribers programmed in the dataplane.
     *
     * @param deviceId  device-id to filter the results.
     * @param port      port to filter the results.
     *
     * @return 200 OK
     */
    @GET
    @Path("programmed-subscribers/{deviceId}/{port}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgrammedSubscribersByConnectPoint(@PathParam("deviceId") String deviceId,
                                                           @PathParam("port") String port) {
        return getProgrammedSubscribers(deviceId, port);
    }

    private Response getProgrammedSubscribers(String deviceId, String port) {
        OltFlowServiceInterface service = get(OltFlowServiceInterface.class);
        Map<ServiceKey, UniTagInformation> info = service.getProgrammedSubscribers();
        Set<Map.Entry<ServiceKey, UniTagInformation>> entries = info.entrySet();
        if (deviceId != null && !deviceId.isEmpty()) {
            entries = entries.stream().filter(entry -> entry.getKey().getPort().connectPoint().deviceId()
                    .equals(DeviceId.deviceId(deviceId))).collect(Collectors.toSet());
        }

        if (port != null && !port.isEmpty()) {
            PortNumber portNumber = PortNumber.portNumber(port);
            entries = entries.stream().filter(entry -> entry.getKey().getPort().connectPoint().port()
                    .equals(portNumber)).collect(Collectors.toSet());
        }

        try {
            entries.forEach(entry -> {
                ConnectPoint location = entry.getKey().getPort().connectPoint();
                UniTagInformation tagInfo = entry.getValue();
                ObjectNode encodedTagInfo = codec(UniTagInformation.class).encode(tagInfo, this);
                ObjectNode encodedEntry = mapper().createObjectNode();
                encodedEntry.put(LOCATION, location.toString())
                        .set(TAG_INFO, encodedTagInfo);
                node.add(encodedEntry);
            });

            return ok(mapper().writeValueAsString(root)).build();
        } catch (Exception e) {
            log.error("Error while fetching programmed subscriber list through REST API: {}", e.getMessage());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }
    }
}
