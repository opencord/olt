/*
 * Copyright 2018-present Open Networking Foundation
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
package org.opencord.olt.xos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Port;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDeviceService;
import org.slf4j.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sends access device events to an external system.
 */
@Component(immediate = true)
public class XosIntegration {

    private final Logger log = getLogger(getClass());
    private static final Class<XosEndpointConfig>
            XOS_ENDPOINT_CONFIG_CLASS = XosEndpointConfig.class;

    private static final String APP_NAME = "org.opencord.xosintegration";
    private ApplicationId appId;

    private static final int DEFAULT_REST_TIMEOUT_MS = 1000;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected AccessDeviceService accessDeviceService;

    private InternalAccessDeviceListener listener = new InternalAccessDeviceListener();

    private ConfigFactory<ApplicationId, XosEndpointConfig> xosEndpointConfigFactory =
            new ConfigFactory<ApplicationId, XosEndpointConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY, XOS_ENDPOINT_CONFIG_CLASS,
                    "xosEndpoint") {
                @Override
                public XosEndpointConfig createConfig() {
                    return new XosEndpointConfig();
                }
            };

    private static final String ITEMS = "items";
    private static final String DATAPATH_ID = "dp_id";
    private static final String ID = "id";
    private static final String VOLT_DEVICE_ID = "volt_device_id";
    private static final String SERIAL_NUMBER = "serial_number";

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_NAME);
        configRegistry.registerConfigFactory(xosEndpointConfigFactory);
        accessDeviceService.addListener(listener);

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        accessDeviceService.removeListener(listener);
        configRegistry.unregisterConfigFactory(xosEndpointConfigFactory);
        log.info("Stopped");
    }

    private String getUrl(String endpoint) {
        return "http://" + endpoint + "/xosapi/v1/volt";
    }

    private Invocation.Builder getClientBuilder(String uri, String user, String password) {
        ClientConfig config = new ClientConfig();
        Client client = ClientBuilder.newClient(config);

        client.property(ClientProperties.CONNECT_TIMEOUT, DEFAULT_REST_TIMEOUT_MS);
        client.property(ClientProperties.READ_TIMEOUT,    DEFAULT_REST_TIMEOUT_MS);
        client.register(HttpAuthenticationFeature.basic(user, password));

        WebTarget wt = client.target(uri);
        return wt.request(JSON_UTF_8.toString());
    }

    private void sendUniAddEvent(AccessDeviceEvent event) {
        Port port = event.port().get();
        XosEndpointConfig config =
                configRegistry.getConfig(appId, XOS_ENDPOINT_CONFIG_CLASS);
        if (config == null) {
            log.warn("No XOS endpoint config found");
            return;
        }

        Invocation.Builder builder = getClientBuilder(getUrl(
                config.getEndpoint()) + "/oltdevices", config.getUsername(), config.getPassword());

        try {
            Response response = builder.get();
            if (response.getStatus() != HTTP_OK) {
                log.error("Failed to get OLT devices: {}, {}", response.getStatus(),
                        response.readEntity(String.class));
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode object = mapper.readTree(response.readEntity(String.class));
            int id = -1;
            for (JsonNode item : object.path(ITEMS)) {
                if (item.path(DATAPATH_ID).asText().equals(port.element().id().toString())) {
                    id = item.path(ID).asInt();
                    break;
                }
            }

            if (id == -1) {
                log.warn("OLT {} not found in XOS", port.element().id());
                return;
            }

            builder = getClientBuilder(getUrl(
                    config.getEndpoint()) + "/onudevices", config.getUsername(), config.getPassword());
            response = builder.get();
            if (response.getStatus() != HTTP_OK) {
                log.error("Failed to get ONU devices: {}, {}", response.getStatus(),
                        response.readEntity(String.class));
                return;
            }

            String serialNumber = port.annotations().value(AnnotationKeys.PORT_NAME);

            JsonNode onus = mapper.readTree(response.readEntity(String.class));
            for (JsonNode item : onus.path(ITEMS)) {
                if (item.path(VOLT_DEVICE_ID).asInt() == id &&
                        item.path(SERIAL_NUMBER).asText().equals(serialNumber)) {
                    log.info("ONU already exists");
                    return;
                }
            }

            ObjectNode onuNode = mapper.createObjectNode();
            onuNode.put(VOLT_DEVICE_ID, id);
            onuNode.put(SERIAL_NUMBER, serialNumber);

            builder = getClientBuilder(
                    getUrl(config.getEndpoint()) + "/onudevices",
                    config.getUsername(), config.getPassword());

            log.info("Sending ONU {}", onuNode.toString());

            response = builder.post(Entity.json(onuNode.toString()));

            if (response.getStatus() != HTTP_OK) {
                log.error("Failed to push ONU device: {}, {}", response.getStatus(),
                        response.readEntity(String.class));
            }

        } catch (ProcessingException e) {
            log.warn("Unable to send event to XOS: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Exception", e);
        }
    }

    private class InternalAccessDeviceListener implements AccessDeviceListener {

        @Override
        public void event(AccessDeviceEvent accessDeviceEvent) {
            switch (accessDeviceEvent.type()) {
            case UNI_ADDED:
                sendUniAddEvent(accessDeviceEvent);
                break;
            case UNI_REMOVED:
            default:
                break;
            }
        }
    }

}
