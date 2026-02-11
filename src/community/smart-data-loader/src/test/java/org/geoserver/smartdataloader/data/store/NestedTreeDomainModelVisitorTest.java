/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.data.store;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.geoserver.smartdataloader.domain.entities.DomainAttributeType;
import org.geoserver.smartdataloader.domain.entities.DomainEntity;
import org.geoserver.smartdataloader.domain.entities.DomainEntitySimpleAttribute;
import org.geoserver.smartdataloader.domain.entities.DomainModel;
import org.geoserver.smartdataloader.domain.entities.DomainRelation;
import org.junit.Test;

/** Tests for {@link NestedTreeDomainModelVisitor}. */
public class NestedTreeDomainModelVisitorTest {

    @Test
    public void testKeepsRelationVisibleWhenDestinationAlreadyVisited() {
        DomainEntity pozzi = new DomainEntity("pozzi", "");
        DomainEntity unita = new DomainEntity("unita", "");
        DomainEntity assorb = new DomainEntity("assorb", "");
        assorb.add(attribute("key"));

        pozzi.add(relation(pozzi, unita, "unita_id", "id"));
        pozzi.add(relation(pozzi, assorb, "key", "key"));
        unita.add(relation(unita, assorb, "unita_id", "unita_id"));

        DomainModel model = new DomainModel(null, pozzi);
        NestedTreeDomainModelVisitor visitor = new NestedTreeDomainModelVisitor();
        model.accept(visitor);

        DefaultTreeModel treeModel = visitor.getTreeModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        assertNotNull(findChild(root, "unita"));
        DefaultMutableTreeNode assorbNode = findChild(root, "assorb");
        assertNotNull(assorbNode);
        assertTrue(assorbNode.getUserObject() instanceof NestedTreeDomainModelVisitor.TreeNodeValue);
        NestedTreeDomainModelVisitor.TreeNodeValue value =
                (NestedTreeDomainModelVisitor.TreeNodeValue) assorbNode.getUserObject();
        assertTrue(value.isRelationReference());
    }

    private DomainRelation relation(
            DomainEntity containing, DomainEntity destination, String containingKey, String destinationKey) {
        DomainRelation relation = new DomainRelation();
        relation.setContainingEntity(containing);
        relation.setDestinationEntity(destination);
        relation.setContainingKeyAttribute(attribute(containingKey));
        relation.setDestinationKeyAttribute(attribute(destinationKey));
        return relation;
    }

    private DomainEntitySimpleAttribute attribute(String name) {
        DomainEntitySimpleAttribute attribute = new DomainEntitySimpleAttribute();
        attribute.setName(name);
        attribute.setType(DomainAttributeType.TEXT);
        return attribute;
    }

    private DefaultMutableTreeNode findChild(DefaultMutableTreeNode node, String name) {
        if (node == null || name == null) {
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) child;
                if (name.equals(childNode.toString())) {
                    return childNode;
                }
            }
        }
        return null;
    }
}
