package io.kestra.plugin.notion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotionResponse {
    
    /**
     * The unique identifier of the page/object
     */
    private String id;
    
    /**
     * The type of object (e.g., "page", "database", "block")
     */
    private String object;
    
    /**
     * When the object was created
     */
    @JsonProperty("created_time")
    private Instant createdTime;
    
    /**
     * Who created the object
     */
    @JsonProperty("created_by")
    private Map<String, Object> createdBy;
    
    /**
     * When the object was last edited
     */
    @JsonProperty("last_edited_time")
    private Instant lastEditedTime;
    
    /**
     * Who last edited the object
     */
    @JsonProperty("last_edited_by")
    private Map<String, Object> lastEditedBy;
    
    /**
     * Whether the object is archived
     */
    private Boolean archived;
    
    /**
     * The URL of the page in Notion
     */
    private String url;
    
    /**
     * The public URL of the page (if public)
     */
    @JsonProperty("public_url")
    private String publicUrl;
    
    /**
     * Properties of the page (title, content, etc.)
     */
    private Map<String, Object> properties;
    
    /**
     * Parent object information
     */
    private Map<String, Object> parent;
    
    /**
     * Icon information (emoji or file)
     */
    private Map<String, Object> icon;
    
    /**
     * Cover image information
     */
    private Map<String, Object> cover;
    
    /**
     * Child content blocks (for pages with content)
     */
    private List<Map<String, Object>> children;
    
    /**
     * Block content (for block responses)
     */
    private Map<String, Object> content;
    
    /**
     * Has more content (pagination)
     */
    @JsonProperty("has_more")
    private Boolean hasMore;
    
    /**
     * Next cursor for pagination
     */
    @JsonProperty("next_cursor")
    private String nextCursor;
    
    /**
     * Type of the object (for blocks)
     */
    private String type;
    
    /**
     * Additional raw data that might not be mapped to specific fields
     */
    private Map<String, Object> additionalData;
} 