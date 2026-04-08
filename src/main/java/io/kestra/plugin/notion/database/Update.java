package io.kestra.plugin.notion.database;

import java.util.HashMap;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a Notion database",
    description = """
        Updates the title and/or description of an existing Notion database entity.
        At least one of `title` or `databaseDescription` must be provided.
        Both properties are optional individually but the task requires at least one to be set."""
)
@Plugin(
    examples = {
        @Example(
            title = "Update a database title and description",
            full = true,
            code = """
                id: notion_update_database
                namespace: company.team

                tasks:
                  - id: update_db
                    type: io.kestra.plugin.notion.database.Update
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    title: "Updated Project Tracker"
                    databaseDescription: "Tracks all active projects managed by the data team."
                """
        ),
        @Example(
            title = "Update only the database title",
            full = true,
            code = """
                id: notion_rename_database
                namespace: company.team

                tasks:
                  - id: rename_db
                    type: io.kestra.plugin.notion.database.Update
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    title: "Q2 Sprint Board"
                """
        )
    }
)
public class Update extends AbstractDatabaseTask implements RunnableTask<Update.Output> {

    @Schema(
        title = "Title",
        description = "The new title to set on the Notion database. When provided, replaces the existing title."
    )
    @PluginProperty(group = "main")
    private Property<String> title;

    @Schema(
        title = "Description",
        description = """
            The new description to set on the Notion database (plain text).
            The plugin wraps the value in the rich text format expected by the Notion API."""
    )
    @PluginProperty(group = "main")
    private Property<String> databaseDescription;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rDatabaseId = renderDatabaseId(runContext);
        var rTitle = runContext.render(this.title).as(String.class).orElse(null);
        var rDescription = runContext.render(this.databaseDescription).as(String.class).orElse(null);

        if (rTitle == null && rDescription == null) {
            throw new IllegalArgumentException("At least one of 'title' or 'description' must be provided");
        }

        var body = new HashMap<String, Object>();

        if (rTitle != null) {
            body.put("title", List.of(Map.of("type", "text", "text", Map.of("content", rTitle))));
        }

        if (rDescription != null) {
            body.put("description", List.of(Map.of("type", "text", "text", Map.of("content", rDescription))));
        }

        var url = buildDatabaseUrl(rDatabaseId);
        logger.info("Updating Notion database {}", rDatabaseId);

        var requestBuilder = buildPatchRequest(runContext, url, body);
        var response = makeCall(runContext, requestBuilder, NotionResponse.class);

        logger.info("Successfully updated Notion database {}", response.getId());

        return Output.builder()
            .databaseId(response.getId())
            .url(response.getUrl())
            .message("Database updated successfully")
            .build();
    }

    @Override
    protected String getEndpoint() {
        return DATABASES_ENDPOINT;
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Database ID",
            description = "The unique identifier of the updated Notion database."
        )
        private String databaseId;

        @Schema(
            title = "URL",
            description = "The Notion URL of the updated database."
        )
        private String url;

        @Schema(
            title = "Message",
            description = "Result message indicating the outcome of the update operation."
        )
        private String message;
    }
}
