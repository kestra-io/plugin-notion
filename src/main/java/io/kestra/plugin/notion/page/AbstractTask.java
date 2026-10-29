package io.kestra.plugin.notion.page;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractTask extends NotionConnection implements RunnableTask<AbstractTask.Output> {

    @Schema(
        title = "Page ID",
        description = "Notion page identifier in UUID format; 32‑character hex is also accepted"
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> pageId;

    /**
     * Common output structure for all Notion page operations
     */
    @Builder
    @Getter
    @NoArgsConstructor
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
            title = "Message",
            description = "Operation result message"
        )
        private String message;

        public Output(String pageId,
            String url,
            String title,
            String content,
            Instant createdTime,
            Instant lastEditedTime,
            Boolean archived,
            Map<String, Object> properties,
            String message) {
            this.pageId = pageId;
            this.url = url;
            this.title = title;
            this.content = content;
            this.createdTime = createdTime;
            this.lastEditedTime = lastEditedTime;
            this.archived = archived;
            this.properties = properties;
            this.message = message;
        }
    }

    /**
     * Helper method to extract page title from Notion page properties
     */
    protected static String extractPageTitle(Map<?, ?> properties) {
        if (properties == null) {
            return null;
        }

        for (var property : properties.values()) {
            if (!(property instanceof Map<?, ?> propertyMap)) {
                continue;
            }

            if ("title".equals(propertyMap.get("type"))) {
                return extractTitleText(propertyMap);
            }
        }

        return null;
    }

    private static String extractTitleText(Map<?, ?> titleProperty) {
        if (titleProperty == null || !(titleProperty.get("title") instanceof List<?> titleArray)) {
            return null;
        }

        var title = new StringBuilder();
        var hasPlainText = false;
        for (var titlePart : titleArray) {
            if (titlePart instanceof Map<?, ?> titlePartMap) {
                var plainText = titlePartMap.get("plain_text");
                if (plainText != null) {
                    hasPlainText = true;
                    title.append(plainText);
                }
            }
        }

        return hasPlainText ? title.toString() : null;
    }

    /**
     * Helper method to build output from NotionResponse.
     *
     * @param response the NotionResponse containing page data
     * @param content the content payload, if any
     * @param message operation result message
     * @return Output object with fields populated
     */
    protected Output buildOutput(io.kestra.plugin.notion.NotionResponse response, String content, String message) {
        return new Output(
            response.getId(),
            response.getUrl(),
            extractPageTitle(response.getProperties()),
            content,
            response.getCreatedTime(),
            response.getLastEditedTime(),
            response.getArchived(),
            response.getProperties(),
            message
        );
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
