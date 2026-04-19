/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.security.decorators.DecoratingFeatureSource;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.Query;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.filter.PropertyIsEqualTo;
import org.geotools.appschema.filter.FilterFactoryImplNamespaceAware;
import org.geotools.data.complex.MappingFeatureSource;
import org.geotools.data.complex.config.AppSchemaDataAccessConfigurator;
import org.geotools.data.util.NullProgressListener;
import org.geotools.feature.FeatureIterator;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.Test;

/** Verifies nested feature chaining across different database schemas in the same JDBC datastore. */
public class InterSchemaFeatureChainingTest extends AbstractAppSchemaTestSupport {

    @Override
    protected InterSchemaFeatureChainingMockData createTestData() {
        return new InterSchemaFeatureChainingMockData();
    }

    @Test
    public void testNestedFilterAndPagingAcrossSchemas() throws Exception {
        FeatureTypeInfo typeInfo = getCatalog().getFeatureTypeByName("ex", "InterSchemaParent");
        assertNotNull(typeInfo);

        FeatureSource fs = typeInfo.getFeatureSource(new NullProgressListener(), null);
        MappingFeatureSource mappingFs = unwrap(fs);
        FeatureSource sourceFs = mappingFs.getMapping().getSource();

        assumeTrue(getTestData().isPostgisOnlineTest());
        assumeTrue(sourceFs.getDataStore() instanceof JDBCDataStore);
        assumeTrue(AppSchemaDataAccessConfigurator.isJoining());

        FilterFactoryImplNamespaceAware ff = new FilterFactoryImplNamespaceAware();
        ff.setNamepaceContext(mappingFs.getMapping().getNamespaces());

        PropertyIsEqualTo equals = ff.equals(
                ff.property(
                        "ex:nestedFeature/ex:InterSchemaFirstNested/ex:nestedFeature/ex:InterSchemaSecondNested/gml:name"),
                ff.literal("SECOND_MATCH"));

        Query query = new Query(mappingFs.getSchema().getName().getLocalPart(), equals);
        query.setStartIndex(0);
        query.setMaxFeatures(1);

        int count = 0;
        try (FeatureIterator it = mappingFs.getFeatures(query).features()) {
            while (it.hasNext()) {
                Feature feature = (Feature) it.next();
                assertNotNull(feature);
                count++;
            }
        }
        assertEquals(1, count);

        // sanity check without paging to ensure the filter still resolves to a single top-level feature
        count = 0;
        try (FeatureIterator it = mappingFs.getFeatures(equals).features()) {
            while (it.hasNext()) {
                assertTrue(it.next() instanceof Feature);
                count++;
            }
        }
        assertEquals(1, count);
    }

    @SuppressWarnings("unchecked")
    private MappingFeatureSource unwrap(FeatureSource fs) {
        if (fs instanceof DecoratingFeatureSource) {
            return ((DecoratingFeatureSource<FeatureType, Feature>) fs).unwrap(MappingFeatureSource.class);
        }
        return (MappingFeatureSource) fs;
    }
}
