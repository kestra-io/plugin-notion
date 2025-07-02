package io.kestra.plugin.notion;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a Notion page",
    description = "Archives (deletes) an existing Notion page by ID. In Notion, pages are archived rather than permanently deleted, moving them to the trash."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a page by ID",
            full = true,
            code = """
                id: notion_delete_page
                namespace: company.team

                inputs:
                  - id: page_id
                    type: STRING

                tasks:
                  - id: delete_page
                    type: io.kestra.plugin.notion.DeletePage
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "{{ inputs.page_id }}"
                """
        ),
        @Example(
            title = "Delete a specific page",
            full = true,
            code = """
                id: notion_delete_specific_page
                namespace: company.team

                tasks:
                  - id: delete_old_page
                    type: io.kestra.plugin.notion.DeletePage
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "12345678-1234-1234-1234-123456789abc"
                """
        )
    }
)
public class DeletePage extends AbstractNotionTask {

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        try {
            // Validate and render page ID
            String renderedPageId = validateAndRenderPageId(runContext);
            
            logger.info("Deleting (archiving) Notion page with ID: {}", renderedPageId);

            // Get page info before deletion for confirmation
            String pageUrl = buildPageURL(renderedPageId);
            HttpRequest.HttpRequestBuilder pageRequestBuilder = buildGetRequest(runContext, pageUrl);
            
            NotionResponse pageInfoResponse = makeCall(runContext, pageRequestBuilder, NotionResponse.class);
            String pageTitle = extractPageTitle(pageInfoResponse.getProperties());
            String pageUrlValue = pageInfoResponse.getUrl();
            
            logger.info("Found page '{}' at URL: {}", pageTitle, pageUrlValue);
            
            // Check if page is already archived
            if (Boolean.TRUE.equals(pageInfoResponse.getArchived())) {
                logger.warn("Page '{}' is already archived", pageTitle);
                
                // Store information about already archived page
                URI fileURI = store(runContext, List.of(Map.of(
                    "pageResponse", pageInfoResponse,
                    "alreadyArchived", true,
                    "pageId", renderedPageId
                )));
                
                return ((Output.OutputBuilder) buildCommonOutput(pageInfoResponse))
                    .uri(fileURI)
                    .message("Page was already archived")
                    .build();
            }
            
            // Archive the page by setting archived = true
            Map<String, Object> requestBody = Map.of("archived", true);
            
            HttpRequest.HttpRequestBuilder deleteRequestBuilder = buildPatchRequest(runContext, pageUrl, requestBody);
            
            logger.debug("Archiving page with request body: {}", mapper.writeValueAsString(requestBody));
            
            NotionResponse deleteResponse = makeCall(runContext, deleteRequestBuilder, NotionResponse.class);
            
            // Store detailed information about the deletion
            URI fileURI = store(runContext, List.of(Map.of(
                "originalPageResponse", pageInfoResponse,
                "deleteResponse", deleteResponse,
                "pageId", renderedPageId,
                "deletedTitle", pageTitle,
                "deletedUrl", pageUrlValue
            )));
            
            logger.info("Successfully archived page '{}' (ID: {})", pageTitle, renderedPageId);
            
            return ((Output.OutputBuilder) buildCommonOutput(deleteResponse))
                .uri(fileURI)
                .message("Page archived successfully")
                .build();
                
        } catch (Exception e) {
            logger.error("Error deleting Notion page: {}", e.getMessage());
            return buildErrorOutput("Failed to delete page: " + e.getMessage());
        }
    }

    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }
} 