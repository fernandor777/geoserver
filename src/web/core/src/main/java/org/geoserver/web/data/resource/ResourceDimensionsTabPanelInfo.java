/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.resource;

import static org.geotools.util.Utilities.ensureArgumentNonNull;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.DimensionDefaultValueSetting;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.DimensionPresentation;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.impl.DimensionInfoImpl;
import org.geoserver.catalog.util.FeatureTypeDimensionsAccessor;
import org.geoserver.catalog.util.ReaderDimensionsAccessor;
import org.geoserver.web.data.resource.ResourceDimensionsTabPanelInfo.VectorCustomDimensionEntry;
import org.geoserver.web.publish.PublishedEditTabPanel;
import org.geoserver.web.util.MetadataMapModel;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.util.logging.Logging;

/**
 * Plugs into the layer page a time/elevation selector for vector data
 *
 * @author Alessio
 */
public class ResourceDimensionsTabPanelInfo extends PublishedEditTabPanel<LayerInfo> {

    private static final long serialVersionUID = 4702596541385329270L;

    static final Logger LOGGER = Logging.getLogger(ResourceDimensionsTabPanelInfo.class);

    @SuppressWarnings("unchecked")
    public ResourceDimensionsTabPanelInfo(String id, IModel<LayerInfo> model) {
        super(id, model);

        final LayerInfo layer = model.getObject();
        final ResourceInfo resource = layer.getResource();

        final PropertyModel<MetadataMap> metadata =
                new PropertyModel<MetadataMap>(model, "resource.metadata");

        // time
        IModel time = new MetadataMapModel(metadata, ResourceInfo.TIME, DimensionInfo.class);
        if (time.getObject() == null) {
            time.setObject(new DimensionInfoImpl());
        }
        add(new DimensionEditor("time", time, resource, Date.class, true));

        // elevation
        IModel elevation =
                new MetadataMapModel(metadata, ResourceInfo.ELEVATION, DimensionInfo.class);
        if (elevation.getObject() == null) {
            elevation.setObject(new DimensionInfoImpl());
        }
        add(new DimensionEditor("elevation", elevation, resource, Number.class));

        // handle raster data custom dimensions
        final List<RasterDimensionModel> customDimensionModels =
                new ArrayList<RasterDimensionModel>();
        if (resource instanceof CoverageInfo) {
            CoverageInfo ci = (CoverageInfo) resource;
            try {
                GridCoverage2DReader reader =
                        (GridCoverage2DReader) ci.getGridCoverageReader(null, null);
                ReaderDimensionsAccessor ra = new ReaderDimensionsAccessor(reader);

                for (String domain : ra.getCustomDomains()) {
                    boolean hasRange = ra.hasRange(domain);
                    boolean hasResolution = ra.hasResolution(domain);
                    RasterDimensionModel mm =
                            new RasterDimensionModel(
                                    metadata, domain, DimensionInfo.class, hasRange, hasResolution);
                    if (mm.getObject() == null) {
                        mm.setObject(new DimensionInfoImpl());
                    }
                    customDimensionModels.add(mm);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to access coverage reader custom dimensions", e);
            }
        }
        RefreshingView customDimensionsEditor =
                new RefreshingView("customDimensions") {

                    @Override
                    protected Iterator getItemModels() {
                        return customDimensionModels.iterator();
                    }

                    @Override
                    protected void populateItem(Item item) {
                        RasterDimensionModel model = (RasterDimensionModel) item.getModel();
                        ParamResourceModel customDimension =
                                new ParamResourceModel(
                                        "customDimension", ResourceDimensionsTabPanelInfo.this);
                        item.add(
                                new Label(
                                        "dimensionName",
                                        customDimension.getString()
                                                + ": "
                                                + model.getExpression()));
                        DimensionEditor editor =
                                new DimensionEditor("dimension", model, resource, String.class);
                        editor.disablePresentationMode(DimensionPresentation.CONTINUOUS_INTERVAL);
                        if (!model.hasRange && !model.hasResolution) {
                            editor.disablePresentationMode(DimensionPresentation.DISCRETE_INTERVAL);
                        }
                        item.add(editor);
                    }
                };
        add(customDimensionsEditor);
        customDimensionsEditor.setVisible(customDimensionModels.size() > 0);
    }
    
    private RefreshingView buildVectorCustomDimensionsView(final IModel<FeatureTypeInfo> typeInfoModel, 
    		final PropertyModel<MetadataMap> metadata) {
    	final RefreshingView view = new RefreshingView("vectorCustomDimensionsView") {

			@Override
			protected Iterator getItemModels() {
				return getCustomDimensionMetadataList(typeInfoModel, metadata).iterator();
			}

			@Override
			protected void populateItem(Item item) {
				final VectorDimensionModel model = (VectorDimensionModel)item.getModel();
				ParamResourceModel customDimension =
                        new ParamResourceModel(
                                "customDimension", ResourceDimensionsTabPanelInfo.this);
			}
		};
		return view;
    }

    private List<VectorDimensionModel> getCustomDimensionMetadataList(final IModel<FeatureTypeInfo> typeInfoModel, 
    		final PropertyModel<MetadataMap> metadata) {
    	final List<VectorDimensionModel> models = new ArrayList<>();
    	final FeatureTypeInfo typeInfo = typeInfoModel.getObject();
    	final FeatureTypeDimensionsAccessor accessor = new FeatureTypeDimensionsAccessor(typeInfo);
    	final Map<String, DimensionInfo> customDimensions = accessor.getCustomDimensions();
    	for (final Entry<String, DimensionInfo> dimension : customDimensions.entrySet()) {
    		final String dimensionName = dimension.getKey();
			models.add(
					new VectorDimensionModel(metadata, dimensionName, DimensionInfo.class));
		}
    	return models;
    }

    private void buildCustomVectorDimensionsView(final ResourceInfo resourceInfo) {
    	
    }
    
    class RasterDimensionModel extends MetadataMapModel {
        private static final long serialVersionUID = 4734439907138483817L;

        boolean hasRange;

        boolean hasResolution;

        public RasterDimensionModel(
                IModel<?> model,
                String expression,
                Class<?> target,
                boolean hasRange,
                boolean hasResolution) {
            super(model, expression, target);
        }

        public Object getObject() {
            return ((MetadataMap) model.getObject())
                    .get(ResourceInfo.CUSTOM_DIMENSION_PREFIX + expression, target);
        }

        public void setObject(Object object) {
            ((MetadataMap) model.getObject())
                    .put(ResourceInfo.CUSTOM_DIMENSION_PREFIX + expression, (Serializable) object);
        }
    }
    
    static class VectorDimensionModel extends MetadataMapModel {
    	
    	private static final String VECTOR_CUSTOM_DIMENSION_PREFIX = "dim_";
    	
    	boolean hasRange;

        boolean hasResolution;

        public VectorDimensionModel(
                IModel<?> model,
                String expression,
                Class<?> target) {
            super(model, expression, target);
        }
    	
        public Object getObject() {
            return ((MetadataMap) model.getObject())
                    .get(VECTOR_CUSTOM_DIMENSION_PREFIX + expression, target);
        }

        public void setObject(Object object) {
            ((MetadataMap) model.getObject())
                    .put(VECTOR_CUSTOM_DIMENSION_PREFIX + expression, (Serializable) object);
        }
    }
    
    public static class VectorCustomDimensionsListModel implements IModel<List<VectorCustomDimensionEntry>> {

    	private final IModel<LayerInfo> layerInfoModel;

		public VectorCustomDimensionsListModel(IModel<LayerInfo> layerInfoModel) {
			ensureArgumentNonNull("layerInfoModel", layerInfoModel);
			this.layerInfoModel = layerInfoModel;
		}

		@Override
		public void detach() {
		}

		@Override
		public List<VectorCustomDimensionEntry> getObject() {
			final List<VectorCustomDimensionEntry> resultList = new ArrayList<>();
			final FeatureTypeInfo typeInfo = getFeatureTypeInfo();
			if (typeInfo == null) return resultList;
			final FeatureTypeDimensionsAccessor dimensionsAccessor = dimensionsAccessorOf(typeInfo);
			final Map<String, DimensionInfo> customDimensions = dimensionsAccessor.getCustomDimensions();
			for (final Entry<String, DimensionInfo> entry : customDimensions.entrySet()) {
				final VectorCustomDimensionEntry dimensionEntry = customDimensionEntryOf(entry);
				resultList.add(dimensionEntry);
			}
			return resultList;
		}

		@Override
		public void setObject(List<VectorCustomDimensionEntry> object) {
			
		}
		
		private MetadataMap getMetadataMap() {
			FeatureTypeInfo typeInfo = getFeatureTypeInfo();
			if (typeInfo == null) return null;
			return typeInfo.getMetadata();
		}
		
		private FeatureTypeInfo getFeatureTypeInfo() {
			final ResourceInfo resourceInfo = layerInfoModel.getObject().getResource();
			if (!(resourceInfo instanceof FeatureTypeInfo)) return null;
			return (FeatureTypeInfo) resourceInfo;
		}
		
		protected FeatureTypeDimensionsAccessor dimensionsAccessorOf(FeatureTypeInfo typeInfo) {
			return new FeatureTypeDimensionsAccessor(typeInfo);
		}
		
		protected VectorCustomDimensionEntry customDimensionEntryOf(Entry<String, DimensionInfo> entry) {
			return new VectorCustomDimensionEntry(entry);
		}
		
    }
    
    public static class VectorCustomDimensionEntry implements DimensionInfo {
    	
    	private String key;
    	private String formerKey;
    	private DimensionInfo dimensionInfo;
    	private boolean removed = false;

		public VectorCustomDimensionEntry(final Entry<String, DimensionInfo> entry) {
    		ensureArgumentNonNull("entry", entry);
    		ensureArgumentNonNull("entry.key", entry.getKey());
    		ensureArgumentNonNull("entry.value", entry.getValue());
    		this.key = entry.getKey();
    		this.formerKey = entry.getKey();
    		this.dimensionInfo = entry.getValue();
    	}
    	
    	public boolean isEnabled() {
			return dimensionInfo.isEnabled();
		}

		public void setEnabled(boolean enabled) {
			dimensionInfo.setEnabled(enabled);
		}

		public String getAttribute() {
			return dimensionInfo.getAttribute();
		}

		public void setAttribute(String attribute) {
			dimensionInfo.setAttribute(attribute);
		}

		public String getEndAttribute() {
			return dimensionInfo.getEndAttribute();
		}

		public void setEndAttribute(String attribute) {
			dimensionInfo.setEndAttribute(attribute);
		}

		public DimensionPresentation getPresentation() {
			return dimensionInfo.getPresentation();
		}

		public void setPresentation(DimensionPresentation presentation) {
			dimensionInfo.setPresentation(presentation);
		}

		public BigDecimal getResolution() {
			return dimensionInfo.getResolution();
		}

		public void setResolution(BigDecimal resolution) {
			dimensionInfo.setResolution(resolution);
		}

		public String getUnits() {
			return dimensionInfo.getUnits();
		}

		public void setUnits(String units) {
			dimensionInfo.setUnits(units);
		}

		public String getUnitSymbol() {
			return dimensionInfo.getUnitSymbol();
		}

		public void setUnitSymbol(String unitSymbol) {
			dimensionInfo.setUnitSymbol(unitSymbol);
		}

		public DimensionDefaultValueSetting getDefaultValue() {
			return dimensionInfo.getDefaultValue();
		}

		public void setDefaultValue(DimensionDefaultValueSetting defaultValue) {
			dimensionInfo.setDefaultValue(defaultValue);
		}

		public boolean isNearestMatchEnabled() {
			return dimensionInfo.isNearestMatchEnabled();
		}

		public void setNearestMatchEnabled(boolean nearestMatch) {
			dimensionInfo.setNearestMatchEnabled(nearestMatch);
		}

		public String getAcceptableInterval() {
			return dimensionInfo.getAcceptableInterval();
		}

		public void setAcceptableInterval(String acceptableInterval) {
			dimensionInfo.setAcceptableInterval(acceptableInterval);
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getFormerKey() {
			return formerKey;
		}

		public void setFormerKey(String formerKey) {
			this.formerKey = formerKey;
		}

		public DimensionInfo getDimensionInfo() {
			return dimensionInfo;
		}

		public void setDimensionInfo(DimensionInfo dimensionInfo) {
			this.dimensionInfo = dimensionInfo;
		}

		public boolean isRemoved() {
			return removed;
		}

		public void setRemoved(boolean removed) {
			this.removed = removed;
		}
		
    }
    
}
