package io.kestra.plugin.notion.database;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionResponse;
import io.kestra.plugin.notion.utils.MarkdownConverter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Create a database item in Notion",
    description = """
        Creates a new page (row) inside a Notion database.
        Properties must follow the Notion property value schema for the target database."""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a database item with properties",
            full = true,
            code = """
                id: notion_create_database_item
                namespace: company.team

                tasks:
                  - id: create_item
                    type: io.kestra.plugin.notion.database.CreateItem
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    title: "New Task"
                    properties:
                      Status:
                        select:
                          name: "To Do"
                      Priority:
                        select:
                          name: "High"
                    content: |
                      ## Description
                      This task was created by Kestra.
                """
        )
    }
)
public class CreateItem extends AbstractDatabaseTask implements RunnableTask<CreateItem.Output> {

    @Schema(
        title = "Item title",
        description = "The title for the new database row. Mapped to the database title property."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> title;

    @Schema(
        title = "Properties",
        description = """
            A map of Notion typed property values to set on the new item.
            Keys are property names as defined in the database schema.
            See the [Notion property value reference](https://developers.notion.com/reference/property-value-object)."""
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> properties;

    @Schema(
        title = "Content",
        description = """
            Optional markdown content appended as paragraph blocks to the page body.
            Note that the Notion API enforces a limit of 2000 characters per [rich text content block](https://developers.notion.com/reference/request-limits).
            Content over 100 blocks is sent across multiple requests (Notion caps a request at 100 blocks); this is not atomic, so a retry may leave a partially-filled or duplicate item."""
    )
    @PluginProperty(group = "advanced")
    private Property<String> content;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rDatabaseId = renderDatabaseId(runContext);
        var rTitle = runContext.render(this.title).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("title is required")
        );

        // Build request body
        var body = new HashMap<String, Object>();

        // Parent: database
        body.put("parent", Map.of("database_id", rDatabaseId));

        // Properties — coerce string "true"/"false" to Boolean so that Notion checkbox
        // properties rendered from Pebble expressions are sent as JSON booleans, not strings.
        var rProperties = new HashMap<String, Object>(
            runContext.render(this.properties).asMap(String.class, Object.class).entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        e -> coerceBooleans(e.getValue())
                    )
                )
        );

        // Set title property (Notion databases use a "title" typed property, usually named "Name")
        rProperties.put(
            "Name", Map.of(
                "title", List.of(
                    Map.of(
                        "type", "text",
                        "text", Map.of("content", rTitle)
                    )
                )
            )
        );
        body.put("properties", rProperties);

        // Content blocks — first <=100 inline; any beyond that are appended after creation.
        var rContent = runContext.render(this.content).as(String.class).orElse(null);
        var blocks = rContent != null && !rContent.isBlank()
            ? MarkdownConverter.markdownToBlocks(rContent)
            : null;
        var firstBatch = firstBlockBatch(blocks);
        if (!firstBatch.isEmpty()) {
            body.put("children", firstBatch);
        }

        var url = getBaseUrl() + PAGES_ENDPOINT;
        logger.info("Creating item in Notion database {} with title: {}", rDatabaseId, rTitle);

        var requestBuilder = buildPostRequest(runContext, url, body);
        var response = makeCall(runContext, requestBuilder, NotionResponse.class);

        appendBlocksInBatches(runContext, response.getId(), blocks, firstBatch.size());

        logger.info("Created database item with ID: {}", response.getId());

        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
            .createdTime(response.getCreatedTime())
            .lastEditedTime(response.getLastEditedTime())
            .archived(response.getArchived())
            .properties(response.getProperties())
            .build();
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Page ID",
            description = "The unique identifier of the created database item."
        )
        private String pageId;

        @Schema(
            title = "URL",
            description = "The Notion URL of the created item."
        )
        private String url;

        @Schema(
            title = "Created time",
            description = "When the item was created."
        )
        private Instant createdTime;

        @Schema(
            title = "Last edited time",
            description = "When the item was last edited."
        )
        private Instant lastEditedTime;

        @Schema(
            title = "Archived",
            description = "Whether the item is archived."
        )
        private Boolean archived;

        @Schema(
            title = "Properties",
            description = "The properties of the created item as returned by the Notion API."
        )
        private Map<String, Object> properties;
    }
}
