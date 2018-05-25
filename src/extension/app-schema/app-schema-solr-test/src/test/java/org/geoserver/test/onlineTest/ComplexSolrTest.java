/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test.onlineTest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geoserver.util.IOUtils;
import org.geotools.data.solr.SolrTypeData;
import org.geotools.data.solr.SolrTypeData.SolrTypes;
import org.geotools.data.solr.StationData;
import org.geotools.data.solr.StationData.Stations;
import org.geotools.data.solr.TestsSolrUtils;
import org.geotools.feature.NameImpl;
import org.geotools.image.test.ImageAssert;
import org.geotools.util.URLs;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * This class contains the integration tests (online tests) for the integration between App-Schema
 * and Apache Solr Create solr.properties file in {{user-dir}}/.geoserver folder Set solr_url
 * property URL config example: solr_url=http://localhost:8983/solr, and create "stations" core in Solr
 *
 */
public final class ComplexSolrTest extends GeoServerSystemTestSupport {
    
    private static final String SOLR_URL_KEY = "solr_url";
    private static final String CORE_NAME = "stations";
    private static final String MAPPING_FILENAME = "mappings_solr.xml";
    
    private Properties fixture;
    
    // xpath engines used to check WFS responses
    private XpathEngine WFS11_XPATH_ENGINE;
    private XpathEngine WFS20_XPATH_ENGINE;
    
    private HttpSolrClient solrClient;
    
    private static final Path ROOT_DIRECTORY = createTempDir();

    @Test
    public void testGetStationFeatures() throws Exception {
        Document document =
                getAsDOM("wfs?request=GetFeature&version=1.1.0&srsName=EPSG:4326&typename=st:Station");
        checkStationData(7,"Bologna","POINT (11.34 44.5)", WFS11_XPATH_ENGINE, document);
        checkStationData(13,"Alessandria","POINT (8.63 44.92)", WFS11_XPATH_ENGINE, document);
    }
    
    @Test
    public void testFilterStationFeatures() throws Exception {
        String postContent = readResourceContent("/querys/postQuery1.xml");
        Document document =
                postAsDOM("wfs?request=GetFeature&version=1.1.0&srsName=EPSG:4326&typename=st:Station", postContent);
        checkStationData(7,"Bologna","POINT (11.34 44.5)", WFS11_XPATH_ENGINE, document);
        checkNoStationId(13, WFS11_XPATH_ENGINE, document);
    }
    
    @Test
    public void testStationsWmsGetMap() throws Exception {
        // execute the WMS GetMap request
        MockHttpServletResponse result =
                getAsServletResponse(
                        "wms?SERVICE=WMS&VERSION=1.1.1"
                                + "&REQUEST=GetMap&FORMAT=image/png&TRANSPARENT=true&STYLES&LAYERS=st:Station"
                                + "&SRS=EPSG:4326&WIDTH=768&HEIGHT=768"
                                + "&BBOX=5,40,15,50");
        assertThat(result.getStatus(), is(200));
        assertThat(result.getContentType(), is("image/png"));
        // check that we got the expected image back
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(getBinary(result)));
        ImageAssert.assertEquals(
                URLs.urlToFile(getClass().getResource("/results/wms_result.png")), image, 10);
    }
    
    @Test
    public void testStationsWmsGetFeatureInfo() throws Exception {
        Document document =
                getAsDOM(
                        "wms?SERVICE=WMS&VERSION=1.1.1"
                                + "&REQUEST=GetFeatureInfo&FORMAT=image/png&TRANSPARENT=true&QUERY_LAYERS=st:Station"
                                + "&STYLES&LAYERS=st:Station&INFO_FORMAT=text/xml; subtype=gml/3.1.1"
                                + "&FEATURE_COUNT=50&X=278&Y=390&SRS=EPSG:4326&WIDTH=768&HEIGHT=768"
                                + "&BBOX=5,40,15,50");
        checkStationData(13,"Alessandria","POINT (8.63 44.92)", WFS11_XPATH_ENGINE, document);
        checkNoStationId(7, WFS11_XPATH_ENGINE, document);
        
        Document document2 =
                getAsDOM(
                        "wms?SERVICE=WMS&VERSION=1.1.1"
                                + "&REQUEST=GetFeatureInfo&FORMAT=image/png&TRANSPARENT=true&QUERY_LAYERS=st:Station"
                                + "&STYLES&LAYERS=st:Station&INFO_FORMAT=text/xml; subtype=gml/3.1.1"
                                + "&FEATURE_COUNT=50&X=486&Y=422&SRS=EPSG:4326&WIDTH=768&HEIGHT=768"
                                + "&BBOX=5,40,15,50");
        checkStationData(7,"Bologna","POINT (11.34 44.5)", WFS11_XPATH_ENGINE, document2);
        checkNoStationId(13, WFS11_XPATH_ENGINE, document2);
    }
    
    private void checkStationData(Integer id, String name, String position,XpathEngine engine, 
            Document document) {
        checkCount(engine, document, 1, String.format("/wfs:FeatureCollection/gml:featureMembers"
                + "/st:Station[@gml:id='%s'][st:stationName='%s'][st:position='%s']", id, name, position));
    }
    
    private void checkNoStationId(Integer id, XpathEngine engine, 
            Document document) {
        checkCount(engine, document, 0, String.format("/wfs:FeatureCollection/gml:featureMembers"
                + "/st:Station[@gml:id='%s']", id));
    }
    
    private String getSolrCoreURL() {
        return fixture.getProperty(SOLR_URL_KEY) + "/" + CORE_NAME;
    }
    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        loadFixture();
        setupStationsMappings();
        setupSolrData();
        
        super.onSetUp(testData);
        Catalog catalog = getCatalog();
        // create necessary stations workspace
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
        File file1 = new File(ROOT_DIRECTORY.toFile(), MAPPING_FILENAME);
        params.put("url", "file:" + file1.getAbsolutePath() );
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
        catalog.add(featureType);
        LayerInfo layer = builder.buildLayer(featureType);
        layer.setDefaultStyle(catalog.getStyleByName("point"));
        catalog.add(layer);
    }
    
    private void setupSolrData() throws Exception {
        solrClient = new HttpSolrClient.Builder(getSolrCoreURL()).build();
        // create geometry type
        TestsSolrUtils.createGeometryFieldType(solrClient);
        // setup collection types:
        JAXBContext typesCtx = JAXBContext.newInstance(SolrTypes.class);
        Unmarshaller typesUm = typesCtx.createUnmarshaller();
        SolrTypes types = (SolrTypes) typesUm.unmarshal(ComplexSolrTest.class
                .getResource("/test-data/appschema/solr_types.xml"));
        for (SolrTypeData adata : types.getTypes()) {
            createField(adata.getName(), adata.getType(), adata.getMulti());
        }
        // add data
        JAXBContext stationsCtx = JAXBContext.newInstance(Stations.class);
        Unmarshaller stationsUm = stationsCtx.createUnmarshaller();
        Stations stations = (Stations) stationsUm.unmarshal(ComplexSolrTest.class
                .getResource("/test-data/appschema/stationsData.xml"));
        for (StationData adata : stations.getStations()) {
            solrClient.add(adata.toSolrDoc());
        }
        solrClient.commit();
    }
    
    protected void createField(String name, String type, boolean multiValued) {
        TestsSolrUtils.createField(solrClient, name, type, multiValued);
    }
    
    private void loadFixture() throws Exception {
        File fixFile = getFixtureFile();
        assumeTrue(fixFile.exists());
        fixture = loadFixtureProperties(fixFile);
    }
    
    private void setupStationsMappings() throws Exception {
        // create a cache directory for schema resolutions if it doesn't exists
        File cache = new File(ROOT_DIRECTORY.toFile(), "app-schema-cache");
        if (cache.mkdir()) {
            // cache directory created
            LOGGER.log(
                    Level.INFO,
                    String.format(
                            "App-Schema schemas resolutions cache directory '%s' created.",
                            cache.getAbsolutePath()));
        }
        // moving schemas files to the test directory
        moveResourceToTempDir("/test-data/appschema/meteo.xsd", "meteo.xsd");
        // moving mapping xml, changing solr url
        moveMappingToTempDir("/test-data/appschema/" + MAPPING_FILENAME, MAPPING_FILENAME);
    }
    
    private void moveMappingToTempDir(String inputFilePath, String OutputFilename) throws Exception {
        // Modify datasource and copy xml
        Document doc =
                DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(ComplexSolrTest.class.getResourceAsStream(inputFilePath)));
        Node solrDs = doc.getElementsByTagName("SolrDataStore").item(0);
        NodeList dsChilds = solrDs.getChildNodes();
        for (int i = 0; i < dsChilds.getLength(); i++) {
            Node achild = dsChilds.item(i);
            if (achild.getNodeName().equals("url")) {
                achild.setTextContent(getSolrCoreURL());
            }
        }
        // write new xml file:
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(ROOT_DIRECTORY.toFile(), OutputFilename));
        transformer.transform(source, result);
    }
    
    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("app-schema-cache");
        } catch (Exception exception) {
            throw new RuntimeException("Error creating temporary directory.", exception);
        }
    }
    
    /** Gets the fixture file for Solr located in %USER%/.geoserver/ , parent directories are created if needed. */
    private static File getFixtureFile() {
        File directory = new File(System.getProperty("user.home") + "/.geoserver");
        if (!directory.exists()) {
            // make sure parent directory exists
            directory.mkdir();
        }
        return new File(directory, "solr.properties");
    }
    
    @Before
    public void beforeTest() {
        // instantiate WFS 1.1 xpath engine
        WFS11_XPATH_ENGINE =
                buildXpathEngine(
                        "wfs", "http://www.opengis.net/wfs",
                        "gml", "http://www.opengis.net/gml");
        // instantiate WFS 2.0 xpath engine
        WFS20_XPATH_ENGINE =
                buildXpathEngine(
                        "wfs", "http://www.opengis.net/wfs/2.0",
                        "gml", "http://www.opengis.net/gml/3.2");
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        // remove the temporary directory
        if (ROOT_DIRECTORY != null) {
//            IOUtils.delete(ROOT_DIRECTORY.toFile());
        }
    }
    
    /**
     * Helper method that builds a XPATH engine using the base namespaces (ow, ogc, etc ...), all
     * the namespaces available in the GeoServer catalog and the provided extra namespaces.
     */
    private XpathEngine buildXpathEngine(String... extraNamespaces) {
        // build xpath engine
        XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        Map<String, String> namespaces = new HashMap<>();
        // add common namespaces
        namespaces.put("ows", "http://www.opengis.net/ows");
        namespaces.put("ogc", "http://www.opengis.net/ogc");
        namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
        namespaces.put("xsd", "http://www.w3.org/2001/XMLSchema");
        namespaces.put("xlink", "http://www.w3.org/1999/xlink");
        namespaces.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        // add catalog namespaces
        for (NamespaceInfo namespace : getCatalog().getNamespaces()) {
            namespaces.put(namespace.getPrefix(), namespace.getURI());
        }
        // add provided namespaces
        if (extraNamespaces.length % 2 != 0) {
            throw new RuntimeException("Invalid number of namespaces provided.");
        }
        for (int i = 0; i < extraNamespaces.length; i += 2) {
            namespaces.put(extraNamespaces[i], extraNamespaces[i + 1]);
        }
        // add namespaces to the xpath engine
        xpathEngine.setNamespaceContext(new SimpleNamespaceContext(namespaces));
        return xpathEngine;
    }
    
    private static Properties loadFixtureProperties(File fixtureFile) {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(fixtureFile)) {
            // load properties from fixture file
            properties.load(input);
            return properties;
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format(
                            "Error reading fixture file '%s'.", fixtureFile.getAbsolutePath()),
                    exception);
        }
    }
    
    /* Helper method that moves a resource to the tests temporary directory and return the resource
    * file path.
    */
    private static File moveResourceToTempDir(String resourcePath, String resourceName) {
       // create the output file
       File outputFile = new File(ROOT_DIRECTORY.toFile(), resourceName);
       try (InputStream input = ComplexSolrTest.class.getResourceAsStream(resourcePath);
               OutputStream output = new FileOutputStream(outputFile)) {
           // copy the resource content to the output file
           IOUtils.copy(input, output);
       } catch (Exception exception) {
           throw new RuntimeException("Error moving resource to temporary directory.", exception);
       }
       return outputFile;
    }
    
    protected void checkCount(XpathEngine xpathEngine, Document document, int expectedCount, String xpath) {
        try {
            // evaluate the xpath and compare the number of nodes found
            assertEquals(expectedCount, xpathEngine.getMatchingNodes(xpath, document).getLength());
        } catch (Exception exception) {
            throw new RuntimeException("Error evaluating xpath.", exception);
        }
    }
    
    /** Helper method that reads the content of a resource to a string. */
    private static String readResourceContent(String resourcePath) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = ComplexSolrTest.class.getResourceAsStream(resourcePath)) {
            IOUtils.copy(input, output);
            return new String(output.toByteArray());
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format("Error reading resource '%s' content.", resourcePath), exception);
        }
    }
    
}
