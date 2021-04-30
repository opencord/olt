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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import org.onlab.packet.ChassisId;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.meter.MeterId;
import org.onosproject.store.service.AsyncConsistentMultimap;
import org.onosproject.store.service.ConsistentMultimap;
import org.onosproject.store.service.ConsistentMultimapBuilder;
import org.onosproject.store.service.MultimapEventListener;
import org.onosproject.store.service.TestConsistentMultimap;
import org.onosproject.store.service.Versioned;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TestBase {

    protected static final String CLIENT_NAS_PORT_ID = "PON 1/1";
    protected static final String CLIENT_CIRCUIT_ID = "CIR-PON 1/1";
    protected static final String OLT_DEV_ID = "of:00000000000000aa";
    protected static final DeviceId DEVICE_ID_1 = DeviceId.deviceId(OLT_DEV_ID);
    protected MeterId usMeterId = MeterId.meterId(1);
    protected MeterId dsMeterId = MeterId.meterId(2);
    protected String usBpId = "HSIA-US";
    protected String dsBpId = "HSIA-DS";
    protected DefaultApplicationId appId = new DefaultApplicationId(1, "OltServices");

    protected static Device olt = new DefaultDevice(null, DeviceId.deviceId(OLT_DEV_ID), Device.Type.SWITCH,
            "VOLTHA Project", "open_pon", "open_pon", "BBSIM_OLT_1", new ChassisId("a0a0a0a0a01"));

    Map<String, BandwidthProfileInformation> bpInformation = Maps.newConcurrentMap();

    protected void addBandwidthProfile(String id) {
        BandwidthProfileInformation bpInfo = new BandwidthProfileInformation();
        bpInfo.setGuaranteedInformationRate(0);
        bpInfo.setCommittedInformationRate(10000);
        bpInfo.setCommittedBurstSize(1000L);
        bpInfo.setExceededBurstSize(2000L);
        bpInfo.setExceededInformationRate(20000);
        bpInformation.put(id, bpInfo);
    }

    protected class MockSadisService implements SadisService {

        @Override
        public BaseInformationService<SubscriberAndDeviceInformation> getSubscriberInfoService() {
            return new MockSubService();
        }

        @Override
        public BaseInformationService<BandwidthProfileInformation> getBandwidthProfileService() {
            return new MockBpService();
        }
    }

    private class MockBpService implements BaseInformationService<BandwidthProfileInformation> {
        @Override
        public void clearLocalData() {

        }

        @Override
        public void invalidateAll() {

        }

        @Override
        public void invalidateId(String id) {

        }

        @Override
        public BandwidthProfileInformation get(String id) {
            return bpInformation.get(id);
        }

        @Override
        public BandwidthProfileInformation getfromCache(String id) {
            return null;
        }
    }

    private class MockSubService implements BaseInformationService<SubscriberAndDeviceInformation> {
        MockSubscriberAndDeviceInformation sub =
                new MockSubscriberAndDeviceInformation(CLIENT_NAS_PORT_ID,
                        CLIENT_NAS_PORT_ID, CLIENT_CIRCUIT_ID, null, null);

        @Override
        public SubscriberAndDeviceInformation get(String id) {
            return sub;
        }

        @Override
        public void clearLocalData() {

        }

        @Override
        public void invalidateAll() {
        }

        @Override
        public void invalidateId(String id) {
        }

        @Override
        public SubscriberAndDeviceInformation getfromCache(String id) {
            return null;
        }
    }

    private class MockSubscriberAndDeviceInformation extends SubscriberAndDeviceInformation {

        MockSubscriberAndDeviceInformation(String id, String nasPortId,
                                           String circuitId, MacAddress hardId,
                                           Ip4Address ipAddress) {
            this.setHardwareIdentifier(hardId);
            this.setId(id);
            this.setIPAddress(ipAddress);
            this.setNasPortId(nasPortId);
            this.setCircuitId(circuitId);
        }
    }

    class MockConsistentMultimap<K, V> implements ConsistentMultimap<K, V> {
        private HashMultimap<K, Versioned<V>> innermap;
        private AtomicLong counter = new AtomicLong();

        public MockConsistentMultimap() {
            this.innermap = HashMultimap.create();
        }

        private Versioned<V> version(V v) {
            return new Versioned<>(v, counter.incrementAndGet(), System.currentTimeMillis());
        }

        private Versioned<Collection<? extends V>> versionCollection(Collection<? extends V> collection) {
            return new Versioned<>(collection, counter.incrementAndGet(), System.currentTimeMillis());
        }

        @Override
        public int size() {
            return innermap.size();
        }

        @Override
        public boolean isEmpty() {
            return innermap.isEmpty();
        }

        @Override
        public boolean containsKey(K key) {
            return innermap.containsKey(key);
        }

        @Override
        public boolean containsValue(V value) {
            return innermap.containsValue(value);
        }

        @Override
        public boolean containsEntry(K key, V value) {
            return innermap.containsEntry(key, value);
        }

        @Override
        public boolean put(K key, V value) {
            return innermap.put(key, version(value));
        }

        @Override
        public Versioned<Collection<? extends V>> putAndGet(K key, V value) {
            innermap.put(key, version(value));
            return (Versioned<Collection<? extends V>>) innermap.get(key);
        }

        @Override
        public boolean remove(K key, V value) {
            return innermap.remove(key, value);
        }

        @Override
        public Versioned<Collection<? extends V>> removeAndGet(K key, V value) {
            innermap.remove(key, value);
            return (Versioned<Collection<? extends V>>) innermap.get(key);
        }

        @Override
        public boolean removeAll(K key, Collection<? extends V> values) {
            return false;
        }

        @Override
        public Versioned<Collection<? extends V>> removeAll(K key) {
            return null;
        }

        @Override
        public boolean putAll(K key, Collection<? extends V> values) {
            return false;
        }

        @Override
        public Versioned<Collection<? extends V>> replaceValues(K key, Collection<V> values) {
            return null;
        }

        @Override
        public void clear() {
            innermap.clear();
        }

        @Override
        public Versioned<Collection<? extends V>> get(K key) {
            Collection<? extends V> values = innermap.get(key).stream()
                    .map(v -> v.value())
                    .collect(Collectors.toList());
            return versionCollection(values);
        }

        @Override
        public Set<K> keySet() {
            return innermap.keySet();
        }

        @Override
        public Multiset<K> keys() {
            return innermap.keys();
        }

        @Override
        public Multiset<V> values() {
            return null;
        }

        @Override
        public Collection<Map.Entry<K, V>> entries() {
            return null;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new ConsistentMultimapIterator(innermap.entries().iterator());
        }

        @Override
        public Map<K, Collection<V>> asMap() {
            return null;
        }

        @Override
        public void addListener(MultimapEventListener<K, V> listener, Executor executor) {
        }

        @Override
        public void removeListener(MultimapEventListener<K, V> listener) {
        }

        @Override
        public String name() {
            return "mock multimap";
        }

        @Override
        public Type primitiveType() {
            return null;
        }

        private class ConsistentMultimapIterator implements Iterator<Map.Entry<K, V>> {

            private final Iterator<Map.Entry<K, Versioned<V>>> it;

            public ConsistentMultimapIterator(Iterator<Map.Entry<K, Versioned<V>>> it) {
                this.it = it;
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
                Map.Entry<K, Versioned<V>> e = it.next();
                return new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().value());
            }
        }

    }

    public static TestConsistentMultimap.Builder builder() {
        return new TestConsistentMultimap.Builder();
    }

    public static class Builder<K, V> extends ConsistentMultimapBuilder<K, V> {

        @Override
        public AsyncConsistentMultimap<K, V> buildMultimap() {
            return null;
        }

        @Override
        public ConsistentMultimap<K, V> build() {
            return new TestConsistentMultimap<K, V>();
        }
    }
}
