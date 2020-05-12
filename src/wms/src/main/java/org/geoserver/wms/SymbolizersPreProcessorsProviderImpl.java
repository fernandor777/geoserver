/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms;

import java.util.Collection;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.renderer.SymbolizersPreProcessor;

/**
 * Provides the SymbolizersPreProcessor collection available on Spring context.
 *
 * @author Fernando Mino - Geosolutions
 */
public class SymbolizersPreProcessorsProviderImpl implements SymbolizersPreProcessorsProvider {

    private Collection<SymbolizersPreProcessor> preProcessors;

    public SymbolizersPreProcessorsProviderImpl() {}

    @Override
    public Collection<SymbolizersPreProcessor> getSymbolizerPreProcessors() {
        if (preProcessors != null) {
            return preProcessors;
        } else {
            return buildSymbolizerPreProcessors();
        }
    }

    /** Initialize the {@link SymbolizersPreProcessor} collection. */
    private synchronized Collection<SymbolizersPreProcessor> buildSymbolizerPreProcessors() {
        if (preProcessors == null) {
            preProcessors = GeoServerExtensions.extensions(SymbolizersPreProcessor.class);
        }
        return preProcessors;
    }

    /**
     * Returns the default {@link SymbolizersPreProcessorsProvider} instance for the current Spring
     * context.
     */
    public static SymbolizersPreProcessorsProvider getInstance() {
        return GeoServerExtensions.bean(SymbolizersPreProcessorsProvider.class);
    }
}
