/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

import java.io.Serializable;

import org.opengis.filter.Filter;

/**
 * Data model class for parsing RENDERLABEL vendor parameter on WMS requests.
 * Used by the {@link AttributesGlobeKvpParser} KVP parser.
 */
public class AttributeLabelParameter implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String layerName;
    private final Filter filter;
    private final AttributeGlobeFonts fonts;

    /**
     * Constructor.
     * @param layerName the layer name
     * @param filter the ECQL filter instance
     */
    public AttributeLabelParameter(String layerName, Filter filter) {
        this(layerName, filter, AttributeGlobeFonts.getDefault());
    }

    /**
     * Constructor.
     * @param layerName the layer name
     * @param filter the ECQL filter instance
     * @param fonts the fonts properties, nullable
     */
    public AttributeLabelParameter(String layerName, Filter filter, 
            AttributeGlobeFonts fonts) {
        this.layerName = layerName;
        this.filter = filter;
        this.fonts = fonts;
    }

    public Filter getFilter() {
        return filter;
    }

    public String getLayerName() {
        return layerName;
    }

    public AttributeGlobeFonts getFonts() {
        return fonts;
    }
}
