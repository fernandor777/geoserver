/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test;

/** Mock data that reproduces nested-filter counting against a view-backed root source. */
public class ViewNestedFilterCountMockData extends AbstractAppSchemaMockData {

    protected static final String EX_PREFIX = "ex";
    protected static final String EX_URI = "http://example.com";

    public ViewNestedFilterCountMockData() {
        // No synthetic PKs so online JDBC setup mirrors view sources without exposed keys.
        super(false);
    }

    @Override
    public void addContent() {
        putNamespace(EX_PREFIX, EX_URI);
        addFeatureType(
                EX_PREFIX,
                "ViewParentFeature",
                "ViewNestedFilterCount.xml",
                "ViewNestedFilterCount.xsd",
                "view_parent.properties",
                "view_parent_base.properties",
                "view_first_nested.properties",
                "view_second_nested.properties");
    }
}
