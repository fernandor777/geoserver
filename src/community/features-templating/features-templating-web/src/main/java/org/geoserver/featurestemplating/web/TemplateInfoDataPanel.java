/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.wicket.Component;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.core.util.string.JavaScriptUtils;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.featurestemplating.configuration.TemplateInfo;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.CodeMirrorEditor;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geotools.util.logging.Logging;

// TODO WICKET8 - Verify this page works OK
public abstract class TemplateInfoDataPanel extends Panel {

    static final Logger LOGGER = Logging.getLogger(TemplateInfoDataPanel.class);

    private final TemplateOpenAIService openAIService = new TemplateOpenAIService();

    private TemplateConfigurationPage page;

    private IModel<TemplateInfo> model;

    private TextField<String> templateName;

    private DropDownChoice<String> wsDropDown;

    private DropDownChoice<String> templateExtension;

    private DropDownChoice<String> ftiDropDown;

    private FileUploadField fileUploadField;

    private AjaxSubmitLink uploadLink;

    private FileUploadField targetSchemaUploadField;

    private TextArea<String> sourceSchemaArea;

    private TextArea<String> targetSchemaArea;

    private TextArea<String> sampleGmlArea;

    private FeedbackPanel aiFeedback;

    private final TemplateAIGenerationModel aiModel = new TemplateAIGenerationModel();

    public TemplateInfoDataPanel(String id, TemplateConfigurationPage page) {
        super(id);
        this.page = page;
        this.model = page.getTemplateInfoModel();
        initUI();
    }

    private void initUI() {
        templateName = new TextField<>("templateName", new PropertyModel<>(model, "templateName"));
        templateName.setOutputMarkupId(true);
        templateName.setRequired(true);
        add(templateName);
        templateExtension = new DropDownChoice<>("extension", new PropertyModel<>(model, "extension"), getExtensions());
        CodeMirrorEditor editor = page.getEditor();
        templateExtension.add(new OnChangeAjaxBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                String mode = templateExtension.getConvertedInput();
                if (mode != null && (mode.equals("xml") || mode.equals("xhtml"))) {
                    editor.setMode("xml");
                } else if (isJsonLd(editor)) {
                    editor.setModeAndSubMode("javascript", "jsonld");
                } else {
                    editor.setModeAndSubMode("javascript", mode);
                }
                ajaxRequestTarget.add(editor);
                TemplatePreviewPanel panel = getPreviewPanel();
                if (panel != null) panel.setOutputFormatsDropDownValues(templateExtension.getModelObject());
            }
        });
        templateExtension.setRequired(true);
        add(templateExtension);
        wsDropDown = new DropDownChoice<>("workspace", new PropertyModel<>(model, "workspace"), getWorkspaces());
        wsDropDown.setNullValid(true);
        wsDropDown.add(new OnChangeAjaxBehavior() {
            private static final long serialVersionUID = 732177308220189475L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                String workspace = wsDropDown.getConvertedInput();
                ftiDropDown.setChoices(getFeatureTypesInfo(workspace));
                ftiDropDown.modelChanged();
                target.add(ftiDropDown);
                ftiDropDown.setEnabled(true);
                TemplatePreviewPanel previewPanel = getPreviewPanel();
                if (previewPanel != null) previewPanel.setWorkspaceValue(workspace);
            }
        });
        add(wsDropDown);

        ftiDropDown = new DropDownChoice<>(
                "featureTypeInfo", new PropertyModel<>(model, "featureType"), Collections.emptyList());
        if (wsDropDown.getValue() == null || wsDropDown.getValue() == "-1") ftiDropDown.setEnabled(false);
        else ftiDropDown.setChoices(getFeatureTypesInfo(wsDropDown.getModelObject()));
        ftiDropDown.add(new OnChangeAjaxBehavior() {

            private static final long serialVersionUID = 3510850205685746576L;

            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                TemplatePreviewPanel previewPanel = getPreviewPanel();
                if (previewPanel != null) previewPanel.setFeatureTypeInfoValue(ftiDropDown.getConvertedInput());
            }
        });
        ftiDropDown.setOutputMarkupId(true);
        ftiDropDown.setNullValid(true);
        add(ftiDropDown);
        fileUploadField = new FileUploadField("filename");
        // Explicitly set model so this doesn't use the form model
        fileUploadField.setDefaultModel(new Model<>(""));
        add(fileUploadField);

        uploadLink = uploadLink();
        add(uploadLink);

        add(new PasswordTextField("openAiApiKey", new PropertyModel<>(aiModel, "openAiApiKey"))
                .setResetPassword(false)
                .setRequired(false));
        TextField<String> openAiModelField =
                new TextField<>("openAiModel", new PropertyModel<>(aiModel, "openAiModel"));
        openAiModelField.setRequired(false);
        add(openAiModelField);
        targetSchemaUploadField = new FileUploadField("targetSchemaFile");
        targetSchemaUploadField.setDefaultModel(new Model<>(""));
        add(targetSchemaUploadField);
        add(new TextField<>("targetSchemaUrl", new PropertyModel<>(aiModel, "targetSchemaUrl")));
        add(new TextField<>("sampleCount", new PropertyModel<>(aiModel, "sampleCount"), Integer.class));
        add(new TextArea<>("additionalInstructions", new PropertyModel<>(aiModel, "additionalInstructions")));

        sourceSchemaArea = new TextArea<>("sourceSchema", new PropertyModel<>(aiModel, "sourceSchema"));
        sourceSchemaArea.setOutputMarkupId(true);
        add(sourceSchemaArea);

        targetSchemaArea = new TextArea<>("targetSchema", new PropertyModel<>(aiModel, "targetSchema"));
        targetSchemaArea.setOutputMarkupId(true);
        add(targetSchemaArea);

        sampleGmlArea = new TextArea<>("sourceSampleGml", new PropertyModel<>(aiModel, "sourceSampleGml"));
        sampleGmlArea.setOutputMarkupId(true);
        add(sampleGmlArea);

        aiFeedback = new FeedbackPanel("aiFeedback");
        aiFeedback.setOutputMarkupId(true);
        add(aiFeedback);
        add(buildCollectContextLink());
        add(buildGenerateTemplateLink());
    }

    private List<String> getWorkspaces() {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        return catalog.getWorkspaces().stream().map(w -> w.getName()).collect(Collectors.toList());
    }

    private List<String> getExtensions() {
        return Arrays.asList("xml", "xhtml", "json");
    }

    private List<String> getFeatureTypesInfo(String workspaceName) {
        Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");
        NamespaceInfo namespaceInfo = catalog.getNamespaceByPrefix(workspaceName);
        return catalog.getFeatureTypesByNamespace(namespaceInfo).stream()
                .map(fti -> fti.getName())
                .collect(Collectors.toList());
    }

    private AjaxSubmitLink buildCollectContextLink() {
        return new AjaxSubmitLink("collectAIContext", page.getForm()) {

            private static final long serialVersionUID = 9101203090672138646L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                clearAIFeedback();
                try {
                    collectContextData();
                    info(getString("aiContextCollected"));
                } catch (Exception e) {
                    error(getString("aiContextCollectError") + " " + e.getMessage());
                    LOGGER.log(Level.FINE, "Unable to collect AI context", e);
                }
                refreshAIPanel(target);
            }

            @Override
            public boolean getDefaultFormProcessing() {
                return false;
            }
        };
    }

    private AjaxSubmitLink buildGenerateTemplateLink() {
        return new AjaxSubmitLink("generateTemplateWithAI", page.getForm()) {

            private static final long serialVersionUID = 3557581985069689128L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                clearAIFeedback();
                try {
                    if (isBlank(aiModel.getSourceSchema())
                            || isBlank(aiModel.getSourceSampleGml())
                            || isBlank(aiModel.getTargetSchema())) {
                        collectContextData();
                    }
                    TemplateInfo templateInfo = model.getObject();
                    String generatedTemplate = openAIService.generateTemplate(
                            aiModel.getOpenAiApiKey(),
                            aiModel.getOpenAiModel(),
                            templateInfo.getWorkspace(),
                            templateInfo.getFeatureType(),
                            aiModel.getSourceSchema(),
                            aiModel.getTargetSchema(),
                            aiModel.getSourceSampleGml(),
                            aiModel.getAdditionalInstructions());
                    templateInfo.setExtension("xml");
                    templateExtension.setModelObject("xml");
                    templateExtension.modelChanged();
                    page.getEditor().setModelObject(generatedTemplate);
                    page.getEditor().setMode("xml");
                    page.getEditor().modelChanged();
                    TemplatePreviewPanel previewPanel = getPreviewPanel();
                    if (previewPanel != null) previewPanel.setOutputFormatsDropDownValues("xml");
                    info(getString("aiTemplateGenerated"));
                    target.add(page.getEditor());
                    target.add(templateExtension);
                } catch (Exception e) {
                    error(getString("aiTemplateGenerationError") + " " + e.getMessage());
                    LOGGER.log(Level.FINE, "Unable to generate template through AI", e);
                }
                refreshAIPanel(target);
            }

            @Override
            public boolean getDefaultFormProcessing() {
                return false;
            }
        };
    }

    private void collectContextData() throws IOException {
        TemplateInfo templateInfo = model.getObject();
        if (isBlank(templateInfo.getWorkspace()) || isBlank(templateInfo.getFeatureType())) {
            throw new IllegalArgumentException(getString("aiMissingLayerSelection"));
        }
        HttpServletRequest request = GeoServerApplication.get().servletRequest();
        if (request == null) {
            throw new IllegalStateException("Unable to read current HTTP request.");
        }
        String uploadedSchema = getUploadedSchemaAsText();
        int sampleCount = aiModel.getSampleCount() == null ? 1 : Math.max(aiModel.getSampleCount(), 1);
        TemplateOpenAIService.ContextData contextData = openAIService.collectContext(
                request,
                templateInfo.getWorkspace(),
                templateInfo.getFeatureType(),
                sampleCount,
                aiModel.getTargetSchemaUrl(),
                uploadedSchema);
        aiModel.setSourceSchema(contextData.getSourceSchema());
        aiModel.setSourceSampleGml(contextData.getSourceSampleGml());
        aiModel.setTargetSchema(contextData.getTargetSchema());
    }

    private String getUploadedSchemaAsText() throws IOException {
        FileUpload upload = targetSchemaUploadField.getFileUpload();
        if (upload == null) {
            return null;
        }
        return IOUtils.toString(upload.getInputStream(), StandardCharsets.UTF_8);
    }

    private void refreshAIPanel(AjaxRequestTarget target) {
        target.add(aiFeedback);
        target.add(sourceSchemaArea);
        target.add(targetSchemaArea);
        target.add(sampleGmlArea);
    }

    private void clearAIFeedback() {
        aiFeedback.getFeedbackMessages().clear();
    }

    AjaxSubmitLink uploadLink() {
        return new ConfirmOverwriteSubmitLink("upload", page.getForm()) {

            private static final long serialVersionUID = 658341311654601761L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                FileUpload upload = fileUploadField.getFileUpload();
                if (upload == null) {
                    warn("No file selected.");
                    return;
                }
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try {
                    IOUtils.copy(upload.getInputStream(), bout);
                    page.getEditor().reset();
                    page.setRawTemplate(new InputStreamReader(new ByteArrayInputStream(bout.toByteArray()), "UTF-8"));
                    upload.getContentType();
                } catch (IOException e) {
                    throw new WicketRuntimeException(e);
                } catch (Exception e) {
                    page.error("Errors occurred uploading the '" + upload.getClientFileName() + "' template");
                    LOGGER.log(
                            Level.WARNING,
                            "Errors occurred uploading the '" + upload.getClientFileName() + "' template",
                            e);
                }

                TemplateInfo templateInfo = model.getObject();
                // set it
                String fileName = upload.getClientFileName();
                if (templateInfo.getTemplateName() == null
                        || "".equals(templateInfo.getTemplateName().trim())) {
                    templateName.setModelValue(new String[] {ResponseUtils.stripExtension(fileName)});
                }
                int index = fileName.lastIndexOf(".");
                String extension = fileName.substring(index + 1);
                templateInfo.setExtension(extension);
                CodeMirrorEditor editor = page.getEditor();
                if (!extension.equals("xml")) {
                    if (isJsonLd(editor)) editor.setModeAndSubMode("javascript", "jsonld");
                    else editor.setModeAndSubMode("javascript", "json");
                } else {
                    editor.setMode(extension);
                }
                editor.modelChanged();
                templateName.modelChanged();
                templateExtension.modelChanged();
                target.add(editor);
                target.add(page);
            }
        };
    }

    class ConfirmOverwriteSubmitLink extends AjaxSubmitLink {

        private static final long serialVersionUID = 2673499149884774636L;

        public ConfirmOverwriteSubmitLink(String id, Form<?> form) {
            super(id, form);
        }

        @Override
        protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
            super.updateAjaxAttributes(attributes);
            attributes.getAjaxCallListeners().add(new AjaxCallListener() {
                /** serialVersionUID */
                private static final long serialVersionUID = 8637613472102572505L;

                @Override
                public CharSequence getPrecondition(Component component) {
                    CharSequence message =
                            new ParamResourceModel("confirmOverwrite", TemplateInfoDataPanel.this).getString();
                    message = JavaScriptUtils.escapeQuotes(message);
                    return "var val = attrs.event.view.document.gsEditors ? "
                            + "attrs.event.view.document.gsEditors."
                            + page.getEditor().getTextAreaMarkupId()
                            + ".getValue() : "
                            + "attrs.event.view.document.getElementById(\""
                            + page.getEditor().getTextAreaMarkupId()
                            + "\").value; "
                            + "if(val != '' &&"
                            + "!confirm('"
                            + message
                            + "')) return false;";
                }
            });
        }

        @Override
        public boolean getDefaultFormProcessing() {
            return false;
        }
    }

    protected abstract TemplatePreviewPanel getPreviewPanel();

    boolean isJsonLd(CodeMirrorEditor editor) {
        String template = editor.getModelObject();
        if (template != null && !template.equals("") && template.contains("@context")) return true;
        return false;
    }

    public AjaxSubmitLink getUploadLink() {
        return uploadLink;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class TemplateAIGenerationModel {

        private String openAiApiKey;

        private String openAiModel = "gpt-4.1-mini";

        private String targetSchemaUrl;

        private Integer sampleCount = 1;

        private String additionalInstructions;

        private String sourceSchema;

        private String targetSchema;

        private String sourceSampleGml;

        public String getOpenAiApiKey() {
            return openAiApiKey;
        }

        public void setOpenAiApiKey(String openAiApiKey) {
            this.openAiApiKey = openAiApiKey;
        }

        public String getOpenAiModel() {
            return openAiModel;
        }

        public void setOpenAiModel(String openAiModel) {
            this.openAiModel = openAiModel;
        }

        public String getTargetSchemaUrl() {
            return targetSchemaUrl;
        }

        public void setTargetSchemaUrl(String targetSchemaUrl) {
            this.targetSchemaUrl = targetSchemaUrl;
        }

        public Integer getSampleCount() {
            return sampleCount;
        }

        public void setSampleCount(Integer sampleCount) {
            this.sampleCount = sampleCount;
        }

        public String getAdditionalInstructions() {
            return additionalInstructions;
        }

        public void setAdditionalInstructions(String additionalInstructions) {
            this.additionalInstructions = additionalInstructions;
        }

        public String getSourceSchema() {
            return sourceSchema;
        }

        public void setSourceSchema(String sourceSchema) {
            this.sourceSchema = sourceSchema;
        }

        public String getTargetSchema() {
            return targetSchema;
        }

        public void setTargetSchema(String targetSchema) {
            this.targetSchema = targetSchema;
        }

        public String getSourceSampleGml() {
            return sourceSampleGml;
        }

        public void setSourceSampleGml(String sourceSampleGml) {
            this.sourceSampleGml = sourceSampleGml;
        }
    }
}
