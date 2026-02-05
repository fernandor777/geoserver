package org.geoserver.smartdataloader.metadata.jdbc;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.util.List;
import org.geoserver.smartdataloader.AbstractJDBCSmartDataLoaderTestSupport;
import org.geoserver.smartdataloader.JDBCFixtureHelper;
import org.geoserver.smartdataloader.metadata.DataStoreMetadata;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.junit.Test;

/** Tests in Smart AppSchema related to use of a DataStoreMetadata linked to a JDBC connection. */
public abstract class JDBCDataStoreMetadataTest extends AbstractJDBCSmartDataLoaderTestSupport {

    public JDBCDataStoreMetadataTest(JDBCFixtureHelper fixtureHelper) {
        super(fixtureHelper);
    }

    @Test
    public void testJdbcDataStoreMetadataLoad() throws Exception {
        Connection connection = this.dataSource.getConnection();
        DataStoreMetadata dm = getDataStoreMetadata(connection);
        List<EntityMetadata> entities = dm.getDataStoreEntities();

        List<RelationMetadata> relations = dm.getDataStoreRelations();

        assertEquals(5, entities.size());
        assertEquals(8, relations.size());

        connection.close();
    }

    @Test
    public void testMeteoObservationsEntityAttributes() throws Exception {
        Connection connection = this.dataSource.getConnection();
        JdbcHelper jdbcHelper = new DefaultJdbcHelper();
        EntityMetadata entity =
                new JdbcTableMetadata(connection, null, ONLINE_DB_SCHEMA, "meteo_observations", jdbcHelper);

        assertEquals(6, entity.getAttributes().size());

        connection.close();
    }

    @Test
    public void testMeteoObservationsEntityRelations() throws Exception {
        Connection connection = this.dataSource.getConnection();
        JdbcHelper jdbcHelper = new DefaultJdbcHelper();
        EntityMetadata entity =
                new JdbcTableMetadata(connection, null, ONLINE_DB_SCHEMA, "meteo_observations", jdbcHelper);
        assertEquals(4, entity.getRelations().size());

        connection.close();
    }
}
