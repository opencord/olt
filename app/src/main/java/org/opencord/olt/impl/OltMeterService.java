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
package org.opencord.olt.impl;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.onlab.util.Tools.groupedThreads;
import static org.opencord.olt.impl.OsgiPropertyConstants.DELETE_METERS;
import static org.opencord.olt.impl.OsgiPropertyConstants.DELETE_METERS_DEFAULT;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.onlab.util.KryoNamespace;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.meter.Band;
import org.onosproject.net.meter.DefaultBand;
import org.onosproject.net.meter.DefaultMeterRequest;
import org.onosproject.net.meter.Meter;
import org.onosproject.net.meter.MeterContext;
import org.onosproject.net.meter.MeterEvent;
import org.onosproject.net.meter.MeterFailReason;
import org.onosproject.net.meter.MeterId;
import org.onosproject.net.meter.MeterKey;
import org.onosproject.net.meter.MeterListener;
import org.onosproject.net.meter.MeterRequest;
import org.onosproject.net.meter.MeterService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMultimap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.opencord.olt.internalapi.AccessDeviceMeterService;
import org.opencord.sadis.BandwidthProfileInformation;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * Provisions Meters on access devices.
 */
@Component(immediate = true, property = {
        DELETE_METERS + ":Boolean=" + DELETE_METERS_DEFAULT,
        })
public class OltMeterService implements AccessDeviceMeterService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    /**
     * Delete meters when reference count drops to zero.
     */
    protected boolean deleteMeters = DELETE_METERS_DEFAULT;

    private ApplicationId appId;
    private static final String APP_NAME = "org.opencord.olt";

    private final MeterListener meterListener = new InternalMeterListener();

    private final Logger log = getLogger(getClass());

    protected ExecutorService eventExecutor;

    private Map<DeviceId, Set<BandwidthProfileInformation>> pendingMeters;
    private Map<DeviceId, Map<MeterKey, AtomicInteger>> pendingRemoveMeters;
    ConsistentMultimap<String, MeterKey> bpInfoToMeter;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = Executors.newFixedThreadPool(5, groupedThreads("onos/olt",
                "events-%d", log));
        appId = coreService.registerApplication(APP_NAME);
        modified(context);

        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(MeterKey.class)
                .build();

        bpInfoToMeter = storageService.<String, MeterKey>consistentMultimapBuilder()
                .withName("volt-bp-info-to-meter")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .build();

        meterService.addListener(meterListener);
        componentConfigService.registerProperties(getClass());
        pendingMeters = Maps.newConcurrentMap();
        pendingRemoveMeters = Maps.newConcurrentMap();
        log.info("Olt Meter service started");
    }

    @Deactivate
    public void deactivate() {
        meterService.removeListener(meterListener);
    }


    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

        Boolean d = Tools.isPropertyEnabled(properties, "deleteMeters");
        if (d != null) {
            deleteMeters = d;
        }
    }

    @Override
    public ImmutableMap<String, Collection<MeterKey>> getBpMeterMappings() {
        return bpInfoToMeter.stream()
                .collect(collectingAndThen(
                        groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toSet())),
                        ImmutableMap::copyOf));
    }

    boolean addMeterIdToBpMapping(DeviceId deviceId, MeterId meterId, String bandwidthProfile) {
        log.debug("adding bp {} to meter {} mapping for device {}",
                 bandwidthProfile, meterId, deviceId);
        return bpInfoToMeter.put(bandwidthProfile, MeterKey.key(deviceId, meterId));
    }

    @Override
    public MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile) {
        if (bpInfoToMeter.get(bandwidthProfile).value().isEmpty()) {
            log.warn("Bandwidth Profile '{}' is not currently mapped to a meter",
                    bandwidthProfile);
            return null;
        }

        Optional<? extends MeterKey> meterKeyForDevice = bpInfoToMeter.get(bandwidthProfile).value()
                .stream()
                .filter(meterKey -> meterKey.deviceId().equals(deviceId))
                .findFirst();
        if (meterKeyForDevice.isPresent()) {
            log.debug("Found meter {} for bandwidth profile {} on {}",
                    meterKeyForDevice.get().meterId(), bandwidthProfile, deviceId);
            return meterKeyForDevice.get().meterId();
        } else {
            log.warn("Bandwidth Profile '{}' is not currently mapped to a meter on {} , {}",
                     bandwidthProfile, deviceId, bpInfoToMeter.get(bandwidthProfile).value());
            return null;
        }
    }

    @Override
    public ImmutableSet<MeterKey> getProgMeters() {
        return bpInfoToMeter.stream()
                .map(Map.Entry::getValue)
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                               CompletableFuture<Object> meterFuture) {
        log.debug("Creating meter on {} for {}", deviceId, bpInfo);
        if (bpInfo == null) {
            log.warn("Requested bandwidth profile on {} information is NULL", deviceId);
            meterFuture.complete(ObjectiveError.BADPARAMS);
            return null;
        }

        MeterId meterId = getMeterIdFromBpMapping(deviceId, bpInfo.id());
        if (meterId != null) {
            log.debug("Meter {} was previously created for bp {}", meterId, bpInfo.id());
            meterFuture.complete(null);
            return meterId;
        }

        List<Band> meterBands = createMeterBands(bpInfo);

        final AtomicReference<MeterId> meterIdRef = new AtomicReference<>();
        MeterRequest meterRequest = DefaultMeterRequest.builder()
                .withBands(meterBands)
                .withUnit(Meter.Unit.KB_PER_SEC)
                .withContext(new MeterContext() {
                    @Override
                    public void onSuccess(MeterRequest op) {
                        log.debug("Meter {} for {} is installed on the device {}",
                                  meterIdRef.get(), bpInfo.id(), deviceId);
                        boolean added = addMeterIdToBpMapping(deviceId, meterIdRef.get(), bpInfo.id());
                        if (added) {
                            meterFuture.complete(null);
                        } else {
                            log.error("Failed to add Meter {} for {} on {} to the meter-bandwidth mapping",
                                      meterIdRef.get(), bpInfo.id(), deviceId);
                            meterFuture.complete(ObjectiveError.UNKNOWN);
                        }
                    }

                    @Override
                    public void onError(MeterRequest op, MeterFailReason reason) {
                        log.error("Failed installing meter {} on {} for {}",
                                  meterIdRef.get(), deviceId, bpInfo.id());
                        bpInfoToMeter.remove(bpInfo.id(),
                                             MeterKey.key(deviceId, meterIdRef.get()));
                        meterFuture.complete(reason);
                    }
                })
                .forDevice(deviceId)
                .fromApp(appId)
                .burst()
                .add();

        Meter meter = meterService.submit(meterRequest);
        meterIdRef.set(meter.id());
        log.info("Meter {} created and sent for installation on {} for {}",
                 meter.id(), deviceId, bpInfo);
        return meter.id();
    }

    @Override
    public void removeFromPendingMeters(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        if (deviceId == null) {
            return;
        }
        pendingMeters.computeIfPresent(deviceId, (id, bwps) -> {
            bwps.remove(bwpInfo);
            return bwps;
        });
    }

    @Override
    public synchronized boolean checkAndAddPendingMeter(DeviceId deviceId, BandwidthProfileInformation bwpInfo) {
        if (pendingMeters.containsKey(deviceId)
                && pendingMeters.get(deviceId).contains(bwpInfo)) {
            log.debug("Meter is already pending on {} with bp {}",
                      deviceId, bwpInfo);
            return false;
        }
        log.debug("Adding bandwidth profile {} to pending on {}",
                  bwpInfo, deviceId);
        pendingMeters.compute(deviceId, (id, bwps) -> {
            if (bwps == null) {
                bwps = new HashSet<>();
            }
            bwps.add(bwpInfo);
            return bwps;
        });

        return true;
    }

    @Override
    public void clearMeters(DeviceId deviceId) {
        log.debug("Removing all meters for device {}", deviceId);
        clearDeviceState(deviceId);
        meterService.purgeMeters(deviceId);
    }

    @Override
    public void clearDeviceState(DeviceId deviceId) {
        log.info("Clearing local device state for {}", deviceId);
        pendingRemoveMeters.remove(deviceId);
        removeMetersFromBpMapping(deviceId);
        //Following call handles cornercase of OLT delete during meter provisioning
        pendingMeters.remove(deviceId);
    }

    private List<Band> createMeterBands(BandwidthProfileInformation bpInfo) {
        List<Band> meterBands = new ArrayList<>();

        meterBands.add(createMeterBand(bpInfo.committedInformationRate(), bpInfo.committedBurstSize()));
        meterBands.add(createMeterBand(bpInfo.exceededInformationRate(), bpInfo.exceededBurstSize()));
        meterBands.add(createMeterBand(bpInfo.assuredInformationRate(), 0L));

        return meterBands;
    }

    private Band createMeterBand(long rate, Long burst) {
        return DefaultBand.builder()
                .withRate(rate) //already Kbps
                .burstSize(burst) // already Kbits
                .ofType(Band.Type.DROP) // no matter
                .build();
    }

    private void removeMeterFromBpMapping(MeterKey meterKey) {
        List<Map.Entry<String, MeterKey>> meters = bpInfoToMeter.stream()
                .filter(e -> e.getValue().equals(meterKey))
                .collect(Collectors.toList());

        meters.forEach(e -> bpInfoToMeter.remove(e.getKey(), e.getValue()));
    }

    private void removeMetersFromBpMapping(DeviceId deviceId) {
        List<Map.Entry<String, MeterKey>> meters = bpInfoToMeter.stream()
                .filter(e -> e.getValue().deviceId().equals(deviceId))
                .collect(Collectors.toList());

        meters.forEach(e -> bpInfoToMeter.remove(e.getKey(), e.getValue()));
    }

    private class InternalMeterListener implements MeterListener {

        @Override
        public void event(MeterEvent meterEvent) {
            eventExecutor.execute(() -> {
                Meter meter = meterEvent.subject();
                if (meter == null) {
                    log.error("Meter in event {} is null", meterEvent);
                    return;
                }
                MeterKey key = MeterKey.key(meter.deviceId(), meter.id());
                if (deleteMeters && MeterEvent.Type.METER_REFERENCE_COUNT_ZERO.equals(meterEvent.type())) {
                    log.info("Zero Count Meter Event is received. Meter is {} on {}",
                             meter.id(), meter.deviceId());
                    incrementMeterCount(meter.deviceId(), key);

                    if (appId.equals(meter.appId()) && pendingRemoveMeters.get(meter.deviceId())
                            .get(key).get() == 3) {
                        log.info("Deleting unreferenced, no longer programmed Meter {} on {}",
                                 meter.id(), meter.deviceId());
                        deleteMeter(meter.deviceId(), meter.id());
                    }
                }
                if (MeterEvent.Type.METER_REMOVED.equals(meterEvent.type())) {
                    log.info("Meter Removed Event is received for {} on {}",
                             meter.id(), meter.deviceId());
                    pendingRemoveMeters.computeIfPresent(meter.deviceId(),
                                                (id, meters) -> {
                                                    if (meters.get(key) == null) {
                                                        log.info("Meters is not pending " +
                                                                         "{} on {}", key, id);
                                                        return meters;
                                                    }
                                                    meters.remove(key);
                                                    return meters;
                                                });
                    removeMeterFromBpMapping(key);
                }
            });
        }

        private void incrementMeterCount(DeviceId deviceId, MeterKey key) {
            if (key == null) {
                return;
            }
            pendingRemoveMeters.compute(deviceId,
                    (id, meters) -> {
                        if (meters == null) {
                            meters = new HashMap<>();

                        }
                        if (meters.get(key) == null) {
                            meters.put(key, new AtomicInteger(1));
                        }
                        meters.get(key).addAndGet(1);
                        return meters;
                    });
        }

        private void deleteMeter(DeviceId deviceId, MeterId meterId) {
            Meter meter = meterService.getMeter(deviceId, meterId);
            if (meter != null) {
                MeterRequest meterRequest = DefaultMeterRequest.builder()
                        .withBands(meter.bands())
                        .withUnit(meter.unit())
                        .forDevice(deviceId)
                        .fromApp(appId)
                        .burst()
                        .remove();

                meterService.withdraw(meterRequest, meterId);
            }
        }
    }
}
