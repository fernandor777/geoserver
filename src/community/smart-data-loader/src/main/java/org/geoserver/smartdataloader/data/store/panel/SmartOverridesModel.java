package org.geoserver.smartdataloader.data.store.panel;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.DataStoreInfo;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class SmartOverridesModel implements IModel<Set<SmartOverrideEntry>> {

    public static final String SMART_OVERRIDE = "smart-override:";

    private final IModel<DataStoreInfo> storeModel;

    SmartOverridesModel(IModel<DataStoreInfo> storeModel) {
        this.storeModel = storeModel;
    }

    @Override
    public Set<SmartOverrideEntry> getObject() {
        return getSmartOverrides();
    }

    @Override
    public void setObject(Set<SmartOverrideEntry> object) {
        setSmartOverrides(object);
    }

    public void add(SmartOverrideEntry entry) {
        Map<String, Serializable> connectionParameters = storeModel.getObject().getConnectionParameters();
        connectionParameters.put(entry.getKey(), entry.getExpression());
    }

    private Set<SmartOverrideEntry> getSmartOverrides() {
        Map<String, Serializable> connectionParameters = storeModel.getObject().getConnectionParameters();
        if (connectionParameters != null) {
            return Collections.emptySet();
        }
        return connectionParameters.entrySet().stream()
                .filter(e -> e.getKey().startsWith(SMART_OVERRIDE))
                .map(e -> new SmartOverrideEntry(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toSet());
    }

    private void setSmartOverrides(Set<SmartOverrideEntry> smartOverrides) {
        Map<String, Serializable> connectionParameters = storeModel.getObject().getConnectionParameters();
        for (String key : connectionParameters.keySet()) {
            if (key.startsWith(SMART_OVERRIDE)) {
                connectionParameters.remove(key);
            }
        }
        smartOverrides.forEach(e -> connectionParameters.put(e.getKey(), e.getExpression()));
    }
}
