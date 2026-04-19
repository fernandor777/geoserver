/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata;

import java.util.logging.Logger;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcDataStoreMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcDataStoreMetadataConfig;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcHelper;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcHelperFactory;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCache;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCacheSupport;
import org.geoserver.smartdataloader.metadata.jdbc.cache.NoOpJdbcMetadataCache;
import org.geotools.util.logging.Logging;

/** Factory class that builds a DataStoreMetadata based on the DataStoreMetadataConfig passed as argument. */
public class DataStoreMetadataFactory {

    private static final Logger LOGGER = Logging.getLogger(DataStoreMetadataFactory.class);

    public DataStoreMetadata getDataStoreMetadata(DataStoreMetadataConfig config) throws Exception {
        return getDataStoreMetadata(config, null);
    }

    public DataStoreMetadata getDataStoreMetadata(DataStoreMetadataConfig config, JdbcHelper jdbcHelper)
            throws Exception {
        if (config.getType().equals(JdbcDataStoreMetadataConfig.TYPE)) {
            JdbcDataStoreMetadataConfig jdmp = (JdbcDataStoreMetadataConfig) config;
            JdbcHelper helper =
                    (jdbcHelper != null) ? jdbcHelper : JdbcHelperFactory.forConnection(jdmp.getConnection());
            DataStoreMetadata store = new JdbcDataStoreMetadata(jdmp, helper, resolveCacheBean());
            store.load();
            return store;
        }
        return null;
    }

    private JdbcMetadataCache resolveCacheBean() {
        JdbcMetadataCache cache = JdbcMetadataCacheSupport.resolveCache(LOGGER);
        return cache != null ? cache : NoOpJdbcMetadataCache.INSTANCE;
    }
}
