/*
 * Copyright 2019-present Open Networking Foundation
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

package org.opencord.olt.impl;

/**
 * Constants for default values of configurable properties.
 */
public final class OsgiPropertyConstants {

    private OsgiPropertyConstants() {
    }

    public static final String DEFAULT_MCAST_SERVICE_NAME = "multicastServiceName";
    public static final String DEFAULT_MCAST_SERVICE_NAME_DEFAULT = "MC";

    public static final String ENABLE_DHCP_ON_NNI = "enableDhcpOnNni";
    public static final boolean ENABLE_DHCP_ON_NNI_DEFAULT = false;

    public static final String ENABLE_DHCP_V4 = "enableDhcpV4";
    public static final boolean ENABLE_DHCP_V4_DEFAULT = true;

    public static final String ENABLE_DHCP_V6 = "enableDhcpV6";
    public static final boolean ENABLE_DHCP_V6_DEFAULT = false;

    public static final String ENABLE_IGMP_ON_NNI = "enableIgmpOnNni";
    public static final boolean ENABLE_IGMP_ON_NNI_DEFAULT = false;

    public static final String DELETE_METERS = "deleteMeters";
    public static final boolean DELETE_METERS_DEFAULT = true;

    public static final String DEFAULT_TP_ID = "defaultTechProfileId";
    public static final int DEFAULT_TP_ID_DEFAULT = 64;

    public static final String ZERO_REFERENCE_METER_COUNT = "zeroReferenceMeterCount";
    public static final int ZERO_REFERENCE_METER_COUNT_DEFAULT = 3;


    public static final String DEFAULT_BP_ID = "defaultBpId";
    public static final String DEFAULT_BP_ID_DEFAULT = "Default";

    public static final String ENABLE_EAPOL = "enableEapol";
    public static final boolean ENABLE_EAPOL_DEFAULT = true;

    public static final String ENABLE_PPPOE_ON_NNI = "enablePppoeOnNni";
    public static final boolean ENABLE_PPPOE_ON_NNI_DEFAULT = false;

    public static final String ENABLE_PPPOE = "enablePppoe";
    public static final boolean ENABLE_PPPOE_DEFAULT = false;

    public static final String WAIT_FOR_REMOVAL = "waitForRemoval";
    public static final boolean WAIT_FOR_REMOVAL_DEFAULT = true;

    public static final String REMOVE_FLOWS_ON_DISABLE = "removeFlowsOnDisable";

    public static final boolean REMOVE_FLOWS_ON_DISABLE_DEFAULT = true;

    public static final String REQUIRED_DRIVERS_PROPERTY_DELAY = "requiredDriversPropertyDelay";
    public static final int REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT = 5;

    public static final String FLOW_PROCESSING_THREADS = "flowProcessingThreads";
    public static final int FLOW_PROCESSING_THREADS_DEFAULT = 32;

    //Giving it a value of * 4 the number of flows.
    public static final String FLOW_EXECUTOR_QUEUE_SIZE = "flowExecutorQueueSize";
    public static final int FLOW_EXECUTOR_QUEUE_SIZE_DEFAULT = 128;

    public static final String SUBSCRIBER_PROCESSING_THREADS = "subscriberProcessingThreads";
    public static final int SUBSCRIBER_PROCESSING_THREADS_DEFAULT = 24;

    public static final String REQUEUE_DELAY = "requeueDelay";
    public static final int REQUEUE_DELAY_DEFAULT = 500;

    public static final String UPSTREAM_ONU = "upstreamOnu";
    public static final String UPSTREAM_OLT = "upstreamOlt";
    public static final String DOWNSTREAM_ONU = "downstreamOnu";
    public static final String DOWNSTREAM_OLT = "downstreamOlt";
}
