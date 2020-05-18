/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.geoserver.ows.KvpParser;
import org.geoserver.util.XCQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;

/**
 * KVP parser for the HIGHLIGHT vendor parameter.  
 * Parses the highlight rules string to a {@code List<HighlightRule>}.
 * <p>The parameter format is:
 * <pre>
 * HIGHLIGHT=
 * {LAYER},{FILTER},{COLOR},{SHAPE_WELL_KNOWN_NAME},{STROKE_SIZE},{MARGIN_SIZE};
 * {LAYER},{FILTER},{COLOR},{SHAPE_WELL_KNOWN_NAME},{STROKE_SIZE},{MARGIN_SIZE};
 * {LAYER},{FILTER},{COLOR},{SHAPE_WELL_KNOWN_NAME},{STROKE_SIZE},{MARGIN_SIZE};
 * </pre> </p>
 * <p>
 * An example:
 * <pre>
 * HIGHLIGHT=
 * st:STATIONS,ID IN (100,101,102),#FF0000,circle,2,1;
 * st:STATIONS,ID IN (102,103,104),#00FF00,square,2,1;
 * st:STATIONS,ID IN (104,105,106),#0000FF,triangle,2,1;
 * </pre></p>
 */
public class HighlightKvpParser extends KvpParser {

    public static final String HIGHLIGHT_PARAM = "highlight";
    
    public HighlightKvpParser() {
        super(HIGHLIGHT_PARAM, List.class);
    }

    @Override
    public Object parse(String value) throws Exception {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("HIGHLIGHT parameter value is empty or null");
        }
        String[] rulesString = splitRulesString(value);
        List<HighlightRule> rules = new ArrayList<>(rulesString.length);
        for (String eruleStr : rulesString) {
            rules.add(toRule(eruleStr));
        }
        return rules;
    }
    
    private String[] splitRulesString(String value) {
        return value.split(Pattern.quote(";"));
    }
    
    /**
     * Converts a string rule definition to a {@link HighlightRule} instance, validating the inner parameters. 
     */
    private HighlightRule toRule(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("HIGHLIGHT rule is empty or null");
        }
        String[] params = value.split(Pattern.quote(","));
        validateParams(value, params);
        String layerName = params[0];
        Filter filter = parseFilter(params[1]);
        String color = params[2];
        Integer strokeSize = getStrokeSize(params);
        Integer marginSize = getMarginSize(params);
        
        return new HighlightRule(layerName, filter, color, strokeSize, marginSize);
    }
    
    private Integer getMarginSize(String[] params) {
        if (params.length > 4) {
            String marginStr = params[4];
            try {
                return Integer.valueOf(marginStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Error parsing margin size integer from string param: " + marginStr);
            }
        }
        return null;
    }
    
    private Integer getStrokeSize(String[] params) {
        if (params.length > 3) {
            String strokeStr = params[3];
            try {
                return Integer.valueOf(strokeStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Error parsing stroke size integer from string param: " + strokeStr);
            }
        }
        return null;
    }

    private void validateParams(String value, String[] params) {
        if (params.length < 3) {
            throw new IllegalArgumentException(
                    "HIGHLIGHT rule requires at least 3 comma separated parameters, but found "
            + params.length + " on: " + value);
        }
        if (StringUtils.isAnyBlank(params)) {
            throw new IllegalArgumentException("Empty parameters are not allowed on HIGHLIGHT rule: "
                    + value);
        }
    }
    
    private Filter parseFilter(String filterStr) {
        try {
            return XCQL.toFilter(filterStr);
        } catch (CQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
