/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcDataStoreMetadataConfig;

/**
 * Utility methods for resolving the shared metadata cache bean and building stable datastore identifiers used as part
 * of cache keys and invalidation hooks.
 */
public final class JdbcMetadataCacheSupport {

    private JdbcMetadataCacheSupport() {
        // utility class
    }

    /**
     * Resolves the module metadata cache bean from the Spring context, returning a no-op implementation when missing.
     */
    public static JdbcMetadataCache resolveCache(Logger logger) {
        try {
            JdbcMetadataCache cache = GeoServerExtensions.bean(JdbcMetadataCache.class);
            if (cache != null) {
                return cache;
            }
        } catch (Exception e) {
            log(logger, Level.FINER, "Unable to resolve JdbcMetadataCache bean by type", e);
        }

        try {
            Object bean = GeoServerExtensions.bean("smartDataLoaderJdbcMetadataCache");
            if (bean instanceof JdbcMetadataCache) {
                return (JdbcMetadataCache) bean;
            }
        } catch (Exception e) {
            log(logger, Level.FINER, "Unable to resolve JdbcMetadataCache bean by name", e);
        }

        return NoOpJdbcMetadataCache.INSTANCE;
    }

    /**
     * Builds a datastore identity token from JDBC metadata and configuration fields.
     *
     * <p>The resulting string intentionally excludes secrets and is deterministic across requests for the same logical
     * datastore.
     */
    public static String buildDatastoreId(Connection connection, JdbcDataStoreMetadataConfig config) throws Exception {
        DatabaseMetaData metaData = connection != null ? connection.getMetaData() : null;
        String url = metaData != null ? metaData.getURL() : "";
        String user = metaData != null ? metaData.getUserName() : "";
        return Objects.toString(url, "")
                + "|"
                + Objects.toString(user, "")
                + "|"
                + Objects.toString(config != null ? config.getCatalog() : null, "")
                + "|"
                + Objects.toString(config != null ? config.getName() : null, "");
    }

    private static void log(Logger logger, Level level, String message, Exception e) {
        if (logger != null && logger.isLoggable(level)) {
            logger.log(level, message, e);
        }
    }
}
