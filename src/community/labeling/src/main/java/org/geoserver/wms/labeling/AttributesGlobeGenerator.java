/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.Map.Entry;

public class AttributesGlobeGenerator {

    private AttributeGlobeFonts fonts;
    private AttributesGlobeConfiguration configuration;
    private Graphics2D graphics2d;
    
    /**
     * 
     * @param attributes map of attributes, key=title value=value
     */
    public void generate(Map<String, String> attributes) {
        AttributesGlobeBounds attributesGlobeBounds = buildAttributesBounds(attributes);
        
        
    }
    
    AttributesGlobeBounds buildAttributesBounds(Map<String, String> attributes) {
        TextBounds textBounds = calculateTextMaxBounds(attributes);
        GlobeBounds globeBounds = calculateGlobeBounds(textBounds);
        
        return new AttributesGlobeBounds(globeBounds, textBounds, configuration);
    }
    
    GlobeBounds calculateGlobeBounds(TextBounds textBounds) {
        double width = textBounds.getWidth() + configuration.getMargin();
        double height = textBounds.getHeight() + configuration.getMargin();
        
        return new GlobeBounds(width, height, configuration.getRoundCornerRadius());
    }
    
    /**
     * Calculates the max bounds will be used by the text.
     * 
     * @param attributes the attributes titles and values Map
     * @return the total computed bounds
     */
    TextBounds calculateTextMaxBounds(Map<String, String> attributes) {
        double maxTitleWidth = 0d;
        double maxValueWidth = 0d;
        double totalHeight = 0d;
        for (Entry<String, String> entry : attributes.entrySet()) {
            TextLineBounds textBounds = calculateTextLineBounds(entry);
            maxTitleWidth = Math.max(maxTitleWidth, textBounds.getTitleBounds().getWidth());
            maxValueWidth = Math.max(maxValueWidth, textBounds.getValueBounds().getWidth());
            totalHeight += configuration.getInterLineSpace() + 
                    Math.max(textBounds.getTitleBounds().getHeight(), 
                            textBounds.getValueBounds().getHeight());
        }
        return new TextBounds(maxTitleWidth, maxValueWidth, totalHeight);
    }
    
    TextLineBounds calculateTextLineBounds(Entry<String, String> entry) {
        Rectangle2D titleBounds = fonts.getTitleFont().getStringBounds(entry.getKey() + ": ", 
                graphics2d.getFontRenderContext());
        Rectangle2D valueBounds = fonts.getTitleFont().getStringBounds(entry.getValue(), 
                graphics2d.getFontRenderContext());
        return new TextLineBounds(titleBounds, valueBounds);
    }
    
    static class TextLineBounds {
        private final Rectangle2D titleBounds;
        private final Rectangle2D valueBounds;
        
        public TextLineBounds(Rectangle2D titleBounds, Rectangle2D valueBounds) {
            super();
            this.titleBounds = titleBounds;
            this.valueBounds = valueBounds;
        }

        public Rectangle2D getTitleBounds() {
            return titleBounds;
        }

        public Rectangle2D getValueBounds() {
            return valueBounds;
        }
    }
}
