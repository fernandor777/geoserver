package org.geoserver.smartdataloader.data.store.virtualfk;

import org.geoserver.smartdataloader.domain.entities.DomainRelationType;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.VirtualRelationMetadata;

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

    public List<RelationMetadata> toRelationMetadataList() {
        return null; // Placeholder
    }

    private RelationMetadata convertToRelationMetadata(Relationship relationship) {
        String sourceTableName = relationship.getSource().getEntity();
        String targetTableName = relationship.getTarget().getEntity();
        String type = relationship.getCardinality();

        return new VirtualRelationMetadata(DomainRelationType.ONEMANY,); // Placeholder
    }
}



