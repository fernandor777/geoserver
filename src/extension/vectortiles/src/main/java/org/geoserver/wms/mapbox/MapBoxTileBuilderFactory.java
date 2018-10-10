/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.mapbox;

import com.google.common.collect.ImmutableSet;
import java.awt.Rectangle;
import java.util.Set;
import org.geoserver.wms.vector.VectorTileBuilderFactory;
import org.geotools.geometry.jts.ReferencedEnvelope;

/** @author Niels Charlier */
public class MapBoxTileBuilderFactory implements VectorTileBuilderFactory {

    public static final String MIME_TYPE = "application/x-protobuf;type=mapbox-vector";

    public static final Set<String> OUTPUT_FORMATS = ImmutableSet.of(MIME_TYPE, "pbf");

    private final String mimeType;
    private final Set<String> outputFormats;

    public MapBoxTileBuilderFactory(String mimeType, String extraOutputFormat) {
        this.mimeType = mimeType;
        this.outputFormats = ImmutableSet.of(mimeType, extraOutputFormat);
    }

    public MapBoxTileBuilderFactory(String mimeType) {
        this.mimeType = mimeType;
        this.outputFormats = ImmutableSet.of(mimeType);
    }

    public MapBoxTileBuilderFactory() {
        this.mimeType = MIME_TYPE;
        this.outputFormats = OUTPUT_FORMATS;
    }

    @Override
    public Set<String> getOutputFormats() {
        return outputFormats;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public MapBoxTileBuilder newBuilder(Rectangle screenSize, ReferencedEnvelope mapArea) {
        return new MapBoxTileBuilder(screenSize, mapArea, mimeType);
    }
}
