/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.smartdataloader.metadata.AttributeMetadata;
import org.geoserver.smartdataloader.metadata.DataStoreMetadataConfig;
import org.geoserver.smartdataloader.metadata.DataStoreMetadataImpl;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.VirtualRelationMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCache;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCacheKey;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataCacheSupport;
import org.geoserver.smartdataloader.metadata.jdbc.cache.JdbcMetadataSnapshot;
import org.geoserver.smartdataloader.metadata.jdbc.cache.NoOpJdbcMetadataCache;
import org.geotools.util.logging.Logging;

/** Concrete class that implements access to JDBC dataStore metadata model, extending DataStoreMetadataImpl. */
public class JdbcDataStoreMetadata extends DataStoreMetadataImpl {

    private static final Logger LOGGER = Logging.getLogger(JdbcDataStoreMetadata.class);

    private final JdbcHelper jdbcHelper;
    private final JdbcMetadataCache metadataCache;

    public JdbcDataStoreMetadata(DataStoreMetadataConfig config, JdbcHelper jdbcHelper) {
        this(config, jdbcHelper, NoOpJdbcMetadataCache.INSTANCE);
    }

    public JdbcDataStoreMetadata(
            DataStoreMetadataConfig config, JdbcHelper jdbcHelper, JdbcMetadataCache metadataCache) {
        super(config);
        this.jdbcHelper = Objects.requireNonNull(jdbcHelper, "jdbcHelper must not be null");
        this.metadataCache = metadataCache == null ? NoOpJdbcMetadataCache.INSTANCE : metadataCache;
    }

    @Override
    public void load() throws Exception {
        JdbcDataStoreMetadataConfig jdbcConfig = (JdbcDataStoreMetadataConfig) this.config;
        Connection connection = jdbcConfig.getConnection();
        JdbcMetadataCacheKey cacheKey = buildCacheKey(jdbcConfig, connection);

        Optional<JdbcMetadataSnapshot> cached = metadataCache.get(cacheKey);
        if (cached.isPresent()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Using cached JDBC metadata snapshot key={0}", cacheKey.asLogToken());
            }
            rehydrateFromSnapshot(connection, cached.get());
            return;
        }

        long start = System.currentTimeMillis();
        loadFromDatabase(jdbcConfig, connection);
        long loadedFromDbAt = System.currentTimeMillis();
        JdbcMetadataSnapshot snapshot = createSnapshot();
        long snapshotAt = System.currentTimeMillis();
        metadataCache.put(cacheKey, snapshot);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(
                    Level.FINE,
                    "Loaded JDBC metadata from database and cached key={0} in {1} ms (dbLoad={2} ms, snapshot={3} ms, tables={4}, relations={5})",
                    new Object[] {
                        cacheKey.asLogToken(),
                        System.currentTimeMillis() - start,
                        loadedFromDbAt - start,
                        snapshotAt - loadedFromDbAt,
                        entities != null ? entities.size() : 0,
                        relations != null ? relations.size() : 0
                    });
        }
    }

    @Override
    public EntityMetadata getEntityMetadata(String name) {
        Iterator<EntityMetadata> ie = this.entities.iterator();
        while (ie.hasNext()) {
            EntityMetadata e = ie.next();
            if (e.getName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return config.getName();
    }

    private void loadFromDatabase(JdbcDataStoreMetadataConfig jdbcConfig, Connection connection) throws Exception {
        entities = new ArrayList<>();
        long tablesStart = System.currentTimeMillis();
        List<JdbcTableMetadata> tableList = jdbcHelper.getSchemaTables(connection, jdbcConfig.getSchema());
        entities.addAll(tableList);
        long tablesLoadedAt = System.currentTimeMillis();

        relations = new ArrayList<>();
        long columnsTimeMs = 0L;
        long relationsTimeMs = 0L;
        Iterator<JdbcTableMetadata> iTables = tableList.iterator();
        while (iTables.hasNext()) {
            JdbcTableMetadata jTable = iTables.next();
            long columnsStart = System.currentTimeMillis();
            List<AttributeMetadata> attributes = jdbcHelper.getColumnsByTable(connection, jTable);
            if (attributes != null) {
                attributes.forEach(jTable::addAttribute);
            }
            jTable.setAttributesLoaded(true);
            columnsTimeMs += (System.currentTimeMillis() - columnsStart);

            long relationsStart = System.currentTimeMillis();
            List<RelationMetadata> tableRelations = jdbcHelper.getRelationsByTable(connection, jTable);
            if (tableRelations != null) {
                tableRelations.forEach(relationMetadata -> {
                    jTable.addRelation(relationMetadata);
                    relations.add(relationMetadata);
                });
            }
            jTable.setRelationsLoaded(true);
            relationsTimeMs += (System.currentTimeMillis() - relationsStart);
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(
                    Level.FINE,
                    "JDBC metadata load phases: tables={0} ms, columns={1} ms, relations={2} ms, tableCount={3}",
                    new Object[] {tablesLoadedAt - tablesStart, columnsTimeMs, relationsTimeMs, tableList.size()});
        }
    }

    private JdbcMetadataCacheKey buildCacheKey(JdbcDataStoreMetadataConfig jdbcConfig, Connection connection)
            throws Exception {
        String datastoreId = JdbcMetadataCacheSupport.buildDatastoreId(connection, jdbcConfig);
        return new JdbcMetadataCacheKey(datastoreId, jdbcConfig.getSchema(), jdbcHelper.cacheFingerprint());
    }

    private JdbcMetadataSnapshot createSnapshot() {
        List<JdbcMetadataSnapshot.TableSnapshot> tableSnapshots = new ArrayList<>();
        List<JdbcMetadataSnapshot.AttributeSnapshot> attributeSnapshots = new ArrayList<>();
        List<JdbcMetadataSnapshot.RelationSnapshot> relationSnapshots = new ArrayList<>();

        Map<JdbcTableMetadata, JdbcMetadataSnapshot.TableSnapshot> tableToSnapshot = new HashMap<>();

        for (EntityMetadata entity : entities) {
            if (!(entity instanceof JdbcTableMetadata)) {
                continue;
            }
            JdbcTableMetadata table = (JdbcTableMetadata) entity;
            JdbcMetadataSnapshot.TableSnapshot tableSnapshot =
                    new JdbcMetadataSnapshot.TableSnapshot(table.getCatalog(), table.getSchema(), table.getName());
            tableSnapshots.add(tableSnapshot);
            tableToSnapshot.put(table, tableSnapshot);

            for (AttributeMetadata attribute : table.getAttributes()) {
                attributeSnapshots.add(new JdbcMetadataSnapshot.AttributeSnapshot(
                        tableSnapshot,
                        attribute.getName(),
                        attribute.getType(),
                        attribute.isExternalReference(),
                        attribute.isIdentifier()));
            }
        }

        for (EntityMetadata entity : entities) {
            if (!(entity instanceof JdbcTableMetadata)) {
                continue;
            }
            JdbcTableMetadata ownerTable = (JdbcTableMetadata) entity;
            JdbcMetadataSnapshot.TableSnapshot ownerSnapshot = tableToSnapshot.get(ownerTable);
            if (ownerSnapshot == null) {
                continue;
            }
            for (RelationMetadata relation : ownerTable.getRelations()) {
                if (relation == null
                        || relation.getSourceAttribute() == null
                        || relation.getDestinationAttribute() == null) {
                    continue;
                }
                if (!(relation.getSourceAttribute().getEntity() instanceof JdbcTableMetadata)
                        || !(relation.getDestinationAttribute().getEntity() instanceof JdbcTableMetadata)) {
                    continue;
                }
                JdbcTableMetadata sourceTable =
                        (JdbcTableMetadata) relation.getSourceAttribute().getEntity();
                JdbcTableMetadata destinationTable =
                        (JdbcTableMetadata) relation.getDestinationAttribute().getEntity();
                JdbcMetadataSnapshot.TableSnapshot sourceSnapshot = tableToSnapshot.get(sourceTable);
                JdbcMetadataSnapshot.TableSnapshot destinationSnapshot = tableToSnapshot.get(destinationTable);
                if (sourceSnapshot == null || destinationSnapshot == null) {
                    continue;
                }
                String relationName = relation instanceof JdbcRelationMetadata
                        ? ((JdbcRelationMetadata) relation).getName()
                        : (relation instanceof VirtualRelationMetadata
                                ? ((VirtualRelationMetadata) relation).getName()
                                : null);
                relationSnapshots.add(new JdbcMetadataSnapshot.RelationSnapshot(
                        relationName,
                        relation.getRelationType(),
                        ownerSnapshot,
                        sourceSnapshot,
                        relation.getSourceAttribute().getName(),
                        destinationSnapshot,
                        relation.getDestinationAttribute().getName()));
            }
        }

        return new JdbcMetadataSnapshot(tableSnapshots, attributeSnapshots, relationSnapshots);
    }

    private void rehydrateFromSnapshot(Connection connection, JdbcMetadataSnapshot snapshot) {
        entities = new ArrayList<>();
        relations = new ArrayList<>();

        Map<JdbcMetadataSnapshot.TableSnapshot, JdbcTableMetadata> tableMap = new HashMap<>();
        for (JdbcMetadataSnapshot.TableSnapshot tableSnapshot : snapshot.getTables()) {
            JdbcTableMetadata table = new JdbcTableMetadata(
                    connection,
                    tableSnapshot.getCatalog(),
                    tableSnapshot.getSchema(),
                    tableSnapshot.getName(),
                    jdbcHelper);
            entities.add(table);
            tableMap.put(tableSnapshot, table);
        }

        Map<String, AttributeMetadata> attributeMap = new HashMap<>();
        for (JdbcMetadataSnapshot.AttributeSnapshot attributeSnapshot : snapshot.getAttributes()) {
            JdbcTableMetadata table = tableMap.get(attributeSnapshot.getTable());
            if (table == null) {
                continue;
            }
            JdbcColumnMetadata column = new JdbcColumnMetadata(
                    table,
                    attributeSnapshot.getName(),
                    attributeSnapshot.getType(),
                    attributeSnapshot.isExternalReference(),
                    attributeSnapshot.isIdentifier());
            table.addAttribute(column);
            attributeMap.put(attributeKey(attributeSnapshot.getTable(), attributeSnapshot.getName()), column);
        }

        for (JdbcMetadataSnapshot.RelationSnapshot relationSnapshot : snapshot.getRelations()) {
            AttributeMetadata source = attributeMap.get(
                    attributeKey(relationSnapshot.getSourceTable(), relationSnapshot.getSourceColumn()));
            AttributeMetadata destination = attributeMap.get(
                    attributeKey(relationSnapshot.getDestinationTable(), relationSnapshot.getDestinationColumn()));
            if (source == null || destination == null) {
                continue;
            }
            RelationMetadata relation = new VirtualRelationMetadata(
                    relationSnapshot.getType(), source, destination, relationSnapshot.getName());
            JdbcTableMetadata owner = tableMap.get(relationSnapshot.getOwnerTable());
            if (owner != null) {
                owner.addRelation(relation);
            } else {
                source.getEntity().addRelation(relation);
            }
            relations.add(relation);
        }

        for (EntityMetadata entity : entities) {
            if (entity instanceof JdbcTableMetadata) {
                JdbcTableMetadata table = (JdbcTableMetadata) entity;
                table.setAttributesLoaded(true);
                table.setRelationsLoaded(true);
            }
        }
    }

    private String attributeKey(JdbcMetadataSnapshot.TableSnapshot table, String column) {
        return Objects.toString(table.getCatalog(), "")
                + "|"
                + Objects.toString(table.getSchema(), "")
                + "|"
                + Objects.toString(table.getName(), "")
                + "|"
                + Objects.toString(column, "");
    }
}
