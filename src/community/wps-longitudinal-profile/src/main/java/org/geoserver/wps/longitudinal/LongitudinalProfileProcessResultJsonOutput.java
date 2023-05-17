package org.geoserver.wps.longitudinal;

import org.geoserver.wps.ppio.CDataPPIO;

import java.io.InputStream;
import java.io.OutputStream;

public class LongitudinalProfileProcessResultJsonOutput extends CDataPPIO {

    public LongitudinalProfileProcessResultJsonOutput(Class<?> externalType, Class<?> internalType, String mimeType) {
        super(externalType, internalType, mimeType);
    }

    @Override
    public void encode(Object value, OutputStream os) throws Exception {
        // encode value (with LongitudinalProfileProcessResult type) into JSON output stream

    }

    @Override
    public Object decode(String input) throws Exception {
        throw new UnsupportedOperationException("JSON parsing is not supported");
    }

    @Override
    public Object decode(InputStream input) throws Exception {
        throw new UnsupportedOperationException("JSON parsing is not supported");
    }

}
