/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import java.sql.Connection;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import org.geoserver.smartdataloader.domain.entities.DomainRelationType;
import org.geoserver.smartdataloader.metadata.AttributeMetadata;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcForeignKeyConstraintMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcPrimaryKeyConstraintMetadata;

/** JdbcHelper wrapper that triggers a full PostgreSQL metadata preload on first use. */
public class OnDemandPostgresCatalogJdbcHelper implements JdbcHelper {

    private final PostgresCatalogJdbcHelper delegate;
    private final Object initLock = new Object();
    private volatile boolean initialized;

    public OnDemandPostgresCatalogJdbcHelper() {
        this(new PostgresCatalogJdbcHelper());
    }

    public OnDemandPostgresCatalogJdbcHelper(PostgresCatalogJdbcHelper delegate) {
        this.delegate = delegate;
    }

    private void ensureInitialized(Connection connection) throws Exception {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            delegate.preloadAllSchemas(connection);
            initialized = true;
        }
    }

    @Override
    public List<JdbcTableMetadata> getSchemaTables(Connection connection, String schema) throws Exception {
        ensureInitialized(connection);
        return delegate.getSchemaTables(connection, schema);
    }

    @Override
    public List<JdbcTableMetadata> getTables(Connection connection) throws Exception {
        ensureInitialized(connection);
        return delegate.getTables(connection);
    }

    @Override
    public SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> getPrimaryKeyColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        ensureInitialized(connection);
        return delegate.getPrimaryKeyColumns(connection, tables);
    }

    @Override
    public SortedMap<JdbcTableMetadata, List<AttributeMetadata>> getColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        ensureInitialized(connection);
        return delegate.getColumns(connection, tables);
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(Connection connection, JdbcTableMetadata table)
            throws Exception {
        ensureInitialized(connection);
        return delegate.getPrimaryKeyColumnsByTable(connection, table);
    }

    @Override
    public List<AttributeMetadata> getColumnsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        ensureInitialized(connection);
        return delegate.getColumnsByTable(connection, table);
    }

    @Override
    public List<RelationMetadata> getRelationsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        ensureInitialized(connection);
        return delegate.getRelationsByTable(connection, table);
    }

    @Override
    public boolean isForeignKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        ensureInitialized(connection);
        return delegate.isForeignKey(connection, table, columnName);
    }

    @Override
    public boolean isPrimaryKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        ensureInitialized(connection);
        return delegate.isPrimaryKey(connection, table, columnName);
    }

    @Override
    public AttributeMetadata getColumnFromTable(Connection connection, JdbcTableMetadata table, String columnName)
            throws Exception {
        ensureInitialized(connection);
        return delegate.getColumnFromTable(connection, table, columnName);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexColumns(
            Connection connection, List<JdbcTableMetadata> tables, boolean unique, boolean approximate)
            throws Exception {
        ensureInitialized(connection);
        return delegate.getIndexColumns(connection, tables, unique, approximate);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexesByTable(
            Connection connection, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception {
        ensureInitialized(connection);
        return delegate.getIndexesByTable(connection, table, unique, approximate);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeys(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        ensureInitialized(connection);
        return delegate.getForeignKeys(connection, tables);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            Connection connection, JdbcTableMetadata table) throws Exception {
        ensureInitialized(connection);
        return delegate.getForeignKeysByTable(connection, table);
    }

    @Override
    public DomainRelationType getCardinality(JdbcTableMetadata table, JdbcForeignKeyConstraintMetadata fkConstraint)
            throws Exception {
        return delegate.getCardinality(table, fkConstraint);
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata isPrimaryKey(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> pkMap) {
        return delegate.isPrimaryKey(table, fkColumnsList, pkMap);
    }

    @Override
    public String isUniqueIndex(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<String, Collection<String>> uniqueIndexMap) {
        return delegate.isUniqueIndex(table, fkColumnsList, uniqueIndexMap);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>>
            getInversedForeignKeysByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        ensureInitialized(connection);
        return delegate.getInversedForeignKeysByTable(connection, table);
    }
}
