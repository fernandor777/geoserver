package org.geoserver.featurestemplating.configuration;

import org.geoserver.config.GeoServerDataDirectory;

/**
 * This class is responsible for loading and saving the schema templates data. It extends the AbstractTemplateInfoDAO
 * class, which provides the basic functionality for handling template information.
 *
 * <p>It uses a properties file to store the schema templates data.
 */
public class SchemaTemplateInfoDAOImpl extends AbstractTemplateInfoDAO {

    public static final String PROPERTY_FILE_NAME = "schemas-templates-data.properties";

    public SchemaTemplateInfoDAOImpl(GeoServerDataDirectory dd) {
        super(dd, PROPERTY_FILE_NAME);
    }
}
