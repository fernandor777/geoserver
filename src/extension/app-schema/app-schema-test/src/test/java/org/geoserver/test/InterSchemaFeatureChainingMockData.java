/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test;

/** Mock data for testing inter-schema feature chaining with joining enabled. */
public class InterSchemaFeatureChainingMockData extends AbstractAppSchemaMockData {

    protected static final String EX_PREFIX = "ex";
    protected static final String EX_URI = "http://example.com";

    @Override
    public void addContent() {
        putNamespace(EX_PREFIX, EX_URI);
        addFeatureType(
                EX_PREFIX,
                "InterSchemaParent",
                "InterSchemaFeatureChaining.xml",
                "InterSchemaFeatureChaining.xsd",
                "InterSchemaParent.properties",
                "InterSchemaFirstNested.properties",
                "appschematest_alt__SCHEMA__InterSchemaSecondNested.properties");
    }
}
