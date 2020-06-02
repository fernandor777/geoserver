/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.catalog.LayerInfo;
import org.geoserver.wms.WMSTestSupport;
import org.geotools.util.logging.Logging;
import org.junit.Test;

public class LabelingWmsTests extends WMSTestSupport {
    
    private static final Logger LOG = Logging.getLogger(LabelingWmsTests.class);
    
    @Test
    public void testGetMap() throws Exception {
        List<LayerInfo> layers = this.getCatalog().getLayers();
        LOG.log(Level.INFO, "layers: {0}", layers);
    }

}
