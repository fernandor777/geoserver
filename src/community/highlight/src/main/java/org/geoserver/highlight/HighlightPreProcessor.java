package org.geoserver.highlight;

import java.util.List;

import org.geotools.map.Layer;
import org.geotools.renderer.SymbolizersPreProcessor;
import org.geotools.styling.Style;
import org.geotools.styling.Symbolizer;
import org.opengis.feature.Feature;

public class HighlightPreProcessor implements SymbolizersPreProcessor {

    @Override
    public boolean appliesTo(Layer layer) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<String> getAttributes(Layer layer) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public double getBuffer(Layer layer, Style style) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public List<Symbolizer> apply(Feature feature, Layer layer, List<Symbolizer> symbolizers) {
        // TODO Auto-generated method stub
        return null;
    }

}
