package org.geoserver.config.util;

import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;

/** Util for INSPIRE extended layer names with colon(:) char */
public class ExtendedLayerNamesUtils {

    public static final String COLON = "~$c~";
    private static final String COLON_PATTERN = "~\\$c~";

    public static String escape(String name) {
        return name.replaceAll(":", COLON_PATTERN);
    }

    public static String unescape(String name) {
        return name.replaceAll(COLON_PATTERN, ":");
    }

    public static Boolean isEnabled() {
        GeoServer geoserver = (GeoServer) GeoServerExtensions.bean("geoServer");
        if (geoserver == null) return false;
        return geoserver.getGlobal().isExtendedCharsOnLayerNamesEnabled();
    }
}
