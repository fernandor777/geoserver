/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.util.Optional;

/** No-op implementation used as safe fallback when no cache bean is available. */
public class NoOpJdbcMetadataCache implements JdbcMetadataCache {

    public static final NoOpJdbcMetadataCache INSTANCE = new NoOpJdbcMetadataCache();

    private NoOpJdbcMetadataCache() {}

    @Override
    public Optional<JdbcMetadataSnapshot> get(JdbcMetadataCacheKey key) {
        return Optional.empty();
    }

    @Override
    public void put(JdbcMetadataCacheKey key, JdbcMetadataSnapshot snapshot) {
        // no-op
    }

    @Override
    public void invalidate(JdbcMetadataCacheKey key) {
        // no-op
    }

    @Override
    public void invalidateByStore(String datastoreId, String schema) {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
