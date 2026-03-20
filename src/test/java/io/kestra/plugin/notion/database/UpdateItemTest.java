package io.kestra.plugin.notion.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class UpdateItemTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private WireMockServer wireMockServer;
    private String previousBaseUrl;

    private static final String DATABASE_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PAGE_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        previousBaseUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMockServer.baseUrl());
        configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
        if (previousBaseUrl == null) {
            System.clearProperty("notion.api.base.url");
        } else {
            System.setProperty("notion.api.base.url", previousBaseUrl);
        }
    }

    @Test
    void updateItem_withProperties_returnsUpdatedOutput() throws Exception {
        stubPatchPage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "Done")))))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), notNullValue());
        assertThat(output.getLastEditedTime(), notNullValue());
        assertThat(output.getArchived(), equalTo(false));
        assertThat(output.getProperties(), notNullValue());
    }

    @Test
    void updateItem_withArchivedTrue_returnsArchivedOutput() throws Exception {
        stubPatchPage(PAGE_ID, true);

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .archived(Property.ofValue(true))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getArchived(), equalTo(true));
    }

    @Test
    void updateItem_withPropertiesAndArchived_sendsBothInBody() throws Exception {
        stubPatchPage(PAGE_ID, true);

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .properties(Property.ofValue(Map.of("Priority", Map.of("select", Map.of("name", "High")))))
            .archived(Property.ofValue(true))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getArchived(), equalTo(true));

        // Verify both properties and archived were sent
        wireMockServer.verify(patchRequestedFor(urlEqualTo("/v1/pages/" + PAGE_ID))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"properties\""))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"archived\"")));
    }

    @Test
    void updateItem_noPropertiesOrArchived_throwsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> updateItem.run(runContext));
        assertThat(exception.getMessage(), containsString("properties"));
    }

    @Test
    void updateItem_with32charHexPageId_normalizesToUuid() throws Exception {
        var hexPageId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        var normalizedPageId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

        stubPatchPage(normalizedPageId, false);

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(hexPageId))
            .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "Done")))))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(normalizedPageId));

        // Verify the request was made to the normalized UUID URL
        wireMockServer.verify(patchRequestedFor(urlEqualTo("/v1/pages/" + normalizedPageId)));
    }

    @Test
    void updateItem_withArchivedFalse_restoresItem() throws Exception {
        stubPatchPage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .archived(Property.ofValue(false))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getArchived(), equalTo(false));

        wireMockServer.verify(patchRequestedFor(urlEqualTo("/v1/pages/" + PAGE_ID))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"archived\":false")));
    }

    @Test
    void updateItem_outputFieldsMappedCorrectly() throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "page");
        responseBody.put("id", PAGE_ID);
        responseBody.put("created_time", "2024-01-01T00:00:00Z");
        responseBody.put("last_edited_time", "2024-06-15T14:30:00Z");
        responseBody.put("archived", false);
        responseBody.put("url", "https://www.notion.so/Updated-Page-" + PAGE_ID);
        responseBody.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Updated Item"))),
            "Status", Map.of("select", Map.of("name", "Done"))
        ));

        wireMockServer.stubFor(
            patch(urlEqualTo("/v1/pages/" + PAGE_ID))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );

        var runContext = runContextFactory.of(Map.of());

        var updateItem = UpdateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "Done")))))
            .build();

        var output = updateItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), equalTo("https://www.notion.so/Updated-Page-" + PAGE_ID));
        assertThat(output.getLastEditedTime().toString(), equalTo("2024-06-15T14:30:00Z"));
        assertThat(output.getArchived(), equalTo(false));
        assertThat(output.getProperties().get("Status"), notNullValue());
    }

    // --- Helper ---

    private void stubPatchPage(String pageId, boolean archived) throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "page");
        responseBody.put("id", pageId);
        responseBody.put("created_time", "2024-01-01T00:00:00Z");
        responseBody.put("last_edited_time", "2024-01-01T00:00:00Z");
        responseBody.put("archived", archived);
        responseBody.put("url", "https://www.notion.so/" + pageId);
        responseBody.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Test Item")))
        ));

        wireMockServer.stubFor(
            patch(urlEqualTo("/v1/pages/" + pageId))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );
    }
}
