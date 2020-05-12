/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.map;

import java.util.Arrays;
import java.util.List;
import org.geotools.map.Layer;
import org.geotools.renderer.SymbolizersPreProcessor;
import org.geotools.styling.PolygonSymbolizerImpl;
import org.geotools.styling.Symbolizer;
import org.opengis.feature.Feature;

/**
 * SymbolizerPreProcessor implementation used for testing purposes only. Needs explicit activation
 * via the enabled attribute.
 */
public class TestingSymbolizerPreProcessor implements SymbolizersPreProcessor {

    private boolean enabled = false;

    @Override
    public List<Symbolizer> apply(Feature feature, Layer layer, List<Symbolizer> symbolizers) {
        if (!enabled) return null;
        Symbolizer symbolizer = symbolizers.get(0);
        if (symbolizer instanceof PolygonSymbolizerImpl) {
            PolygonSymbolizerImpl polygonSymb = (PolygonSymbolizerImpl) symbolizer;
            polygonSymb.setFill(null);
            // polygonSymb.getFill().setOpacity(new LiteralExpressionImpl(0.0d));
        }
        return Arrays.asList(symbolizer);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
