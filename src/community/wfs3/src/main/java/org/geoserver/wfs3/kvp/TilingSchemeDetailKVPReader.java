package org.geoserver.wfs3.kvp;

import java.util.Map;
import org.geoserver.wfs3.TilingSchemeDetailRequest;
import org.geowebcache.grid.GridSet;

public class TilingSchemeDetailKVPReader extends BaseKvpRequestReader {

    public TilingSchemeDetailKVPReader() {
        super(TilingSchemeDetailRequest.class);
    }

    @Override
    public Object read(Object request, Map kvp, Map rawKvp) throws Exception {
        TilingSchemeDetailRequest req =
                (TilingSchemeDetailRequest) super.read(request, kvp, rawKvp);
        if (kvp.containsKey("tilingScheme")) {
            req.setGridSet((GridSet) kvp.get("tilingScheme"));
        }
        return req;
    }
}
