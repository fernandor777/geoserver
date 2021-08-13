package org.geoserver.wms;

import java.awt.Graphics2D;
import java.awt.Shape;

import org.geotools.renderer.style.MarkFactory;
import org.opengis.feature.Feature;
import org.opengis.filter.expression.Expression;

/**
 * Mock mark factory for testing purposes, always return null. 
 */
public class MockMarkFactory implements MarkFactory {

    @Override
    public Shape getShape(Graphics2D graphics, Expression symbolUrl, Feature feature) throws Exception {
        return null;
    }
}
