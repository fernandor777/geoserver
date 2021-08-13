package org.geoserver.wms.wms_1_1_1;

import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.geoserver.wms.WMSTestMarkFactoriesPostProcessor;
import org.geoserver.wms.WMSTestSupport;
import org.geotools.renderer.style.DynamicSymbolFactoryFinder;
import org.geotools.renderer.style.MarkFactory;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

public class WMSMarkFactoriesProcessorTest extends WMSTestSupport {

    final String bbox = "-180,-90,180,90";
    final String styles = "point";
    final String layers = "cgf:Points";
    
    @Test
    public void testDefaultFactories() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse(
                        "wms?bbox="
                                + bbox
                                + "&styles=&layers="
                                + layers
                                + "&Format=image/png"
                                + "&request=GetMap"
                                + "&width=550"
                                + "&height=250"
                                + "&srs=EPSG:4326");
        checkImage(response);
        List<MarkFactory> factories = WMSTestMarkFactoriesPostProcessor.getLastFetchedFactories();
        List<MarkFactory> unfilteredFactories = getUnfilteredMarkFactories();
        
        assertTrue(CollectionUtils.isEqualCollection(factories, unfilteredFactories));
    }

    protected List<MarkFactory> getUnfilteredMarkFactories() {
        return IteratorUtils.toList(DynamicSymbolFactoryFinder.getMarkFactories());
    }
    
}
