/*
 * Copyright 2021-2023 Open Networking Foundation (ONF) and the ONF Contributors
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

package org.opencord.olt.driver;

import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cfg.ConfigProperty;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.net.driver.AbstractDriverLoader;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onlab.util.Tools.get;
import static org.opencord.olt.impl.OsgiPropertyConstants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Loader for olt device drivers.
 */
@Component(immediate = true, property = {
        REQUIRED_DRIVERS_PROPERTY_DELAY + ":Integer=" + REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT
})
public class OltDriversLoader extends AbstractDriverLoader {

    public static final String DRIVER_REGISTRY_MANAGER =
            "org.onosproject.net.driver.impl.DriverRegistryManager";
    public static final String REQUIRED_DRIVERS = "requiredDrivers";
    public static final String VOLTHA_DRIVER_NAME = "voltha";
    public static final String DRIVER_UPDATE_LEADSHIP_TOPIC = "driver-update";
    public static final String OLT_DRIVERS_LOADER = "org.opencord.olt.driver.OltDriversLoader";

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private ComponentConfigService compCfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;

    /**
     * Default amounts of eapol retry.
     **/
    protected int requiredDriversPropertyDelay = REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT;

    private final Logger log = getLogger(getClass());

    public OltDriversLoader() {
        super("/olt-drivers.xml");
    }

    @Override
    public void activate() {
        log.info("Activating OLT Driver loader");
        compCfgService.registerProperties(getClass());
        //The AbstractDriversLoader does not pass the context, to avoid changes to all
        // inheriting classes getting the property differently
        ConfigProperty requiredDriversPropertyDelayNew =
                compCfgService.getProperty(OLT_DRIVERS_LOADER, REQUIRED_DRIVERS_PROPERTY_DELAY);
        requiredDriversPropertyDelay = requiredDriversPropertyDelayNew == null ?
                REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT : requiredDriversPropertyDelayNew.asInteger();
        log.info("OLT Driver loader requiredDriversPropertyDelay: {}", requiredDriversPropertyDelay);
        super.activate();
        // Verify if this node is the leader
        NodeId leader = leadershipService.runForLeadership(DRIVER_UPDATE_LEADSHIP_TOPIC).leaderNodeId();
        if (clusterService.getLocalNode().id().equals(leader)) {
            Thread thread = new Thread(() -> {
                // Sleep is needed to allow driver correct initialization and
                // flow objective cache invalidation.
                try {
                    TimeUnit.SECONDS.sleep(requiredDriversPropertyDelay);
                } catch (InterruptedException e) {
                    log.error("Interrupted thread while activating", e);
                }
                String currentRequiredDrivers =
                        compCfgService.getProperty(DRIVER_REGISTRY_MANAGER, REQUIRED_DRIVERS)
                                .asString();
                //insertion of voltha in the required drivers
                if (!currentRequiredDrivers.contains(VOLTHA_DRIVER_NAME)) {
                    String updatedRequiredDrivers = currentRequiredDrivers;
                    if (!updatedRequiredDrivers.endsWith(",")) {
                        updatedRequiredDrivers = updatedRequiredDrivers + ",";
                    }
                    updatedRequiredDrivers = updatedRequiredDrivers + VOLTHA_DRIVER_NAME;
                    compCfgService.setProperty(DRIVER_REGISTRY_MANAGER,
                                                  REQUIRED_DRIVERS, updatedRequiredDrivers);
                    log.debug("Added voltha driver to required drivers {}",
                              updatedRequiredDrivers);
                }
            });
            thread.start();
        }
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        try {
            String requiredDriversPropertyDelayNew = get(properties, REQUIRED_DRIVERS_PROPERTY_DELAY);
            requiredDriversPropertyDelay = isNullOrEmpty(requiredDriversPropertyDelayNew) ?
                    REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT :
                    Integer.parseInt(requiredDriversPropertyDelayNew.trim());

            log.info("OLT Driver loader requiredDriversPropertyDelay: {}", requiredDriversPropertyDelay);

        } catch (Exception e) {
            log.error("Error while modifying the properties", e);
            requiredDriversPropertyDelay = REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT;
        }
    }

    @Override
    public void deactivate() {
        log.info("Deactivating OLT Driver loader");
        //The AbstractDriversLoader does not pass the context, to avoid changes to all
        // inheriting classes getting the property differently
        ConfigProperty requiredDriversPropertyDelayNew =
                compCfgService.getProperty(OLT_DRIVERS_LOADER, REQUIRED_DRIVERS_PROPERTY_DELAY);
        requiredDriversPropertyDelay = requiredDriversPropertyDelayNew == null ?
                REQUIRED_DRIVERS_PROPERTY_DELAY_DEFAULT : requiredDriversPropertyDelayNew.asInteger();
        log.info("OLT Driver loader requiredDriversPropertyDelay: {}", requiredDriversPropertyDelay);
        NodeId leader = leadershipService.runForLeadership(DRIVER_UPDATE_LEADSHIP_TOPIC).leaderNodeId();
        if (clusterService.getLocalNode().id().equals(leader)) {
            String currentRequiredDrivers =
                    compCfgService.getProperty(DRIVER_REGISTRY_MANAGER, REQUIRED_DRIVERS)
                            .asString();
            //removal of voltha from the required driver
            if (currentRequiredDrivers.contains(VOLTHA_DRIVER_NAME)) {
                String updatedRequiredDrivers = currentRequiredDrivers.replace(VOLTHA_DRIVER_NAME, "");
                //handling the case where `voltha` was not the last required driver in the list
                if (updatedRequiredDrivers.contains(",,")) {
                    updatedRequiredDrivers = updatedRequiredDrivers.replace(",,", ",");
                }
                if (updatedRequiredDrivers.endsWith(",")) {
                    updatedRequiredDrivers = updatedRequiredDrivers.substring(0, updatedRequiredDrivers.length() - 1);
                }
                compCfgService.setProperty(DRIVER_REGISTRY_MANAGER,
                                           REQUIRED_DRIVERS, updatedRequiredDrivers);
                log.debug("Removed voltha from required drivers {}", updatedRequiredDrivers);
            }
        }
        // Sleep is needed to allow proper property sharing across the instances through accumulator
        // of component config manager
        try {
            TimeUnit.SECONDS.sleep(requiredDriversPropertyDelay);
        } catch (InterruptedException e) {
            log.error("Interrupted while de-activating", e);
        }
        compCfgService.unregisterProperties(getClass(), false);
        super.deactivate();
    }
}
