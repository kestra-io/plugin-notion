package io.kestra.plugin.notion.page;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionResponse;
import io.kestra.plugin.notion.utils.MarkdownConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a Notion page",
    description = "Updates a Notion page by appending markdown blocks to the bottom and optionally replacing the title. Fails if neither title nor content is provided."
)
@Plugin(
    examples = {
        @Example(
            title = "Update page content only",
            full = true,
            code = """
                id: notion_update_page_content
                namespace: company.team

                tasks:
                  - id: update_page
                    type: io.kestra.plugin.notion.page.Update
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "12345678-1234-1234-1234-123456789abc"
                    content: |
                      # Updated Content
                      
                      This page has been updated with new content.
                      
                      ## New Section
                      - Updated item 1
                      - Updated item 2
                """
        ),
        @Example(
            title = "Update page title and content",
            full = true,
            code = """
                id: notion_update_page_full
                namespace: company.team

                tasks:
                  - id: update_page_full
                    type: io.kestra.plugin.notion.page.Update
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "12345678-1234-1234-1234-123456789abc"
                    title: "Updated Meeting Notes"
                    content: |
                      # Meeting Summary - Updated
                      
                      **Date:** {{ now() }}
                      
                      ## Key Decisions
                      - Decision 1
                      - Decision 2
                      
                      ## Next Steps
                      - [ ] Action item 1
                      - [ ] Action item 2
                """
        )
    }
)
public class Update extends AbstractTask {

    @Schema(
        title = "New page title",
        description = "Optional new title for the page. If not provided, the existing title will be kept."
    )
    private Property<String> title;

    @Schema(
        title = "New page content",
        description = "The new content for the page in markdown format. This will be appended to the bottom of the page."
    )
    private Property<String> content;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        try {
            // Validate and render page ID
            String renderedPageId = validateAndRenderPageId(runContext);
            
            // Render optional inputs
            String renderedTitle = null;
            if (this.title != null) {
                renderedTitle = runContext.render(this.title).as(String.class).orElse(null);
            }
            
            String renderedContent = runContext.render(this.content).as(String.class).orElse("");
            
            // Validate that at least one field is provided for update
            boolean hasTitle = renderedTitle != null && !renderedTitle.trim().isEmpty();
            boolean hasContent = renderedContent != null && !renderedContent.trim().isEmpty();
            
            if (!hasTitle && !hasContent) {
                throw new IllegalArgumentException("At least one of 'title' or 'content' must be provided for update operation");
            }
            
            logger.info("Updating Notion page with ID: {}", renderedPageId);
            
            // Step 1: Update page properties (title) if provided
            NotionResponse pageResponse = null;
            if (renderedTitle != null && !renderedTitle.trim().isEmpty()) {
                pageResponse = updatePageTitle(runContext, renderedPageId, renderedTitle);
                logger.info("Updated page title to: {}", renderedTitle);
            } else {
                // Get current page info if not updating title
                String pageUrl = buildPageURL(renderedPageId);
                HttpRequest.HttpRequestBuilder pageRequestBuilder = buildGetRequest(runContext, pageUrl);
                pageResponse = makeCall(runContext, pageRequestBuilder, NotionResponse.class);
            }
            
            // Step 2: Append page content to the bottom
            if (renderedContent != null && !renderedContent.trim().isEmpty()) {
                appendPageContent(runContext, renderedPageId, renderedContent);
                logger.info("Appended new markdown content to the page");
            } else {
                logger.info("No content provided, skipping content update");
            }
            
            // Get updated page info
            String pageUrl = buildPageURL(renderedPageId);
            HttpRequest.HttpRequestBuilder pageRequestBuilder = buildGetRequest(runContext, pageUrl);
            NotionResponse finalPageResponse = makeCall(runContext, pageRequestBuilder, NotionResponse.class);
            
            // Store detailed information
            return buildOutput(finalPageResponse, renderedContent, "Page updated successfully");
                
        } catch (Exception e) {
            logger.error("Error updating Notion page: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Updates the page title by modifying page properties
     */
    private NotionResponse updatePageTitle(RunContext runContext, String pageId, String newTitle) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        
        // Set page properties (title)
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> titleProperty = new HashMap<>();
        titleProperty.put("title", List.of(Map.of(
            "type", "text",
            "text", Map.of("content", newTitle)
        )));
        properties.put("title", titleProperty);
        requestBody.put("properties", properties);
        
        // Make PATCH request to update page properties
        String url = buildPageURL(pageId);
        HttpRequest.HttpRequestBuilder requestBuilder = buildPatchRequest(runContext, url, requestBody);
        
        return makeCall(runContext, requestBuilder, NotionResponse.class);
    }

    /**
     * Appends new markdown content to the bottom of the page
     */
    protected void appendPageContent(RunContext runContext, String pageId, String newContent) throws Exception {
        // Add new blocks from markdown to the end of the page
        if (newContent != null && !newContent.trim().isEmpty()) {
            ArrayNode newBlocksArray = MarkdownConverter.markdownToBlocks(newContent);
            if (newBlocksArray.size() > 0) {
                addBlocksToPage(runContext, pageId, newBlocksArray);
            }
        }
    }

    /**
     * Adds new blocks to a page
     */
    protected void addBlocksToPage(RunContext runContext, String pageId, ArrayNode blocks) throws Exception {
        Map<String, Object> requestBody = Map.of("children", blocks);
        
        String url = buildPageChildrenURL(pageId);
        HttpRequest.HttpRequestBuilder requestBuilder = buildPatchRequest(runContext, url, requestBody);
        
        makeCall(runContext, requestBuilder, NotionResponse.class);
    }



    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }
} 
