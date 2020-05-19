/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

public class AttributesGlobeBounds {

    private final GlobeBounds globeBounds;
    private final TextBounds textBounds;
    private final AttributesGlobeConfiguration configuration;
    
    public AttributesGlobeBounds(GlobeBounds globeBounds, TextBounds textBounds,
            AttributesGlobeConfiguration configuration) {
        this.globeBounds = globeBounds;
        this.textBounds = textBounds;
        this.configuration = configuration;
    }

    public GlobeBounds getGlobeBounds() {
        return globeBounds;
    }

    public TextBounds getTextBounds() {
        return textBounds;
    }

    public AttributesGlobeConfiguration getConfiguration() {
        return configuration;
    }
}
