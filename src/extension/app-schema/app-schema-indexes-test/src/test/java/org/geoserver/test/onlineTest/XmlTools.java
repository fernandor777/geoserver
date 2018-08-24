package org.geoserver.test.onlineTest;

import org.springframework.util.xml.SimpleNamespaceContext;

public final class XmlTools {

    public static SimpleNamespaceContext getStationsNSContext() {
        SimpleNamespaceContext nsCtx = new SimpleNamespaceContext();
        nsCtx.bindNamespaceUri("wfs", "http://www.opengis.net/wfs/2.0");
        nsCtx.bindNamespaceUri("gml", "http://www.opengis.net/gml/3.2");
        nsCtx.bindNamespaceUri("st", "http://www.stations.org/1.0");
        return nsCtx;
    }
}
