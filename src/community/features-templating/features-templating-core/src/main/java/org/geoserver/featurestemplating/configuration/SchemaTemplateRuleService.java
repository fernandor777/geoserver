package org.geoserver.featurestemplating.configuration;

import org.geoserver.catalog.FeatureTypeInfo;

/**
 * Class that provides methods to add, update or delete Feature Template Rules. This class is used to manage the schema
 * templates for a specific feature type.
 *
 * <p>It uses a properties file to store the schema templates data.
 */
public class SchemaTemplateRuleService extends AbstractTemplateRuleService {

    public static final String METADATA_KEY = "SCHEMA_TEMPLATING_LAYER_CONF";

    public SchemaTemplateRuleService(FeatureTypeInfo featureTypeInfo) {
        super(featureTypeInfo, METADATA_KEY);
    }
}
