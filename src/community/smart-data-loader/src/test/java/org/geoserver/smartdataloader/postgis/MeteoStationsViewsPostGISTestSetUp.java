package org.geoserver.smartdataloader.postgis;

/** PostGIS test setup that loads the dataset including database views. */
public class MeteoStationsViewsPostGISTestSetUp extends MeteoStationsPostGISTestSetUp {

    public MeteoStationsViewsPostGISTestSetUp() {
        super("meteo_db_views.sql");
    }
}
