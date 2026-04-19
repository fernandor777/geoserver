/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.smartdataloader.metadata.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/** Factory that selects the most appropriate {@link JdbcHelper} for a JDBC connection. */
public final class JdbcHelperFactory {

    private static final Logger LOGGER = Logging.getLogger(JdbcHelperFactory.class);
    private static final String POSTGRES_PRODUCT = "postgresql";

    private JdbcHelperFactory() {}

    public static JdbcHelper forConnection(Connection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData != null ? metaData.getDatabaseProductName() : null;
            if (product != null && product.toLowerCase(Locale.ROOT).contains(POSTGRES_PRODUCT)) {
                return new OnDemandPostgresCatalogJdbcHelper(new PostgresCatalogJdbcHelper());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Failed to detect database product, using default JDBC helper.", e);
        }
        return new DefaultJdbcHelper();
    }
}
