/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.smartdataloader.data.JDBCDataStoreFactoryFinder;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcDataStoreMetadataConfig;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCache;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCacheSupport;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.util.logging.Logging;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints that expose manual metadata cache refresh operations for the Smart Data Loader module.
 *
 * <p>All operations target the same Spring-managed {@link JdbcMetadataCache} instance used by
 * {@code JdbcDataStoreMetadata}.
 */
@RestController
@RequestMapping(path = "/rest/smartdataloader/metadata/cache", produces = MediaType.APPLICATION_JSON_VALUE)
public class SmartDataLoaderMetadataCacheController {

    private static final Logger LOGGER = Logging.getLogger(SmartDataLoaderMetadataCacheController.class);

    private final JdbcMetadataCache metadataCache;
    private final Catalog catalog;

    public SmartDataLoaderMetadataCacheController(JdbcMetadataCache metadataCache, Catalog catalog) {
        this.metadataCache = metadataCache;
        this.catalog = catalog;
    }

    /** Clears all cached metadata entries managed by the module cache bean. */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAll() {
        metadataCache.clear();
        LOGGER.info("Cleared Smart Data Loader metadata cache using REST endpoint.");
        return ResponseEntity.ok(response("cleared", "all"));
    }

    /**
     * Invalidates cached metadata for a specific source datastore.
     *
     * @param storeId GeoServer datastore id referenced by Smart Data Loader configuration
     * @param schema optional schema override; when omitted, the datastore schema is used
     */
    @PostMapping(path = "/refresh")
    public ResponseEntity<Map<String, Object>> refreshStore(
            @RequestParam("storeId") String storeId, @RequestParam(value = "schema", required = false) String schema) {

        if (!metadataCache.isEnabled()) {
            LOGGER.fine("REST metadata cache refresh requested while cache is disabled.");
            return ResponseEntity.ok(response("cache-disabled", storeId));
        }

        if (storeId == null || storeId.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Parameter 'storeId' is required.");
        }

        DataStoreInfo storeInfo = catalog.getDataStore(storeId);
        if (storeInfo == null) {
            throw new ResponseStatusException(NOT_FOUND, "Datastore with id '" + storeId + "' was not found.");
        }

        JDBCDataStoreFactory factory = new JDBCDataStoreFactoryFinder().getFactoryFromType(storeInfo.getType());
        if (factory == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Datastore '" + storeInfo.getName() + "' is not backed by a JDBC factory.");
        }

        DataStoreInfo clonedStore;
        JDBCDataStore jdbcDataStore = null;
        try {
            clonedStore = catalog.getResourcePool().clone(storeInfo, true);
            Map<String, Serializable> connectionParameters = clonedStore.getConnectionParameters();
            jdbcDataStore = factory.createDataStore(connectionParameters);
            if (jdbcDataStore == null) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Unable to create JDBC datastore for '" + storeId + "'.");
            }
            String password = Objects.toString(connectionParameters.get("passwd"), "");
            JdbcDataStoreMetadataConfig config = new JdbcDataStoreMetadataConfig(jdbcDataStore, password);
            String selectedSchema = (schema == null || schema.trim().isEmpty()) ? config.getSchema() : schema.trim();
            try (java.sql.Connection connection = jdbcDataStore.getDataSource().getConnection()) {
                String datastoreKey = JdbcMetadataCacheSupport.buildDatastoreId(connection, config);
                metadataCache.invalidateByStore(datastoreKey, selectedSchema);
                LOGGER.log(
                        Level.INFO,
                        "Invalidated Smart Data Loader metadata cache via REST for datastore {0} schema {1}.",
                        new Object[] {storeInfo.getName(), selectedSchema});
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while refreshing Smart Data Loader metadata cache via REST.", e);
            throw new ResponseStatusException(
                    BAD_REQUEST, "Unable to refresh metadata cache for datastore '" + storeId + "'.");
        } finally {
            if (jdbcDataStore != null) {
                jdbcDataStore.dispose();
            }
        }

        return ResponseEntity.ok(response("refreshed", storeId));
    }

    private Map<String, Object> response(String action, String scope) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("action", action);
        payload.put("scope", scope);
        return payload;
    }
}
