/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.domain.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.geoserver.smartdataloader.domain.DomainModelVisitor;

/** Entity of the domain, an entity contains attributes and relations. */
public final class DomainEntity {

    private final List<DomainEntitySimpleAttribute> attributes = new ArrayList<>();
    private final List<DomainRelation> relations = new ArrayList<>();

    private final String name;
    private final String schema;
    private final GmlInfo gmlInfo;

    public DomainEntity(String name, String entityPrefix) {
        this(name, entityPrefix, null, name);
    }

    public DomainEntity(String name, String entityPrefix, String schema) {
        this(name, entityPrefix, schema, name);
    }

    public DomainEntity(String name, String entityPrefix, String schema, String gmlBaseName) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Entity name cannot be null or empty");
        }
        if (StringUtils.isBlank(entityPrefix)) {
            entityPrefix = "";
        }
        if (StringUtils.isBlank(gmlBaseName)) {
            gmlBaseName = name;
        }
        this.name = name;
        this.schema = StringUtils.isBlank(schema) ? null : schema;
        this.gmlInfo = new GmlInfo(entityPrefix + gmlBaseName);
    }

    public String getName() {
        return this.name;
    }

    public String getSchema() {
        return schema;
    }

    public GmlInfo getGmlInfo() {
        return gmlInfo;
    }

    public List<DomainEntitySimpleAttribute> getAttributes() {
        return attributes;
    }

    public List<DomainRelation> getRelations() {
        return relations;
    }

    public void add(DomainEntitySimpleAttribute attribute) {
        if (attribute == null) {
            return;
        }
        boolean alreadyPresent =
                attributes.stream().anyMatch(existing -> Objects.equals(existing.getName(), attribute.getName()));
        if (!alreadyPresent) {
            attributes.add(attribute);
        }
    }

    public void add(DomainRelation relation) {
        if (relation == null) {
            return;
        }
        boolean alreadyPresent = relations.stream().anyMatch(existing -> isSameRelation(existing, relation));
        if (!alreadyPresent) {
            relations.add(relation);
        }
    }

    private static boolean isSameRelation(DomainRelation left, DomainRelation right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        String leftContaining =
                left.getContainingEntity() != null ? left.getContainingEntity().getName() : null;
        String rightContaining = right.getContainingEntity() != null
                ? right.getContainingEntity().getName()
                : null;
        String leftContainingSchema =
                left.getContainingEntity() != null ? left.getContainingEntity().getSchema() : null;
        String rightContainingSchema = right.getContainingEntity() != null
                ? right.getContainingEntity().getSchema()
                : null;
        String leftDestination = left.getDestinationEntity() != null
                ? left.getDestinationEntity().getName()
                : null;
        String rightDestination = right.getDestinationEntity() != null
                ? right.getDestinationEntity().getName()
                : null;
        String leftDestinationSchema = left.getDestinationEntity() != null
                ? left.getDestinationEntity().getSchema()
                : null;
        String rightDestinationSchema = right.getDestinationEntity() != null
                ? right.getDestinationEntity().getSchema()
                : null;
        String leftContainingKey = left.getContainingKeyAttribute() != null
                ? left.getContainingKeyAttribute().getName()
                : null;
        String rightContainingKey = right.getContainingKeyAttribute() != null
                ? right.getContainingKeyAttribute().getName()
                : null;
        String leftDestinationKey = left.getDestinationKeyAttribute() != null
                ? left.getDestinationKeyAttribute().getName()
                : null;
        String rightDestinationKey = right.getDestinationKeyAttribute() != null
                ? right.getDestinationKeyAttribute().getName()
                : null;
        return Objects.equals(leftContaining, rightContaining)
                && Objects.equals(leftContainingSchema, rightContainingSchema)
                && Objects.equals(leftDestination, rightDestination)
                && Objects.equals(leftDestinationSchema, rightDestinationSchema)
                && Objects.equals(leftContainingKey, rightContainingKey)
                && Objects.equals(leftDestinationKey, rightDestinationKey);
    }

    public void accept(DomainModelVisitor visitor, boolean isRoot) {
        if (isRoot) visitor.visitDomainRootEntity(this);
        else visitor.visitDomainChainedEntity(this);
        this.getAttributes().forEach(attrib -> attrib.accept(visitor));
        this.getRelations().forEach(relation -> relation.accept(visitor));
    }

    /** Contain GML naming info related with a domain entity. */
    public static final class GmlInfo {

        private final String entityName;

        private GmlInfo(String name) {
            entityName = getEntityName(name);
        }

        /** Utility method that will convert the name of an entity to a readable word. */
        private static String getEntityName(String name) {
            name = WordUtils.capitalizeFully(name, '_', ' ', '.');
            name = name.replace("_", "");
            name = name.replace(" ", "");
            return name.replace(".", "");
        }

        public String featureTypeName() {
            return entityName + "Feature";
        }

        public String complexTypeName() {
            return entityName + "Type";
        }

        public String complexPropertyTypeName() {
            return entityName + "PropertyType";
        }

        public String complexTypeAttributeName() {
            return StringUtils.uncapitalize(entityName);
        }
    }
}
