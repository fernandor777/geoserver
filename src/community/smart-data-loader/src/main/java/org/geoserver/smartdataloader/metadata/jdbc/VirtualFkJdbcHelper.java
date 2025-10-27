/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import org.geoserver.smartdataloader.data.store.virtualfk.Relationship;
import org.geoserver.smartdataloader.data.store.virtualfk.Relationships;
import org.geoserver.smartdataloader.data.store.virtualfk.RelationshipsXmlParser;
import org.geoserver.smartdataloader.domain.entities.DomainRelationType;
import org.geoserver.smartdataloader.metadata.AttributeMetadata;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.VirtualRelationMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcForeignKeyConstraintMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcPrimaryKeyConstraintMetadata;

public class VirtualFkJdbcHelper implements JdbcHelper {

    private final JdbcHelper delegate;
    private final Relationships relationships;

    public VirtualFkJdbcHelper(Relationships relationships) {
        this(new DefaultJdbcHelper(), relationships);
    }

    /** Allow injecting a different delegate (useful for testing or custom implementations). */
    public VirtualFkJdbcHelper(JdbcHelper delegate, Relationships relationships) {
        this.delegate = (delegate != null) ? delegate : new DefaultJdbcHelper();
        this.relationships = (relationships != null) ? relationships : new Relationships();
    }

    @Override
    public List<JdbcTableMetadata> getSchemaTables(DatabaseMetaData metaData, String schema) throws Exception {
        List<JdbcTableMetadata> tables = delegate.getSchemaTables(metaData, schema);
        applyVirtualHelper(tables);
        return tables;
    }

    @Override
    public List<JdbcTableMetadata> getTables(DatabaseMetaData metaData) throws Exception {
        List<JdbcTableMetadata> tables = delegate.getTables(metaData);
        applyVirtualHelper(tables);
        return tables;
    }

    @Override
    public SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> getPrimaryKeyColumns(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getPrimaryKeyColumns(metaData, tables);
    }

    @Override
    public SortedMap<JdbcTableMetadata, List<AttributeMetadata>> getColumns(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getColumns(metaData, tables);
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        return delegate.getPrimaryKeyColumnsByTable(metaData, table);
    }

    @Override
    public List<AttributeMetadata> getColumnsByTable(DatabaseMetaData metaData, JdbcTableMetadata table)
            throws Exception {
        return delegate.getColumnsByTable(metaData, table);
    }

    @Override
    public List<RelationMetadata> getRelationsByTable(DatabaseMetaData metaData, JdbcTableMetadata table)
            throws Exception {
        if (table != null) {
            table.setJdbcHelper(this);
        }
        List<RelationMetadata> relations = new ArrayList<>();
        for (Relationship relationship : relationships.getRelationships()) {
            if (relationship.getSource().getEntity().equals(table.getName())
                    && relationship.getSource().getSchema().equals(table.getSchema())) {
                String sourceColumn = relationship.getSource().getKey().getColumn();
                String targetTable = relationship.getTarget().getEntity();
                String targetSchema = relationship.getTarget().getSchema();
                String targetColumn = relationship.getTarget().getKey().getColumn();
                // resolve the cardinality
                DomainRelationType cardinality =
                        RelationshipsXmlParser.resolveCardinality(relationship.getCardinality());
                // find source attribute metadata
                AttributeMetadata sourceAttr = table.getAttributes().stream()
                        .filter(attr -> attr.getName().equals(sourceColumn))
                        .findFirst()
                        .orElse(null);
                // find target table metadata
                List<JdbcTableMetadata> targetSchemaTables = delegate.getSchemaTables(metaData, targetSchema);
                applyVirtualHelper(targetSchemaTables);
                JdbcTableMetadata targetTableMetadata = targetSchemaTables.stream()
                        .filter(t -> t.getName().equals(targetTable))
                        .findFirst()
                        .orElse(null);
                if (targetTableMetadata == null) {
                    continue; // skip if target table not found
                }
                AttributeMetadata targetAttr = targetTableMetadata.getAttributes().stream()
                        .filter(attr -> attr.getName().equals(targetColumn))
                        .findFirst()
                        .orElse(null);
                if (sourceAttr == null || targetAttr == null) {
                    continue; // skip if attributes are not found
                }
                RelationMetadata virtualRelation =
                        new VirtualRelationMetadata(cardinality, sourceAttr, targetAttr, relationship.getName());
                relations.add(virtualRelation);
            }
        }
        relations.addAll(delegate.getRelationsByTable(metaData, table));
        return relations;
    }

    @Override
    public boolean isForeignKey(DatabaseMetaData metaData, JdbcTableMetadata table, String columnName)
            throws Exception {
        return delegate.isForeignKey(metaData, table, columnName) || isVirtualForeignKey(table, columnName);
    }

    private boolean isVirtualForeignKey(JdbcTableMetadata table, String columnName) {
        // check if column matches any source in relationships
        return relationships.getRelationships().stream()
                .anyMatch(rel -> rel.getSource().getEntity().equals(table.getName())
                        && rel.getSource().getKey().getColumn().equals(columnName)
                        && rel.getSource().getSchema().equals(table.getSchema()));
    }

    @Override
    public boolean isPrimaryKey(DatabaseMetaData metaData, JdbcTableMetadata table, String columnName)
            throws Exception {
        return delegate.isPrimaryKey(metaData, table, columnName);
    }

    @Override
    public AttributeMetadata getColumnFromTable(DatabaseMetaData metaData, JdbcTableMetadata table, String columnName)
            throws Exception {
        return delegate.getColumnFromTable(metaData, table, columnName);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexColumns(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables, boolean unique, boolean approximate)
            throws Exception {
        return delegate.getIndexColumns(metaData, tables, unique, approximate);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexesByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception {
        return delegate.getIndexesByTable(metaData, table, unique, approximate);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeys(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getForeignKeys(metaData, tables);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        return delegate.getForeignKeysByTable(metaData, table);
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
            getInversedForeignKeysByTable(DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        return delegate.getInversedForeignKeysByTable(metaData, table);
    }

    private void applyVirtualHelper(List<JdbcTableMetadata> tables) {
        if (tables == null) {
            return;
        }
        for (JdbcTableMetadata table : tables) {
            if (table != null) {
                table.setJdbcHelper(this);
            }
        }
    }
}
