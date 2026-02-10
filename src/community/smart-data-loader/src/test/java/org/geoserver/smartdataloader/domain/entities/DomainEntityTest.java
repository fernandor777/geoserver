package org.geoserver.smartdataloader.domain.entities;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DomainEntityTest {

    @Test
    public void testAddAttributeSkipsDuplicateNames() {
        DomainEntity entity = new DomainEntity("entity_a", "");
        DomainEntitySimpleAttribute id1 = new DomainEntitySimpleAttribute();
        id1.setName("id");
        id1.setType(DomainAttributeType.INT);

        DomainEntitySimpleAttribute id2 = new DomainEntitySimpleAttribute();
        id2.setName("id");
        id2.setType(DomainAttributeType.INT);

        entity.add(id1);
        entity.add(id2);

        assertEquals(1, entity.getAttributes().size());
    }

    @Test
    public void testAddRelationSkipsEquivalentRelation() {
        DomainEntity containing = new DomainEntity("entity_a", "");
        DomainEntity destination = new DomainEntity("entity_b", "");

        DomainEntitySimpleAttribute source = new DomainEntitySimpleAttribute();
        source.setName("id_a");
        source.setType(DomainAttributeType.INT);

        DomainEntitySimpleAttribute target = new DomainEntitySimpleAttribute();
        target.setName("id_b");
        target.setType(DomainAttributeType.INT);

        DomainRelation relation1 = new DomainRelation();
        relation1.setContainingEntity(containing);
        relation1.setDestinationEntity(destination);
        relation1.setContainingKeyAttribute(source);
        relation1.setDestinationKeyAttribute(target);

        DomainRelation relation2 = new DomainRelation();
        relation2.setContainingEntity(containing);
        relation2.setDestinationEntity(destination);
        relation2.setContainingKeyAttribute(source);
        relation2.setDestinationKeyAttribute(target);

        containing.add(relation1);
        containing.add(relation2);

        assertEquals(1, containing.getRelations().size());
    }
}
