package io.kestra.plugin.notion.page;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notion.utils.MarkdownConverter;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Update functionality.
 * These tests focus on input validation, builder patterns, and inheritance
 * without requiring actual API calls.
 */
@KestraTest
class UpdateTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("Updated Title"))
            .content(Property.ofValue("Updated content"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), nullValue());
        assertThat(task.getContent(), nullValue());
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test handling of optional fields
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("Updated Title"))
            .content(Property.ofValue("# Updated Content\n\nThis is updated content."))
            .build();

        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that it has the correct generic type (inherited from AbstractTask)
        Update task = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Update task = Update.builder()
            .pageId(Property.ofValue("test-page-id"))
            .title(Property.ofValue("Test Title"))
            .content(Property.ofValue("Test content"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testOutputInheritance() {
        // Test that Update uses the common Output from AbstractTask
        // This test verifies the structure is properly inherited

        // Test output builder
        AbstractTask.Output output = AbstractTask.Output.builder()
            .pageId("test-page-id")
            .title("Updated Title")
            .content("Updated content")
            .message("Page updated successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getTitle(), equalTo("Updated Title"));
        assertThat(output.getContent(), equalTo("Updated content"));
        assertThat(output.getMessage(), equalTo("Page updated successfully"));
    }

    @Test
    void testFieldValidation() {
        // Test required field validation concepts
        Update task = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        // Test that required fields are properly set
        assertThat(task.getPageId(), notNullValue());

        // Optional fields should be null if not set
        assertThat(task.getTitle(), nullValue());
        assertThat(task.getContent(), nullValue());
    }

    @Test
    void testFieldCombinations() {
        // Test different combinations of optional fields

        // Title only
        Update titleOnly = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("New Title"))
            .build();

        assertThat(titleOnly.getTitle(), notNullValue());
        assertThat(titleOnly.getContent(), nullValue());

        // Content only
        Update contentOnly = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .content(Property.ofValue("New content"))
            .build();

        assertThat(contentOnly.getTitle(), nullValue());
        assertThat(contentOnly.getContent(), notNullValue());

        // Both title and content
        Update both = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("New Title"))
            .content(Property.ofValue("New content"))
            .build();

        assertThat(both.getTitle(), notNullValue());
        assertThat(both.getContent(), notNullValue());
    }

    @Test
    void integration_update_appends_content_instead_of_replacing() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        String unique = UUID.randomUUID().toString().substring(0, 8);
        String title = "Kestra IT - Update append - " + unique;
        String apiToken = "test-token";
        String pageId = "11111111-1111-1111-1111-111111111111";

        String initialContent = """
            # Initial Content

            This is the initial content for append verification.
            - item A
            - item B
            """;

        String appendedContent = """
            ## Appended Section

            This content must be appended at the bottom.
            - item C
            - item D
            """;

        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        String previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            wireMockServer.stubFor(post(urlEqualTo("/v1/pages"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(pageResponseJson(pageId, title))
                    .withStatus(200)));

            wireMockServer.stubFor(get(urlEqualTo("/v1/pages/" + pageId))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(pageResponseJson(pageId, title))
                    .withStatus(200)));

            ArrayNode initialBlocks = ensurePlainText(MarkdownConverter.markdownToBlocks(initialContent));
            ArrayNode appendedBlocks = ensurePlainText(MarkdownConverter.markdownToBlocks(appendedContent));
            ArrayNode combinedBlocks = initialBlocks.deepCopy();
            combinedBlocks.addAll(appendedBlocks);

            wireMockServer.stubFor(get(urlEqualTo("/v1/blocks/" + pageId + "/children"))
                .inScenario("page-content")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(childrenResponseJson(initialBlocks))
                    .withStatus(200)));

            wireMockServer.stubFor(patch(urlEqualTo("/v1/blocks/" + pageId + "/children"))
                .inScenario("page-content")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"object\":\"list\",\"results\":[]}")
                    .withStatus(200))
                .willSetStateTo("appended"));

            wireMockServer.stubFor(get(urlEqualTo("/v1/blocks/" + pageId + "/children"))
                .inScenario("page-content")
                .whenScenarioStateIs("appended")
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(childrenResponseJson(combinedBlocks))
                    .withStatus(200)));

            // 1) Create
            Create create = Create.builder()
                .apiToken(Property.ofValue(apiToken))
                .title(Property.ofValue(title))
                .content(Property.ofValue(initialContent))
                .build();

            Create.Output created = create.run(runContext);
            assertThat(created, notNullValue());
            assertThat(created.getPageId(), equalTo(pageId));

            // 2) Read -> capture "before"
            Read read1 = Read.builder()
                .apiToken(Property.ofValue(apiToken))
                .pageId(Property.ofValue(pageId))
                .build();

            AbstractTask.Output before = read1.run(runContext);
            assertThat(before, notNullValue());
            assertThat(before.getContent(), notNullValue());
            String beforeContent = before.getContent();

            assertThat(beforeContent, containsString("Initial Content"));
            assertThat(beforeContent, containsString("item A"));
            assertThat(beforeContent, containsString("item B"));

            // 3) Update -> append
            Update update = Update.builder()
                .apiToken(Property.ofValue(apiToken))
                .pageId(Property.ofValue(pageId))
                .content(Property.ofValue(appendedContent))
                .build();

            AbstractTask.Output updated = update.run(runContext);
            assertThat(updated, notNullValue());
            assertThat(updated.getMessage(), anyOf(nullValue(), containsString("success")));

            // 4) Read -> capture "after"
            Read read2 = Read.builder()
                .apiToken(Property.ofValue(apiToken))
                .pageId(Property.ofValue(pageId))
                .build();

            AbstractTask.Output after = read2.run(runContext);
            assertThat(after, notNullValue());
            assertThat(after.getContent(), notNullValue());
            String afterContent = after.getContent();

            // initial content still present
            assertThat(afterContent, containsString("Initial Content"));
            assertThat(afterContent, containsString("item A"));
            assertThat(afterContent, containsString("item B"));

            // new content present too
            assertThat(afterContent, containsString("Appended Section"));
            assertThat(afterContent, containsString("item C"));
            assertThat(afterContent, containsString("item D"));

            int initialPos = afterContent.indexOf("Initial Content");
            int appendedPos = afterContent.indexOf("Appended Section");
            assertTrue(initialPos >= 0, "Initial Content not found in final markdown");
            assertTrue(appendedPos >= 0, "Appended Section not found in final markdown");
            assertTrue(appendedPos > initialPos, "Appended content should appear after initial content (append behavior)");
        } finally {
            wireMockServer.stop();
            if (previousBaseUrl == null) {
                System.clearProperty("notion.api.base.url");
            } else {
                System.setProperty("notion.api.base.url", previousBaseUrl);
            }
        }
    }

    private static String pageResponseJson(String pageId, String title) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("object", "page");
        response.put("id", pageId);
        response.put("created_time", "2024-01-01T00:00:00Z");
        response.put("last_edited_time", "2024-01-01T00:00:00Z");
        response.put("archived", false);
        response.put("url", "https://www.notion.so/" + pageId);

        Map<String, Object> titleProperty = new HashMap<>();
        List<Map<String, Object>> titleValues = new ArrayList<>();
        titleValues.add(Map.of("plain_text", title));
        titleProperty.put("title", titleValues);

        response.put("properties", Map.of("title", titleProperty));
        return mapper.writeValueAsString(response);
    }

    private static String childrenResponseJson(ArrayNode blocks) throws Exception {
        return mapper.writeValueAsString(Map.of("results", blocks));
    }

    private static ArrayNode ensurePlainText(ArrayNode blocks) {
        ArrayNode normalized = blocks.deepCopy();
        for (JsonNode block : normalized) {
            if (!(block instanceof ObjectNode objectBlock)) {
                continue;
            }
            String type = objectBlock.path("type").asText();
            JsonNode blockData = objectBlock.path(type);
            JsonNode richText = blockData.path("rich_text");
            if (!richText.isArray()) {
                continue;
            }
            for (JsonNode richTextNode : richText) {
                if (!(richTextNode instanceof ObjectNode richTextObject)) {
                    continue;
                }
                if (!richTextObject.has("plain_text")) {
                    String content = richTextObject.path("text").path("content").asText("");
                    richTextObject.put("plain_text", content);
                }
            }
        }
        return normalized;
    }
}
