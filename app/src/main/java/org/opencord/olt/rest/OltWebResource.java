/*
 * Copyright 2016-present Open Networking Laboratory
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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.opencord.olt.AccessDeviceService;
import org.onosproject.rest.AbstractWebResource;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;


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
     * @param vlan vlan id
     * @return 200 OK
     */
    @POST
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
    }

    /**
     * Provision subscribers by json input stream.
     *
     * @param stream input stream in json format
     * e.g.
     * {"subscribers":
     * [
     * {"device":"of:000008002704dc82","port":2,"vlan":17},
     * {"device":"of:000008002704dc82","port":5,"vlan":19},
     * {"device":"of:000008002704dc82","port":8,"vlan":17},
     * {"device":"of:000008002704dc82","port":6,"vlan":20}
     * ]
     * }
     * @return 200 OK
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/subscribers")
    public Response provisionSubscriber(InputStream stream) {
        AccessDeviceService service = get(AccessDeviceService.class);

        try {
            JsonNode jsonTree = mapper().readTree(stream);
            Iterator<JsonNode> iter  = jsonTree.withArray("subscribers").iterator();

            while (iter.hasNext()) {
                JsonNode node = iter.next();
                DeviceId deviceId = DeviceId.deviceId(node.get("device").textValue());
                PortNumber portNumber = PortNumber.portNumber(node.get("port").intValue());
                VlanId vlanId = VlanId.vlanId(node.get("vlan").shortValue());
                ConnectPoint connectPoint = new ConnectPoint(deviceId, portNumber);
                service.provisionSubscriber(connectPoint, vlanId);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
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
     * Get subscriber VLAN id for given device id and port .
     *
     * @param device device id
     * @param port port number
     * @return vlanid or just "{}"
     * e.g. {"device":"of:000008002704dc82","port":8,"vlan":17}
     */
    @GET
    @Path("{device}/{port}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscriber(
            @PathParam("device")String device,
            @PathParam("port")long port) {
        AccessDeviceService service = get(AccessDeviceService.class);
        ConnectPoint connectPoint = new ConnectPoint(
                DeviceId.deviceId(device),
                PortNumber.portNumber(port));
        ObjectNode root = mapper().createObjectNode();

        Iterator<Map.Entry<ConnectPoint, VlanId>> iterator = service.getSubscribers().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ConnectPoint, VlanId> map = iterator.next();
            if (map.getKey().equals(connectPoint)) {
                root.put("device", device);
                root.put("port", port);
                root.put("vlan", map.getValue().toShort());
                return Response.ok(root).build();
            }
        }
        return Response.status(Response.Status.NOT_FOUND).build();

    }

    /**
     * Get subscriber vlan id for given device id and port .
     *
     * @returnl subscribers infomation as json format
     * e.g.
     * {"subscribers":
     * [
     * {"device":"of:000008002704dc82","port":2,"vlan":17},
     * {"device":"of:000008002704dc82","port":5,"vlan":19},
     * {"device":"of:000008002704dc82","port":8,"vlan":17},
     * {"device":"of:000008002704dc82","port":6,"vlan":20}
     * ]
     * }
     */
    @GET
    @Path("/subscribers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscriber() {
        ObjectNode root = mapper().createObjectNode();

        AccessDeviceService service = get(AccessDeviceService.class);

        service.getSubscribers().forEach(map ->
                        root.withArray("subscribers").add(
                                mapper().createObjectNode()
                                        .put("device", map.getKey().deviceId().toString())
                                        .put("port", map.getKey().port().toLong())
                                        .put("vlan", map.getValue().toShort()))
        );

        return Response.ok(root).build();
    }
}
