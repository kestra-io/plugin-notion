package io.kestra.plugin.notion;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.utils.MarkdownConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Read a Notion page",
    description = "Retrieves an existing Notion page by ID and returns its content in markdown format along with metadata."
)
@Plugin(
    examples = {
        @Example(
            title = "Read a page by ID",
            full = true,
            code = """
                id: notion_read_page
                namespace: company.team

                inputs:
                  - id: page_id
                    type: STRING

                tasks:
                  - id: read_page
                    type: io.kestra.plugin.notion.ReadPage
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "{{ inputs.page_id }}"
                """
        ),
        @Example(
            title = "Read a specific page",
            full = true,
            code = """
                id: notion_read_specific_page
                namespace: company.team

                tasks:
                  - id: read_meeting_notes
                    type: io.kestra.plugin.notion.ReadPage
                    apiToken: "{{ secret('NOTION_API_TOKEN') }}"
                    pageId: "12345678-1234-1234-1234-123456789abc"
                """
        )
    }
)
public class ReadPage extends AbstractNotionTask {

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        try {
            // Validate and render page ID
            String renderedPageId = validateAndRenderPageId(runContext);
            
            logger.info("Reading Notion page with ID: {}", renderedPageId);

            // Get page metadata
            String pageUrl = buildPageURL(renderedPageId);
            HttpRequest.HttpRequestBuilder pageRequestBuilder = buildGetRequest(runContext, pageUrl);
            
            NotionResponse pageResponse = makeCall(runContext, pageRequestBuilder, NotionResponse.class);
            
            // Get page content (blocks/children)
            String childrenUrl = buildPageChildrenURL(renderedPageId);
            HttpRequest.HttpRequestBuilder childrenRequestBuilder = buildGetRequest(runContext, childrenUrl);
            
            NotionResponse childrenResponse = makeCall(runContext, childrenRequestBuilder, NotionResponse.class);
            
            // Convert blocks to markdown
            String markdownContent = MarkdownConverter.blocksToMarkdown(mapper.valueToTree(childrenResponse.getChildren()));
            
            // Store detailed information
            URI fileURI = store(runContext, List.of(Map.of(
                "pageResponse", pageResponse,
                "childrenResponse", childrenResponse,
                "markdownContent", markdownContent,
                "pageId", renderedPageId
            )));
            
            return ((Output.OutputBuilder) buildCommonOutput(pageResponse))
                .content(markdownContent)
                .uri(fileURI)
                .message("Page read successfully")
                .build();
                
        } catch (Exception e) {
            logger.error("Error reading Notion page: {}", e.getMessage());
            return buildErrorOutput("Failed to read page: " + e.getMessage());
        }
    }



    @Override
    protected String getEndpoint() {
        return PAGES_ENDPOINT;
    }
} 