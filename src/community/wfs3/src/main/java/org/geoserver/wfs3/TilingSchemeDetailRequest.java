package org.geoserver.wfs3;

import org.geowebcache.grid.GridSet;

public class TilingSchemeDetailRequest extends BaseRequest {

    private GridSet gridSet;

    public GridSet getGridSet() {
        return gridSet;
    }

    public void setGridSet(GridSet gridSet) {
        this.gridSet = gridSet;
    }
}
