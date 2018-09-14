package org.geoserver.wfs.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.WfsFactory;
import org.geoserver.platform.Operation;
import org.geoserver.wfs.WFSTestSupport;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.feature.FeatureCollection;
import org.junit.Before;

public abstract class BaseShapeZipTest extends WFSTestSupport {

    Operation op;
    protected GetFeatureType gft;

    @Before
    public void init() throws Exception {
        gft = WfsFactory.eINSTANCE.createGetFeatureType();
        op = new Operation("GetFeature", getServiceDescriptor10(), null, new Object[] {gft});
    }

    public BaseShapeZipTest() {
        super();
    }

    /**
     * Saves the feature source contents into a zipped shapefile, returns the output as a byte array
     */
    protected byte[] writeOut(FeatureCollection fc, long maxShpSize, long maxDbfSize)
            throws IOException {
        ShapeZipOutputFormat zip = new ShapeZipOutputFormat();
        zip.setMaxDbfSize(maxDbfSize);
        zip.setMaxShpSize(maxShpSize);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FeatureCollectionResponse fct =
                FeatureCollectionResponse.adapt(WfsFactory.eINSTANCE.createFeatureCollectionType());
        fct.getFeature().add(fc);
        zip.write(fct, bos, op);
        return bos.toByteArray();
    }

    /**
     * Saves the feature source contents into a zipped shapefile, returns the output as a byte array
     */
    protected byte[] writeOut(FeatureCollection fc) throws IOException {
        ShapeZipOutputFormat zip = new ShapeZipOutputFormat();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FeatureCollectionResponse fct =
                FeatureCollectionResponse.adapt(WfsFactory.eINSTANCE.createFeatureCollectionType());
        fct.getFeature().add(fc);
        zip.write(fct, bos, op);
        return bos.toByteArray();
    }
}
