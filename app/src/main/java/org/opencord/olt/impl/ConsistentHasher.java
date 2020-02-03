package org.opencord.olt.impl;

import com.google.common.hash.Hashing;
import org.onosproject.cluster.NodeId;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Returns a server hosting a given key based on consistent hashing.
 */
public class ConsistentHasher {

    private static class Entry implements Comparable<Entry> {
        private final NodeId server;
        private final int hash;

        public Entry(NodeId server, int hash) {
            this.server = server;
            this.hash = hash;
        }

        public Entry(int hash) {
            server = null;
            this.hash = hash;
        }

        @Override
        public int compareTo(Entry o) {
            if (this.hash > o.hash) {
                return 1;
            } else if (this.hash < o.hash) {
                return -1;
            } // else
            return 0;
        }
    }

    private final int weight;

    private List<Entry> table;

    /**
     * Creates a new hasher with the given list of servers.
     *
     * @param servers list of servers
     * @param weight weight
     */
    public ConsistentHasher(List<NodeId> servers, int weight) {
        this.weight = weight;

        this.table = new ArrayList<>();

        servers.forEach(this::addServer);
    }

    /**
     * Adds a new server to the list of servers.
     *
     * @param server server ID
     */
    public synchronized void addServer(NodeId server) {
        // Add weight number of buckets for each server
        for (int i = 0; i < weight; i++) {
            String label = server.toString() + i;
            int hash = getHash(label);
            Entry e = new Entry(server, hash);
            table.add(e);
        }

        Collections.sort(table);
    }

    /**
     * Removes a server from the list of servers.
     *
     * @param server server ID
     */
    public synchronized void removeServer(NodeId server) {
        table.removeIf(e -> e.server.equals(server));
    }

    /**
     * Hashes a given input string and finds that server that should
     * handle the given ID.
     *
     * @param s input
     * @return server ID
     */
    public synchronized NodeId hash(String s) {
        Entry temp = new Entry(getHash(s));

        int pos = Collections.binarySearch(this.table, temp);

        // translate a negative not-found result into the closest following match
        if (pos < 0) {
            pos = Math.abs(pos + 1);
        }

        // wraparound if the hash was after the last entry in the table
        if (pos == this.table.size()) {
            pos = 0;
        }

        return table.get(pos).server;
    }

    private int getHash(String s) {
        // stable, uniformly-distributed hash
        return Hashing.murmur3_128().hashString(s, Charset.defaultCharset()).asInt();
    }
}
