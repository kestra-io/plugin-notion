package io.kestra.plugin.notion.database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateItemTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private WireMockServer wireMockServer;
    private String previousBaseUrl;

    private static final String DATABASE_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PAGE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

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
    void createItem_withTitlePropertiesAndContent_returnsExpectedOutput() throws Exception {
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Test Item"))
            .properties(Property.ofValue(Map.of("Status", Map.of("select", Map.of("name", "To Do")))))
            .content(Property.ofValue("## Description\nCreated by test."))
            .build();

        var output = createItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), equalTo("https://www.notion.so/" + PAGE_ID));
        assertThat(output.getCreatedTime(), notNullValue());
        assertThat(output.getLastEditedTime(), notNullValue());
        assertThat(output.getArchived(), equalTo(false));
        assertThat(output.getProperties(), notNullValue());
    }

    @Test
    void createItem_withTitleOnly_succeeds() throws Exception {
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Minimal Item"))
            .build();

        var output = createItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), notNullValue());
        assertThat(output.getArchived(), equalTo(false));
    }

    @Test
    void createItem_withContent_sendsChildrenBlocks() throws Exception {
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Item with content"))
            .content(Property.ofValue("Some paragraph content"))
            .build();

        var output = createItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));

        // Verify the request body contained a "children" key
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/pages"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"children\"")));
    }

    @Test
    void createItem_withoutContent_doesNotSendChildren() throws Exception {
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("No content item"))
            .build();

        createItem.run(runContext);

        // Verify the request body did NOT contain a "children" key
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/pages")));
        assertThat(requests.size(), equalTo(1));
        var body = requests.getFirst().getBodyAsString();
        assertThat(body, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"children\"")));
    }

    @Test
    void createItem_outputFieldsMappedCorrectly() throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "page");
        responseBody.put("id", PAGE_ID);
        responseBody.put("created_time", "2024-06-15T10:30:00Z");
        responseBody.put("last_edited_time", "2024-06-15T11:00:00Z");
        responseBody.put("archived", false);
        responseBody.put("url", "https://www.notion.so/My-Page-" + PAGE_ID);
        responseBody.put("properties", Map.of(
            "Name", Map.of("title", List.of(Map.of("plain_text", "Test Item"))),
            "Status", Map.of("select", Map.of("name", "To Do"))
        ));

        wireMockServer.stubFor(
            post(urlEqualTo("/v1/pages"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Test Item"))
            .build();

        var output = createItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
        assertThat(output.getUrl(), equalTo("https://www.notion.so/My-Page-" + PAGE_ID));
        assertThat(output.getCreatedTime().toString(), equalTo("2024-06-15T10:30:00Z"));
        assertThat(output.getLastEditedTime().toString(), equalTo("2024-06-15T11:00:00Z"));
        assertThat(output.getArchived(), equalTo(false));
        assertThat(output.getProperties().get("Status"), notNullValue());
    }

    @Test
    void createItem_missingTitle_throwsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .build();

        var exception = assertThrows(Exception.class, () -> createItem.run(runContext));
        assertThat(exception.getMessage(), org.hamcrest.Matchers.containsString("title"));
    }

    @Test
    void createItem_with32charHexDatabaseId_succeeds() throws Exception {
        var hexId = "33333333333333333333333333333333";
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(hexId))
            .title(Property.ofValue("Hex DB ID Item"))
            .build();

        var output = createItem.run(runContext);

        assertThat(output.getPageId(), equalTo(PAGE_ID));
    }

    @Test
    void createItem_withCheckboxAsStringBoolean_sendsJsonBoolean() throws Exception {
        // Simulates what happens when YAML has `checkbox: "{{ myVar }}"` and Pebble renders
        // the expression: the surrounding double-quotes force the result to be a String.
        // The coerceBooleans fix must turn "true"/"false" strings into real JSON booleans
        // before the request is sent, otherwise Notion rejects the payload.
        stubCreatePage(PAGE_ID, false);

        var runContext = runContextFactory.of(Map.of());

        // "true" and "false" as Strings, exactly as Pebble would produce them
        var properties = Map.<String, Object>of(
            "Done",   Map.<String, Object>of("checkbox", "true"),
            "Active", Map.<String, Object>of("checkbox", "false")
        );

        var createItem = CreateItem.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Checkbox Item"))
            .properties(Property.ofValue(properties))
            .build();

        createItem.run(runContext);

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/pages")));
        assertThat(requests.size(), equalTo(1));
        var body = requests.getFirst().getBodyAsString();

        // Must contain bare JSON booleans, not quoted strings
        assertThat(body, org.hamcrest.Matchers.containsString("\"checkbox\":true"));
        assertThat(body, org.hamcrest.Matchers.containsString("\"checkbox\":false"));

        // Must NOT contain the string form
        assertThat(body, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"checkbox\":\"true\"")));
        assertThat(body, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"checkbox\":\"false\"")));
    }

    // --- Helper ---

    private void stubCreatePage(String pageId, boolean archived) throws Exception {
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
            post(urlEqualTo("/v1/pages"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );
    }
}
