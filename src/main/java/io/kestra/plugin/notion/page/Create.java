package io.kestra.plugin.notion.page;

import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionConnection;
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
    title = "Create a Notion page",
    description = "Creates a Notion page with a title and optional markdown content. Defaults to the workspace root when parentPageId is empty; markdown is rendered to Notion blocks."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a simple page",
            full = true,
            code = """
                id: notion_create_page
                namespace: company.team

                tasks:
                  - id: create_page
                    type: io.kestra.plugin.notion.page.Create
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    title: "Meeting Notes"
                    content: |
                      # Meeting Notes - {{ now() }}

                      ## Attendees
                      - John Doe
                      - Jane Smith

                      ## Action Items
                      - [ ] Review proposal
                      - [ ] Schedule follow-up
                """
        ),
        @Example(
            title = "Create a page with parent",
            full = true,
            code = """
                id: notion_create_child_page
                namespace: company.team

                tasks:
                  - id: create_child_page
                    type: io.kestra.plugin.notion.page.Create
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    title: "Sprint Planning"
                    parentPageId: "12345678-1234-1234-1234-123456789abc"
                    content: |
                      # Sprint Planning

                      **Sprint Goal:** Improve user authentication

                      ## Stories
                      - Implement OAuth2
                      - Add password reset
                """
        )
    }
)
public class Create extends NotionConnection implements RunnableTask<Create.Output> {

    @Schema(
        title = "Page title",
        description = "Required page title text"
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> title;

    @Schema(
        title = "Page content",
        description = """
            Optional markdown content converted to Notion blocks; empty leaves the page body blank.
            Note that the Notion API enforces a limit of 2000 characters per [rich text content block](https://developers.notion.com/reference/request-limits).
            Content over 100 blocks is sent across multiple requests (Notion caps a request at 100 blocks); this is not atomic, so a retry may leave a partially-filled or duplicate page."""
    )
    @PluginProperty(group = "advanced")
    private Property<String> content;

    @Schema(
        title = "Parent page ID",
        description = "Optional parent page ID (UUID). When absent, the page is created at the workspace root"
    )
    @PluginProperty(group = "advanced")
    private Property<String> parentPageId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        // Render and validate inputs
        String renderedTitle = runContext.render(this.title).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("title is required")
        );

        String renderedContent = runContext.render(this.content).as(String.class).orElse(null);
        var blocks = renderedContent != null && !renderedContent.isEmpty()
            ? MarkdownConverter.markdownToBlocks(renderedContent)
            : null;
        // First <=100 blocks go inline with the create; the remainder is appended afterwards.
        var firstBatch = firstBlockBatch(blocks);

        String renderedParentPageId = runContext.render(this.parentPageId).as(String.class).orElse(null);
        if (renderedParentPageId != null && !renderedParentPageId.isEmpty()) {
            // Validate parent page ID format
            if (
                !renderedParentPageId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") &&
                    !renderedParentPageId.matches("^[0-9a-f]{32}$")
            ) {
                throw new IllegalArgumentException("parentPageId must be a valid Notion page ID (UUID format)");
            }
        }

        // Build the request body
        Map<String, Object> requestBody = buildCreatePageRequest(renderedTitle, firstBatch, renderedParentPageId);

        // Make the API call
        String url = buildCreatePageURL();
        HttpRequest.HttpRequestBuilder requestBuilder = buildPostRequest(runContext, url, requestBody);

        logger.info("Creating Notion page with title: {}", renderedTitle);
        logger.debug("Request body: {}", mapper.writeValueAsString(requestBody));

        NotionResponse response = makeCall(runContext, requestBuilder, NotionResponse.class);

        // Append the blocks that didn't fit in the create request (Notion caps a request at 100 blocks).
        appendBlocksInBatches(runContext, response.getId(), blocks, firstBatch.size());

        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
            .title(renderedTitle)
            .content(renderedContent)
            .createdTime(response.getCreatedTime())
            .lastEditedTime(response.getLastEditedTime())
            .archived(response.getArchived())
            .properties(response.getProperties())
            .message("Page created successfully")
            .build();
    }

    /**
     * Builds the request body for creating a Notion page
     */
    private Map<String, Object> buildCreatePageRequest(String title, ArrayNode firstBatch, String parentPageId) {
        Map<String, Object> requestBody = new HashMap<>();

        // Set parent (either a page or workspace)
        Map<String, Object> parent = new HashMap<>();
        if (parentPageId != null && !parentPageId.isEmpty()) {
            parent.put("type", "page_id");
            parent.put("page_id", parentPageId);
        } else {
            parent.put("type", "workspace");
            parent.put("workspace", true);
        }
        requestBody.put("parent", parent);

        // Set page properties (title)
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> titleProperty = new HashMap<>();
        titleProperty.put(
            "title", List.of(
                Map.of(
                    "type", "text",
                    "text", Map.of("content", title)
                )
            )
        );
        properties.put("title", titleProperty);
        requestBody.put("properties", properties);

        // First batch of content blocks (<=100); any beyond that are appended after the page is created.
        if (firstBatch != null && !firstBatch.isEmpty()) {
            requestBody.put("children", firstBatch);
        }

        return requestBody;
    }

    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }

    /**
     * Output structure for Create operation
     */
    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Page ID",
            description = "The unique identifier of the created page"
        )
        private String pageId;

        @Schema(
            title = "Page URL",
            description = "The URL of the created page in Notion"
        )
        private String url;

        @Schema(
            title = "Page Title",
            description = "The title of the created page"
        )
        private String title;

        @Schema(
            title = "Content",
            description = "The page content in markdown format"
        )
        private String content;

        @Schema(
            title = "Created Time",
            description = "When the page was created"
        )
        private Instant createdTime;

        @Schema(
            title = "Last Edited Time",
            description = "When the page was last edited"
        )
        private Instant lastEditedTime;

        @Schema(
            title = "Archived",
            description = "Whether the page is archived"
        )
        private Boolean archived;

        @Schema(
            title = "Properties",
            description = "Page properties returned by Notion"
        )
        private Map<String, Object> properties;

        @Schema(
            title = "Message",
            description = "Operation result message"
        )
        private String message;
    }
}
