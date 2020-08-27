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

    public static final String DEFAULT_BP_ID = "defaultBpId";
    public static final String DEFAULT_BP_ID_DEFAULT = "Default";

    public static final String ENABLE_EAPOL = "enableEapol";
    public static final boolean ENABLE_EAPOL_DEFAULT = true;

    public static final String EAPOL_DELETE_RETRY_MAX_ATTEMPS = "eapolDeleteRetryMaxAttempts";
    public static final int EAPOL_DELETE_RETRY_MAX_ATTEMPS_DEFAULT = 3;
}
