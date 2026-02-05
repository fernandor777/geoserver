/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.geoserver.smartdataloader.domain.entities.DomainRelationType;
import org.geoserver.smartdataloader.metadata.AttributeMetadata;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcForeignKeyConstraintMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcIndexConstraintMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.constraint.JdbcPrimaryKeyConstraintMetadata;
import org.geotools.jdbc.JDBCDataStore;

/**
 * JDBC utilities singleton class. It encapsulates a GeoTools JDBCDataStore instance in order to use some useful methods
 * and extends it based on JDBC API use.
 */
public class DefaultJdbcHelper implements JdbcHelper {

    private final JDBCDataStore jdbcDataStore;
    private final Map<TableId, List<AttributeMetadata>> columnsCache = new HashMap<>();
    private final Map<TableId, Map<String, String>> columnTypeCache = new HashMap<>();
    private final Map<TableId, Set<String>> primaryKeyColumnsCache = new HashMap<>();
    private final Map<TableId, Set<String>> foreignKeyColumnsCache = new HashMap<>();
    private final Map<TableId, JdbcPrimaryKeyConstraintMetadata> primaryKeyCache = new HashMap<>();
    private final Map<TableId, SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>>>
            foreignKeysCache = new HashMap<>();
    private final Map<TableId, SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>>>
            exportedKeysCache = new HashMap<>();
    private final Map<IndexKey, SortedMap<String, Collection<String>>> indexCache = new HashMap<>();

    public DefaultJdbcHelper() {
        this.jdbcDataStore = new JDBCDataStore();
    }

    private TableId tableId(JdbcTableMetadata table) {
        return new TableId(table.getCatalog(), table.getSchema(), table.getName());
    }

    private Set<String> getPrimaryKeyColumnNames(DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        if (table == null) {
            return Collections.emptySet();
        }
        TableId id = tableId(table);
        if (!primaryKeyColumnsCache.containsKey(id)) {
            loadPrimaryKey(metaData, table);
        }
        return primaryKeyColumnsCache.getOrDefault(id, Collections.emptySet());
    }

    private Set<String> getForeignKeyColumnNames(DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        if (table == null) {
            return Collections.emptySet();
        }
        TableId id = tableId(table);
        if (foreignKeyColumnsCache.containsKey(id)) {
            return foreignKeyColumnsCache.get(id);
        }
        Set<String> fkColumns = new HashSet<>();
        try (ResultSet foreignKeys = metaData.getImportedKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            if (foreignKeys != null) {
                while (foreignKeys.next()) {
                    fkColumns.add(foreignKeys.getString("FKCOLUMN_NAME"));
                }
            }
        }
        foreignKeyColumnsCache.put(id, fkColumns);
        return fkColumns;
    }

    private JdbcPrimaryKeyConstraintMetadata loadPrimaryKey(DatabaseMetaData metaData, JdbcTableMetadata table)
            throws Exception {
        if (table == null) {
            return null;
        }
        TableId id = tableId(table);
        if (primaryKeyCache.containsKey(id)) {
            return primaryKeyCache.get(id);
        }
        JdbcPrimaryKeyConstraintMetadata primaryKey = null;
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet primaryKeyColumns =
                metaData.getPrimaryKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            if (primaryKeyColumns != null && primaryKeyColumns.next()) {
                JdbcTableMetadata pkTable = new JdbcTableMetadata(
                        metaData.getConnection(),
                        primaryKeyColumns.getString("TABLE_CAT"),
                        primaryKeyColumns.getString("TABLE_SCHEM"),
                        primaryKeyColumns.getString("TABLE_NAME"),
                        this);
                String pkConstraintName = primaryKeyColumns.getString("PK_NAME");
                List<String> pkColumnNames = new ArrayList<>();
                do {
                    String columnName = primaryKeyColumns.getString("COLUMN_NAME");
                    pkColumnNames.add(columnName);
                    pkColumns.add(columnName);
                } while (primaryKeyColumns.next());
                primaryKey = new JdbcPrimaryKeyConstraintMetadata(pkTable, pkConstraintName, pkColumnNames);
            }
        }
        primaryKeyCache.put(id, primaryKey);
        primaryKeyColumnsCache.put(id, pkColumns);
        return primaryKey;
    }

    private Map<String, String> loadColumnTypes(DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        if (table == null) {
            return Collections.emptyMap();
        }
        TableId id = tableId(table);
        if (columnTypeCache.containsKey(id)) {
            return columnTypeCache.get(id);
        }
        Map<String, String> columnTypes = new LinkedHashMap<>();
        try (ResultSet columns = metaData.getColumns(table.getCatalog(), table.getSchema(), table.getName(), "%")) {
            if (columns != null) {
                while (columns.next()) {
                    columnTypes.put(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME"));
                }
            }
        }
        columnTypeCache.put(id, columnTypes);
        return columnTypes;
    }

    private String getColumnType(DatabaseMetaData metaData, JdbcTableMetadata table, String columnName)
            throws Exception {
        if (table == null || columnName == null) {
            return null;
        }
        Map<String, String> columnTypes = loadColumnTypes(metaData, table);
        return columnTypes.get(columnName);
    }

    private List<JdbcTableMetadata> getListOfTablesFromResultSet(DatabaseMetaData metaData, ResultSet tables)
            throws Exception {
        if (tables != null) {
            List<JdbcTableMetadata> tableList = new ArrayList<>();
            while (tables.next()) {
                String tableType = tables.getString("TABLE_TYPE");
                if (tableType != null && (tableType.equals("TABLE") || tableType.equals("VIEW"))) {
                    tableList.add(new JdbcTableMetadata(
                            metaData.getConnection(),
                            tables.getString("TABLE_CAT"),
                            tables.getString("TABLE_SCHEM"),
                            tables.getString("TABLE_NAME"),
                            this));
                }
            }
            return tableList;
        }
        return null;
    }

    @Override
    public List<JdbcTableMetadata> getSchemaTables(Connection connection, String schema) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        List<JdbcTableMetadata> tables = new ArrayList<>();
        try (ResultSet tablesRs =
                metaData.getTables(null, jdbcDataStore.escapeNamePattern(metaData, schema), "%", null)) {
            List<JdbcTableMetadata> baseTables = getListOfTablesFromResultSet(metaData, tablesRs);
            if (baseTables != null) {
                tables.addAll(baseTables);
            }
        }
        preloadSchemaMetadata(metaData, schema, tables);
        discoverCrossSchemaTables(metaData, tables);
        return tables;
    }

    @Override
    public List<JdbcTableMetadata> getTables(Connection connection) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, null, "%", null)) {
            return getListOfTablesFromResultSet(metaData, tables);
        }
    }

    private void discoverCrossSchemaTables(DatabaseMetaData metaData, List<JdbcTableMetadata> tables) throws Exception {
        boolean addedTables;
        do {
            addedTables = false;
            List<JdbcTableMetadata> snapshot = new ArrayList<>(tables);
            for (JdbcTableMetadata table : snapshot) {
                addedTables |= addImportedKeyTables(metaData, tables, table);
                addedTables |= addExportedKeyTables(metaData, tables, table);
            }
        } while (addedTables);
    }

    private boolean addImportedKeyTables(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables, JdbcTableMetadata table) throws Exception {
        boolean added = false;
        try (ResultSet rs = metaData.getImportedKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            while (rs.next()) {
                added |= addTableIfMissing(
                        metaData,
                        tables,
                        rs.getString("PKTABLE_CAT"),
                        rs.getString("PKTABLE_SCHEM"),
                        rs.getString("PKTABLE_NAME"));
            }
        }
        return added;
    }

    private boolean addExportedKeyTables(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables, JdbcTableMetadata table) throws Exception {
        boolean added = false;
        try (ResultSet rs = metaData.getExportedKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            while (rs.next()) {
                added |= addTableIfMissing(
                        metaData,
                        tables,
                        rs.getString("FKTABLE_CAT"),
                        rs.getString("FKTABLE_SCHEM"),
                        rs.getString("FKTABLE_NAME"));
            }
        }
        return added;
    }

    private boolean addTableIfMissing(
            DatabaseMetaData metaData, List<JdbcTableMetadata> tables, String catalog, String schema, String tableName)
            throws Exception {
        if (tableName == null) {
            return false;
        }
        for (JdbcTableMetadata existing : tables) {
            if (Objects.equals(existing.getName(), tableName)
                    && Objects.equals(existing.getSchema(), schema)
                    && Objects.equals(existing.getCatalog(), catalog)) {
                return false;
            }
        }
        JdbcTableMetadata relatedTable =
                new JdbcTableMetadata(metaData.getConnection(), catalog, schema, tableName, this);
        tables.add(relatedTable);
        return true;
    }

    @Override
    public SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> getPrimaryKeyColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (tables != null) {
            SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> pkMap = new TreeMap<>();
            for (EntityMetadata table : tables) {
                JdbcPrimaryKeyConstraintMetadata primaryKey =
                        getPrimaryKeyColumnsByTable(metaData, (JdbcTableMetadata) table);
                if (primaryKey != null) {
                    pkMap.put(table, primaryKey);
                }
            }
            return pkMap;
        }
        return null;
    }

    @Override
    public SortedMap<JdbcTableMetadata, List<AttributeMetadata>> getColumns(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (tables != null) {
            SortedMap<JdbcTableMetadata, List<AttributeMetadata>> cMap = new TreeMap<>();
            for (JdbcTableMetadata table : tables) {
                List<AttributeMetadata> columnList = getColumnsByTable(metaData, table);
                if (columnList != null) {
                    cMap.put(table, columnList);
                }
            }
            return cMap;
        }
        return null;
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(Connection connection, JdbcTableMetadata table)
            throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        return getPrimaryKeyColumnsByTable(metaData, table);
    }

    @Override
    public List<AttributeMetadata> getColumnsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        return getColumnsByTable(metaData, table);
    }

    private JdbcPrimaryKeyConstraintMetadata getPrimaryKeyColumnsByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        return loadPrimaryKey(metaData, table);
    }

    private List<AttributeMetadata> getColumnsByTable(DatabaseMetaData metaData, JdbcTableMetadata table)
            throws Exception {
        if (table == null) {
            return null;
        }
        TableId id = tableId(table);
        if (columnsCache.containsKey(id)) {
            return columnsCache.get(id);
        }
        Map<String, String> cachedTypes = columnTypeCache.get(id);
        if (cachedTypes != null && !cachedTypes.isEmpty()) {
            Set<String> pkColumns = getPrimaryKeyColumnNames(metaData, table);
            Set<String> fkColumns = getForeignKeyColumnNames(metaData, table);
            List<AttributeMetadata> columnsList = new ArrayList<>();
            for (Map.Entry<String, String> entry : cachedTypes.entrySet()) {
                String columnName = entry.getKey();
                String columnType = entry.getValue();
                boolean isFK = fkColumns.contains(columnName);
                boolean isPK = pkColumns.contains(columnName);
                JdbcColumnMetadata aColumn = new JdbcColumnMetadata(table, columnName, columnType, isFK, isPK);
                columnsList.add(aColumn);
            }
            columnsCache.put(id, columnsList);
            return columnsList;
        }
        Set<String> pkColumns = getPrimaryKeyColumnNames(metaData, table);
        Set<String> fkColumns = getForeignKeyColumnNames(metaData, table);
        Map<String, String> columnTypes = new LinkedHashMap<>();
        List<AttributeMetadata> columnsList = null;
        try (ResultSet columns = metaData.getColumns(table.getCatalog(), table.getSchema(), table.getName(), "%")) {
            if (columns != null && columns.next()) {
                columnsList = new ArrayList<>();
                do {
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    columnTypes.put(columnName, columnType);
                    boolean isFK = fkColumns.contains(columnName);
                    boolean isPK = pkColumns.contains(columnName);
                    JdbcColumnMetadata aColumn = new JdbcColumnMetadata(table, columnName, columnType, isFK, isPK);
                    columnsList.add(aColumn);
                } while (columns.next());
            }
        }
        columnsCache.put(id, columnsList);
        columnTypeCache.put(id, columnTypes);
        return columnsList;
    }

    @Override
    public List<RelationMetadata> getRelationsByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        ArrayList<RelationMetadata> relations = new ArrayList<>();
        // add all foreignkeys relations
        SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> fkMap =
                getForeignKeysByTable(metaData, table);
        if (fkMap != null) {
            Iterator<JdbcForeignKeyConstraintMetadata> iFkConstraint =
                    fkMap.keySet().iterator();
            while (iFkConstraint.hasNext()) {
                JdbcForeignKeyConstraintMetadata key = iFkConstraint.next();
                Collection<JdbcForeignKeyColumnMetadata> fkColumns = fkMap.get(key);
                Iterator<JdbcForeignKeyColumnMetadata> iFkColumns = fkColumns.iterator();
                while (iFkColumns.hasNext()) {
                    JdbcForeignKeyColumnMetadata aFkColumn = iFkColumns.next();
                    DomainRelationType type = getCardinality(metaData, table, key);
                    JdbcRelationMetadata relation = new JdbcRelationMetadata(key.getName(), type, aFkColumn);
                    relations.add(relation);
                    table.addRelation(relation);
                }
            }
        }
        // add all inverted foreignkeys relations
        SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> iFkMap =
                getInversedForeignKeysByTable(metaData, table);
        if (iFkMap != null) {

            Iterator<JdbcForeignKeyConstraintMetadata> iFkConstraint =
                    iFkMap.keySet().iterator();
            while (iFkConstraint.hasNext()) {
                JdbcForeignKeyConstraintMetadata key = iFkConstraint.next();
                Collection<JdbcForeignKeyColumnMetadata> fkColumns = iFkMap.get(key);
                Iterator<JdbcForeignKeyColumnMetadata> iFkColumns = fkColumns.iterator();
                while (iFkColumns.hasNext()) {
                    JdbcForeignKeyColumnMetadata aFkColumn = iFkColumns.next();
                    DomainRelationType type = DomainRelationType.ONEMANY;
                    JdbcRelationMetadata relation = new JdbcRelationMetadata(key.getName(), type, aFkColumn);
                    relations.add(relation);
                    table.addRelation(relation);
                }
            }
        }
        return relations;
    }

    @Override
    public boolean isForeignKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (table == null || columnName == null) {
            return false;
        }
        Set<String> fkColumns = getForeignKeyColumnNames(metaData, table);
        return fkColumns.contains(columnName);
    }

    @Override
    public boolean isPrimaryKey(Connection connection, JdbcTableMetadata table, String columnName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (table == null || columnName == null) {
            return false;
        }
        Set<String> pkColumns = getPrimaryKeyColumnNames(metaData, table);
        return pkColumns.contains(columnName);
    }

    @Override
    public AttributeMetadata getColumnFromTable(Connection connection, JdbcTableMetadata table, String columnName)
            throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (table == null || columnName == null) {
            return null;
        }
        String columnType = getColumnType(metaData, table, columnName);
        if (columnType == null) {
            return null;
        }
        boolean isFK = isForeignKey(connection, table, columnName);
        boolean isPK = isPrimaryKey(connection, table, columnName);
        return new JdbcColumnMetadata(table, columnName, columnType, isFK, isPK);
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexColumns(
            Connection connection, List<JdbcTableMetadata> tables, boolean unique, boolean approximate)
            throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (tables != null) {
            SortedMap<String, Collection<String>> indexMap = new TreeMap<>();
            for (JdbcTableMetadata table : tables) {
                SortedMap<String, Collection<String>> tableIndexMap =
                        getIndexesByTable(metaData, table, unique, approximate);
                if (tableIndexMap != null) {
                    indexMap.putAll(tableIndexMap);
                }
            }
            return indexMap;
        }
        return null;
    }

    @Override
    public SortedMap<String, Collection<String>> getIndexesByTable(
            Connection connection, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        return getIndexesByTable(metaData, table, unique, approximate);
    }

    private SortedMap<String, Collection<String>> getIndexesByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table, boolean unique, boolean approximate) throws Exception {
        if (table == null) {
            return null;
        }
        IndexKey indexKey = new IndexKey(tableId(table), unique, approximate);
        if (indexCache.containsKey(indexKey)) {
            return indexCache.get(indexKey);
        }
        SortedMap<String, Collection<String>> indexMap = null;
        try (ResultSet indexColumns =
                metaData.getIndexInfo(table.getCatalog(), table.getSchema(), table.getName(), unique, approximate)) {
            if (indexColumns != null && indexColumns.next()) {
                SortedSetMultimap<String, String> indexMultimap = TreeMultimap.create();
                JdbcTableMetadata tableAux = new JdbcTableMetadata(
                        metaData.getConnection(),
                        indexColumns.getString("TABLE_CAT"),
                        indexColumns.getString("TABLE_SCHEM"),
                        indexColumns.getString("TABLE_NAME"),
                        this);
                do {
                    String indexConstraintName = indexColumns.getString("INDEX_NAME");
                    JdbcIndexConstraintMetadata indexConstraint =
                            new JdbcIndexConstraintMetadata(tableAux, indexConstraintName);
                    String indexColumnName = indexColumns.getString("COLUMN_NAME");
                    indexMultimap.put(indexConstraint.toString(), indexColumnName);
                } while (indexColumns.next());
                indexMap = new TreeMap<>();
                indexMap.putAll(indexMultimap.asMap());
            }
        }
        indexCache.put(indexKey, indexMap);
        return indexMap;
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeys(
            Connection connection, List<JdbcTableMetadata> tables) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        if (tables != null) {
            SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> fkMap =
                    new TreeMap<>();
            for (JdbcTableMetadata table : tables) {
                SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> tableFKMap =
                        getForeignKeysByTable(metaData, table);
                if (tableFKMap != null) {
                    fkMap.putAll(tableFKMap);
                }
            }
            return fkMap;
        }
        return null;
    }

    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            Connection connection, JdbcTableMetadata table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        return getForeignKeysByTable(metaData, table);
    }

    private SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> getForeignKeysByTable(
            DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        if (table == null) {
            return null;
        }
        TableId id = tableId(table);
        if (foreignKeysCache.containsKey(id)) {
            return foreignKeysCache.get(id);
        }
        SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> fkMap = null;
        Set<String> fkColumns = foreignKeyColumnsCache.getOrDefault(id, new HashSet<>());
        try (ResultSet foreignKeys = metaData.getImportedKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            if (foreignKeys != null && foreignKeys.next()) {
                SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata> fkMultimap =
                        TreeMultimap.create();
                JdbcTableMetadata fkTable = new JdbcTableMetadata(
                        metaData.getConnection(),
                        foreignKeys.getString("FKTABLE_CAT"),
                        foreignKeys.getString("FKTABLE_SCHEM"),
                        foreignKeys.getString("FKTABLE_NAME"),
                        this);
                String columnType = getColumnType(metaData, table, foreignKeys.getString("FKCOLUMN_NAME"));
                do {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    fkColumns.add(fkColumnName);
                    String fkConstraintName = foreignKeys.getString("FK_NAME");
                    JdbcTableMetadata pkTable = new JdbcTableMetadata(
                            metaData.getConnection(),
                            foreignKeys.getString("PKTABLE_CAT"),
                            foreignKeys.getString("PKTABLE_SCHEM"),
                            foreignKeys.getString("PKTABLE_NAME"),
                            this);
                    JdbcForeignKeyConstraintMetadata fkConstraint =
                            new JdbcForeignKeyConstraintMetadata(fkTable, fkConstraintName, pkTable);
                    JdbcForeignKeyColumnMetadata fkColumn = new JdbcForeignKeyColumnMetadata(
                            fkTable,
                            fkColumnName,
                            columnType,
                            new JdbcColumnMetadata(pkTable, foreignKeys.getString("PKCOLUMN_NAME"), columnType, false));
                    fkMultimap.put(fkConstraint, fkColumn);
                } while (foreignKeys.next());
                fkMap = new TreeMap<>();
                fkMap.putAll(fkMultimap.asMap());
            }
        }
        foreignKeyColumnsCache.put(id, fkColumns);
        foreignKeysCache.put(id, fkMap);
        return fkMap;
    }

    @Override
    public DomainRelationType getCardinality(JdbcTableMetadata table, JdbcForeignKeyConstraintMetadata fkConstraint)
            throws Exception {
        DatabaseMetaData metaData = table.getConnection().getMetaData();
        return getCardinality(metaData, table, fkConstraint);
    }

    private DomainRelationType getCardinality(
            DatabaseMetaData metaData, JdbcTableMetadata table, JdbcForeignKeyConstraintMetadata fkConstraint)
            throws Exception {
        SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> fkMultimap =
                getForeignKeysByTable(metaData, table);
        JdbcPrimaryKeyConstraintMetadata primaryKey = getPrimaryKeyColumnsByTable(metaData, table);
        SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> pkMap = new TreeMap<>();
        pkMap.put(table, primaryKey);
        SortedMap<String, Collection<String>> uniqueIndexMultimap = getIndexesByTable(metaData, table, true, true);
        if (fkMultimap != null) {
            for (JdbcForeignKeyConstraintMetadata aFkConstraint : fkMultimap.keySet()) {
                if (aFkConstraint.equals(fkConstraint)) {
                    Collection<JdbcForeignKeyColumnMetadata> fkColumnsList = fkMultimap.get(aFkConstraint);
                    JdbcPrimaryKeyConstraintMetadata isPrimaryKey =
                            isPrimaryKey(aFkConstraint.getTable(), fkColumnsList, pkMap);
                    if (isPrimaryKey != null) {
                        return DomainRelationType.ONEONE;
                    } else {
                        String uniqueIndexConstraint =
                                isUniqueIndex(aFkConstraint.getTable(), fkColumnsList, uniqueIndexMultimap);
                        if (uniqueIndexConstraint != null) {
                            return DomainRelationType.ONEONE;
                        } else {
                            if (aFkConstraint.equals(fkConstraint)) return DomainRelationType.MANYONE;
                            else return DomainRelationType.ONEMANY;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public JdbcPrimaryKeyConstraintMetadata isPrimaryKey(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<EntityMetadata, JdbcPrimaryKeyConstraintMetadata> pkMap) {
        JdbcPrimaryKeyConstraintMetadata primaryKey = pkMap.get(table);
        if (primaryKey == null) {
            return null;
        }
        for (String columnName : primaryKey.getColumnNames()) {
            boolean containsPkColumnName = false;
            for (JdbcForeignKeyColumnMetadata fkColumns : fkColumnsList) {
                if (columnName.equals(fkColumns.getName())) {
                    containsPkColumnName = true;
                }
            }
            if (!containsPkColumnName) {
                return null;
            }
        }
        return primaryKey;
    }

    @Override
    public String isUniqueIndex(
            JdbcTableMetadata table,
            Collection<JdbcForeignKeyColumnMetadata> fkColumnsList,
            SortedMap<String, Collection<String>> uniqueIndexMap) {
        if (uniqueIndexMap == null) {
            return null;
        }
        indexLoop:
        for (String uniqueIndexConstraint : uniqueIndexMap.keySet()) {
            if (uniqueIndexConstraint.startsWith(table.toString() + " - ")) {
                Collection<String> uniqueIndexColumns = uniqueIndexMap.get(uniqueIndexConstraint);
                for (String uniqueIndexColumn : uniqueIndexColumns) {
                    boolean containsUniqueIndexColumn = false;
                    for (JdbcForeignKeyColumnMetadata fkColumns : fkColumnsList) {
                        if (uniqueIndexColumn.equals(fkColumns.getName())) {
                            containsUniqueIndexColumn = true;
                        }
                    }
                    if (!containsUniqueIndexColumn) {
                        continue indexLoop;
                    }
                }
                return uniqueIndexConstraint;
            }
        }
        return null;
    }

    /**
     * @param connection the database connection used to obtain metadata
     * @param table the table metadata to use to pick up the column metadata
     * @return a SortedMap mapping the foreignKeys metadata to the column metadata. Returns null if no column
     *     referencing the table are found
     * @throws Exception
     */
    @Override
    public SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>>
            getInversedForeignKeysByTable(Connection connection, JdbcTableMetadata table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        return getInversedForeignKeysByTable(metaData, table);
    }

    private SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>>
            getInversedForeignKeysByTable(DatabaseMetaData metaData, JdbcTableMetadata table) throws Exception {
        if (table == null) {
            return null;
        }
        TableId id = tableId(table);
        if (exportedKeysCache.containsKey(id)) {
            return exportedKeysCache.get(id);
        }
        SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> inversedFkMap = null;
        try (ResultSet foreignKeys = metaData.getExportedKeys(table.getCatalog(), table.getSchema(), table.getName())) {
            if (foreignKeys != null && foreignKeys.next()) {
                SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata> inversedFkMultimap =
                        TreeMultimap.create();
                do {
                    JdbcTableMetadata pkTable = new JdbcTableMetadata(
                            metaData.getConnection(),
                            foreignKeys.getString("PKTABLE_CAT"),
                            foreignKeys.getString("PKTABLE_SCHEM"),
                            foreignKeys.getString("PKTABLE_NAME"),
                            this);
                    String pkConstraintName = foreignKeys.getString("PK_NAME");
                    JdbcTableMetadata fkTable = new JdbcTableMetadata(
                            metaData.getConnection(),
                            foreignKeys.getString("FKTABLE_CAT"),
                            foreignKeys.getString("FKTABLE_SCHEM"),
                            foreignKeys.getString("FKTABLE_NAME"),
                            this);
                    JdbcForeignKeyConstraintMetadata pkConstraint =
                            new JdbcForeignKeyConstraintMetadata(pkTable, pkConstraintName, fkTable);

                    String columnType = getColumnType(metaData, fkTable, foreignKeys.getString("FKCOLUMN_NAME"));
                    JdbcForeignKeyColumnMetadata fkColumns = new JdbcForeignKeyColumnMetadata(
                            fkTable,
                            foreignKeys.getString("FKCOLUMN_NAME"),
                            columnType,
                            new JdbcColumnMetadata(pkTable, foreignKeys.getString("PKCOLUMN_NAME"), columnType, false));
                    inversedFkMultimap.put(pkConstraint, fkColumns);
                } while (foreignKeys.next());
                inversedFkMap = new TreeMap<>();
                inversedFkMap.putAll(inversedFkMultimap.asMap());
            }
        }
        exportedKeysCache.put(id, inversedFkMap);
        return inversedFkMap;
    }

    private void preloadSchemaMetadata(DatabaseMetaData metaData, String schema, List<JdbcTableMetadata> tables)
            throws Exception {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        preloadColumnTypes(metaData, schema);
        preloadPrimaryKeys(metaData, schema);
        preloadForeignKeys(metaData, schema);
        preloadExportedKeys(metaData, schema);
        preloadIndexes(metaData, tables);
    }

    private void preloadColumnTypes(DatabaseMetaData metaData, String schema) throws Exception {
        String schemaPattern = jdbcDataStore.escapeNamePattern(metaData, schema);
        try (ResultSet columns = metaData.getColumns(null, schemaPattern, "%", "%")) {
            if (columns == null) {
                return;
            }
            while (columns.next()) {
                TableId id = new TableId(
                        columns.getString("TABLE_CAT"),
                        columns.getString("TABLE_SCHEM"),
                        columns.getString("TABLE_NAME"));
                Map<String, String> columnTypes = columnTypeCache.computeIfAbsent(id, key -> new LinkedHashMap<>());
                columnTypes.put(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME"));
            }
        }
    }

    private void preloadPrimaryKeys(DatabaseMetaData metaData, String schema) throws Exception {
        String schemaPattern = jdbcDataStore.escapeNamePattern(metaData, schema);
        Map<TableId, PrimaryKeyBuilder> builders = new HashMap<>();
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, schemaPattern, "%")) {
            if (primaryKeys == null) {
                return;
            }
            while (primaryKeys.next()) {
                TableId id = new TableId(
                        primaryKeys.getString("TABLE_CAT"),
                        primaryKeys.getString("TABLE_SCHEM"),
                        primaryKeys.getString("TABLE_NAME"));
                String pkName = primaryKeys.getString("PK_NAME");
                PrimaryKeyBuilder builder = builders.computeIfAbsent(id, key -> new PrimaryKeyBuilder(pkName));
                builder.addColumn(primaryKeys.getString("COLUMN_NAME"));
            }
        }
        for (Map.Entry<TableId, PrimaryKeyBuilder> entry : builders.entrySet()) {
            TableId id = entry.getKey();
            PrimaryKeyBuilder builder = entry.getValue();
            JdbcTableMetadata pkTable =
                    new JdbcTableMetadata(metaData.getConnection(), id.catalog, id.schema, id.name, this);
            JdbcPrimaryKeyConstraintMetadata primaryKey =
                    new JdbcPrimaryKeyConstraintMetadata(pkTable, builder.name, builder.columns);
            primaryKeyCache.put(id, primaryKey);
            primaryKeyColumnsCache.put(id, new HashSet<>(builder.columns));
        }
    }

    private void preloadForeignKeys(DatabaseMetaData metaData, String schema) {
        String schemaPattern;
        try {
            schemaPattern = jdbcDataStore.escapeNamePattern(metaData, schema);
        } catch (Exception e) {
            return;
        }
        Map<TableId, SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata>> fkMultimaps =
                new HashMap<>();
        try (ResultSet foreignKeys = metaData.getImportedKeys(null, schemaPattern, "%")) {
            if (foreignKeys == null) {
                return;
            }
            while (foreignKeys.next()) {
                TableId fkId = new TableId(
                        foreignKeys.getString("FKTABLE_CAT"),
                        foreignKeys.getString("FKTABLE_SCHEM"),
                        foreignKeys.getString("FKTABLE_NAME"));
                JdbcTableMetadata fkTable =
                        new JdbcTableMetadata(metaData.getConnection(), fkId.catalog, fkId.schema, fkId.name, this);
                JdbcTableMetadata pkTable = new JdbcTableMetadata(
                        metaData.getConnection(),
                        foreignKeys.getString("PKTABLE_CAT"),
                        foreignKeys.getString("PKTABLE_SCHEM"),
                        foreignKeys.getString("PKTABLE_NAME"),
                        this);
                String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                String columnType = getColumnType(metaData, fkTable, fkColumnName);
                JdbcForeignKeyConstraintMetadata fkConstraint =
                        new JdbcForeignKeyConstraintMetadata(fkTable, foreignKeys.getString("FK_NAME"), pkTable);
                JdbcForeignKeyColumnMetadata fkColumn = new JdbcForeignKeyColumnMetadata(
                        fkTable,
                        fkColumnName,
                        columnType,
                        new JdbcColumnMetadata(pkTable, foreignKeys.getString("PKCOLUMN_NAME"), columnType, false));
                SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata> fkMultimap =
                        fkMultimaps.computeIfAbsent(fkId, key -> TreeMultimap.create());
                fkMultimap.put(fkConstraint, fkColumn);
                foreignKeyColumnsCache
                        .computeIfAbsent(fkId, key -> new HashSet<>())
                        .add(fkColumnName);
            }
        } catch (Exception e) {
            return;
        }
        for (Map.Entry<TableId, SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata>>
                entry : fkMultimaps.entrySet()) {
            SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> fkMap =
                    new TreeMap<>();
            fkMap.putAll(entry.getValue().asMap());
            foreignKeysCache.put(entry.getKey(), fkMap);
        }
    }

    private void preloadExportedKeys(DatabaseMetaData metaData, String schema) {
        String schemaPattern;
        try {
            schemaPattern = jdbcDataStore.escapeNamePattern(metaData, schema);
        } catch (Exception e) {
            return;
        }
        Map<TableId, SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata>>
                exportedMultimaps = new HashMap<>();
        try (ResultSet foreignKeys = metaData.getExportedKeys(null, schemaPattern, "%")) {
            if (foreignKeys == null) {
                return;
            }
            while (foreignKeys.next()) {
                TableId pkId = new TableId(
                        foreignKeys.getString("PKTABLE_CAT"),
                        foreignKeys.getString("PKTABLE_SCHEM"),
                        foreignKeys.getString("PKTABLE_NAME"));
                JdbcTableMetadata pkTable =
                        new JdbcTableMetadata(metaData.getConnection(), pkId.catalog, pkId.schema, pkId.name, this);
                JdbcTableMetadata fkTable = new JdbcTableMetadata(
                        metaData.getConnection(),
                        foreignKeys.getString("FKTABLE_CAT"),
                        foreignKeys.getString("FKTABLE_SCHEM"),
                        foreignKeys.getString("FKTABLE_NAME"),
                        this);
                String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                String columnType = getColumnType(metaData, fkTable, fkColumnName);
                JdbcForeignKeyConstraintMetadata pkConstraint =
                        new JdbcForeignKeyConstraintMetadata(pkTable, foreignKeys.getString("PK_NAME"), fkTable);
                JdbcForeignKeyColumnMetadata fkColumns = new JdbcForeignKeyColumnMetadata(
                        fkTable,
                        fkColumnName,
                        columnType,
                        new JdbcColumnMetadata(pkTable, foreignKeys.getString("PKCOLUMN_NAME"), columnType, false));
                SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata> pkMultimap =
                        exportedMultimaps.computeIfAbsent(pkId, key -> TreeMultimap.create());
                pkMultimap.put(pkConstraint, fkColumns);
            }
        } catch (Exception e) {
            return;
        }
        for (Map.Entry<TableId, SortedSetMultimap<JdbcForeignKeyConstraintMetadata, JdbcForeignKeyColumnMetadata>>
                entry : exportedMultimaps.entrySet()) {
            SortedMap<JdbcForeignKeyConstraintMetadata, Collection<JdbcForeignKeyColumnMetadata>> inversedFkMap =
                    new TreeMap<>();
            inversedFkMap.putAll(entry.getValue().asMap());
            exportedKeysCache.put(entry.getKey(), inversedFkMap);
        }
    }

    private void preloadIndexes(DatabaseMetaData metaData, List<JdbcTableMetadata> tables) {
        if (tables == null) {
            return;
        }
        for (JdbcTableMetadata table : tables) {
            try {
                getIndexesByTable(metaData, table, true, true);
            } catch (Exception e) {
                return;
            }
        }
    }

    private static final class TableId {
        private final String catalog;
        private final String schema;
        private final String name;

        private TableId(String catalog, String schema, String name) {
            this.catalog = catalog;
            this.schema = schema;
            this.name = name;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TableId)) {
                return false;
            }
            TableId other = (TableId) object;
            return Objects.equals(catalog, other.catalog)
                    && Objects.equals(schema, other.schema)
                    && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog, schema, name);
        }
    }

    private static final class PrimaryKeyBuilder {
        private final String name;
        private final List<String> columns = new ArrayList<>();

        private PrimaryKeyBuilder(String name) {
            this.name = name;
        }

        private void addColumn(String column) {
            columns.add(column);
        }
    }

    private static final class IndexKey {
        private final TableId tableId;
        private final boolean unique;
        private final boolean approximate;

        private IndexKey(TableId tableId, boolean unique, boolean approximate) {
            this.tableId = tableId;
            this.unique = unique;
            this.approximate = approximate;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof IndexKey)) {
                return false;
            }
            IndexKey other = (IndexKey) object;
            return unique == other.unique && approximate == other.approximate && Objects.equals(tableId, other.tableId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tableId, unique, approximate);
        }
    }
}
