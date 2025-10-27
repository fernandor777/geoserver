package org.geoserver.smartdataloader.data.store.virtualfk;

import java.util.ArrayList;
import java.util.List;

public class Relationships {
    private List<Relationship> relationships = new ArrayList<>();

    public Relationships() {}

    public List<Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships;
    }

    public void addRelationship(Relationship relationship) {
        this.relationships.add(relationship);
    }
}
