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

public interface JdbcHelper {
    List<JdbcTableMetadata> getSchemaTables(Connection connection, String schema) throws Exception;

    List<JdbcTableMetadata> getTables(Connection connection) throws Exception;

    SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> getPrimaryKeyColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception;

    SortedMap<JdbcTableMetadata, List<AttributeMetadata>> getColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception;

    JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(Connection connection, JdbcTableMetadata table)
            throws Exception;

    List<AttributeMetadata> getColumnsByTable(Connection connection, JdbcTableMetadata table) throws Exception;

    List<RelationMetadata> getRelationsByTable(Connection connection, JdbcTableMetadata table) throws Exception;

    boolean isForeignKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception;

    boolean isPrimaryKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception;

    AttributeMetadata getColumnFromTable(Connection connection, JdbcTableMetadata table, String columnName)
            throws Exception;

    SortedMap<String, Collection<String>> getIndexColumns(
            Connection connection, List<JdbcTableMetadata> tables, boolean unique, boolean approximate)
            throws Exception;

    SortedMap<String, Collection<String>> getIndexesByTable(
            Connection connection, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception;

    SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeys(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception;

    SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            Connection connection, JdbcTableMetadata table) throws Exception;

    DomainRelationType getCardinality(JdbcTableMetadata table, JdbcForeignKeyConstraintMetadata fkConstraint)
            throws Exception;

    JdbcPrimaryKeyConstraintMetadata isPrimaryKey(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> pkMap);

    String isUniqueIndex(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<String, Collection<String>> uniqueIndexMap);

    SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getInversedForeignKeysByTable(
            Connection connection, JdbcTableMetadata table) throws Exception;
}
