/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.labeling;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.geotools.image.test.ImageAssert;
import org.junit.Test;

public class GlobeRenderTests {

    @Test
    public void testGlobeRendering() throws Exception {
        GlobeBounds bounds = new GlobeBounds(280d, 280d, 20);
        
        BufferedImage img = new BufferedImage(
                320, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2d = img.createGraphics();
        
        GlobeRender.renderGlobe(graphics2d, bounds);
        saveToPngFile(img, "/home/fernando/Documents/EMSA/globe-test.png");
        // load expected image
        InputStream inputStream = this.getClass().getResourceAsStream("globe-test.png");
        BufferedImage expectedImage = ImageIO.read(inputStream);
        // check images
        //ImageAssert.assertEquals(expectedImage, img, 10);
    }
    
    void saveToPngFile(RenderedImage image, String path) {
        try {
            File outputfile = new File(path);
            ImageIO.write(image, "png", outputfile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
