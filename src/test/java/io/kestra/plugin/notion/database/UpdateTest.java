package io.kestra.plugin.notion.database;

import java.util.HashMap;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class UpdateTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private WireMockServer wireMockServer;
    private String previousBaseUrl;

    private static final String DATABASE_ID = "44444444-4444-4444-4444-444444444444";

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
    void update_withTitleAndDescription_updatesDatabase() throws Exception {
        stubPatchDatabase(DATABASE_ID);

        var runContext = runContextFactory.of(Map.of());

        var update = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Updated Title"))
            .databaseDescription(Property.ofValue("Updated description text"))
            .build();

        var output = update.run(runContext);

        assertThat(output.getDatabaseId(), equalTo(DATABASE_ID));
        assertThat(output.getUrl(), notNullValue());
        assertThat(output.getMessage(), equalTo("Database updated successfully"));

        // Verify both title and description were sent
        wireMockServer.verify(patchRequestedFor(urlEqualTo("/v1/databases/" + DATABASE_ID))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"title\""))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("\"description\"")));
    }

    @Test
    void update_withTitleOnly_updatesTitleOnly() throws Exception {
        stubPatchDatabase(DATABASE_ID);

        var runContext = runContextFactory.of(Map.of());

        var update = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .title(Property.ofValue("Only Title"))
            .build();

        var output = update.run(runContext);

        assertThat(output.getDatabaseId(), equalTo(DATABASE_ID));
        assertThat(output.getMessage(), equalTo("Database updated successfully"));

        // Verify title is present but description is NOT
        var requests = wireMockServer.findAll(patchRequestedFor(urlEqualTo("/v1/databases/" + DATABASE_ID)));
        assertThat(requests.size(), equalTo(1));
        var body = requests.getFirst().getBodyAsString();
        assertThat(body, containsString("\"title\""));
        assertThat(body, not(containsString("\"description\"")));
    }

    @Test
    void update_withDescriptionOnly_updatesDescriptionOnly() throws Exception {
        stubPatchDatabase(DATABASE_ID);

        var runContext = runContextFactory.of(Map.of());

        var update = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .databaseDescription(Property.ofValue("Only description"))
            .build();

        var output = update.run(runContext);

        assertThat(output.getDatabaseId(), equalTo(DATABASE_ID));
        assertThat(output.getMessage(), equalTo("Database updated successfully"));

        // Verify description is present but title is NOT
        var requests = wireMockServer.findAll(patchRequestedFor(urlEqualTo("/v1/databases/" + DATABASE_ID)));
        assertThat(requests.size(), equalTo(1));
        var body = requests.getFirst().getBodyAsString();
        assertThat(body, containsString("\"description\""));
        assertThat(body, not(containsString("\"title\"")));
    }

    @Test
    void update_withNeitherTitleNorDescription_throwsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var update = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .databaseId(Property.ofValue(DATABASE_ID))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> update.run(runContext));
        assertThat(exception.getMessage(), containsString("title"));
        assertThat(exception.getMessage(), containsString("description"));
    }

    // --- Helper ---

    private void stubPatchDatabase(String databaseId) throws Exception {
        var responseBody = new HashMap<String, Object>();
        responseBody.put("object", "database");
        responseBody.put("id", databaseId);
        responseBody.put("url", "https://www.notion.so/" + databaseId);

        wireMockServer.stubFor(
            patch(urlEqualTo("/v1/databases/" + databaseId))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(responseBody))
                        .withStatus(200)
                )
        );
    }
}
