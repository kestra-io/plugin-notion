package io.kestra.plugin.notion.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notion.NotionResponse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class QueryTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    // --- normalizeId unit tests (no credentials needed) ---

    @Test
    void normalizeId_validUuid_returnsSame() {
        var uuid = "12345678-1234-1234-1234-123456789abc";
        assertThat(AbstractDatabaseTask.normalizeId(uuid), equalTo(uuid));
    }

    @Test
    void normalizeId_32charHex_convertsToUuid() {
        var hex = "123456781234123412341234567890ab";
        var expected = "12345678-1234-1234-1234-1234567890ab";
        assertThat(AbstractDatabaseTask.normalizeId(hex), equalTo(expected));
    }

    @Test
    void normalizeId_32charHexUppercase_convertsToUuid() {
        var hex = "123456781234123412341234567890AB";
        var expected = "12345678-1234-1234-1234-1234567890AB";
        assertThat(AbstractDatabaseTask.normalizeId(hex), equalTo(expected));
    }

    @Test
    void normalizeId_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractDatabaseTask.normalizeId(null));
    }

    @Test
    void normalizeId_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractDatabaseTask.normalizeId("  "));
    }

    @Test
    void normalizeId_invalidString_throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractDatabaseTask.normalizeId("not-a-uuid"));
    }

    @Test
    void normalizeId_tooShortHex_throws() {
        assertThrows(IllegalArgumentException.class, () -> AbstractDatabaseTask.normalizeId("1234abcd"));
    }

    // --- Query WireMock test ---

    @Test
    void query_happyPath_returnsRows() throws Exception {
        var databaseId = "11111111-1111-1111-1111-111111111111";
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            var responseBody = Map.of(
                "object", "list",
                "results", List.of(
                    Map.of("id", "page-1", "object", "page"),
                    Map.of("id", "page-2", "object", "page")
                ),
                "has_more", false,
                "next_cursor", (Object) "null"
            );

            wireMockServer.stubFor(
                post(urlEqualTo("/v1/databases/" + databaseId + "/query"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mapper.writeValueAsString(responseBody))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var query = Query.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(databaseId))
                .pageSize(Property.ofValue(50))
                .build();

            var output = query.run(runContext);

            assertThat(output.getRows(), hasSize(2));
            assertThat(output.getTotal(), equalTo(2));
            assertThat(output.getHasMore(), equalTo(false));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    @Test
    void query_withFilterAndSorts_succeeds() throws Exception {
        var databaseId = "22222222-2222-2222-2222-222222222222";
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            var responseBody = Map.of(
                "object", "list",
                "results", List.of(Map.of("id", "filtered-page", "object", "page")),
                "has_more", false
            );

            wireMockServer.stubFor(
                post(urlEqualTo("/v1/databases/" + databaseId + "/query"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mapper.writeValueAsString(responseBody))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var query = Query.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(databaseId))
                .filter(Property.ofValue(Map.of("property", "Status", "select", Map.of("equals", "Done"))))
                .sorts(Property.ofValue(List.of(Map.of("property", "Created", "direction", "descending"))))
                .build();

            var output = query.run(runContext);

            assertThat(output.getRows(), hasSize(1));
            assertThat(output.getTotal(), equalTo(1));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    @Test
    void query_with32charHexDatabaseId_succeeds() throws Exception {
        var hexId = "11111111111111111111111111111111";
        var normalizedId = "11111111-1111-1111-1111-111111111111";
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            wireMockServer.stubFor(
                post(urlEqualTo("/v1/databases/" + normalizedId + "/query"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(mapper.writeValueAsString(Map.of(
                                "object", "list",
                                "results", List.of(),
                                "has_more", false
                            )))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var query = Query.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(hexId))
                .build();

            var output = query.run(runContext);

            assertThat(output.getRows(), empty());
            assertThat(output.getTotal(), equalTo(0));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    // --- CreateItem + UpdateItem roundtrip WireMock test ---

    @Test
    void createItem_happyPath_returnsPageId() throws Exception {
        var databaseId = "33333333-3333-3333-3333-333333333333";
        var pageId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
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
                            .withBody(pageResponseJson(pageId, false))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var createItem = CreateItem.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(databaseId))
                .title(Property.ofValue("Test Item"))
                .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "To Do")))))
                .content(Property.ofValue("## Description\nCreated by test."))
                .build();

            var output = createItem.run(runContext);

            assertThat(output.getPageId(), equalTo(pageId));
            assertThat(output.getUrl(), notNullValue());
            assertThat(output.getArchived(), equalTo(false));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    @Test
    void updateItem_happyPath_returnsUpdatedProperties() throws Exception {
        var databaseId = "33333333-3333-3333-3333-333333333333";
        var pageId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            wireMockServer.stubFor(
                patch(urlEqualTo("/v1/pages/" + pageId))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(pageResponseJson(pageId, false))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var updateItem = UpdateItem.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(databaseId))
                .pageId(Property.ofValue(pageId))
                .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "Done")))))
                .build();

            var output = updateItem.run(runContext);

            assertThat(output.getPageId(), equalTo(pageId));
            assertThat(output.getArchived(), equalTo(false));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    @Test
    void updateItem_archive_setsArchivedTrue() throws Exception {
        var databaseId = "33333333-3333-3333-3333-333333333333";
        var pageId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        var wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        var previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());

        try {
            configureFor("localhost", wireMockServer.port());

            wireMockServer.stubFor(
                patch(urlEqualTo("/v1/pages/" + pageId))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(pageResponseJson(pageId, true))
                            .withStatus(200)
                    )
            );

            var runContext = runContextFactory.of(Map.of());

            var updateItem = UpdateItem.builder()
                .apiToken(Property.ofValue("test-token"))
                .databaseId(Property.ofValue(databaseId))
                .pageId(Property.ofValue(pageId))
                .archived(Property.ofValue(true))
                .build();

            var output = updateItem.run(runContext);

            assertThat(output.getPageId(), equalTo(pageId));
            assertThat(output.getArchived(), equalTo(true));
        } finally {
            wireMockServer.stop();
            restoreBaseUrl(previousBaseUrl);
        }
    }

    @Test
    void updateItem_noPropertiesOrArchived_throws() {
        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue("33333333-3333-3333-3333-333333333333"))
            .pageId(Property.ofValue("cccccccc-cccc-cccc-cccc-cccccccccccc"))
            .build();

        var runContext = runContextFactory.of(Map.of());

        assertThrows(IllegalArgumentException.class, () -> updateItem.run(runContext));
    }

    // --- Integration tests (require real Notion API credentials) ---

    @Test
    @Disabled("Requires NOTION_API_TOKEN and a real database — run manually")
    void integration_query_realDatabase() throws Exception {
        var runContext = runContextFactory.of(Map.of());

        var query = Query.builder()
            .apiToken(Property.ofValue(System.getenv("NOTION_API_TOKEN")))
            .databaseId(Property.ofValue(System.getenv("NOTION_DATABASE_ID")))
            .pageSize(Property.ofValue(10))
            .build();

        var output = query.run(runContext);

        assertThat(output.getRows(), notNullValue());
        assertThat(output.getTotal(), greaterThanOrEqualTo(0));
    }

    @Test
    @Disabled("Requires NOTION_API_TOKEN and a real database — run manually")
    void integration_createAndUpdate_roundtrip() throws Exception {
        var runContext = runContextFactory.of(Map.of());
        var unique = UUID.randomUUID().toString().substring(0, 8);

        // Create
        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue(System.getenv("NOTION_API_TOKEN")))
            .databaseId(Property.ofValue(System.getenv("NOTION_DATABASE_ID")))
            .title(Property.ofValue("IT-" + unique))
            .content(Property.ofValue("Created by integration test"))
            .build();

        var created = createItem.run(runContext);
        assertThat(created.getPageId(), notNullValue());

        // Update (archive)
        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue(System.getenv("NOTION_API_TOKEN")))
            .databaseId(Property.ofValue(System.getenv("NOTION_DATABASE_ID")))
            .pageId(Property.ofValue(created.getPageId()))
            .archived(Property.ofValue(true))
            .build();

        var updated = updateItem.run(runContext);
        assertThat(updated.getArchived(), equalTo(true));
    }

    // --- Helpers ---

    private static String pageResponseJson(String pageId, boolean archived) throws Exception {
        var response = new HashMap<String, Object>();
        response.put("object", "page");
        response.put("id", pageId);
        response.put("created_time", "2024-01-01T00:00:00Z");
        response.put("last_edited_time", "2024-01-01T00:00:00Z");
        response.put("archived", archived);
        response.put("url", "https://www.notion.so/" + pageId);
        response.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Test Item")))
        ));
        return mapper.writeValueAsString(response);
    }

    private static void restoreBaseUrl(String previousBaseUrl) {
        if (previousBaseUrl == null) {
            System.clearProperty("notion.api.base.url");
        } else {
            System.setProperty("notion.api.base.url", previousBaseUrl);
        }
    }
}
