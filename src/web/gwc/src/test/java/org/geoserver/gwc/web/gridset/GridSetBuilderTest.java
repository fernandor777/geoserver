/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.web.gridset;

import static org.junit.Assert.assertTrue;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.junit.Test;

public class GridSetBuilderTest {

    /** Checks yCoordinateFirst value detection based on EPSG:4326 GridSet */
    @Test
    public void testYCoordinateFirstEPSG4326() {
        GridSet epsg4326 =
                GridSetFactory.createGridSet(
                        "GlobalCRS84Geometric",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        false,
                        GridSetFactory.DEFAULT_LEVELS,
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        256,
                        256,
                        false);
        epsg4326.setDescription(
                "A default WGS84 tile matrix set where the first zoom level "
                        + "covers the world with two tiles on the horizonal axis and one tile "
                        + "over the vertical axis and each subsequent zoom level is calculated by half "
                        + "the resolution of its previous one.");
        GridSetInfo info = new GridSetInfo(epsg4326, false);
        GridSet finalGridSet = GridSetBuilder.build(info);
        assertTrue(finalGridSet.isyCoordinateFirst());
    }
}
