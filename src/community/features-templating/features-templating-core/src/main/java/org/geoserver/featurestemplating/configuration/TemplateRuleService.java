/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.configuration;

import org.geoserver.catalog.FeatureTypeInfo;

/** Class that provides methods to add, update or delete Feature Template Rules */
public class TemplateRuleService extends AbstractTemplateRuleService {

    public TemplateRuleService(FeatureTypeInfo featureTypeInfo) {
        super(featureTypeInfo, TemplateLayerConfig.METADATA_KEY);
    }
}
