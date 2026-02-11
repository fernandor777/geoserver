/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.data.store;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.geoserver.smartdataloader.domain.IndexedDomainModelVisitorImpl;
import org.geoserver.smartdataloader.domain.entities.DomainEntity;
import org.geoserver.smartdataloader.domain.entities.DomainEntitySimpleAttribute;
import org.geoserver.smartdataloader.domain.entities.DomainRelation;

/** DomainModelVisitor that translates DomainModel representation into the required structure for NestedTree. */
public class NestedTreeDomainModelVisitor extends IndexedDomainModelVisitorImpl {

    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode root;

    private Map<DomainEntity, DefaultMutableTreeNode> entities = new HashMap<>();

    private DefaultMutableTreeNode currentTreeNode;

    @Override
    public void visitDomainRootEntity(DomainEntity entity) {
        this.visitedEntities.add(entity);
        String de = entity.getName();
        if (treeModel == null) {
            root = new DefaultMutableTreeNode(de);
            treeModel = new DefaultTreeModel(root);
            entities.put(entity, root);
            currentTreeNode = root;
        }
    }

    @Override
    public void visitDomainChainedEntity(DomainEntity entity) {
        this.visitedEntities.add(entity);
        String de = entity.getName();
        DefaultMutableTreeNode chainedEntity = addNodes(currentTreeNode, de);
        entities.put(entity, chainedEntity);
        currentTreeNode = chainedEntity;
    }

    @Override
    public void visitDomainEntitySimpleAttribute(DomainEntitySimpleAttribute domainAttribute) {
        String da = domainAttribute.getName();
        addNodes(currentTreeNode, da);
    }

    @Override
    public void visitDomainRelation(DomainRelation relation) {
        currentTreeNode = entities.get(relation.getContainingEntity());
        if (currentTreeNode == null || relation == null || relation.getDestinationEntity() == null) {
            return;
        }
        // Keep relation selectable even if the destination entity subtree was already visited elsewhere.
        if (isVisited(relation.getDestinationEntity())) {
            String destinationName = relation.getDestinationEntity().getName();
            if (findChildNode(currentTreeNode, destinationName) == null) {
                addRelationReferenceNode(currentTreeNode, destinationName);
            }
        }
    }

    public DefaultTreeModel getTreeModel() {
        return treeModel;
    }

    private DefaultMutableTreeNode addNodes(DefaultMutableTreeNode parent, String... childrenNode) {
        DefaultMutableTreeNode newNode = null;
        for (String childNode : childrenNode) {
            newNode = new DefaultMutableTreeNode(childNode);
            parent.add(newNode);
        }
        return newNode;
    }

    private DefaultMutableTreeNode findChildNode(DefaultMutableTreeNode parent, String childName) {
        if (parent == null || childName == null) {
            return null;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            Object child = parent.getChildAt(i);
            if (child instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) child;
                if (childName.equals(childNode.toString())) {
                    return childNode;
                }
            }
        }
        return null;
    }

    private DefaultMutableTreeNode addRelationReferenceNode(DefaultMutableTreeNode parent, String childNode) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new TreeNodeValue(childNode, true));
        parent.add(node);
        return node;
    }

    /** User object used to tag a node while preserving the displayed label. */
    public static final class TreeNodeValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String label;
        private final boolean relationReference;

        public TreeNodeValue(String label, boolean relationReference) {
            this.label = label;
            this.relationReference = relationReference;
        }

        public boolean isRelationReference() {
            return relationReference;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
