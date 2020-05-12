/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms;

import java.util.Collection;
import org.geotools.renderer.SymbolizersPreProcessor;

/**
 * Provides the collection of {@link SymbolizersPreProcessor} available for the execution context.
 *
 * @author Fernando Mino - Geosolutions
 */
public interface SymbolizersPreProcessorsProvider {

    /**
     * Returns the collection of {@link SymbolizersPreProcessor} available for the execution
     * context.
     */
    Collection<SymbolizersPreProcessor> getSymbolizerPreProcessors();
}
