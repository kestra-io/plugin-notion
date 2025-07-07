package io.kestra.plugin.notion;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.utils.MarkdownConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a Notion page",
    description = "Creates a new page in Notion with the specified title and markdown content. Optionally specify a parent page for hierarchical organization."
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
                    type: io.kestra.plugin.notion.CreatePage
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
                    type: io.kestra.plugin.notion.CreatePage
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
public class CreatePage extends NotionConnection implements RunnableTask<CreatePage.Output> {

    @Schema(
        title = "Page title",
        description = "The title of the new page"
    )
    @NotNull
    private Property<String> title;

    @Schema(
        title = "Page content",
        description = "The content of the page in markdown format"
    )
    private Property<String> content;

    @Schema(
        title = "Parent page ID",
        description = "Optional parent page ID for creating a child page. If not specified, the page will be created at the workspace root."
    )
    private Property<String> parentPageId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        // Render and validate inputs
        String renderedTitle = runContext.render(this.title).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("title is required")
        );

        String renderedContent = runContext.render(this.content).as(String.class).orElse("");

        String renderedParentPageId = null;
        if (this.parentPageId != null) {
            renderedParentPageId = runContext.render(this.parentPageId).as(String.class).orElse(null);
            if (renderedParentPageId != null && !renderedParentPageId.isEmpty()) {
                // Validate parent page ID format
                if (!renderedParentPageId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") && 
                    !renderedParentPageId.matches("^[0-9a-f]{32}$")) {
                    throw new IllegalArgumentException("parentPageId must be a valid Notion page ID (UUID format)");
                }
            }
        }

        // Build the request body
        Map<String, Object> requestBody = buildCreatePageRequest(renderedTitle, renderedContent, renderedParentPageId);

        // Make the API call
        String url = buildCreatePageURL();
        HttpRequest.HttpRequestBuilder requestBuilder = buildPostRequest(runContext, url, requestBody);

        logger.info("Creating Notion page with title: {}", renderedTitle);
        logger.debug("Request body: {}", mapper.writeValueAsString(requestBody));

        NotionResponse response = makeCall(runContext, requestBuilder, NotionResponse.class);

        // Store detailed response
        URI fileURI = store(runContext, List.of(Map.of(
            "response", response,
            "title", renderedTitle,
            "content", renderedContent,
            "parentPageId", renderedParentPageId
        )));

        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
            .title(renderedTitle)
            .content(renderedContent)
            .createdTime(response.getCreatedTime())
            .lastEditedTime(response.getLastEditedTime())
            .archived(response.getArchived())
            .properties(response.getProperties())
            .uri(fileURI)
            .success(true)
            .message("Page created successfully")
            .build();
    }

    /**
     * Builds the request body for creating a Notion page
     */
    private Map<String, Object> buildCreatePageRequest(String title, String content, String parentPageId) {
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
        titleProperty.put("title", List.of(Map.of(
            "type", "text",
            "text", Map.of("content", title)
        )));
        properties.put("title", titleProperty);
        requestBody.put("properties", properties);

        // Convert markdown content to Notion blocks
        if (content != null && !content.isEmpty()) {
            requestBody.put("children", MarkdownConverter.markdownToBlocks(content));
        }

        return requestBody;
    }



    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }

    /**
     * Output structure for CreatePage operation
     */
    @Getter
    @lombok.Builder
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
            title = "Storage URI",
            description = "URI of the stored file containing detailed page information"
        )
        private URI uri;

        @Schema(
            title = "Success",
            description = "Whether the operation was successful"
        )
        private Boolean success;

        @Schema(
            title = "Message",
            description = "Operation result message"
        )
        private String message;
    }
} 