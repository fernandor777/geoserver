package org.geoserver.wms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.IteratorUtils;
import org.geotools.renderer.style.MarkFactoriesProcessor;
import org.geotools.renderer.style.MarkFactory;

/**
 * Fetch and saves the latest Mark Factory iterator items in a static cache variable, so tests can check the current filtered and ordered Mark factories from extensions. 
 */
public class WMSTestMarkFactoriesPostProcessor implements MarkFactoriesProcessor {

    private static volatile List<MarkFactory> lastFetchedFactories = Collections.emptyList();
    
    @Override
    public Iterator<MarkFactory> processFactories(Iterator<MarkFactory> factories) {
        List<MarkFactory> list = IteratorUtils.toList(factories);
        updateLast(list);
        return list.iterator();
    }
    
    public static synchronized void updateLast(List<MarkFactory> list) {
        List<MarkFactory> factories = new ArrayList<>(list);
        lastFetchedFactories = Collections.unmodifiableList(factories);
    }
    
    public static List<MarkFactory> getLastFetchedFactories() {
        return lastFetchedFactories;
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

}
