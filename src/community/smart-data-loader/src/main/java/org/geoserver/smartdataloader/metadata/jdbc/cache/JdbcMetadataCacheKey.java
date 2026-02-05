/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc.cache;

import java.util.Objects;

/** Stable key used to cache metadata snapshots by datastore and metadata-shaping parameters. */
public final class JdbcMetadataCacheKey {

    private final String datastoreId;
    private final String schema;
    private final String helperFingerprint;

    public JdbcMetadataCacheKey(String datastoreId, String schema, String helperFingerprint) {
        this.datastoreId = normalize(datastoreId);
        this.schema = normalize(schema);
        this.helperFingerprint = normalize(helperFingerprint);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    public String getDatastoreId() {
        return datastoreId;
    }

    public String getSchema() {
        return schema;
    }

    public String getHelperFingerprint() {
        return helperFingerprint;
    }

    public String asLogToken() {
        return Integer.toHexString(hashCode());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof JdbcMetadataCacheKey)) {
            return false;
        }
        JdbcMetadataCacheKey other = (JdbcMetadataCacheKey) object;
        return Objects.equals(datastoreId, other.datastoreId)
                && Objects.equals(schema, other.schema)
                && Objects.equals(helperFingerprint, other.helperFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datastoreId, schema, helperFingerprint);
    }
}
