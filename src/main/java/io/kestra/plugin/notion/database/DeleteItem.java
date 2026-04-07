package io.kestra.plugin.notion.database;

import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    title = "Archive a Notion database item",
    description = """
        Archives (soft-deletes) a row inside a Notion database by setting `archived=true` on the target page.
        Notion has no hard-delete API — archiving is the API equivalent and the page remains recoverable from Notion trash.
        Requires a valid page ID that identifies the row within the database."""
)
@Plugin(
    examples = {
        @Example(
            title = "Archive a database row by page ID",
            full = true,
            code = """
                id: notion_archive_database_item
                namespace: company.team

                inputs:
                  - id: page_id
                    type: STRING

                tasks:
                  - id: archive_row
                    type: io.kestra.plugin.notion.database.DeleteItem
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    pageId: "{{ inputs.page_id }}"
                """
        ),
        @Example(
            title = "Archive a specific database row",
            full = true,
            code = """
                id: notion_archive_specific_row
                namespace: company.team

                tasks:
                  - id: archive_row
                    type: io.kestra.plugin.notion.database.DeleteItem
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    databaseId: "12345678-1234-1234-1234-123456789abc"
                    pageId: "abcdef12-3456-7890-abcd-ef1234567890"
                """
        )
    }
)
public class DeleteItem extends AbstractDatabaseTask implements RunnableTask<DeleteItem.Output> {

    @Schema(
        title = "Page ID",
        description = """
            The unique identifier of the database item (row) to archive.
            Both UUID and 32-character hex formats are accepted."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> pageId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rPageId = normalizeId(
            runContext.render(this.pageId).as(String.class).orElseThrow(
                () -> new IllegalArgumentException("pageId is required")
            )
        );

        logger.info("Checking Notion database item with page ID: {}", rPageId);

        var pageUrl = buildPageURL(rPageId);
        var getRequestBuilder = buildGetRequest(runContext, pageUrl);
        var pageResponse = makeCall(runContext, getRequestBuilder, NotionResponse.class);

        if (Boolean.TRUE.equals(pageResponse.getArchived())) {
            logger.warn("Database item {} is already archived", rPageId);
            return Output.builder()
                .pageId(rPageId)
                .url(pageResponse.getUrl())
                .message("Page is already archived")
                .build();
        }

        logger.info("Archiving Notion database item {}", rPageId);

        var body = Map.<String, Object>of("archived", true);
        var patchRequestBuilder = buildPatchRequest(runContext, pageUrl, body);
        var archiveResponse = makeCall(runContext, patchRequestBuilder, NotionResponse.class);

        logger.info("Successfully archived database item {}", rPageId);

        return Output.builder()
            .pageId(archiveResponse.getId())
            .url(archiveResponse.getUrl())
            .message("Page archived successfully")
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
            title = "Page ID",
            description = "The unique identifier of the archived database item."
        )
        private String pageId;

        @Schema(
            title = "URL",
            description = "The Notion URL of the archived item."
        )
        private String url;

        @Schema(
            title = "Message",
            description = "Result message indicating the outcome of the archive operation."
        )
        private String message;
    }
}
