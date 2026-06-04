package io.kestra.plugin.notion.page;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class UpdateTriggerTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    @Test
    void builderPattern_setsPropertiesCorrectly() {
        var trigger = UpdateTrigger.builder()
            .id("test-trigger")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .interval(Duration.ofMinutes(5))
            .query(Property.ofValue("Meeting"))
            .parentPageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(trigger, notNullValue());
        assertThat(trigger.getApiToken(), notNullValue());
        assertThat(trigger.getInterval(), equalTo(Duration.ofMinutes(5)));
        assertThat(trigger.getQuery(), notNullValue());
        assertThat(trigger.getParentPageId(), notNullValue());
    }

    @Test
    void builderPattern_minimalFields_works() {
        var trigger = UpdateTrigger.builder()
            .id("minimal")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .build();

        assertThat(trigger, notNullValue());
        assertThat(trigger.getQuery(), nullValue());
        assertThat(trigger.getParentPageId(), nullValue());
        assertThat(trigger.getInterval(), equalTo(Duration.ofMinutes(5)));
    }

    @Test
    void propertyTypes_areCorrect() {
        var trigger = UpdateTrigger.builder()
            .id("types")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("token"))
            .query(Property.ofValue("q"))
            .parentPageId(Property.ofValue("pid"))
            .build();

        assertThat(trigger.getApiToken(), instanceOf(Property.class));
        assertThat(trigger.getQuery(), instanceOf(Property.class));
        assertThat(trigger.getParentPageId(), instanceOf(Property.class));
    }

    @Test
    void inheritance_extendsAbstractTrigger() {
        var trigger = UpdateTrigger.builder()
            .id("inherit")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("token"))
            .build();

        assertThat(trigger, instanceOf(io.kestra.core.models.triggers.AbstractTrigger.class));
        assertThat(trigger, instanceOf(io.kestra.core.models.triggers.PollingTriggerInterface.class));
        assertThat(trigger, instanceOf(io.kestra.core.models.triggers.TriggerOutput.class));
    }

    @Test
    void singlePage_newerThanWatermark_firesExecution() throws Exception {
        var trigger = UpdateTrigger.builder()
            .id("t1")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .interval(Duration.ofMinutes(5))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            var recentTime = Instant.now().toString();
            var responseBody = Map.of(
                "object", "list",
                "results", List.of(pageJson("page-1", "https://notion.so/page-1", "My Page", recentTime)),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(responseBody))
                        .withStatus(200))
            );

            // Watermark 10 minutes in the past so the "recent" page qualifies.
            var context = triggerContext(ZonedDateTime.now().minusMinutes(10));

            Optional<Execution> result = trigger.evaluate(conditionContext, context);

            assertThat(result.isPresent(), is(true));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    void allPagesOlderThanWatermark_returnsEmpty() throws Exception {
        var trigger = UpdateTrigger.builder()
            .id("t2")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            // Page edited 2 hours ago; watermark is 1 hour ago → page is older.
            var oldTime = Instant.now().minusSeconds(7200).toString();
            var responseBody = Map.of(
                "object", "list",
                "results", List.of(pageJson("page-old", "https://notion.so/page-old", "Old Page", oldTime)),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(responseBody))
                        .withStatus(200))
            );

            var context = triggerContext(ZonedDateTime.now().minusHours(1));

            Optional<Execution> result = trigger.evaluate(conditionContext, context);

            assertThat(result.isPresent(), is(false));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    void pagination_collectsAllQualifyingPages() throws Exception {
        var trigger = UpdateTrigger.builder()
            .id("t3")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            var recentTime = Instant.now().toString();

            var firstPage = Map.of(
                "object", "list",
                "results", List.of(pageJson("page-1", "https://notion.so/page-1", "Page 1", recentTime)),
                "has_more", true,
                "next_cursor", "cursor-abc"
            );
            var secondPage = Map.of(
                "object", "list",
                "results", List.of(pageJson("page-2", "https://notion.so/page-2", "Page 2", recentTime)),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .withRequestBody(containing("\"page_size\":" + 100))
                    .inScenario("pagination")
                    .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(firstPage))
                        .withStatus(200))
                    .willSetStateTo("second")
            );
            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .withRequestBody(containing("cursor-abc"))
                    .inScenario("pagination")
                    .whenScenarioStateIs("second")
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(secondPage))
                        .withStatus(200))
            );

            var context = triggerContext(ZonedDateTime.now().minusHours(1));

            Optional<Execution> result = trigger.evaluate(conditionContext, context);

            assertThat(result.isPresent(), is(true));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    void parentPageIdFilter_onlyMatchingParentSurvives() throws Exception {
        var targetParent = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        var otherParent = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

        var trigger = UpdateTrigger.builder()
            .id("t4")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .parentPageId(Property.ofValue(targetParent))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            var recentTime = Instant.now().toString();
            var matchingPage = pageJsonWithParent("match-page", "https://notion.so/match", "Match", recentTime, targetParent);
            var nonMatchingPage = pageJsonWithParent("skip-page", "https://notion.so/skip", "Skip", recentTime, otherParent);

            var responseBody = Map.of(
                "object", "list",
                "results", List.of(matchingPage, nonMatchingPage),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(responseBody))
                        .withStatus(200))
            );

            var context = triggerContext(ZonedDateTime.now().minusHours(1));

            Optional<Execution> result = trigger.evaluate(conditionContext, context);

            assertThat(result.isPresent(), is(true));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    void parentPageIdFilter_noMatch_returnsEmpty() throws Exception {
        var targetParent = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        var otherParent = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

        var trigger = UpdateTrigger.builder()
            .id("t5")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .parentPageId(Property.ofValue(targetParent))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            var recentTime = Instant.now().toString();
            var nonMatchingPage = pageJsonWithParent("skip-page", "https://notion.so/skip", "Skip", recentTime, otherParent);

            var responseBody = Map.of(
                "object", "list",
                "results", List.of(nonMatchingPage),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(responseBody))
                        .withStatus(200))
            );

            var context = triggerContext(ZonedDateTime.now().minusHours(1));

            Optional<Execution> result = trigger.evaluate(conditionContext, context);

            assertThat(result.isPresent(), is(false));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    void query_sentInPostBody_whenConfigured() throws Exception {
        var trigger = UpdateTrigger.builder()
            .id("t6")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue("test-token"))
            .query(Property.ofValue("Sprint"))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        var previousUrl = System.getProperty("notion.api.base.url");
        System.setProperty("notion.api.base.url", wireMock.baseUrl());

        try {
            configureFor("localhost", wireMock.port());

            var responseBody = Map.of(
                "object", "list",
                "results", List.of(),
                "has_more", false
            );

            wireMock.stubFor(
                post(urlEqualTo("/v1/search"))
                    .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsString(responseBody))
                        .withStatus(200))
            );

            var context = triggerContext(ZonedDateTime.now().minusMinutes(30));

            trigger.evaluate(conditionContext, context);

            wireMock.verify(postRequestedFor(urlEqualTo("/v1/search"))
                .withRequestBody(containing("\"query\":\"Sprint\"")));
        } finally {
            wireMock.stop();
            restoreBaseUrl(previousUrl);
        }
    }

    @Test
    @Disabled("Requires NOTION_API_TOKEN set in the environment — run manually")
    void integration_realApi_firesTriggerOnUpdatedPages() throws Exception {
        var trigger = UpdateTrigger.builder()
            .id("integration")
            .type(UpdateTrigger.class.getName())
            .apiToken(Property.ofValue(System.getenv("NOTION_API_TOKEN")))
            .interval(Duration.ofMinutes(1))
            .build();

        var entry = TestsUtils.mockTrigger(runContextFactory, trigger);
        var conditionContext = entry.getKey();

        var context = triggerContext(ZonedDateTime.now().minusDays(7));

        var result = trigger.evaluate(conditionContext, context);
        assertThat(result, notNullValue());
    }

    // --- helpers ---

    private static TriggerContext triggerContext(ZonedDateTime date) {
        return TriggerContext.builder()
            .tenantId(null)
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("test-trigger")
            .date(date)
            .build();
    }

    private static Map<String, Object> pageJson(String id, String url, String title, String lastEditedTime) {
        return Map.of(
            "object", "page",
            "id", id,
            "url", url,
            "last_edited_time", lastEditedTime,
            "properties", Map.of(
                "title", Map.of(
                    "title", List.of(Map.of("plain_text", title))
                )
            )
        );
    }

    private static Map<String, Object> pageJsonWithParent(
        String id, String url, String title, String lastEditedTime, String parentPageId
    ) {
        return Map.of(
            "object", "page",
            "id", id,
            "url", url,
            "last_edited_time", lastEditedTime,
            "parent", Map.of("type", "page_id", "page_id", parentPageId),
            "properties", Map.of(
                "title", Map.of(
                    "title", List.of(Map.of("plain_text", title))
                )
            )
        );
    }

    private static void restoreBaseUrl(String previousUrl) {
        if (previousUrl == null) {
            System.clearProperty("notion.api.base.url");
        } else {
            System.setProperty("notion.api.base.url", previousUrl);
        }
    }
}
