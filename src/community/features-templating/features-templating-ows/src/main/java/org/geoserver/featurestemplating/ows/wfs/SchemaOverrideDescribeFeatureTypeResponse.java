/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.ows.wfs;

import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.featurestemplating.builders.EncodingHints;
import org.geoserver.featurestemplating.builders.VendorOptions;
import org.geoserver.featurestemplating.builders.impl.RootBuilder;
import org.geoserver.featurestemplating.configuration.TemplateIdentifier;
import org.geoserver.featurestemplating.configuration.TemplateLoader;
import org.geoserver.featurestemplating.configuration.schema.SchemaLoader;
import org.geoserver.featurestemplating.writers.GeoJSONWriter;
import org.geoserver.featurestemplating.writers.TemplateOutputWriter;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSDescribeFeatureTypeOutputFormat;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geotools.geometry.jts.ReferencedEnvelope;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.geoserver.featurestemplating.builders.EncodingHints.isSingleFeatureRequest;

/** Write a valid GeoJSON output from a template */
public class SchemaOverrideDescribeFeatureTypeResponse extends WFSDescribeFeatureTypeOutputFormat {

    private String outputFormat;

    private String content;

    public SchemaOverrideDescribeFeatureTypeResponse(
            GeoServer gs, String outputFormat, String content) {
        super(gs, outputFormat);
        this.outputFormat = outputFormat;
        this.content = content;
    }

    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return outputFormat;
    }

    @Override
    protected void write(FeatureTypeInfo[] featureTypeInfos, OutputStream output, Operation describeFeatureType) throws IOException {
        try {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ServiceException(e);
        }
    }
}
