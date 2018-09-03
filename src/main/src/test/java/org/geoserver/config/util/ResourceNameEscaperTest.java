package org.geoserver.config.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResourceNameEscaperTest {

    @Test
    public void testEscapeColon() {
        ExtendedLayerNamesUtils escaper = new ExtendedLayerNamesUtils();
        String result = escaper.escape("layer:name");
        assertEquals("layer" + ExtendedLayerNamesUtils.COLON + "name", result);
    }

    @Test
    public void testUnescapeColon() {
        ExtendedLayerNamesUtils escaper = new ExtendedLayerNamesUtils();
        String result = escaper.unescape("layer~$c~name");
        assertEquals("layer:name", result);
    }
}
