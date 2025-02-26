package org.geoserver.smartdataloader.postgis;

import org.geoserver.smartdataloader.visitors.JDBCAppSchemaVisitorTest;
import org.geotools.jdbc.JDBCTestSetup;

public class PostGisAppSchemaVisitorTest extends JDBCAppSchemaVisitorTest {

    public PostGisAppSchemaVisitorTest() {
        super(new PostGisFixtureHelper());
    }

    @Override
    public void testStationsRootEntityOverridePk() throws Exception {
        super.testStationsRootEntityOverridePk();
    }

    @Override
    protected JDBCTestSetup createTestSetup() {
        return new MeteoStationsPostGISTestSetUp();
    }
}
