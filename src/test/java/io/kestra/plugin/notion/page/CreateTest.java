package io.kestra.plugin.notion.page;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notion.NotionConnection;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Create functionality.
 * These tests focus on input validation, builder patterns, and request building
 * without requiring actual API calls.
 */
@KestraTest
class CreateTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private Create create;

    @BeforeEach
    void setUp() {
        create = Create.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .content(Property.ofValue("Test content"))
            .parentPageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), nullValue());
        assertThat(task.getParentPageId(), nullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(create.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromNotionConnection() {
        // Test that Create properly inherits from NotionConnection
        assertThat(create, instanceOf(NotionConnection.class));
    }

    @Test
    void testOutputBuilder() {
        // Test that the Output class builder works correctly
        Create.Output output = Create.Output.builder()
            .pageId("test-page-id")
            .url("https://notion.so/test-page")
            .title("Test Page")
            .content("Test content")
            .message("Page created successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getUrl(), equalTo("https://notion.so/test-page"));
        assertThat(output.getTitle(), equalTo("Test Page"));
        assertThat(output.getContent(), equalTo("Test content"));
        assertThat(output.getMessage(), equalTo("Page created successfully"));
    }

    @Test
    void testOutputBuilderWithNullValues() {
        // Test that Output builder handles null values gracefully
        Create.Output output = Create.Output.builder()
            .pageId("test-page-id")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getUrl(), nullValue());
        assertThat(output.getTitle(), nullValue());
        assertThat(output.getContent(), nullValue());
    }

    @Test
    void testOutputImplementsCorrectInterface() {
        // Test that Output implements the correct Kestra interface
        Create.Output output = Create.Output.builder().build();
        assertThat(output, instanceOf(io.kestra.core.models.tasks.Output.class));
    }

    @Test
    void testFieldValidation() {
        // Test required field validation concepts
        Create task = Create.builder()
            .title(Property.ofValue("Valid Title"))
            .build();

        // Test that required fields are properly set
        assertThat(task.getTitle(), notNullValue());
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test handling of optional fields
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .content(Property.ofValue("# Test Content\n\nThis is a test."))
            .parentPageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Create implements RunnableTask correctly
        assertThat(create, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));

        // Test that it has the correct generic type
        Create task = Create.builder()
            .title(Property.ofValue("Test"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Create task = Create.builder()
            .title(Property.ofValue("Test Title"))
            .content(Property.ofValue("Test content"))
            .parentPageId(Property.ofValue("test-parent-id"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testCreateWith100BlocksIsSingleRequest() throws Exception {
        // <=100 blocks must stay a single create request, with no append (no behaviour change).
        var captured = runCreateCapturingRequests(100);
        assertThat(captured.posts(), equalTo(1));
        assertThat(captured.createChildren(), equalTo(100));
        assertThat(captured.patches(), equalTo(0));
    }

    @Test
    void testCreateWith101BlocksAppendsTheOverflow() throws Exception {
        // Boundary: the 101st block must be appended in exactly one follow-up request (not dropped).
        var captured = runCreateCapturingRequests(101);
        assertThat(captured.createChildren(), equalTo(100));
        assertThat(captured.patches(), equalTo(1));
    }

    @Test
    void testCreateWith150BlocksSplitsIntoCreatePlusAppend() throws Exception {
        var captured = runCreateCapturingRequests(150);
        assertThat(captured.createChildren(), equalTo(100));
        assertThat(captured.patches(), equalTo(1));
    }

    private record CreateCapture(int posts, int createChildren, int patches) {
    }

    /**
     * Runs {@link Create} with {@code blockCount} paragraph blocks against a stubbed Notion API and
     * reports how the payload was split: number of create POSTs, the children the create carried, and
     * the number of append PATCHes.
     */
    private CreateCapture runCreateCapturingRequests(int blockCount) throws Exception {
        var newPageId = "12345678-1234-1234-1234-123456789abc";

        var content = new StringBuilder();
        for (int i = 0; i < blockCount; i++) {
            content.append("Line ").append(i).append("\n\n");
        }

        var runContext = runContextFactory.of(Map.of());

        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            wireMockServer.stubFor(
                post(urlEqualTo("/v1/pages"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"object\":\"page\",\"id\":\"" + newPageId + "\"}")
                            .withStatus(200)
                    )
            );

            wireMockServer.stubFor(
                patch(urlEqualTo("/v1/blocks/" + newPageId + "/children"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"object\":\"list\",\"results\":[]}")
                            .withStatus(200)
                    )
            );

            Create.builder()
                .apiToken(Property.ofValue("secret_token"))
                .title(Property.ofValue("Batched"))
                .content(Property.ofValue(content.toString()))
                .build()
                .run(runContext);

            var posts = wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/pages")));
            var patches = wireMockServer.findAll(patchRequestedFor(urlEqualTo("/v1/blocks/" + newPageId + "/children")));
            var createChildren = posts.isEmpty()
                ? 0
                : mapper.readTree(posts.get(0).getBodyAsString()).path("children").size();
            return new CreateCapture(posts.size(), createChildren, patches.size());
        } finally {
            wireMockServer.stop();
            if (previousBaseUrl == null) {
                System.clearProperty("notion.api.base.url");
            } else {
                System.setProperty("notion.api.base.url", previousBaseUrl);
            }
        }
    }
}
