/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.geoserver.smartdataloader.domain.entities.DomainAttributeType;
import org.geoserver.smartdataloader.domain.entities.DomainEntity;
import org.geoserver.smartdataloader.domain.entities.DomainEntitySimpleAttribute;
import org.geoserver.smartdataloader.domain.entities.DomainModel;
import org.geoserver.smartdataloader.domain.entities.DomainRelation;
import org.geoserver.smartdataloader.metadata.AttributeMetadata;
import org.geoserver.smartdataloader.metadata.DataStoreMetadata;
import org.geoserver.smartdataloader.metadata.EntityMetadata;
import org.geoserver.smartdataloader.metadata.RelationMetadata;
import org.geoserver.smartdataloader.metadata.jdbc.JdbcTableMetadata;
import org.geotools.util.logging.Logging;

/**
 * Smart AppSchema model builder. Given a DomainModelConfig object and a DataStoreMetadata it allows to get the Smart
 * AppSchema model.
 */
public final class DomainModelBuilder {

    private static final Logger LOGGER = Logging.getLogger(DomainModelBuilder.class);

    private final DataStoreMetadata dataStoreMetadata;
    private final DomainModelConfig domainModelConfig;

    private final Map<EntityMetadata, DomainEntity> domainEntitiesIndex = new HashMap<>();
    private final Set<EntityMetadata> visitedEntities = new HashSet<>();
    private final Set<EntityMetadata> initializedEntities = new HashSet<>();
    private final Set<String> duplicatedEntityNames;

    public DomainModelBuilder(DataStoreMetadata dataStoreMetadata, DomainModelConfig domainModelConfig) {
        this.dataStoreMetadata = dataStoreMetadata;
        this.domainModelConfig = domainModelConfig;
        this.duplicatedEntityNames = findDuplicatedEntityNames(dataStoreMetadata);
    }

    public DomainModel buildDomainModel() {
        EntityMetadata rootEntityMetadata = dataStoreMetadata.getEntityMetadata(domainModelConfig.getRootEntityName());
        if (rootEntityMetadata == null) {
            throw new RuntimeException(
                    "Root entity name '" + domainModelConfig.getRootEntityName() + "' does not exists!");
        }
        DomainEntity rootEntity = this.buildRootDomainEntity(rootEntityMetadata);
        DomainModel dm = new DomainModel(this.dataStoreMetadata, rootEntity);
        return dm;
    }

    private DomainEntity buildRootDomainEntity(EntityMetadata entityMetadata) {
        return buildDomainEntity(entityMetadata, null);
    }

    private DomainEntity indexEntity(EntityMetadata entityMetadata) {
        // let's try to retrieve the domain entity
        DomainEntity entity = domainEntitiesIndex.get(entityMetadata);
        if (entity == null) {
            // first time we are visiting this entity metadata so we need to build a domain entity
            String schema = null;
            if (entityMetadata instanceof JdbcTableMetadata) {
                schema = ((JdbcTableMetadata) entityMetadata).getSchema();
            }
            String gmlEntityName = resolveGmlEntityName(entityMetadata, schema);
            entity = new DomainEntity(
                    entityMetadata.getName(), domainModelConfig.getEntitiesPrefix(), schema, gmlEntityName);
            domainEntitiesIndex.put(entityMetadata, entity);
        } else {
            // we already have our entity
            return entity;
        }
        // we got our entity, it still an empty shell at this stage
        return entity;
    }

    private DomainEntity buildDomainEntity(EntityMetadata entityMetadata, DomainRelation fromRelation) {
        EntityMetadata resolvedMetadata = resolveEntityMetadata(entityMetadata);
        if (resolvedMetadata == null) {
            throw new RuntimeException("Could not find metadata for entity");
        }
        // retrieve the metadata for our entity
        // let's try to retrieve the domain entity or create it if needed
        DomainEntity entity = indexEntity(resolvedMetadata);
        if (initializedEntities.contains(resolvedMetadata) || visitedEntities.contains(resolvedMetadata)) {
            return entity;
        }
        visitedEntities.add(resolvedMetadata);
        try {
            // let's add the relations of our entity
            resolvedMetadata.getRelations().forEach(relation -> {
                if (fromRelation == null || !relationInvolvesEntity(relation, fromRelation.getContainingEntity())) {
                    if (targetsVisitedEntity(entity, relation)) {
                        if (LOGGER.isLoggable(java.util.logging.Level.FINER)) {
                            LOGGER.log(
                                    java.util.logging.Level.FINER,
                                    "Skipping recursive relation from {0} to an already visited entity.",
                                    entity.getName());
                        }
                        return;
                    }
                    DomainRelation domainRelation = buildDomainRelation(entity, relation, fromRelation);
                    entity.add(domainRelation);
                }
            });
            // let's add attributes of our entity, excluding all attributes that are foreign keys
            resolvedMetadata.getAttributes().forEach(attribute -> {
                // exclude external attributes references
                if (!attribute.isExternalReference()) {
                    DomainEntitySimpleAttribute domainAttribute = buildDomainEntitySimpleAttribute(attribute);
                    if (domainAttribute == null) {
                        // unsupported attribute type, we skip it
                        return;
                    }
                    entity.add(domainAttribute);
                }
            });
            initializedEntities.add(resolvedMetadata);
        } finally {
            visitedEntities.remove(resolvedMetadata);
        }
        return entity;
    }

    private DomainRelation buildDomainRelation(
            DomainEntity containingDomainEntity, RelationMetadata relationMetadata, DomainRelation fromDomainRelation) {
        // retrieve the source and targeted attributes of the relation
        AttributeMetadata sourceAttribute = relationMetadata.getSourceAttribute();
        AttributeMetadata destinationAttribute = relationMetadata.getDestinationAttribute();
        if (isSameEntity(destinationAttribute.getEntity(), containingDomainEntity)) {
            // the containing entity was actually the destination, we need to swap the attributes
            sourceAttribute = relationMetadata.getDestinationAttribute();
            destinationAttribute = relationMetadata.getSourceAttribute();
        }
        // let's build our domain relation for our containing entity
        DomainRelation domainRelation = new DomainRelation();
        // set the containing entity and attribute
        domainRelation.setContainingEntity(containingDomainEntity);
        domainRelation.setContainingKeyAttribute(buildRelationShipAttribute(sourceAttribute));
        // set the destination entity and attribute
        DomainEntity destinationDomainEntity = buildDomainEntity(destinationAttribute.getEntity(), domainRelation);
        domainRelation.setDestinationEntity(destinationDomainEntity);
        domainRelation.setDestinationKeyAttribute(buildRelationShipAttribute(destinationAttribute));
        return domainRelation;
    }

    private EntityMetadata resolveEntityMetadata(EntityMetadata entityMetadata) {
        if (entityMetadata == null) {
            return null;
        }
        if (!(entityMetadata instanceof JdbcTableMetadata)) {
            EntityMetadata candidate = dataStoreMetadata.getEntityMetadata(entityMetadata.getName());
            return candidate != null ? candidate : entityMetadata;
        }
        JdbcTableMetadata jdbcEntity = (JdbcTableMetadata) entityMetadata;
        for (EntityMetadata candidate : dataStoreMetadata.getDataStoreEntities()) {
            if (candidate instanceof JdbcTableMetadata) {
                JdbcTableMetadata jdbcCandidate = (JdbcTableMetadata) candidate;
                if (matchesTable(jdbcEntity, jdbcCandidate)) {
                    return jdbcCandidate;
                }
            } else if (candidate.getName().equals(jdbcEntity.getName())) {
                return candidate;
            }
        }
        return entityMetadata;
    }

    private boolean matchesTable(JdbcTableMetadata left, JdbcTableMetadata right) {
        if (left == null || right == null) {
            return false;
        }
        if (!left.getName().equals(right.getName())) {
            return false;
        }
        if (!Objects.equals(left.getSchema(), right.getSchema())) {
            return false;
        }
        return Objects.equals(left.getCatalog(), right.getCatalog());
    }

    private Set<String> findDuplicatedEntityNames(DataStoreMetadata metadata) {
        Set<String> duplicatedNames = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        if (metadata == null || metadata.getDataStoreEntities() == null) {
            return duplicatedNames;
        }
        for (EntityMetadata entity : metadata.getDataStoreEntities()) {
            if (entity == null || entity.getName() == null) {
                continue;
            }
            counts.merge(entity.getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 1) {
                duplicatedNames.add(entry.getKey());
            }
        }
        return duplicatedNames;
    }

    private String resolveGmlEntityName(EntityMetadata entityMetadata, String schema) {
        String name = entityMetadata != null ? entityMetadata.getName() : null;
        if (name == null) {
            return null;
        }
        if (schema == null || schema.isEmpty()) {
            return name;
        }
        if (!duplicatedEntityNames.contains(name)) {
            return name;
        }
        return schema + "_" + name;
    }

    private boolean isSameEntity(EntityMetadata metadataEntity, DomainEntity domainEntity) {
        if (metadataEntity == null || domainEntity == null) {
            return false;
        }
        if (!Objects.equals(metadataEntity.getName(), domainEntity.getName())) {
            return false;
        }
        if (metadataEntity instanceof JdbcTableMetadata) {
            String metadataSchema = ((JdbcTableMetadata) metadataEntity).getSchema();
            return Objects.equals(metadataSchema, domainEntity.getSchema());
        }
        return true;
    }

    private boolean relationInvolvesEntity(RelationMetadata relation, DomainEntity entity) {
        if (relation == null || entity == null) {
            return false;
        }
        if (isSameEntity(relation.getSourceAttribute().getEntity(), entity)) {
            return true;
        }
        return isSameEntity(relation.getDestinationAttribute().getEntity(), entity);
    }

    private boolean targetsVisitedEntity(DomainEntity containingEntity, RelationMetadata relation) {
        if (containingEntity == null || relation == null) {
            return false;
        }
        EntityMetadata sourceEntity = relation.getSourceAttribute() != null
                ? relation.getSourceAttribute().getEntity()
                : null;
        EntityMetadata destinationEntity = relation.getDestinationAttribute() != null
                ? relation.getDestinationAttribute().getEntity()
                : null;
        EntityMetadata targetEntity = null;
        if (isSameEntity(sourceEntity, containingEntity)) {
            targetEntity = destinationEntity;
        } else if (isSameEntity(destinationEntity, containingEntity)) {
            targetEntity = sourceEntity;
        }
        if (targetEntity == null) {
            return false;
        }
        EntityMetadata resolvedTarget = resolveEntityMetadata(targetEntity);
        return resolvedTarget != null && visitedEntities.contains(resolvedTarget);
    }

    /**
     * Builds a domain entity simple attribute from the given attribute metadata.
     *
     * @param attributeMetadata the attribute metadata
     * @return the domain entity simple attribute, or null if the attribute type is unsupported
     */
    private DomainEntitySimpleAttribute buildDomainEntitySimpleAttribute(AttributeMetadata attributeMetadata) {
        DomainEntitySimpleAttribute domainAttribute = new DomainEntitySimpleAttribute();
        domainAttribute.setName(attributeMetadata.getName());
        String attribType = normalizeTypeName(attributeMetadata.getType());
        domainAttribute.setIdentifier(attributeMetadata.isIdentifier());
        DomainAttributeType domainAttributeType = getDomainAttributeType(attribType);
        if (domainAttributeType == null) {
            LOGGER.warning(() -> String.format(
                    "Attribute type '%s' is unsupported for attribute '%s'.",
                    attributeMetadata.getType().toLowerCase(), attributeMetadata.getName()));
            return null;
        } else {
            domainAttribute.setType(domainAttributeType);
        }

        return domainAttribute;
    }

    /**
     * Normalizes vendor-specific SQL type labels to a stable base type token consumed by
     * {@link #getDomainAttributeType(String)}.
     */
    private String normalizeTypeName(String rawType) {
        if (rawType == null) {
            return null;
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < normalized.length() - 1) {
            normalized = normalized.substring(dotIndex + 1);
        }
        normalized = normalized.replace("\"", "");
        int paramsStart = normalized.indexOf('(');
        if (paramsStart >= 0) {
            normalized = normalized.substring(0, paramsStart).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ").trim();
        switch (normalized) {
            case "character varying":
                return "varchar";
            case "character":
                return "bpchar";
            case "double precision":
                return "float8";
            case "real":
                return "float4";
            case "timestamp with time zone":
                return "timestamptz";
            case "timestamp without time zone":
                return "timestamp";
            case "time with time zone":
            case "time without time zone":
                return "time";
            default:
                return normalized;
        }
    }

    /**
     * Builds a domain entity simple attribute from the given attribute metadata, which is a relation attribute.
     *
     * @param attributeMetadata the attribute metadata
     * @return the domain entity simple attribute
     */
    private DomainEntitySimpleAttribute buildRelationShipAttribute(AttributeMetadata attributeMetadata) {
        DomainEntitySimpleAttribute domainAttribute = buildDomainEntitySimpleAttribute(attributeMetadata);
        if (domainAttribute == null) {
            throw new RuntimeException("Unsupported attribute type '"
                    + attributeMetadata.getType().toLowerCase()
                    + "' for attribute '"
                    + attributeMetadata.getName()
                    + "'");
        }
        return domainAttribute;
    }

    /**
     * Returns the domain attribute type for the given type string.
     *
     * @param type the type string
     * @return the corresponding DomainAttributeType, or null if not found
     */
    private DomainAttributeType getDomainAttributeType(String type) {
        if (type == null) {
            return null;
        }
        switch (type.toLowerCase()) {
            case "number":
            case "numeric":
            case "float8":
            case "float4":
            case "decimal":
                return DomainAttributeType.NUMBER;
            case "serial":
            case "smallint":
            case "int2":
            case "int4":
            case "integer":
                return DomainAttributeType.INT;
            case "bigint":
            case "int8":
            case "bigserial":
                return DomainAttributeType.INTEGER;
            case "text":
            case "varchar":
            case "uuid":
            case "bpchar":
                return DomainAttributeType.TEXT;
            case "time":
            case "date":
            case "timestamptz":
            case "interval":
            case "timestamp":
                return DomainAttributeType.DATE;
            case "geometry":
            case "geography":
                return DomainAttributeType.GEOMETRY;
            case "bool":
            case "boolean":
                return DomainAttributeType.BOOLEAN;
            default:
                return null;
        }
    }
}
