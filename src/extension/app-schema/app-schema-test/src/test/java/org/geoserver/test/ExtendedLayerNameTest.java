package org.geoserver.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.data.test.SystemTestData;
import org.geotools.feature.NameImpl;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;

public class ExtendedLayerNameTest extends GeoServerSystemTestSupport {

    // xpath engines used to check WFS responses
    private XpathEngine WFS11_XPATH_ENGINE;
    private XpathEngine WFS20_XPATH_ENGINE;

    private static Path testFolderPath;
    private static Path mappingsFolderPath;

    @BeforeClass
    public static void prepare() throws IOException {
        testFolderPath = Files.createTempDirectory(Paths.get("target/test-classes"), "layernames");
        File srcDir = new File("target/test-classes/test-data/stations/extendedLayerNameTest");
        File destDir = Paths.get(testFolderPath.toString(), "extendedLayerNameTest").toFile();
        FileUtils.copyDirectory(srcDir, destDir);
        mappingsFolderPath = destDir.toPath();
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        // set extended char support on layer names on
        GeoServerInfo gsInfo = getGeoServer().getGlobal();
        gsInfo.setExtendedCharsOnLayerNamesEnabled(true);
        getGeoServer().save(gsInfo);
        // setup data
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        WorkspaceInfoImpl workspace = new WorkspaceInfoImpl();
        workspace.setName("st");
        NamespaceInfoImpl nameSpace = new NamespaceInfoImpl();
        nameSpace.setPrefix("st");
        nameSpace.setURI("http://www.stations.org/1.0");
        catalog.add(workspace);
        catalog.add(nameSpace);
        // create the app-schema data store
        Map<String, Serializable> params = new HashMap<>();
        params.put("dbtype", "app-schema");
        params.put(
                "url",
                Paths.get(mappingsFolderPath.toString(), "stationsDefaultGeometry.xml")
                        .toUri()
                        .toString());
        DataStoreInfoImpl dataStore = new DataStoreInfoImpl(getCatalog());
        dataStore.setId("stations");
        dataStore.setName("stations");
        dataStore.setType("app-schema");
        dataStore.setConnectionParameters(params);
        dataStore.setWorkspace(workspace);
        dataStore.setEnabled(true);
        catalog.add(dataStore);
        // build the feature type for the root mapping (StationFeature)
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setStore(dataStore);
        builder.setWorkspace(workspace);
        FeatureTypeInfo featureType =
                builder.buildFeatureType(new NameImpl(nameSpace.getURI(), "Station"));
        featureType.setName("other:testStation");
        featureType.setNativeName("Station");
        catalog.add(featureType);
        LayerInfo layer = builder.buildLayer(featureType);
        layer.setDefaultStyle(catalog.getStyleByName("point"));
        catalog.add(layer);
    }

    @Test
    public void testStationGet() throws Exception {
        Document document =
                getAsDOM("st/wfs?request=GetFeature&version=1.1.0&typenames=other%3AtestStation");
        checkCount(
                WFS11_XPATH_ENGINE,
                document,
                3,
                "//wfs:FeatureCollection/gml:featureMembers/st:Station");
    }

    @Test
    public void testStationPostQuery() throws Exception {
        String xmlQuery = resourceToString("wfs110query.xml");
        Document document = postAsDOM("st/wfs", xmlQuery);
        checkCount(
                WFS11_XPATH_ENGINE,
                document,
                1,
                "//wfs:FeatureCollection/gml:featureMembers/st:Station");
    }

    @Test
    public void testStationGet20() throws Exception {
        Document document =
                getAsDOM("st/wfs?request=GetFeature&version=2.0.0&typeNames=other%3AtestStation");
        checkCount(
                WFS20_XPATH_ENGINE, document, 3, "//wfs:FeatureCollection/wfs:member/st:Station");
    }

    @Test
    public void testStationPostQuery20() throws Exception {
        // load wfs 2.0.0 query body at wfs200query.xml
        String xmlQuery = resourceToString("wfs200query.xml");
        Document document = postAsDOM("st/wfs", xmlQuery);
        checkCount(
                WFS20_XPATH_ENGINE, document, 1, "//wfs:FeatureCollection/wfs:member/st:Station");
    }

    private String resourceToString(String filename) throws IOException {
        return IOUtils.toString(
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "test-data/stations/extendedLayerNameTest/" + filename),
                StandardCharsets.UTF_8);
    }

    @Before
    public void beforeTest() {
        // instantiate WFS 1.1 xpath engine
        WFS11_XPATH_ENGINE =
                StationsMockData.buildXpathEngine(
                        getBaseNamespaces(),
                        "wfs",
                        "http://www.opengis.net/wfs",
                        "gml",
                        "http://www.opengis.net/gml");
        // instantiate WFS 2.0 xpath engine
        WFS20_XPATH_ENGINE =
                StationsMockData.buildXpathEngine(
                        getBaseNamespaces(),
                        "wfs",
                        "http://www.opengis.net/wfs/2.0",
                        "gml",
                        "http://www.opengis.net/gml/3.2");
    }

    protected Map<String, String> getBaseNamespaces() {
        Map<String, String> nss = new HashMap<>();
        nss.put("st", "http://www.stations.org/1.0");
        nss.put("ms", "http://www.measurements.org/1.0");
        return nss;
    }

    /**
     * Returns xml String from Document Object
     *
     * @param document
     * @return
     * @throws TransformerException
     */
    protected String toString(Document document) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Helper method that checks if the provided XPath expression evaluated against the provided XML
     * document yields the expected number of matches.
     */
    private void checkCount(
            XpathEngine xpathEngine, Document document, int expectedCount, String xpath) {
        try {
            // evaluate the xpath and compare the number of nodes found
            assertEquals(expectedCount, xpathEngine.getMatchingNodes(xpath, document).getLength());
        } catch (Exception exception) {
            throw new RuntimeException("Error evaluating xpath.", exception);
        }
    }

    public static void copyResource(String resourcePath, Path directoryPath) throws IOException {
        ClassLoader cl = LayerNamesTest.class.getClassLoader();
        Files.copy(cl.getResourceAsStream(resourcePath), directoryPath);
    }
}
