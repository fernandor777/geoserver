package org.geoserver.smartdataloader.data.store.virtualfk;

public class Key {
    private String column;

    public Key(String column) {
        this.column = column;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }
}
