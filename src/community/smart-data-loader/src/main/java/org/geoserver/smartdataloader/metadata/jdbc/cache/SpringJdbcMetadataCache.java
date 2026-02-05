/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/** Spring-managed in-memory cache with TTL semantics for datastore metadata snapshots. */
public class SpringJdbcMetadataCache implements JdbcMetadataCache {

    public static final String PROP_ENABLED = "smartdataloader.metadata.cache.enabled";
    public static final String ENV_ENABLED = "SMART_DATALOADER_METADATA_CACHE_ENABLED";
    public static final String PROP_TTL_SECONDS = "smartdataloader.metadata.cache.ttl.seconds";
    public static final String ENV_TTL_SECONDS = "SMART_DATALOADER_METADATA_CACHE_TTL_SECONDS";
    public static final String PROP_MAX_ENTRIES = "smartdataloader.metadata.cache.max.entries";
    public static final String ENV_MAX_ENTRIES = "SMART_DATALOADER_METADATA_CACHE_MAX_ENTRIES";

    private static final Logger LOGGER = Logging.getLogger(SpringJdbcMetadataCache.class);
    private static final boolean DEFAULT_ENABLED = true;
    private static final long DEFAULT_TTL_SECONDS = 900L;
    private static final int DEFAULT_MAX_ENTRIES = 256;

    private final Object lock = new Object();
    private final boolean enabled;
    private final long ttlMillis;
    private final int maxEntries;
    private final LinkedHashMap<JdbcMetadataCacheKey, Entry> entries;

    public SpringJdbcMetadataCache() {
        this.enabled = readBoolean(PROP_ENABLED, ENV_ENABLED, DEFAULT_ENABLED);
        this.ttlMillis = readLongSeconds(PROP_TTL_SECONDS, ENV_TTL_SECONDS, DEFAULT_TTL_SECONDS) * 1000L;
        this.maxEntries = readInt(PROP_MAX_ENTRIES, ENV_MAX_ENTRIES, DEFAULT_MAX_ENTRIES);
        this.entries = new LinkedHashMap<JdbcMetadataCacheKey, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<JdbcMetadataCacheKey, Entry> eldest) {
                boolean remove = size() > SpringJdbcMetadataCache.this.maxEntries;
                if (remove && LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.log(
                            Level.FINER,
                            "Evicting eldest JDBC metadata cache entry {0}",
                            eldest.getKey().asLogToken());
                }
                return remove;
            }
        };
        LOGGER.log(
                Level.INFO,
                "JDBC metadata cache initialized: enabled={0}, ttlSeconds={1}, maxEntries={2}",
                new Object[] {enabled, ttlMillis / 1000L, maxEntries});
    }

    @Override
    public Optional<JdbcMetadataSnapshot> get(JdbcMetadataCacheKey key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry == null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "JDBC metadata cache miss for key {0}", key.asLogToken());
                }
                return Optional.empty();
            }
            if (isExpired(entry, System.currentTimeMillis())) {
                entries.remove(key);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "JDBC metadata cache expired for key {0}", key.asLogToken());
                }
                return Optional.empty();
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "JDBC metadata cache hit for key {0}", key.asLogToken());
            }
            return Optional.of(entry.snapshot);
        }
    }

    @Override
    public void put(JdbcMetadataCacheKey key, JdbcMetadataSnapshot snapshot) {
        if (!enabled || key == null || snapshot == null) {
            return;
        }
        synchronized (lock) {
            purgeExpired(System.currentTimeMillis());
            entries.put(key, new Entry(snapshot, System.currentTimeMillis()));
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Stored JDBC metadata cache entry for key {0}", key.asLogToken());
        }
    }

    @Override
    public void invalidate(JdbcMetadataCacheKey key) {
        if (key == null) {
            return;
        }
        synchronized (lock) {
            entries.remove(key);
        }
        LOGGER.log(Level.INFO, "Invalidated JDBC metadata cache entry for key {0}", key.asLogToken());
    }

    @Override
    public void invalidateByStore(String datastoreId, String schema) {
        synchronized (lock) {
            Iterator<Map.Entry<JdbcMetadataCacheKey, Entry>> iterator =
                    entries.entrySet().iterator();
            while (iterator.hasNext()) {
                JdbcMetadataCacheKey key = iterator.next().getKey();
                if (equalsNullable(datastoreId, key.getDatastoreId()) && equalsNullable(schema, key.getSchema())) {
                    iterator.remove();
                }
            }
        }
        LOGGER.log(Level.INFO, "Invalidated JDBC metadata cache for datastoreId={0}, schema={1}", new Object[] {
            datastoreId, schema
        });
    }

    @Override
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
        LOGGER.info("Cleared JDBC metadata cache.");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void purgeExpired(long now) {
        Iterator<Map.Entry<JdbcMetadataCacheKey, Entry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (isExpired(entry, now)) {
                iterator.remove();
            }
        }
    }

    private boolean isExpired(Entry entry, long now) {
        return ttlMillis > 0L && (now - entry.createdAtMillis) > ttlMillis;
    }

    private static boolean equalsNullable(String expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return actual == null || actual.isEmpty();
        }
        return expected.equals(actual);
    }

    private static boolean readBoolean(String propertyName, String envName, boolean defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(envName);
        }
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int readInt(String propertyName, String envName, int defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(envName);
        }
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long readLongSeconds(String propertyName, String envName, long defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(envName);
        }
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class Entry {
        private final JdbcMetadataSnapshot snapshot;
        private final long createdAtMillis;

        private Entry(JdbcMetadataSnapshot snapshot, long createdAtMillis) {
            this.snapshot = snapshot;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
