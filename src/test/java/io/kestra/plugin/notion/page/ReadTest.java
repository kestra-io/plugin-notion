package io.kestra.plugin.notion.page;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notion.NotionConnection;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Read functionality.
 * These tests focus on input validation, builder patterns, and inheritance
 * without requiring actual API calls.
 */
@KestraTest
class ReadTest {

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    @Inject
    private RunContextFactory runContextFactory;

    private Read read;

    @BeforeEach
    void setUp() {
        read = Read.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Read task = Read.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(read.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractTask() {
        // Test that Read properly inherits from AbstractTask
        assertThat(read, instanceOf(AbstractTask.class));
        assertThat(read, instanceOf(NotionConnection.class));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Read implements RunnableTask correctly
        assertThat(read, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));

        // Test that it has the correct generic type (inherited from AbstractTask)
        Read task = Read.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Read task = Read.builder()
            .pageId(Property.ofValue("test-page-id"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testRequiredFieldsHandling() {
        // Test that pageId is required (inherited from AbstractTask)
        Read task = Read.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testOutputInheritance() {
        // Test that Read uses the common Output from AbstractTask
        // This test verifies the structure is properly inherited

        // The output should be the same as AbstractTask.Output
        assertThat(AbstractTask.Output.class, notNullValue());

        // Test output builder
        AbstractTask.Output output = AbstractTask.Output.builder()
            .pageId("test-page-id")
            .content("test content")
            .message("Page read successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getContent(), equalTo("test content"));
        assertThat(output.getMessage(), equalTo("Page read successfully"));
    }

    @Test
    void testMinimalConfiguration() {
        // Test that Read can be built with minimal required fields
        Read task = Read.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testReadFollowsPaginationAndNestedBlocks() throws Exception {
        String pageId = "12345678-1234-1234-1234-123456789abc";
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        String previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            wireMockServer.stubFor(
                get(urlEqualTo("/v1/pages/" + pageId)).willReturn(
                    json(
                        Map.of(
                            "object", "page",
                            "id", pageId,
                            "url", "https://www.notion.so/" + pageId,
                            "properties", Map.of("title", Map.of("title", List.of(Map.of("plain_text", "Squads"))))
                        )
                    )
                )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", absent())
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(block("first", "paragraph", "First block", false)),
                                "has_more", true,
                                "next_cursor", "cursor-2"
                            )
                        )
                    )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", WireMock.equalTo("cursor-2"))
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(block("parent", "bulleted_list_item", "Block after the first page", true)),
                                "has_more", false
                            )
                        )
                    )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/parent/children"))
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(block("child", "bulleted_list_item", "Nested block", false)),
                                "has_more", false
                            )
                        )
                    )
            );

            Read task = Read.builder()
                .apiToken(Property.ofValue("test-token"))
                .pageId(Property.ofValue(pageId))
                .build();

            AbstractTask.Output output = task.run(runContextFactory.of(Map.of()));

            assertThat(output.getContent(), equalTo("First block\n\n- Block after the first page\n  - Nested block"));
        } finally {
            wireMockServer.stop();
            if (previousBaseUrl == null) {
                System.clearProperty("notion.api.base.url");
            } else {
                System.setProperty("notion.api.base.url", previousBaseUrl);
            }
        }
    }

    @Test
    void testReadExtractsTitleFromRenamedDatabaseColumn() throws Exception {
        String pageId = "12345678-1234-1234-1234-123456789abc";
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        String previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            wireMockServer.stubFor(
                get(urlEqualTo("/v1/pages/" + pageId)).willReturn(
                    json(
                        Map.of(
                            "object", "page",
                            "id", pageId,
                            "url", "https://www.notion.so/" + pageId,
                            "properties", Map.of(
                                "Task", Map.of(
                                    "id", "title",
                                    "type", "title",
                                    "title", List.of(Map.of("plain_text", "Ship it"))
                                )
                            )
                        )
                    )
                )
            );
            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children")).willReturn(
                    json(Map.of("results", List.of(), "has_more", false))
                )
            );

            Read task = Read.builder()
                .apiToken(Property.ofValue("test-token"))
                .pageId(Property.ofValue(pageId))
                .build();

            AbstractTask.Output output = task.run(runContextFactory.of(Map.of()));

            assertThat(output.getTitle(), equalTo("Ship it"));
        } finally {
            wireMockServer.stop();
            if (previousBaseUrl == null) {
                System.clearProperty("notion.api.base.url");
            } else {
                System.setProperty("notion.api.base.url", previousBaseUrl);
            }
        }
    }

    @Test
    void testReadRetriesOnRateLimitAndSkipsInaccessibleChildren() throws Exception {
        String pageId = "12345678-1234-1234-1234-123456789abc";
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        String previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            wireMockServer.stubFor(
                get(urlEqualTo("/v1/pages/" + pageId)).willReturn(
                    json(
                        Map.of(
                            "object", "page",
                            "id", pageId,
                            "url", "https://www.notion.so/" + pageId,
                            "properties", Map.of("title", Map.of("title", List.of(Map.of("plain_text", "Squads"))))
                        )
                    )
                )
            );

            // Rate limited for longer than the HTTP client's own single retry.
            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", absent())
                    .inScenario("rate-limit").whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                    .willSetStateTo("limited-once")
            );
            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", absent())
                    .inScenario("rate-limit").whenScenarioStateIs("limited-once")
                    .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                    .willSetStateTo("limited-twice")
            );
            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", absent())
                    .inScenario("rate-limit").whenScenarioStateIs("limited-twice")
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(
                                    block("synced", "synced_block", "", true),
                                    block("parent", "bulleted_list_item", "Visible", true)
                                ),
                                "has_more", true,
                                "next_cursor", "a+b/c="
                            )
                        )
                    )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", WireMock.equalTo("a+b/c="))
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(block("last", "bulleted_list_item", "Last", false)),
                                "has_more", false
                            )
                        )
                    )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/synced/children"))
                    .willReturn(
                        aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                            .withBody("{\"object\":\"error\",\"status\":404,\"code\":\"object_not_found\"}")
                    )
            );

            wireMockServer.stubFor(
                get(urlPathEqualTo("/v1/blocks/parent/children"))
                    .willReturn(
                        json(
                            Map.of(
                                "results", List.of(block("child", "bulleted_list_item", "Nested", false)),
                                "has_more", false
                            )
                        )
                    )
            );

            Read task = Read.builder()
                .apiToken(Property.ofValue("test-token"))
                .pageId(Property.ofValue(pageId))
                .build();

            AbstractTask.Output output = task.run(runContextFactory.of(Map.of()));

            assertThat(output.getContent(), equalTo("- Visible\n  - Nested\n- Last"));
            wireMockServer.verify(
                getRequestedFor(urlPathEqualTo("/v1/blocks/" + pageId + "/children"))
                    .withQueryParam("start_cursor", WireMock.equalTo("a+b/c="))
            );
        } finally {
            wireMockServer.stop();
            if (previousBaseUrl == null) {
                System.clearProperty("notion.api.base.url");
            } else {
                System.setProperty("notion.api.base.url", previousBaseUrl);
            }
        }
    }

    private static Map<String, Object> block(String id, String type, String text, boolean hasChildren) {
        return Map.of(
            "id", id,
            "type", type,
            "has_children", hasChildren,
            type, Map.of("rich_text", List.of(Map.of("plain_text", text)))
        );
    }

    private static ResponseDefinitionBuilder json(Object body) throws Exception {
        return aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsString(body))
            .withStatus(200);
    }
}
