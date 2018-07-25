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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.rest.AbstractWebResource;
import org.opencord.olt.AccessDeviceService;
import org.opencord.olt.Subscriber;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

import static org.onlab.util.Tools.readTreeFromStream;

/**
 * OLT REST APIs.
 */

@Path("oltapp")
public class OltWebResource extends AbstractWebResource {

    /*@POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{device}/{port}/{vlan}")
    public Response provisionSubscriber(
            @PathParam("device")String device,
            @PathParam("port")long port,
            @PathParam("vlan")short vlan) {
        AccessDeviceService service = get(AccessDeviceService.class);
        DeviceId deviceId = DeviceId.deviceId(device);
        PortNumber portNumber = PortNumber.portNumber(port);
        VlanId vlanId = VlanId.vlanId(vlan);
        ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);
        service.provisionSubscriber(connectPoint, vlanId);
        return ok("").build();
    }*/
    /*
     * Provision a subscriber.
     *
     * @param device device id
     * @param port port number
     * @param vlan vlan id
     * @return 200 OK
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("subscribers")
    public Response provisionSubscriber(InputStream stream) {
            //@PathParam("device")String device,
            //@PathParam("port")long port,
            //@PathParam("vlan")short vlan) {
        try {
            ObjectNode node = readTreeFromStream(mapper(), stream);
            Subscriber subscriber = codec(Subscriber.class).decode(node, this);

            AccessDeviceService service = get(AccessDeviceService.class);

            /*DeviceId deviceId = DeviceId.deviceId(device);
            PortNumber portNumber = PortNumber.portNumber(port);
            VlanId vlanId = VlanId.vlanId(vlan);*/
            //ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);
            service.provisionSubscriber(subscriber.uniPort(), subscriber.sVlan(), subscriber.cVlan());
            return ok("").build();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
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
}
