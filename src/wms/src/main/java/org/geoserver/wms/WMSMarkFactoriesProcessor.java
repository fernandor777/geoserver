package org.geoserver.wms;

import java.util.Iterator;

import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geotools.renderer.style.MarkFactoriesProcessor;
import org.geotools.renderer.style.MarkFactory;

/**
 * WMSInfo driven {@link MarkFactoriesProcessor} extension point implementation.
 * Retrieves the saved configuration from the global or workspace level {@link WMSInfo} metadata map and applies
 * the specified order and filtering if found.
 * If no configuration is found, it returns the same input iterator.
 */
public class WMSMarkFactoriesProcessor implements MarkFactoriesProcessor {

    @Override
    public Iterator<MarkFactory> processFactories(Iterator<MarkFactory> factories) {
        Request request = Dispatcher.REQUEST.get();
        
        
        return null;
    }

    @Override
    public int priority() {
        return 100;
    }

    private void getCurrentRequest() {
        Request request = Dispatcher.REQUEST.get();
        
    }
    
}
