/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.featurestemplating.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TemplateOpenAIServiceTest {

    @Test
    public void testExtractTemplateXmlFromMarkdownFence() {
        String response = "Here is the template:\n"
                + "```xml\n"
                + "<gft:Template xmlns:gft=\"http://geoserver.org/features-templating\">\n"
                + "  <my:feature>${value}</my:feature>\n"
                + "</gft:Template>\n"
                + "```";

        String extracted = TemplateOpenAIService.extractTemplateXml(response);
        assertTrue(extracted.startsWith("<gft:Template"));
        assertTrue(extracted.endsWith("</gft:Template>"));
    }

    @Test
    public void testExtractGenericTemplateFallback() {
        String response = "<Template><a>1</a></Template>";
        String extracted = TemplateOpenAIService.extractTemplateXml(response);
        assertEquals(response, extracted);
    }

    @Test
    public void testPromptContainsDocumentationLinksAndSections() {
        TemplateOpenAIService service = new TemplateOpenAIService("http://localhost/mock-openai");

        String prompt = service.buildPrompt(
                "cite",
                "states",
                "<xsd:schema/>",
                "<xsd:schema id=\"target\"/>",
                "<wfs:FeatureCollection/>",
                "Prioritize nested collections.");

        assertTrue(prompt.contains(TemplateOpenAIService.DOCS_URL));
        assertTrue(prompt.contains(TemplateOpenAIService.TRAINING_URL));
        assertTrue(prompt.contains("SOURCE_SCHEMA_XSD"));
        assertTrue(prompt.contains("TARGET_SCHEMA_XSD"));
        assertTrue(prompt.contains("SOURCE_SAMPLE_GML"));
        assertTrue(prompt.contains("cite:states"));
        assertTrue(prompt.contains("Prioritize nested collections."));
    }
}
