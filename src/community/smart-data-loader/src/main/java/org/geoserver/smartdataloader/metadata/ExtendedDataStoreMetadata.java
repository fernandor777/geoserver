package org.geoserver.smartdataloader.metadata;

import org.geoserver.smartdataloader.metadata.jdbc.JdbcDataStoreMetadata;
import org.geoserver.smartdataloader.data.store.virtualfk.Relationships;
import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedDataStoreMetadata wraps a JdbcDataStoreMetadata and injects additional RelationMetadata
 * instances based on the provided Relationships object.
 */
public class ExtendedDataStoreMetadata implements DataStoreMetadata {
    private final JdbcDataStoreMetadata delegate;
    private final Relationships relationships;
    private final List<RelationMetadata> injectedRelations;

    public ExtendedDataStoreMetadata(JdbcDataStoreMetadata delegate, Relationships relationships) {
        this.delegate = delegate;
        this.relationships = relationships;
        this.injectedRelations = new ArrayList<>();
        if (relationships != null) {
            // Assuming Relationships provides a method to convert to RelationMetadata list
            this.injectedRelations.addAll(relationships.toRelationMetadataList());
        }
    }

    @Override
    public List<EntityMetadata> getDataStoreEntities() {
        return delegate.getDataStoreEntities();
    }

    @Override
    public List<RelationMetadata> getEntityMetadataRelations(EntityMetadata entity) {
        List<RelationMetadata> result = new ArrayList<>();
        result.addAll(delegate.getEntityMetadataRelations(entity));
        for (RelationMetadata rel : injectedRelations) {
            if (rel.participatesInRelation(entity.getName())) {
                result.add(rel);
            }
        }
        return result;
    }

    @Override
    public List<RelationMetadata> getDataStoreRelations() {
        List<RelationMetadata> result = new ArrayList<>();
        result.addAll(delegate.getDataStoreRelations());
        result.addAll(injectedRelations);
        return result;
    }

    @Override
    public DataStoreMetadataConfig getDataStoreMetadataConfig() {
        return delegate.getDataStoreMetadataConfig();
    }

    @Override
    public void setDataStoreMetadataConfig(DataStoreMetadataConfig config) {
        delegate.setDataStoreMetadataConfig(config);
    }
}

