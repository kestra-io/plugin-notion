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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class DeleteItemTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private WireMockServer wireMockServer;
    private String previousBaseUrl;

    private static final String DATABASE_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PAGE_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

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
    void deleteItem_archivesPage_successfully() throws Exception {
        // Stub GET to return a non-archived page
        stubGetPage(PAGE_ID, false);
        // Stub PATCH to return the archived page
        stubPatchPage(PAGE_ID, true);

        var runContext = runContextFactory.of(Map.of());

        var deleteItem = DeleteItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .build();

        var output = deleteItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), notNullValue());
        assertThat(output.getMessage(), containsString("archived"));

        // Verify PATCH was made with archived=true
        wireMockServer.verify(patchRequestedFor(urlEqualTo("/v1/pages/" + PAGE_ID))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"archived\":true")));
    }

    @Test
    void deleteItem_alreadyArchived_returnsEarlyMessage() throws Exception {
        // Stub GET to return an already-archived page
        stubGetPage(PAGE_ID, true);

        var runContext = runContextFactory.of(Map.of());

        var deleteItem = DeleteItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .pageId(Property.ofValue(PAGE_ID))
            .build();

        var output = deleteItem.run(runContext);

        assertThat(output.getMessage(), containsString("already archived"));

        // Verify no PATCH was made
        wireMockServer.verify(0, patchRequestedFor(urlEqualTo("/v1/pages/" + PAGE_ID)));
    }

    // --- Helpers ---

    private void stubGetPage(String pageId, boolean archived) throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "page");
        responseBody.put("id", pageId);
        responseBody.put("archived", archived);
        responseBody.put("url", "https://www.notion.so/" + pageId);
        responseBody.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Test Row")))
        ));

        wireMockServer.stubFor(
            get(urlEqualTo("/v1/pages/" + pageId))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );
    }

    private void stubPatchPage(String pageId, boolean archived) throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "page");
        responseBody.put("id", pageId);
        responseBody.put("archived", archived);
        responseBody.put("url", "https://www.notion.so/" + pageId);
        responseBody.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Test Row")))
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
