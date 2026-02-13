/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geotools.util.logging.Logging;

/** Helper service for prototype template generation through OpenAI. */
public class TemplateOpenAIService {

    static final String DOCS_URL = "https://docs.geoserver.org/main/en/user/community/features-templating/index.html";

    static final String TRAINING_URL =
            "https://docs.geoserver.geo-solutions.it/draft/edu/en/complex_features/features_templates/index.html";

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private static final int DEFAULT_TIMEOUT_MILLIS = 120000;

    private static final int MAX_PROMPT_CHARS_PER_SECTION = 100000;

    private static final java.util.logging.Logger LOGGER = Logging.getLogger(TemplateOpenAIService.class);

    private final ObjectMapper mapper;

    private final String openAiApiUrl;

    TemplateOpenAIService(String openAiApiUrl) {
        this.openAiApiUrl = openAiApiUrl;
        this.mapper = new ObjectMapper();
    }

    public TemplateOpenAIService() {
        this(DEFAULT_OPENAI_URL);
    }

    public ContextData collectContext(
            HttpServletRequest request,
            String workspace,
            String featureType,
            int sampleCount,
            String targetSchemaUrl,
            String uploadedTargetSchemaContent)
            throws IOException {
        String sourceSchema = fetchDescribeFeatureType(request, workspace, featureType);
        String sourceSample = fetchSampleGml(request, workspace, featureType, sampleCount);
        String targetSchema = resolveTargetSchema(targetSchemaUrl, uploadedTargetSchemaContent);
        return new ContextData(
                prettyPrintXMLSafe(sourceSchema), prettyPrintXMLSafe(sourceSample), prettyPrintXMLSafe(targetSchema));
    }

    public String generateTemplate(
            String apiKey,
            String model,
            String workspace,
            String featureType,
            String sourceSchema,
            String targetSchema,
            String sourceGmlSample,
            String extraInstructions)
            throws IOException {
        if (isBlank(apiKey)) {
            throw new IllegalArgumentException("OpenAI API key is required.");
        }
        if (isBlank(model)) {
            throw new IllegalArgumentException("OpenAI model is required.");
        }
        if (isBlank(sourceSchema) || isBlank(targetSchema) || isBlank(sourceGmlSample)) {
            throw new IllegalArgumentException("Source schema, target schema and source GML sample are all required.");
        }

        String prompt =
                buildPrompt(workspace, featureType, sourceSchema, targetSchema, sourceGmlSample, extraInstructions);

        JsonNode payload = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("model", model);
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("temperature", 0.1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                .set(
                        "messages",
                        mapper.createArrayNode()
                                .add(
                                        mapper.createObjectNode()
                                                .put("role", "system")
                                                .put(
                                                        "content",
                                                        "You are an expert in GeoServer Features Templating. Return only a valid XML template."))
                                .add(mapper.createObjectNode()
                                        .put("role", "user")
                                        .put("content", prompt)));

        String body = mapper.writeValueAsString(payload);
        String completion = executeOpenAiCall(apiKey, body);
        String template = extractTemplateXml(completion);
        if (isBlank(template)) {
            throw new IllegalStateException("OpenAI returned an empty template.");
        }
        return template;
    }

    String buildPrompt(
            String workspace,
            String featureType,
            String sourceSchema,
            String targetSchema,
            String sourceGmlSample,
            String extraInstructions) {
        String layerName = buildTypeName(workspace, featureType);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a GeoServer Features Templating XML template.\n");
        prompt.append("Layer: ").append(layerName).append("\n\n");
        prompt.append("Documentation links to use as context:\n");
        prompt.append("- ").append(DOCS_URL).append("\n");
        prompt.append("- ").append(TRAINING_URL).append("\n\n");
        prompt.append("Requirements:\n");
        prompt.append("- Output must be a valid XML template rooted at <gft:Template>.\n");
        prompt.append("- Map source GML structure to the target schema structure.\n");
        prompt.append("- Include namespaces and schema locations needed by the template.\n");
        prompt.append("- Prioritize robust mappings for app-schema/smart-data-loader nested features.\n");
        prompt.append(
                "- Use gft:source, gft:isCollection and xpath(...) where appropriate for nested complex paths.\n");
        prompt.append("- Keep the response XML-only, with no markdown and no explanations.\n");
        if (!isBlank(extraInstructions)) {
            prompt.append("- Additional instructions from user: ")
                    .append(extraInstructions)
                    .append("\n");
        }
        prompt.append("\nSOURCE_SCHEMA_XSD:\n");
        prompt.append("```xml\n").append(limitPromptSection(sourceSchema)).append("\n```\n\n");
        prompt.append("TARGET_SCHEMA_XSD:\n");
        prompt.append("```xml\n").append(limitPromptSection(targetSchema)).append("\n```\n\n");
        prompt.append("SOURCE_SAMPLE_GML:\n");
        prompt.append("```xml\n").append(limitPromptSection(sourceGmlSample)).append("\n```\n");
        return prompt.toString();
    }

    static String extractTemplateXml(String responseContent) {
        if (responseContent == null) {
            return "";
        }
        String cleaned = stripCodeFence(responseContent.trim());
        int templateStart = cleaned.indexOf("<gft:Template");
        int templateEnd = cleaned.lastIndexOf("</gft:Template>");
        if (templateStart >= 0 && templateEnd > templateStart) {
            int declaration = cleaned.indexOf("<?xml");
            if (declaration >= 0 && declaration < templateStart) {
                templateStart = declaration;
            }
            int endIndex = templateEnd + "</gft:Template>".length();
            return cleaned.substring(templateStart, endIndex).trim();
        }

        int genericStart = cleaned.indexOf("<Template");
        int genericEnd = cleaned.lastIndexOf("</Template>");
        if (genericStart >= 0 && genericEnd > genericStart) {
            int endIndex = genericEnd + "</Template>".length();
            return cleaned.substring(genericStart, endIndex).trim();
        }
        return cleaned;
    }

    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewLine = text.indexOf('\n');
        if (firstNewLine > 0) {
            text = text.substring(firstNewLine + 1);
        }
        int endFence = text.lastIndexOf("```");
        if (endFence >= 0) {
            text = text.substring(0, endFence);
        }
        return text.trim();
    }

    private String executeOpenAiCall(String apiKey, String payload) throws IOException {
        try (CloseableHttpClient client = buildDefaultHttpClient()) {
            HttpPost post = new HttpPost(openAiApiUrl);
            post.addHeader("Authorization", "Bearer " + apiKey.trim());
            post.addHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = client.execute(post)) {
                String body = readResponseBody(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("OpenAI request failed: " + extractErrorMessage(body, statusCode));
                }
                return extractCompletionText(body);
            }
        }
    }

    private String extractCompletionText(String responseBody) throws IOException {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new IOException("OpenAI response is missing completion choices.");
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode node : contentNode) {
                JsonNode inner = node.path("text");
                if (inner.isTextual()) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(inner.asText());
                }
            }
            return text.toString();
        }
        throw new IOException("OpenAI response does not contain a textual message content.");
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (isBlank(responseBody)) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");
            if (message.isTextual()) {
                return "HTTP " + statusCode + " - " + message.asText();
            }
        } catch (Exception e) {
            LOGGER.fine("Unable to parse OpenAI error response JSON.");
        }
        return "HTTP " + statusCode;
    }

    private String fetchDescribeFeatureType(HttpServletRequest request, String workspace, String featureType)
            throws IOException {
        String url = buildDescribeFeatureTypeUrl(request, workspace, featureType);
        return executeSessionGet(request, url);
    }

    private String fetchSampleGml(HttpServletRequest request, String workspace, String featureType, int sampleCount)
            throws IOException {
        String url = buildSampleGmlUrl(request, workspace, featureType, sampleCount);
        return executeSessionGet(request, url);
    }

    String buildDescribeFeatureTypeUrl(HttpServletRequest request, String workspace, String featureType) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("service", "WFS");
        params.put("version", "2.0.0");
        params.put("request", "DescribeFeatureType");
        params.put("typeNames", buildTypeName(workspace, featureType));
        return ResponseUtils.buildURL(
                ResponseUtils.baseURL(request), getServicePath(workspace), params, URLMangler.URLType.SERVICE);
    }

    String buildSampleGmlUrl(HttpServletRequest request, String workspace, String featureType, int sampleCount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("service", "WFS");
        params.put("version", "2.0.0");
        params.put("request", "GetFeature");
        params.put("typeNames", buildTypeName(workspace, featureType));
        params.put("outputFormat", "application/gml+xml; version=3.2");
        params.put("count", Integer.toString(Math.max(sampleCount, 1)));
        return ResponseUtils.buildURL(
                ResponseUtils.baseURL(request), getServicePath(workspace), params, URLMangler.URLType.SERVICE);
    }

    private String getServicePath(String workspace) {
        if (isBlank(workspace)) {
            return "ows";
        }
        return workspace + "/ows";
    }

    private String buildTypeName(String workspace, String featureType) {
        if (isBlank(featureType)) {
            throw new IllegalArgumentException("Feature type is required.");
        }
        if (featureType.contains(":") || isBlank(workspace)) {
            return featureType;
        }
        return workspace + ":" + featureType;
    }

    private String resolveTargetSchema(String targetSchemaUrl, String uploadedTargetSchemaContent) throws IOException {
        if (!isBlank(uploadedTargetSchemaContent)) {
            return uploadedTargetSchemaContent;
        }
        if (isBlank(targetSchemaUrl)) {
            throw new IllegalArgumentException("Target schema URL or uploaded schema content is required.");
        }
        URI uri = URI.create(targetSchemaUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Target schema URL must use http or https.");
        }
        try (CloseableHttpClient client = buildDefaultHttpClient()) {
            HttpGet get = new HttpGet(uri);
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("Target schema download failed with status " + status);
                }
                return readResponseBody(response.getEntity());
            }
        }
    }

    private String executeSessionGet(HttpServletRequest request, String url) throws IOException {
        try (CloseableHttpClient client = buildSessionHttpClient(request)) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = readResponseBody(response.getEntity());
                if (status < 200 || status >= 300) {
                    throw new IOException("Internal WFS request failed with status " + status);
                }
                return body;
            }
        }
    }

    private CloseableHttpClient buildSessionHttpClient(HttpServletRequest request) {
        RequestConfig clientConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_TIMEOUT_MILLIS)
                .setSocketTimeout(DEFAULT_TIMEOUT_MILLIS)
                .build();

        CookieStore cookieStore = new BasicCookieStore();
        if (request.getSession(false) != null) {
            BasicClientCookie cookie = new BasicClientCookie(
                    "JSESSIONID", request.getSession(false).getId());
            cookie.setPath(request.getContextPath());
            Calendar calendar = Calendar.getInstance();
            int maxInactive = request.getSession(false).getMaxInactiveInterval();
            calendar.add(Calendar.SECOND, maxInactive > 0 ? maxInactive : 30);
            cookie.setExpiryDate(calendar.getTime());
            cookie.setDomain(request.getServerName());
            cookieStore.addCookie(cookie);
        }
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(clientConfig)
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    private CloseableHttpClient buildDefaultHttpClient() {
        RequestConfig clientConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_TIMEOUT_MILLIS)
                .setSocketTimeout(DEFAULT_TIMEOUT_MILLIS)
                .build();
        return HttpClientBuilder.create().setDefaultRequestConfig(clientConfig).build();
    }

    private String readResponseBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return "";
        }
        return IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
    }

    private String limitPromptSection(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_PROMPT_CHARS_PER_SECTION) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_PROMPT_CHARS_PER_SECTION) + "\n<!-- truncated for prompt size -->";
    }

    private String prettyPrintXMLSafe(String input) {
        if (isBlank(input)) {
            return "";
        }
        try {
            return prettyPrintXML(input);
        } catch (Exception e) {
            return input.trim();
        }
    }

    private String prettyPrintXML(String input) throws Exception {
        Source xmlInput = new StreamSource(IOUtils.toInputStream(input, StandardCharsets.UTF_8));
        java.io.StringWriter stringWriter = new java.io.StringWriter();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(xmlInput, new StreamResult(stringWriter));
        return stringWriter.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static class ContextData {
        private final String sourceSchema;
        private final String sourceSampleGml;
        private final String targetSchema;

        ContextData(String sourceSchema, String sourceSampleGml, String targetSchema) {
            this.sourceSchema = sourceSchema;
            this.sourceSampleGml = sourceSampleGml;
            this.targetSchema = targetSchema;
        }

        String getSourceSchema() {
            return sourceSchema;
        }

        String getSourceSampleGml() {
            return sourceSampleGml;
        }

        String getTargetSchema() {
            return targetSchema;
        }
    }
}
