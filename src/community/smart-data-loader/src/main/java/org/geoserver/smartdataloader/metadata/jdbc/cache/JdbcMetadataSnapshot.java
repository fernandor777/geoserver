/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.geoserver.smartdataloader.domain.entities.DomainRelationType;

/** Immutable snapshot of metadata to safely cache and rehydrate runtime metadata objects. */
public final class JdbcMetadataSnapshot {

    private final List<TableSnapshot> tables;
    private final List<AttributeSnapshot> attributes;
    private final List<RelationSnapshot> relations;

    public JdbcMetadataSnapshot(
            List<TableSnapshot> tables, List<AttributeSnapshot> attributes, List<RelationSnapshot> relations) {
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
        this.relations = Collections.unmodifiableList(new ArrayList<>(relations));
    }

    public List<TableSnapshot> getTables() {
        return tables;
    }

    public List<AttributeSnapshot> getAttributes() {
        return attributes;
    }

    public List<RelationSnapshot> getRelations() {
        return relations;
    }

    /** Immutable table descriptor. */
    public static final class TableSnapshot {
        private final String catalog;
        private final String schema;
        private final String name;

        public TableSnapshot(String catalog, String schema, String name) {
            this.catalog = catalog;
            this.schema = schema;
            this.name = name;
        }

        public String getCatalog() {
            return catalog;
        }

        public String getSchema() {
            return schema;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TableSnapshot)) {
                return false;
            }
            TableSnapshot other = (TableSnapshot) object;
            return Objects.equals(catalog, other.catalog)
                    && Objects.equals(schema, other.schema)
                    && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog, schema, name);
        }
    }

    /** Immutable attribute descriptor. */
    public static final class AttributeSnapshot {
        private final TableSnapshot table;
        private final String name;
        private final String type;
        private final boolean externalReference;
        private final boolean identifier;

        public AttributeSnapshot(
                TableSnapshot table, String name, String type, boolean externalReference, boolean identifier) {
            this.table = table;
            this.name = name;
            this.type = type;
            this.externalReference = externalReference;
            this.identifier = identifier;
        }

        public TableSnapshot getTable() {
            return table;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isExternalReference() {
            return externalReference;
        }

        public boolean isIdentifier() {
            return identifier;
        }
    }

    /** Immutable relation descriptor. */
    public static final class RelationSnapshot {
        private final String name;
        private final DomainRelationType type;
        private final TableSnapshot ownerTable;
        private final TableSnapshot sourceTable;
        private final String sourceColumn;
        private final TableSnapshot destinationTable;
        private final String destinationColumn;

        public RelationSnapshot(
                String name,
                DomainRelationType type,
                TableSnapshot ownerTable,
                TableSnapshot sourceTable,
                String sourceColumn,
                TableSnapshot destinationTable,
                String destinationColumn) {
            this.name = name;
            this.type = type;
            this.ownerTable = ownerTable;
            this.sourceTable = sourceTable;
            this.sourceColumn = sourceColumn;
            this.destinationTable = destinationTable;
            this.destinationColumn = destinationColumn;
        }

        public String getName() {
            return name;
        }

        public DomainRelationType getType() {
            return type;
        }

        public TableSnapshot getOwnerTable() {
            return ownerTable;
        }

        public TableSnapshot getSourceTable() {
            return sourceTable;
        }

        public String getSourceColumn() {
            return sourceColumn;
        }

        public TableSnapshot getDestinationTable() {
            return destinationTable;
        }

        public String getDestinationColumn() {
            return destinationColumn;
        }
    }
}
