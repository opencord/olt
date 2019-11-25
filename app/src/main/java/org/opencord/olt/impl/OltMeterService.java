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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
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
import org.opencord.olt.internalapi.AccessDeviceMeterService;
import org.opencord.sadis.BandwidthProfileInformation;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;

import java.util.ArrayList;

import java.util.Dictionary;
import java.util.Iterator;
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

import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

@Service
@Component(immediate = true)
public class OltMeterService implements AccessDeviceMeterService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterService meterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentConfigService componentConfigService;

    @Property(name = "deleteMeters", boolValue = true,
            label = "Deleting Meters based on flow count statistics")
    protected boolean deleteMeters = true;

    protected Map<String, Set<MeterKey>> bpInfoToMeter;
    protected Set<MeterKey> programmedMeters;
    private ApplicationId appId;
    private static final String APP_NAME = "org.opencord.olt";

    private final MeterListener meterListener = new InternalMeterListener();

    private final Logger log = getLogger(getClass());

    protected ExecutorService eventExecutor;

    @Activate
    public void activate(ComponentContext context) {
        eventExecutor = Executors.newFixedThreadPool(5, groupedThreads("onos/olt",
                "events-%d", log));
        appId = coreService.registerApplication(APP_NAME);
        bpInfoToMeter = Maps.newConcurrentMap();
        programmedMeters = Sets.newConcurrentHashSet();
        meterService.addListener(meterListener);
        componentConfigService.registerProperties(getClass());
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
    public ImmutableMap<String, Set<MeterKey>> getBpMeterMappings() {
        return ImmutableMap.copyOf(bpInfoToMeter);
    }

    @Override
    public void addMeterIdToBpMapping(DeviceId deviceId, MeterId meterId, String bandwidthProfile) {
        bpInfoToMeter.compute(bandwidthProfile, (k, v) -> {
            if (v == null) {
                return Sets.newHashSet(MeterKey.key(deviceId, meterId));
            } else {
                v.add(MeterKey.key(deviceId, meterId));
                return v;
            }
        });
    }

    @Override
    public MeterId getMeterIdFromBpMapping(DeviceId deviceId, String bandwidthProfile) {
        if (bpInfoToMeter.get(bandwidthProfile) == null) {
            log.warn("Bandwidth Profile '{}' is not currently mapped to a meter",
                    bandwidthProfile);
            return null;
        }

        Optional<MeterKey> meterKeyForDevice = bpInfoToMeter.get(bandwidthProfile)
                .stream()
                .filter(meterKey -> meterKey.deviceId().equals(deviceId))
                .findFirst();
        if (meterKeyForDevice.isPresent()) {
            log.debug("Found meter {} for bandwidth profile {}",
                    meterKeyForDevice.get().meterId(), bandwidthProfile);
            return meterKeyForDevice.get().meterId();
        } else {
            log.warn("Bandwidth profile '{}' is not currently mapped to a meter",
                    bandwidthProfile);
            return null;
        }
    }

    @Override
    public ImmutableSet<MeterKey> getProgMeters() {
        return ImmutableSet.copyOf(programmedMeters);
    }

    @Override
    public MeterId createMeter(DeviceId deviceId, BandwidthProfileInformation bpInfo,
                               CompletableFuture<Object> meterFuture) {
        if (bpInfo == null) {
            log.warn("Requested bandwidth profile information is NULL");
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
                        meterFuture.complete(null);
                    }

                    @Override
                    public void onError(MeterRequest op, MeterFailReason reason) {
                        bpInfoToMeter.remove(MeterKey.key(deviceId, meterIdRef.get()));
                        meterFuture.complete(reason);
                    }
                })
                .forDevice(deviceId)
                .fromApp(appId)
                .burst()
                .add();

        Meter meter = meterService.submit(meterRequest);
        meterIdRef.set(meter.id());
        addMeterIdToBpMapping(deviceId, meterIdRef.get(), bpInfo.id());
        programmedMeters.add(MeterKey.key(deviceId, meter.id()));
        log.info("Meter is created. Meter Id {}", meter.id());
        return meter.id();
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

    private class InternalMeterListener implements MeterListener {

        Map<MeterKey, AtomicInteger> pendingRemoveMeters = Maps.newConcurrentMap();

        @Override
        public void event(MeterEvent meterEvent) {
            eventExecutor.execute(() -> {
                Meter meter = meterEvent.subject();
                MeterKey key = MeterKey.key(meter.deviceId(), meter.id());
                if (deleteMeters && MeterEvent.Type.METER_REFERENCE_COUNT_ZERO.equals(meterEvent.type())) {
                    log.info("Zero Count Meter Event is received. Meter is {}", meter.id());
                    incrementMeterCount(key);

                    if (meter != null && appId.equals(meter.appId()) && pendingRemoveMeters.get(key).get() == 3) {
                        log.info("Deleting unreferenced, no longer programmed Meter {}", meter.id());
                        deleteMeter(meter.deviceId(), meter.id());
                    }
                }
                if (MeterEvent.Type.METER_REMOVED.equals(meterEvent.type())) {
                    log.info("Meter Removed Event is received for {}", meter.id());
                    programmedMeters.remove(key);
                    pendingRemoveMeters.remove(key);
                    removeMeterFromBpMapping(meter);
                }
            });
        }

        private void incrementMeterCount(MeterKey key) {
            if (key == null) {
                return;
            }
            pendingRemoveMeters.compute(key,
                    (k, v) -> {
                        if (v == null) {
                            return new AtomicInteger(1);
                        }
                        v.addAndGet(1);
                        return v;
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

        private void removeMeterFromBpMapping(Meter meter) {
            MeterKey meterKey = MeterKey.key(meter.deviceId(), meter.id());
            Iterator<Map.Entry<String, Set<MeterKey>>> iterator = bpInfoToMeter.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<MeterKey>> entry = iterator.next();
                if (entry.getValue().contains(meterKey)) {
                    iterator.remove();
                    log.info("Deleted meter for MeterKey {} - Last prog meters {}", meterKey, programmedMeters);
                    break;
                }
            }
        }
    }
}
