package io.kestra.plugin.notion.page;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractTask extends NotionConnection implements RunnableTask<AbstractTask.Output> {

    @Schema(
        title = "Page ID",
        description = "The unique identifier of the Notion page"
    )
    @NotNull
    protected Property<String> pageId;

    /**
     * Common output structure for all Notion page operations
     */
    @Builder
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
    protected Object buildCommonOutput(io.kestra.plugin.notion.NotionResponse response) {
        return Output.builder()
            .pageId(response.getId())
            .url(response.getUrl())
            .title(extractPageTitle(response.getProperties()))
            .createdTime(response.getCreatedTime())
            .lastEditedTime(response.getLastEditedTime())
            .archived(response.getArchived())
            .properties(response.getProperties());
    }



    /**
     * Helper method to check if a string is a valid UUID
     */
    public static boolean isUUID(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Helper method to format a 32-character hex string as a UUID
     */
    private static String formatAsUUID(String hexString) {
        if (hexString.length() != 32) {
            return hexString;
        }
        return hexString.substring(0, 8) + "-" + 
               hexString.substring(8, 12) + "-" + 
               hexString.substring(12, 16) + "-" + 
               hexString.substring(16, 20) + "-" + 
               hexString.substring(20);
    }

    /**
     * Validates that the pageId property is provided and not empty
     */
    protected String validateAndRenderPageId(RunContext runContext) throws Exception {
        String renderedPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        
        // Check if it's already a valid UUID
        if (isUUID(renderedPageId)) {
            return renderedPageId;
        }
        
        // Check if it's a 32-character hex string that can be formatted as UUID
        if (renderedPageId.matches("^[0-9a-f]{32}$")) {
            String formattedUUID = formatAsUUID(renderedPageId);
            if (isUUID(formattedUUID)) {
                return formattedUUID;
            }
        }
        
        throw new IllegalArgumentException("pageId must be a valid Notion page ID (UUID format)");
    }
} 