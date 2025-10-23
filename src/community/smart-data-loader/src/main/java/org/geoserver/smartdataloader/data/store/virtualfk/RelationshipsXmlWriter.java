package org.geoserver.smartdataloader.data.store.virtualfk;

/**
 * Utility class that serializes {@link Relationships} to the XML representation expected by the virtual foreign key
 * parser.
 */
public final class RelationshipsXmlWriter {

    private RelationshipsXmlWriter() {
        // utility class
    }

    public static String toXml(Relationships relationships) {
        if (relationships == null
                || relationships.getRelationships() == null
                || relationships.getRelationships().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<relationships>");
        for (Relationship relationship : relationships.getRelationships()) {
            if (relationship == null) {
                continue;
            }
            sb.append("<relationship");
            appendAttribute(sb, "name", relationship.getName());
            appendAttribute(sb, "cardinality", relationship.getCardinality());
            sb.append(">");
            appendEntityRef(sb, "source", relationship.getSource());
            appendEntityRef(sb, "target", relationship.getTarget());
            sb.append("</relationship>");
        }
        sb.append("</relationships>");
        return sb.toString();
    }

    private static void appendEntityRef(StringBuilder sb, String tagName, EntityRef ref) {
        if (ref == null) {
            return;
        }
        sb.append("<").append(tagName);
        appendAttribute(sb, "schema", ref.getSchema());
        appendAttribute(sb, "entity", ref.getEntity());
        appendAttribute(sb, "kind", ref.getKind());
        sb.append(">");
        if (ref.getKey() != null) {
            sb.append("<key");
            appendAttribute(sb, "column", ref.getKey().getColumn());
            sb.append("/>");
        }
        sb.append("</").append(tagName).append(">");
    }

    private static void appendAttribute(StringBuilder sb, String name, String value) {
        if (value == null) {
            return;
        }
        sb.append(" ").append(name).append("=\"").append(escape(value)).append("\"");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    escaped.append(ch);
                    break;
            }
        }
        return escaped.toString();
    }
}
