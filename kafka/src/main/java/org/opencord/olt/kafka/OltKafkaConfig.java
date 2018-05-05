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

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

/**
 * Configuration of the Kafka publishing endpoint.
 */
public class OltKafkaConfig extends Config<ApplicationId> {

    private static final String BOOTSTRAP_SERVERS = "bootstrapServers";
    private static final String RETRIES = "retries";
    private static final String RECONNECT_BACKOFF = "reconnectBackoff";
    private static final String INFLIGHT_REQUESTS = "inflightRequests";
    private static final String ACKS = "acks";

    private static final String DEFAULT_RETRIES = "1";
    private static final String DEFAULT_RECONNECT_BACKOFF = "500";
    private static final String DEFAULT_INFLIGHT_REQUESTS = "5";
    private static final String DEFAULT_ACKS = "1";

    @Override
    public boolean isValid() {
        return hasOnlyFields(BOOTSTRAP_SERVERS, RETRIES, RECONNECT_BACKOFF,
                INFLIGHT_REQUESTS, ACKS) && hasFields(BOOTSTRAP_SERVERS);
    }

    /**
     * Returns the Kafka bootstrap servers.
     * <p>
     * This can be a hostname/IP and port (e.g. 10.1.1.1:9092).
     * </p>
     *
     * @return Kafka bootstrap servers
     */
    public String getBootstrapServers() {
        return object.path(BOOTSTRAP_SERVERS).asText();
    }

    /**
     * Returns the number of retries.
     *
     * @return retries
     */
    public String getRetries() {
        return get(RETRIES, DEFAULT_RETRIES);
    }

    /**
     * Returns the reconnect backoff in milliseconds.
     *
     * @return reconnect backoff
     */
    public String getReconnectBackoff() {
        return get(RECONNECT_BACKOFF, DEFAULT_RECONNECT_BACKOFF);
    }

    /**
     * Returns the number of inflight requests.
     *
     * @return inflight requests
     */
    public String getInflightRequests() {
        return get(INFLIGHT_REQUESTS, DEFAULT_INFLIGHT_REQUESTS);
    }

    /**
     * Returns the number of acks.
     *
     * @return acks
     */
    public String getAcks() {
        return get(ACKS, DEFAULT_ACKS);
    }
}
