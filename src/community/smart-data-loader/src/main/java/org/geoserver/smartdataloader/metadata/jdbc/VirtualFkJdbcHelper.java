/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import org.geoserver.smartdataloader.data.store.virtualfk.EntityRef;
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

/**
 * {@link JdbcHelper} decorator that augments the metadata retrieved from the underlying database with the virtual
 * relationships provided by the Smart Data Loader configuration. For each declared relationship the inverse direction
 * is synthesized so the virtual link is visible from both the source and target entities.
 */
public class VirtualFkJdbcHelper implements JdbcHelper {

    private final JdbcHelper delegate;
    private final Relationships relationships;
    private final Map<SchemaKey, List<JdbcTableMetadata>> schemaTablesCache = new HashMap<>();
    private final Map<EntityKey, List<Relationship>> relationshipsBySource = new HashMap<>();
    private final Map<EntityKey, List<Relationship>> relationshipsByTarget = new HashMap<>();

    /**
     * Builds a helper using the default JDBC metadata implementation as delegate.
     *
     * @param relationships user provided relationships (may be {@code null}); inverse relations will be synthesized
     *     automatically
     */
    public VirtualFkJdbcHelper(Relationships relationships) {
        this(new DefaultJdbcHelper(), relationships);
    }

    /**
     * Validates that all configured virtual relationships point to existing entities/columns and adhere to the optional
     * schema constraint. Throws {@link IllegalArgumentException} on any violation.
     */
    public void validateVirtualRelationships(Connection connection, String allowedSchema) throws Exception {
        for (Relationship relationship : relationships.getRelationships()) {
            validateEndpoint(connection, relationship.getSource(), relationship.getName(), "source", allowedSchema);
            validateEndpoint(connection, relationship.getTarget(), relationship.getName(), "target", allowedSchema);
        }
    }

    /**
     * Allow injecting a different delegate (useful for testing or custom implementations).
     *
     * @param delegate base helper to delegate all standard JDBC operations to
     * @param relationships user provided relationships (may be {@code null})
     */
    public VirtualFkJdbcHelper(JdbcHelper delegate, Relationships relationships) {
        this.delegate = (delegate != null) ? delegate : new DefaultJdbcHelper();
        this.relationships = (relationships != null) ? relationships : new Relationships();
        indexRelationships();
    }

    @Override
    public List<JdbcTableMetadata> getSchemaTables(Connection connection, String schema) throws Exception {
        List<JdbcTableMetadata> tables = new ArrayList<>(getSchemaTablesCached(connection, schema));
        for (Relationship relationship : relationships.getRelationships()) {
            includeRelationshipEndpoint(connection, tables, relationship.getSource());
            includeRelationshipEndpoint(connection, tables, relationship.getTarget());
        }
        return tables;
    }

    @Override
    public List<JdbcTableMetadata> getTables(Connection connection) throws Exception {
        List<JdbcTableMetadata> tables = delegate.getTables(connection);
        applyVirtualHelper(tables);
        return tables;
    }

    @Override
    public SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> getPrimaryKeyColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getPrimaryKeyColumns(connection, tables);
    }

    @Override
    public SortedMap<JdbcTableMetadata, List<AttributeMetadata>> getColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getColumns(connection, tables);
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(Connection connection, JdbcTableMetadata table)
            throws Exception {
        return delegate.getPrimaryKeyColumnsByTable(connection, table);
    }

    @Override
    public List<AttributeMetadata> getColumnsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        return delegate.getColumnsByTable(connection, table);
    }

    @Override
    public List<RelationMetadata> getRelationsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        if (table != null) {
            table.setJdbcHelper(this);
        }
        List<RelationMetadata> relations = new ArrayList<>();
        EntityKey tableKey = EntityKey.from(table);
        List<Relationship> sourceRelationships = relationshipsBySource.get(tableKey);
        if (sourceRelationships != null) {
            for (Relationship relationship : sourceRelationships) {
                DomainRelationType cardinality =
                        RelationshipsXmlParser.resolveCardinality(relationship.getCardinality());
                AttributeMetadata sourceAttr =
                        findAttribute(table, relationship.getSource().getKey().getColumn());
                JdbcTableMetadata targetTableMetadata = findTableMetadata(
                        connection,
                        relationship.getTarget().getSchema(),
                        relationship.getTarget().getEntity());
                AttributeMetadata targetAttr = findAttribute(
                        targetTableMetadata, relationship.getTarget().getKey().getColumn());
                if (sourceAttr != null && targetAttr != null) {
                    RelationMetadata virtualRelation =
                            new VirtualRelationMetadata(cardinality, sourceAttr, targetAttr, relationship.getName());
                    relations.add(virtualRelation);
                }
            }
        }
        List<Relationship> targetRelationships = relationshipsByTarget.get(tableKey);
        if (targetRelationships != null) {
            for (Relationship relationship : targetRelationships) {
                DomainRelationType cardinality =
                        RelationshipsXmlParser.resolveCardinality(relationship.getCardinality());
                // Generate the complementary direction so App-Schema sees a bidirectional mapping.
                AttributeMetadata targetAttr =
                        findAttribute(table, relationship.getTarget().getKey().getColumn());
                JdbcTableMetadata sourceTableMetadata = findTableMetadata(
                        connection,
                        relationship.getSource().getSchema(),
                        relationship.getSource().getEntity());
                AttributeMetadata sourceAttr = findAttribute(
                        sourceTableMetadata, relationship.getSource().getKey().getColumn());
                if (targetAttr != null && sourceAttr != null) {
                    DomainRelationType inverseCardinality = invertCardinality(cardinality);
                    RelationMetadata inverseRelation = new VirtualRelationMetadata(
                            inverseCardinality, targetAttr, sourceAttr, relationship.getName());
                    relations.add(inverseRelation);
                }
            }
        }
        relations.addAll(delegate.getRelationsByTable(connection, table));
        return relations;
    }

    @Override
    public boolean isForeignKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        return delegate.isForeignKey(connection, table, columnName) || isVirtualForeignKey(table, columnName);
    }

    private boolean isVirtualForeignKey(JdbcTableMetadata table, String columnName) {
        if (table == null || columnName == null) {
            return false;
        }
        List<Relationship> sourceRelationships = relationshipsBySource.get(EntityKey.from(table));
        if (sourceRelationships == null) {
            return false;
        }
        return sourceRelationships.stream()
                .anyMatch(rel -> columnName.equals(rel.getSource().getKey().getColumn()));
    }

    private void validateEndpoint(
            Connection connection, EntityRef endpoint, String relationshipName, String role, String allowedSchema)
            throws Exception {
        if (endpoint == null || endpoint.getKey() == null) {
            throw new IllegalArgumentException(
                    "Missing " + role + " definition for relationship '" + relationshipName + "'");
        }
        if (!isSingleColumn(endpoint.getKey().getColumn())) {
            throw new IllegalArgumentException(
                    "Relationship '" + relationshipName + "' " + role + " key must reference a single column");
        }
        JdbcTableMetadata table = findTableMetadata(connection, endpoint.getSchema(), endpoint.getEntity());
        if (table == null) {
            throw new IllegalArgumentException("Relationship '" + relationshipName + "' references missing " + role
                    + " entity '" + endpoint.getEntity() + "'");
        }
        AttributeMetadata column = findAttribute(table, endpoint.getKey().getColumn());
        if (column == null) {
            throw new IllegalArgumentException("Relationship '" + relationshipName + "' references missing column '"
                    + endpoint.getKey().getColumn() + "' on " + role + " entity '" + endpoint.getEntity() + "'");
        }
    }

    private boolean isSingleColumn(String column) {
        if (column == null) {
            return false;
        }
        return !column.contains(",") && !column.contains(";") && !column.contains(" ");
    }

    @Override
    public boolean isPrimaryKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        return delegate.isPrimaryKey(connection, table, columnName);
    }

    @Override
    public AttributeMetadata getColumnFromTable(Connection connection, JdbcTableMetadata table, String columnName)
            throws Exception {
        return delegate.getColumnFromTable(connection, table, columnName);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexColumns(
            Connection connection, List<JdbcTableMetadata> tables, boolean unique, boolean approximate)
            throws Exception {
        return delegate.getIndexColumns(connection, tables, unique, approximate);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexesByTable(
            Connection connection, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception {
        return delegate.getIndexesByTable(connection, table, unique, approximate);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeys(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        return delegate.getForeignKeys(connection, tables);
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            Connection connection, JdbcTableMetadata table) throws Exception {
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
        return delegate.getInversedForeignKeysByTable(connection, table);
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

    private void includeRelationshipEndpoint(Connection connection, List<JdbcTableMetadata> tables, EntityRef endpoint)
            throws Exception {
        if (endpoint == null || endpoint.getEntity() == null) {
            return;
        }
        boolean alreadyPresent = tables.stream()
                .anyMatch(t -> Objects.equals(t.getName(), endpoint.getEntity())
                        && Objects.equals(t.getSchema(), endpoint.getSchema()));
        if (alreadyPresent) {
            return;
        }
        JdbcTableMetadata tableMetadata = findTableMetadata(connection, endpoint.getSchema(), endpoint.getEntity());
        if (tableMetadata != null) {
            tables.add(tableMetadata);
        }
    }

    private JdbcTableMetadata findTableMetadata(Connection connection, String schema, String tableName)
            throws Exception {
        if (tableName == null) {
            return null;
        }
        List<JdbcTableMetadata> schemaTables = getSchemaTablesCached(connection, schema);
        if (schemaTables == null) {
            return null;
        }
        for (JdbcTableMetadata table : schemaTables) {
            if (table != null && Objects.equals(table.getName(), tableName)) {
                return table;
            }
        }
        return null;
    }

    private List<JdbcTableMetadata> getSchemaTablesCached(Connection connection, String schema) throws Exception {
        SchemaKey key = new SchemaKey(schema);
        List<JdbcTableMetadata> cached = schemaTablesCache.get(key);
        if (cached == null) {
            List<JdbcTableMetadata> delegateTables = delegate.getSchemaTables(connection, schema);
            cached = (delegateTables != null) ? delegateTables : new ArrayList<>();
            applyVirtualHelper(cached);
            schemaTablesCache.put(key, cached);
        }
        return cached;
    }

    private void indexRelationships() {
        for (Relationship relationship : relationships.getRelationships()) {
            if (relationship.getSource() != null) {
                EntityKey sourceKey = new EntityKey(
                        relationship.getSource().getSchema(),
                        relationship.getSource().getEntity());
                relationshipsBySource
                        .computeIfAbsent(sourceKey, key -> new ArrayList<>())
                        .add(relationship);
            }
            if (relationship.getTarget() != null) {
                EntityKey targetKey = new EntityKey(
                        relationship.getTarget().getSchema(),
                        relationship.getTarget().getEntity());
                relationshipsByTarget
                        .computeIfAbsent(targetKey, key -> new ArrayList<>())
                        .add(relationship);
            }
        }
    }

    private static final class SchemaKey {
        private final String schema;

        private SchemaKey(String schema) {
            this.schema = schema;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SchemaKey)) {
                return false;
            }
            SchemaKey other = (SchemaKey) object;
            return Objects.equals(schema, other.schema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schema);
        }
    }

    private static final class EntityKey {
        private final String schema;
        private final String name;

        private EntityKey(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }

        static EntityKey from(JdbcTableMetadata table) {
            if (table == null) {
                return new EntityKey(null, null);
            }
            return new EntityKey(table.getSchema(), table.getName());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof EntityKey)) {
                return false;
            }
            EntityKey other = (EntityKey) object;
            return Objects.equals(schema, other.schema) && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schema, name);
        }
    }

    private AttributeMetadata findAttribute(JdbcTableMetadata tableMetadata, String columnName) {
        if (tableMetadata == null || columnName == null) {
            return null;
        }
        if (tableMetadata.getAttributes() == null) {
            return null;
        }
        return tableMetadata.getAttributes().stream()
                .filter(attr -> Objects.equals(attr.getName(), columnName))
                .findFirst()
                .orElse(null);
    }

    private DomainRelationType invertCardinality(DomainRelationType cardinality) {
        if (cardinality == null) {
            return null;
        }
        switch (cardinality) {
            case ONEMANY:
                return DomainRelationType.MANYONE;
            case MANYONE:
                return DomainRelationType.ONEMANY;
            case ONEONE:
                return DomainRelationType.ONEONE;
            case MANYMANY:
                return DomainRelationType.MANYMANY;
            default:
                return cardinality;
        }
    }
}
