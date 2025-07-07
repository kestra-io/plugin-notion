package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractNotionTask extends NotionConnection implements RunnableTask<AbstractNotionTask.Output> {

    @Schema(
        title = "Page ID",
        description = "The unique identifier of the Notion page"
    )
    @NotNull
    protected Property<String> pageId;

    /**
     * Common output structure for all Notion page operations
     */
    @Builder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        
        @Schema(
            title = "Page ID",
            description = "The unique identifier of the page"
        )
        private String pageId;

        @Schema(
            title = "Page URL",
            description = "The URL of the page in Notion"
        )
        private String url;

        @Schema(
            title = "Page Title",
            description = "The title of the page"
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
            description = "Additional page properties"
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

    /**
     * Helper method to extract page title from Notion page properties
     */
    protected String extractPageTitle(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }

        // Notion stores title in different formats, try common patterns
        Map<String, Object> titleProp = (Map<String, Object>) properties.get("title");
        if (titleProp == null) {
            titleProp = (Map<String, Object>) properties.get("Name");
        }
        
        if (titleProp != null && titleProp.get("title") instanceof List) {
            List<?> titleArray = (List<?>) titleProp.get("title");
            if (!titleArray.isEmpty() && titleArray.get(0) instanceof Map) {
                Map<String, Object> firstTitle = (Map<String, Object>) titleArray.get(0);
                Object plainText = firstTitle.get("plain_text");
                if (plainText != null) {
                    return plainText.toString();
                }
            }
        }

        return null;
    }

    /**
     * Helper method to build common output from NotionResponse.
     * Returns a builder that can be further customized before calling build().
     * 
     * @param response the NotionResponse containing page data
     * @return Output builder with common fields populated
     */
    protected Object buildCommonOutput(NotionResponse response) {
        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
            .title(extractPageTitle(response.getProperties()))
            .createdTime(response.getCreatedTime())
            .lastEditedTime(response.getLastEditedTime())
            .archived(response.getArchived())
            .properties(response.getProperties())
            .success(true);
    }

    /**
     * Helper method to build error output
     */
    protected Output buildErrorOutput(String message) {
        return Output.builder()
            .success(false)
            .message(message)
            .build();
    }

    /**
     * Validates that the pageId property is provided and not empty
     */
    protected String validateAndRenderPageId(RunContext runContext) throws Exception {
        String renderedPageId = runContext.render(this.pageId).as(String.class).orElse("");
        
        if (renderedPageId.isEmpty()) {
            throw new IllegalArgumentException("pageId is required and cannot be empty");
        }
        
        // Basic validation for Notion page ID format (UUID format)
        if (!renderedPageId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") && 
            !renderedPageId.matches("^[0-9a-f]{32}$")) {
            throw new IllegalArgumentException("pageId must be a valid Notion page ID (UUID format)");
        }
        
        return renderedPageId;
    }
} 