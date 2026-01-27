package io.kestra.plugin.notion.page;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Archive a Notion page",
    description = "Archives an existing Notion page by ID. In Notion, pages are archived rather than permanently deleted, moving them to the trash."
)
@Plugin(
    examples = {
        @Example(
            title = "Archive a page by ID",
            full = true,
            code = """
                id: notion_archive_page
                namespace: company.team

                inputs:
                  - id: page_id
                    type: STRING

                tasks:
                  - id: archive_page
                    type: io.kestra.plugin.notion.page.Archive
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "{{ inputs.page_id }}"
                """
        ),
        @Example(
            title = "Archive a specific page",
            full = true,
            code = """
                id: notion_archive_specific_page
                namespace: company.team

                tasks:
                  - id: archive_old_page
                    type: io.kestra.plugin.notion.page.Archive
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "12345678-1234-1234-1234-123456789abc"
                """
        )
    }
)
public class Archive extends AbstractTask {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        try {
            // Validate and render page ID
            String renderedPageId = validateAndRenderPageId(runContext);
            
            logger.info("Archiving Notion page with ID: {}", renderedPageId);

            // Get page info before archiving for confirmation
            String pageUrl = buildPageURL(renderedPageId);
            HttpRequest.HttpRequestBuilder pageRequestBuilder = buildGetRequest(runContext, pageUrl);
            
            NotionResponse pageInfoResponse = makeCall(runContext, pageRequestBuilder, NotionResponse.class);
            String pageTitle = extractPageTitle(pageInfoResponse.getProperties());
            String pageUrlValue = pageInfoResponse.getUrl();
            
            logger.info("Found page '{}' at URL: {}", pageTitle, pageUrlValue);
            
            // Check if page is already archived
            if (Boolean.TRUE.equals(pageInfoResponse.getArchived())) {
                logger.warn("Page '{}' is already archived", pageTitle);
                
                return buildOutput(pageInfoResponse, null, "Page was already archived");
            }
            
            // Archive the page by setting archived = true
            Map<String, Object> requestBody = Map.of("archived", true);
            
            HttpRequest.HttpRequestBuilder archiveRequestBuilder = buildPatchRequest(runContext, pageUrl, requestBody);
            
            logger.debug("Archiving page with request body: {}", mapper.writeValueAsString(requestBody));
            
            NotionResponse archiveResponse = makeCall(runContext, archiveRequestBuilder, NotionResponse.class);
            
            logger.info("Successfully archived page '{}' (ID: {})", pageTitle, renderedPageId);
            
            return buildOutput(archiveResponse, null, "Page archived successfully");
                
        } catch (Exception e) {
            logger.error("Error archiving Notion page: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }
} 
