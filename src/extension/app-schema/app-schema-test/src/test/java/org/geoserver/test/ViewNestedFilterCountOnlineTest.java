/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.security.decorators.DecoratingFeatureSource;
import org.geoserver.test.onlineTest.setup.AppSchemaTestPostgisSetup;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.data.complex.MappingFeatureSource;
import org.geotools.data.complex.config.AppSchemaDataAccessConfigurator;
import org.geotools.data.util.NullProgressListener;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * Integration test for nested-filter counting when the root app-schema source is a SQL view without exposed id columns.
 */
public class ViewNestedFilterCountOnlineTest extends AbstractAppSchemaTestSupport {

    @Override
    protected ViewNestedFilterCountMockData createTestData() {
        return new ViewNestedFilterCountMockData();
    }

    @Test
    public void testNestedFilterCountWithViewSource() throws Exception {
        assumeTrue(getTestData().isPostgisOnlineTest());
        assumeTrue(AppSchemaDataAccessConfigurator.isJoining());

        // Replace the root table with a view to exercise the same code path as real deployments.
        replaceRootTableWithView();

        Document doc = postAsDOM("wfs", request());
        LOGGER.info("WFS nested-filter response:\n" + prettyString(doc));

        assertEquals("FeatureCollection", doc.getDocumentElement().getLocalName());
        assertEquals("http://www.opengis.net/wfs/2.0", doc.getDocumentElement().getNamespaceURI());
        assertEquals(
                1,
                doc.getElementsByTagNameNS(ViewNestedFilterCountMockData.EX_URI, "ViewParentFeature")
                        .getLength());
        assertEquals("1", doc.getDocumentElement().getAttribute("numberMatched"));
    }

    private void replaceRootTableWithView() throws Exception {
        FeatureTypeInfo typeInfo =
                getCatalog().getFeatureTypeByName(ViewNestedFilterCountMockData.EX_PREFIX, "ViewParentFeature");
        assertNotNull(typeInfo);

        FeatureSource fs = typeInfo.getFeatureSource(new NullProgressListener(), null);
        MappingFeatureSource mappingFs = unwrap(fs);
        FeatureSource sourceFs = mappingFs.getMapping().getSource();
        assumeTrue(sourceFs.getDataStore() instanceof JDBCDataStore);

        JDBCDataStore store = (JDBCDataStore) sourceFs.getDataStore();
        String schema = AppSchemaTestPostgisSetup.ONLINE_DB_SCHEMA;
        try (Connection cx = store.getConnection(Transaction.AUTO_COMMIT);
                Statement st = cx.createStatement()) {
            dropRelationIfExists(cx, st, schema, "VIEW_PARENT");
            st.execute(
                    "CREATE VIEW " + quoteIdentifier(schema) + ".\"VIEW_PARENT\" AS SELECT \"GML_ID\", \"NAME\" FROM "
                            + quoteIdentifier(schema)
                            + ".\"VIEW_PARENT_BASE\"");
        }
    }

    private void dropRelationIfExists(Connection cx, Statement st, String schema, String relationName)
            throws Exception {
        String relationKind = null;
        String relationKindSql = "SELECT c.relkind FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement ps = cx.prepareStatement(relationKindSql)) {
            ps.setString(1, schema);
            ps.setString(2, relationName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    relationKind = rs.getString(1);
                }
            }
        }
        if (relationKind == null || relationKind.isEmpty()) {
            return;
        }
        String qualifiedName = quoteIdentifier(schema) + "." + quoteIdentifier(relationName);
        switch (relationKind.charAt(0)) {
            case 'v':
                st.execute("DROP VIEW " + qualifiedName);
                break;
            case 'm':
                st.execute("DROP MATERIALIZED VIEW " + qualifiedName);
                break;
            case 'f':
                st.execute("DROP FOREIGN TABLE " + qualifiedName);
                break;
            default:
                st.execute("DROP TABLE " + qualifiedName);
                break;
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String request() {
        return "<wfs:GetFeature "
                + "service=\"WFS\" "
                + "version=\"2.0.0\" "
                + "count=\"1\" "
                + "outputFormat=\"application/gml+xml; version=3.2\" "
                + "xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" "
                + "xmlns:fes=\"http://www.opengis.net/fes/2.0\" "
                + "xmlns:gml=\"http://www.opengis.net/gml/3.2\" "
                + "xmlns:ex=\"http://example.com\">"
                + "<wfs:Query typeNames=\"ex:ViewParentFeature\">"
                + "<fes:Filter>"
                + "<fes:PropertyIsEqualTo>"
                + "<fes:ValueReference>"
                + "ex:nestedFeature/ex:ViewFirstNestedFeature/ex:nestedFeature/ex:ViewSecondNestedFeature/gml:name"
                + "</fes:ValueReference>"
                + "<fes:Literal>SECOND_MATCH</fes:Literal>"
                + "</fes:PropertyIsEqualTo>"
                + "</fes:Filter>"
                + "</wfs:Query>"
                + "</wfs:GetFeature>";
    }

    @SuppressWarnings("unchecked")
    private MappingFeatureSource unwrap(FeatureSource fs) {
        if (fs instanceof DecoratingFeatureSource) {
            return ((DecoratingFeatureSource<FeatureType, Feature>) fs).unwrap(MappingFeatureSource.class);
        }
        return (MappingFeatureSource) fs;
    }
}
