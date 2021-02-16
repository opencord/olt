/*
 * Copyright 2016-present Open Networking Foundation
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

import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.AccessSubscriberId;

import java.util.Optional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * OLT REST APIs.
 */

@Path("oltapp")
public class OltWebResource extends AbstractWebResource {

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
            return Response.status(INTERNAL_SERVER_ERROR).build();
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
        service.removeSubscriber(connectPoint);
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
        if (service.provisionSubscriber(new AccessSubscriberId(portName), emptyVlan, emptyVlan, emptyTpId)) {
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

        if (service.provisionSubscriber(new AccessSubscriberId(portName), Optional.of(sTag),
                Optional.of(cTag), Optional.of(tpId))) {
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

        Optional<VlanId> emptyVlan = Optional.empty();
        Optional<Integer> emptyTpId = Optional.empty();
        if (service.removeSubscriber(new AccessSubscriberId(portName), emptyVlan, emptyVlan, emptyTpId)) {
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

        if (service.removeSubscriber(new AccessSubscriberId(portName), Optional.of(sTag),
                Optional.of(cTag), Optional.of(tpId))) {
            return ok("").build();
        }
        return Response.noContent().build();
    }

}
