package org.geoserver.wms;

import org.geotools.map.Layer;

public class WMSMarkFactoriesGetMapCallback implements GetMapCallback {

    @Override
    public GetMapRequest initRequest(GetMapRequest request) {
        return request;
    }

    @Override
    public void initMapContent(WMSMapContent mapContent) {
    }

    @Override
    public Layer beforeLayer(WMSMapContent mapContent, Layer layer) {
        return layer;
    }

    @Override
    public WMSMapContent beforeRender(WMSMapContent mapContent) {
        return mapContent;
    }

    @Override
    public WebMap finished(WebMap map) {
        return map;
    }

    @Override
    public void failed(Throwable t) {
    }

}
