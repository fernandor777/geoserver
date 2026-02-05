/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.util.Optional;

/** Cache abstraction for JDBC datastore metadata snapshots. */
public interface JdbcMetadataCache {

    Optional<JdbcMetadataSnapshot> get(JdbcMetadataCacheKey key);

    void put(JdbcMetadataCacheKey key, JdbcMetadataSnapshot snapshot);

    void invalidate(JdbcMetadataCacheKey key);

    void invalidateByStore(String datastoreId, String schema);

    void clear();

    boolean isEnabled();
}
