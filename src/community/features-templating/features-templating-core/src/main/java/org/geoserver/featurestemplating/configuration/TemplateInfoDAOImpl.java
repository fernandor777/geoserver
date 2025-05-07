/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.configuration;

import org.geoserver.config.GeoServerDataDirectory;

/** A template info DAO that use a property file for persistence. */
public class TemplateInfoDAOImpl extends AbstractTemplateInfoDAO {

    private static final String PROPERTY_FILE_NAME = "features-templates-data.properties";

    public TemplateInfoDAOImpl(GeoServerDataDirectory dd) {
        super(dd, PROPERTY_FILE_NAME);
    }
}
