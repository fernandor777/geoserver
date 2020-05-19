/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.geoserver.ows.KvpParser;
import org.geoserver.util.XCQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;

/**
 * GeoServer KVP parser for labeling attributes.
 * Parses the RENDERLABEL vendor parameter into a list of {@link AttributeLabelParameter} instances. <br>
 * Example:
 * <pre>
 * RENDERLABEL=st:stations;my_id IN (1,2,4)|st:other;my_id IN (7,8,20)
 * </pre>
 */
public class AttributesGlobeKvpParser extends KvpParser {

    private static final Logger LOG = Logging.getLogger(AttributesGlobeKvpParser.class);
    
    public static final String RENDERLABEL = "RENDERLABEL";
    static final String VALUE_SEPARATOR = ";";
    static final String RULE_SEPARATOR = "|";
    
    public AttributesGlobeKvpParser() {
        super(RENDERLABEL, List.class);
    }

    @Override
    public Object parse(String value) throws Exception {
        LOG.fine(() -> "Starting parsing " + RENDERLABEL + " vendor parameters for content: " + value);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(RENDERLABEL + " parameter value is empty or null");
        }
        // convert the rules string array to a List<AttributeLabelParameter>
        String[] rulesString = splitRulesString(value);
        return Arrays.stream(rulesString)
                .map(ruleStr -> parseRule(ruleStr))
                .collect(Collectors.toList());
    }
    
    /**
     * Parses a render label rule to a {@link AttributeLabelParameter} instance.
     * <p>
     * A rule has the form:
     * <pre>
     * st:stations;my_id IN (1,2,4)
     * </pre>
     *  </p>
     * @param ruleStr the rule string definition
     * @return the resulting {@link AttributeLabelParameter} instance
     */
    protected AttributeLabelParameter parseRule(String ruleStr) {
        String[] valuesArray = ruleStr.split(Pattern.quote(VALUE_SEPARATOR));
        if (valuesArray.length < 2 || 
                StringUtils.isAnyBlank(valuesArray)) {
            throw new IllegalArgumentException("RENDERLABEL rule requires at least 2 parameters. Found: "
                    + ruleStr);
        }
        String layerName = valuesArray[0];
        Filter filter = parseFilter(valuesArray[1]);
        return new AttributeLabelParameter(layerName, filter);
    }
    
    private Filter parseFilter(String filterStr) {
        try {
            return XCQL.toFilter(filterStr);
        } catch (CQLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private String[] splitRulesString(String value) {
        String[] rulesString = value.split(Pattern.quote(RULE_SEPARATOR));
        rulesString = Arrays.stream(rulesString)
                .filter(rule -> StringUtils.isNotBlank(rule))
                .map(rule -> rule.trim())
                .toArray(String[]::new);
        return rulesString;
    }
    
}
