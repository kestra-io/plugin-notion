package io.kestra.plugin.notion.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Query a Notion database",
    description = """
        Queries a Notion database with optional filter and sort criteria.
        Returns the matching rows as raw Notion page objects."""
)
@Plugin(
    examples = {
        @Example(
            title = "Query a database with a filter",
            full = true,
            code = """
                id: notion_query_database
                namespace: company.team

                tasks:
                  - id: query_db
                    type: io.kestra.plugin.notion.database.Query
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    filter:
                      property: "Status"
                      select:
                        equals: "Done"
                    sorts:
                      - property: "Created"
                        direction: "descending"
                    pageSize: 50
                """
        )
    }
)
public class Query extends AbstractDatabaseTask implements RunnableTask<Query.Output> {

    @Schema(
        title = "Filter",
        description = """
            A Notion filter object applied to the query.
            See the [Notion API filter reference](https://developers.notion.com/reference/post-database-query-filter) for the full schema."""
    )
    private Property<Map<String, Object>> filter;

    @Schema(
        title = "Sorts",
        description = """
            A list of sort objects controlling result ordering.
            Each entry must have a `property` and a `direction` (`ascending` or `descending`)."""
    )
    private Property<List<Map<String, Object>>> sorts;

    @Builder.Default
    @Schema(
        title = "Page size",
        description = "Maximum number of results per page (1-100). Defaults to 100."
    )
    private Property<Integer> pageSize = Property.ofValue(100);

    @Schema(
        title = "Start cursor",
        description = "Pagination cursor returned by a previous query to fetch the next page of results."
    )
    private Property<String> startCursor;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rDatabaseId = renderDatabaseId(runContext);

        // Build request body
        var body = new HashMap<String, Object>();

        var rFilter = runContext.render(this.filter).asMap(String.class, Object.class);
        if (!rFilter.isEmpty()) {
            body.put("filter", rFilter);
        }

        var rSorts = runContext.render(this.sorts).asList(Map.class);
        if (!rSorts.isEmpty()) {
            body.put("sorts", rSorts);
        }

        var rPageSize = runContext.render(this.pageSize).as(Integer.class).orElse(100);
        body.put("page_size", rPageSize);

        var rStartCursor = runContext.render(this.startCursor).as(String.class).orElse(null);
        if (rStartCursor != null && !rStartCursor.isBlank()) {
            body.put("start_cursor", rStartCursor);
        }

        var url = getBaseUrl() + DATABASES_ENDPOINT + "/" + rDatabaseId + "/query";
        logger.info("Querying Notion database {}", rDatabaseId);
        logger.debug("Query body: {}", mapper.writeValueAsString(body));

        var requestBuilder = buildPostRequest(runContext, url, body);

        @SuppressWarnings("unchecked")
        var response = makeCall(runContext, requestBuilder, Map.class);

        @SuppressWarnings("unchecked")
        var results = (List<Map<String, Object>>) response.getOrDefault("results", List.of());
        var hasMore = Boolean.TRUE.equals(response.get("has_more"));
        var nextCursor = response.get("next_cursor") != null ? response.get("next_cursor").toString() : null;

        logger.info("Query returned {} rows (hasMore={})", results.size(), hasMore);

        return Output.builder()
            .rows(results)
            .total(results.size())
            .hasMore(hasMore)
            .nextCursor(nextCursor)
            .build();
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Rows",
            description = "List of raw Notion page objects matching the query."
        )
        private List<Map<String, Object>> rows;

        @Schema(
            title = "Total",
            description = "Number of rows returned in this page of results."
        )
        private Integer total;

        @Schema(
            title = "Has more",
            description = "Whether more results are available beyond this page."
        )
        private Boolean hasMore;

        @Schema(
            title = "Next cursor",
            description = "Cursor to pass as `startCursor` to fetch the next page; null when there are no more results."
        )
        private String nextCursor;
    }
}
