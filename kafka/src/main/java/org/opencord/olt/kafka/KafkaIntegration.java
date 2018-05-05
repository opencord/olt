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
package org.opencord.olt.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Port;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
import org.opencord.olt.AccessDeviceEvent;
import org.opencord.olt.AccessDeviceListener;
import org.opencord.olt.AccessDeviceService;
import org.slf4j.Logger;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sends access device events to an external system.
 */
@Component(immediate = true)
public class KafkaIntegration {

    private final Logger log = getLogger(getClass());
    private static final Class<OltKafkaConfig>
            OLT_KAFKA_CONFIG_CLASS = OltKafkaConfig.class;

    private static final String APP_NAME = "org.opencord.olt";
    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry configRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected AccessDeviceService accessDeviceService;

    private static StringSerializer stringSerializer = new StringSerializer();

    private KafkaProducer<String, String> kafkaProducer;

    private InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();
    private InternalAccessDeviceListener listener =
            new InternalAccessDeviceListener();

    private final ExecutorService executor = newSingleThreadExecutor(
            groupedThreads(this.getClass().getSimpleName(), "events", log));

    private ConfigFactory<ApplicationId, OltKafkaConfig> kafkaConfigFactory =
            new ConfigFactory<ApplicationId, OltKafkaConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY, OLT_KAFKA_CONFIG_CLASS,
                    "kafka") {
                @Override
                public OltKafkaConfig createConfig() {
                    return new OltKafkaConfig();
                }
            };

    private static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    private static final String RETRIES = "retries";
    private static final String RECONNECT_BACKOFF = "reconnect.backoff.ms";
    private static final String INFLIGHT_REQUESTS =
            "max.in.flight.requests.per.connection";
    private static final String ACKS = "acks";
    private static final String KEY_SERIALIZER = "key.serializer";
    private static final String VALUE_SERIALIZER = "value.serializer";
    private static final String STRING_SERIALIZER =
            stringSerializer.getClass().getCanonicalName();

    private static final String ONU_TOPIC = "onu.events";

    private static final String STATUS = "status";
    private static final String SERIAL_NUMBER = "serial_number";
    private static final String UNI_PORT_ID = "uni_port_id";
    private static final String OF_DPID = "of_dpid";
    private static final String ACTIVATED = "activated";

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_NAME);
        configRegistry.registerConfigFactory(kafkaConfigFactory);
        configRegistry.addListener(configListener);
        accessDeviceService.addListener(listener);

        configure();

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        accessDeviceService.removeListener(listener);
        configRegistry.removeListener(configListener);
        configRegistry.unregisterConfigFactory(kafkaConfigFactory);

        executor.shutdownNow();

        shutdownKafka();
        log.info("Stopped");
    }

    private void configure() {
        OltKafkaConfig config =
                configRegistry.getConfig(appId, OLT_KAFKA_CONFIG_CLASS);
        if (config == null) {
            log.info("OLT Kafka config not found");
            return;
        }
        configure(config);
    }

    private void configure(OltKafkaConfig config) {
        checkNotNull(config);

        Properties properties = new Properties();
        properties.put(BOOTSTRAP_SERVERS, config.getBootstrapServers());
        properties.put(RETRIES, config.getRetries());
        properties.put(RECONNECT_BACKOFF, config.getReconnectBackoff());
        properties.put(INFLIGHT_REQUESTS, config.getInflightRequests());
        properties.put(ACKS, config.getAcks());
        properties.put(KEY_SERIALIZER, STRING_SERIALIZER);
        properties.put(VALUE_SERIALIZER, STRING_SERIALIZER);

        startKafka(properties);
    }

    private void unconfigure() {
        shutdownKafka();
    }

    private void startKafka(Properties properties) {
        shutdownKafka();

        // Kafka client doesn't play nice with the default OSGi classloader
        // This workaround temporarily changes the thread's classloader so that
        // the Kafka client can load the serializer classes.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            kafkaProducer = new KafkaProducer<>(properties);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private void shutdownKafka() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
            kafkaProducer = null;
        }
    }

    private void sendUniAddEvent(AccessDeviceEvent event) {
        if (kafkaProducer == null) {
            return;
        }

        Port port = event.port().get();
        String serialNumber = port.annotations().value(AnnotationKeys.PORT_NAME);

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode onuNode = mapper.createObjectNode();
        onuNode.put(STATUS, ACTIVATED);
        onuNode.put(SERIAL_NUMBER, serialNumber);
        onuNode.put(UNI_PORT_ID, port.number().toLong());
        onuNode.put(OF_DPID, port.element().id().toString());

        if (log.isDebugEnabled()) {
            log.debug("Sending UNI ADD event: {}", onuNode.toString());
        }

        kafkaProducer.send(new ProducerRecord<>(ONU_TOPIC, onuNode.toString()),
                (r, e) -> logException(e));
    }

    private void logException(Exception e) {
        if (e != null) {
            log.error("Exception while sending to Kafka", e);
        }
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {
            case CONFIG_ADDED:
            case CONFIG_UPDATED:
                configure((OltKafkaConfig) event.config().get());
                break;
            case CONFIG_REMOVED:
                unconfigure();
                break;
            case CONFIG_REGISTERED:
            case CONFIG_UNREGISTERED:
            default:
                break;
            }
        }

        @Override
        public boolean isRelevant(NetworkConfigEvent event) {
            return event.configClass().equals(OLT_KAFKA_CONFIG_CLASS);
        }
    }

    private class InternalAccessDeviceListener implements AccessDeviceListener {

        @Override
        public void event(AccessDeviceEvent accessDeviceEvent) {
            switch (accessDeviceEvent.type()) {
            case UNI_ADDED:
                executor.execute(() ->
                        KafkaIntegration.this.sendUniAddEvent(accessDeviceEvent));
                break;
            case UNI_REMOVED:
            default:
                break;
            }
        }
    }
}
