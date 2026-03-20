package io.kestra.plugin.notion.database;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionResponse;

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
    title = "Update a database item in Notion",
    description = """
        Updates properties of an existing Notion database item (page).
        Can also soft-delete the item by setting `archived` to true."""
)
@Plugin(
    examples = {
        @Example(
            title = "Update a database item status",
            full = true,
            code = """
                id: notion_update_database_item
                namespace: company.team

                tasks:
                  - id: update_item
                    type: io.kestra.plugin.notion.database.UpdateItem
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    pageId: "abcdef12-3456-7890-abcd-ef1234567890"
                    properties:
                      Status:
                        select:
                          name: "Done"
                """
        ),
        @Example(
            title = "Soft-delete a database item",
            full = true,
            code = """
                id: notion_archive_database_item
                namespace: company.team

                tasks:
                  - id: archive_item
                    type: io.kestra.plugin.notion.database.UpdateItem
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    pageId: "abcdef12-3456-7890-abcd-ef1234567890"
                    archived: true
                """
        )
    }
)
public class UpdateItem extends AbstractDatabaseTask implements RunnableTask<UpdateItem.Output> {

    @Schema(
        title = "Page ID",
        description = "The ID of the database item (page) to update; both UUID and 32-character hex formats are accepted."
    )
    @NotNull
    private Property<String> pageId;

    @Schema(
        title = "Properties",
        description = """
            A partial map of Notion typed property values to update.
            Only the provided properties will be changed; others remain untouched.
            See the [Notion property value reference](https://developers.notion.com/reference/property-value-object)."""
    )
    private Property<Map<String, Object>> properties;

    @Schema(
        title = "Archived",
        description = "Set to `true` to soft-delete (archive) the item, or `false` to restore it."
    )
    private Property<Boolean> archived;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rPageId = normalizeId(
            runContext.render(this.pageId).as(String.class).orElseThrow(
                () -> new IllegalArgumentException("pageId is required")
            )
        );

        var body = new HashMap<String, Object>();

        var rProperties = runContext.render(this.properties).asMap(String.class, Object.class);
        if (!rProperties.isEmpty()) {
            body.put("properties", rProperties);
        }

        var rArchived = runContext.render(this.archived).as(Boolean.class).orElse(null);
        if (rArchived != null) {
            body.put("archived", rArchived);
        }

        if (body.isEmpty()) {
            throw new IllegalArgumentException("At least one of 'properties' or 'archived' must be provided");
        }

        var url = getBaseUrl() + PAGES_ENDPOINT + "/" + rPageId;
        logger.info("Updating Notion database item {}", rPageId);

        var requestBuilder = buildPatchRequest(runContext, url, body);
        var response = makeCall(runContext, requestBuilder, NotionResponse.class);

        logger.info("Updated database item {}", response.getId());

        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
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
            description = "The unique identifier of the updated database item."
        )
        private String pageId;

        @Schema(
            title = "URL",
            description = "The Notion URL of the updated item."
        )
        private String url;

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
            description = "The properties of the updated item as returned by the Notion API."
        )
        private Map<String, Object> properties;
    }
}
