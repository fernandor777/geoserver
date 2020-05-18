package org.geoserver.highlight;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;

import org.opengis.filter.Filter;

public class HighlightRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String layerName;
    private final Filter filter;
    private final String color;
    private final Integer strokeSize;
    private final Integer marginSize;
    
    public HighlightRule(String layerName, Filter filter, String color, 
            Integer strokeSize, Integer marginSize) {
        this.layerName = requireNonNull(layerName);
        this.filter = requireNonNull(filter);
        this.color = requireNonNull(color);
        this.strokeSize = strokeSize;
        this.marginSize = marginSize;
    }

    public String getLayerName() {
        return layerName;
    }

    public Filter getFilter() {
        return filter;
    }

    public String getColor() {
        return color;
    }

    public Integer getStrokeSize() {
        return strokeSize;
    }

    public Integer getMarginSize() {
        return marginSize;
    }
}
