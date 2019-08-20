package org.geoserver.web.data.resource;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.impl.DimensionInfoImpl;

public class DimensionEditor extends DimensionEditorBase<DimensionInfo> {

    public DimensionEditor(
            String id, IModel<DimensionInfo> model, ResourceInfo resource, Class<?> type) {
        super(id, model, resource, type);
    }

    public DimensionEditor(
            String id,
            IModel<DimensionInfo> model,
            ResourceInfo resource,
            Class<?> type,
            boolean editNearestMatch) {
        super(id, model, resource, type, editNearestMatch);
    }

    @Override
    protected DimensionInfo infoOf() {
        return new DimensionInfoImpl();
    }

    @Override
    protected DimensionInfo infoOf(DimensionInfo info) {
        return new DimensionInfoImpl(info);
    }
}
