package io.kestra.plugin.notion.page;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.notion.NotionConnection;
import io.kestra.plugin.notion.NotionResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "React to Notion page updates.",
    description = """
        Polls the Notion Search API at the configured interval.
        On each evaluation it collects all pages whose `last_edited_time` is strictly greater than
        the watermark (taken from `TriggerContext.date`, which is the last evaluation timestamp).
        When at least one updated page is found, a flow execution is fired; otherwise the trigger
        is silent and waits for the next interval.

        Optional `query` narrows the search server-side using Notion full-text search.
        Optional `parentPageId` retains only pages that are direct children of that parent page
        (client-side filter, because the Notion Search API does not expose a server-side parent filter)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger on any Notion page update every 5 minutes",
            full = true,
            code = """
                id: notion_page_updated
                namespace: company.team

                tasks:
                  - id: log_updated_pages
                    type: io.kestra.plugin.core.log.Log
                    message: "Updated pages: {{ trigger.updatedPages }}"

                triggers:
                  - id: watch_pages
                    type: io.kestra.plugin.notion.page.UpdateTrigger
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    interval: PT5M
                """
        ),
        @Example(
            title = "Trigger only for pages matching a keyword search",
            full = true,
            code = """
                id: notion_keyword_page_updated
                namespace: company.team

                tasks:
                  - id: process_pages
                    type: io.kestra.plugin.core.log.Log
                    message: "Sprint pages updated: {{ trigger.updatedPages }}"

                triggers:
                  - id: watch_sprint_pages
                    type: io.kestra.plugin.notion.page.UpdateTrigger
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    query: "Sprint"
                    interval: PT10M
                """
        ),
        @Example(
            title = "Trigger only for pages under a specific parent page",
            full = true,
            code = """
                id: notion_child_page_updated
                namespace: company.team

                tasks:
                  - id: handle_update
                    type: io.kestra.plugin.core.log.Log
                    message: "Child page updated: {{ trigger.updatedPages }}"

                triggers:
                  - id: watch_child_pages
                    type: io.kestra.plugin.notion.page.UpdateTrigger
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    parentPageId: "12345678-1234-1234-1234-123456789abc"
                    interval: PT15M
                """
        )
    }
)
public class UpdateTrigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<UpdateTrigger.Output> {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);
    private static final int PAGE_SIZE = 100;

    @Builder.Default
    @Schema(
        title = "Interval between polls.",
        description = "ISO 8601 duration. A minimum of PT30S is recommended to avoid overloading the Notion API."
    )
    @PluginProperty(group = "execution")
    @NotNull
    private Duration interval = Duration.ofMinutes(5);

    @Schema(
        title = "Notion API token.",
        description = "The Notion internal integration token used to authenticate API requests."
    )
    @PluginProperty(group = "connection", secret = true)
    @NotNull
    private Property<String> apiToken;

    @Schema(title = "HTTP client configuration.")
    @PluginProperty(group = "connection")
    private HttpConfiguration options;

    @Schema(
        title = "Full-text search query.",
        description = "Optional text passed as the `query` field of the Notion Search API request. Narrows results server-side to pages whose title or content matches the search terms."
    )
    @PluginProperty(group = "processing")
    private Property<String> query;

    @Schema(
        title = "Parent page ID filter.",
        description = """
            Optional Notion page UUID. When set, only pages whose direct parent is this page are reported.
            This is a client-side filter applied after the API response because the Notion Search API
            does not expose a server-side parent filter."""
    )
    @PluginProperty(group = "processing")
    private Property<String> parentPageId;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rApiToken = runContext.render(apiToken).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
        var rQuery = runContext.render(query).as(String.class).orElse(null);
        var rParentPageId = runContext.render(parentPageId).as(String.class).orElse(null);

        // The watermark is the last trigger evaluation time. Pages edited strictly after this are new.
        Instant watermark = context.getDate().toInstant();

        logger.debug("Polling Notion for pages updated after {}", watermark);

        var updatedPages = collectUpdatedPages(runContext, rApiToken, rQuery, rParentPageId, watermark);

        if (updatedPages.isEmpty()) {
            return Optional.empty();
        }

        logger.info("Found {} updated Notion page(s) since {}", updatedPages.size(), watermark);

        var output = Output.builder().updatedPages(updatedPages).build();
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private List<PageInfo> collectUpdatedPages(
        RunContext runContext,
        String rApiToken,
        String rQuery,
        String rParentPageId,
        Instant watermark
    ) throws Exception {
        var result = new ArrayList<PageInfo>();
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            var body = buildSearchBody(rQuery, cursor);
            var response = callSearchApi(runContext, rApiToken, body);

            var results = response.getChildren();
            if (results == null || results.isEmpty()) {
                break;
            }

            for (var item : results) {
                var lastEditedRaw = (String) item.get("last_edited_time");
                if (lastEditedRaw == null) {
                    continue;
                }
                var lastEditedTime = Instant.parse(lastEditedRaw);

                // Results are sorted descending by last_edited_time; early-exit once we reach older pages.
                if (!lastEditedTime.isAfter(watermark)) {
                    return result;
                }

                if (rParentPageId != null && !matchesParent(item, rParentPageId)) {
                    continue;
                }

                result.add(toPageInfo(item, lastEditedTime));
            }

            hasMore = Boolean.TRUE.equals(response.getHasMore());
            cursor = response.getNextCursor();
        }

        return result;
    }

    private Map<String, Object> buildSearchBody(String rQuery, String cursor) {
        var body = new HashMap<String, Object>();
        body.put("filter", Map.of("property", "object", "value", "page"));
        body.put("sort", Map.of("direction", "descending", "timestamp", "last_edited_time"));
        body.put("page_size", PAGE_SIZE);
        if (rQuery != null) {
            body.put("query", rQuery);
        }
        if (cursor != null) {
            body.put("start_cursor", cursor);
        }
        return body;
    }

    private NotionResponse callSearchApi(RunContext runContext, String rApiToken, Map<String, Object> body) throws Exception {
        var jsonBody = MAPPER.writeValueAsString(body);
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(NotionConnection.getBaseUrl() + NotionConnection.SEARCH_ENDPOINT))
            .method("POST")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build())
            .addHeader("Authorization", "Bearer " + rApiToken)
            .addHeader("Notion-Version", NotionConnection.NOTION_API_VERSION)
            .addHeader("Content-Type", NotionConnection.JSON_CONTENT_TYPE);

        try (var client = new HttpClient(runContext, options)) {
            return client.request(requestBuilder.build(), NotionResponse.class).getBody();
        }
    }

    private boolean matchesParent(Map<String, Object> page, String expectedParentId) {
        var parent = page.get("parent");
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return false;
        }
        return "page_id".equals(parentMap.get("type")) && expectedParentId.equals(parentMap.get("page_id"));
    }

    private PageInfo toPageInfo(Map<String, Object> page, Instant lastEditedTime) {
        var pageId = (String) page.get("id");
        var url = (String) page.get("url");
        var title = extractTitle(page);
        return PageInfo.builder()
            .pageId(pageId)
            .url(url)
            .title(title)
            .lastEditedTime(lastEditedTime)
            .build();
    }

    @SuppressWarnings("unchecked")
    private String extractTitle(Map<String, Object> page) {
        var properties = page.get("properties");
        if (!(properties instanceof Map<?, ?> propsMap)) {
            return null;
        }
        // Try "title" then "Name" — Notion uses "title" for pages, "Name" for database items.
        for (var key : new String[]{"title", "Name"}) {
            var prop = propsMap.get(key);
            if (!(prop instanceof Map<?, ?> propMap)) {
                continue;
            }
            var titleArray = propMap.get("title");
            if (!(titleArray instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            var first = list.getFirst();
            if (first instanceof Map<?, ?> entry) {
                var plainText = entry.get("plain_text");
                if (plainText != null) {
                    return plainText.toString();
                }
            }
        }
        return null;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {

        @Schema(
            title = "Page ID",
            description = "The unique identifier of the updated Notion page."
        )
        private String pageId;

        @Schema(
            title = "Page URL",
            description = "The URL of the updated page in Notion."
        )
        private String url;

        @Schema(
            title = "Page title",
            description = "The title of the updated page, or null if the title could not be extracted."
        )
        private String title;

        @Schema(
            title = "Last edited time",
            description = "The timestamp when the page was last edited."
        )
        private Instant lastEditedTime;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Updated pages",
            description = "The list of Notion pages that were updated since the last trigger evaluation."
        )
        private List<PageInfo> updatedPages;
    }
}
