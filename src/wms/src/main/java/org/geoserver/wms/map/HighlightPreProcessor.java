package org.geoserver.wms.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.Layer;
import org.geotools.renderer.SymbolizersPreProcessor;
import org.geotools.styling.Graphic;
import org.geotools.styling.Mark;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.StyleFactoryImpl;
import org.geotools.styling.Symbolizer;
import org.geotools.util.factory.GeoTools;
import org.opengis.feature.Feature;
import org.opengis.filter.FilterFactory;

public class HighlightPreProcessor implements SymbolizersPreProcessor {

    private static final String COLOR1 = "#25BF20";
    
    private StyleFactory styleFactory = new StyleFactoryImpl();
    private FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
    
    private boolean enabled = false;
    
    @Override
    public List<Symbolizer> apply(Feature feature, Layer layer, List<Symbolizer> symbolizers) {
        if(!enabled) return null;
        PointSymbolizer highlightSymbolizer = styleFactory.pointSymbolizer("name", 
                filterFactory.property(null), 
                null, null, 
                createHighlightGraphic());
        List<Symbolizer> resultList = new ArrayList<Symbolizer>(symbolizers);
        resultList.add(highlightSymbolizer);
        return resultList;
    }
    
    private Graphic createHighlightGraphic() {
        return styleFactory.graphic(Arrays.asList(createHighlightMark(COLOR1)), 
                filterFactory.literal(1.0), 
                filterFactory.literal(25), 
                filterFactory.literal(0.0), 
                styleFactory.anchorPoint(filterFactory.literal(-12.5), filterFactory.literal(-12.5)), 
                styleFactory.displacement(filterFactory.literal(0.0), filterFactory.literal(0.0)));
    }
    
    private Mark createHighlightMark(String color) {
        Mark circleMark = styleFactory.getCircleMark();
        circleMark.setStroke(styleFactory.createStroke(
                filterFactory.literal(color), filterFactory.literal(2)));
        circleMark.setFill(styleFactory.createFill(filterFactory.literal(color), filterFactory.literal(0.0)));
        return circleMark;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
