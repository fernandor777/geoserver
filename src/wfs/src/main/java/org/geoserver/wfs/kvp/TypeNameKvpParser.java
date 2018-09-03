/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.kvp;

import javax.xml.namespace.QName;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.LocalWorkspace;
import org.geoserver.wfs.WFSInfo;
import org.opengis.feature.type.Name;

/**
 * Parses a {@code typeName} GetFeature parameter the form "([prefix:]local)+".
 *
 * <p>This parser will parse strings of the above format into a list of {@link
 * javax.xml.namespace.QName}
 *
 * @author Justin Deoliveira, The Open Planning Project
 * @author groldan
 */
public class TypeNameKvpParser extends QNameKvpParser {

    GeoServer geoserver;

    public TypeNameKvpParser(String key, GeoServer geoserver, Catalog catalog) {
        super(key, catalog, false);
        this.geoserver = geoserver;
    }

    protected Object parseToken(String token) throws Exception {
        int i = token.indexOf(':');
        boolean isCiteCompliant = geoserver.getService(WFSInfo.class).isCiteCompliant();
        // if token has colon char (:)
        if (i != -1 || isCiteCompliant) {
            // if we are on a local workspace context (virtual service)
            // and Extended Layer names is enabled
            if (geoserver.getGlobal().isExtendedCharsOnLayerNamesEnabled()
                    && !isCiteCompliant
                    && LocalWorkspace.get() != null) {
                String part1 = token.substring(0, i);
                String part2 = token.substring(i + 1);
                // if part1: equals to local workspace, cut part1
                // and return it with local workspace name
                if (part1.equals(LocalWorkspace.get().getName())) {
                    return new QName(LocalWorkspace.get().getName(), part2);
                }
                // if part1: not equals to local workspace, check if layername exists
                FeatureTypeInfo ftInfo =
                        catalog.getFeatureTypeByName(LocalWorkspace.get().getName(), token);
                // if layername exists, return it
                if (ftInfo != null) {
                    return new QName(ftInfo.getNamespace().getURI(), ftInfo.getName());
                }
            }
            return super.parseToken(token);
        } else {
            // we don't have the namespace, use the catalog to lookup the feature type
            // mind, this is lenient behavior so we use it only if the server is not runnig in cite
            // mode
            FeatureTypeInfo ftInfo = catalog.getFeatureTypeByName(token);
            if (ftInfo == null) {
                return new QName(null, token);
            } else {
                final Name name = ftInfo.getFeatureType().getName();
                return new QName(name.getNamespaceURI(), name.getLocalPart());
            }
        }
    }
}
