package io.kestra.plugin.notion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class NotionConnection extends Task {

    protected final static ObjectMapper mapper = JacksonMapper.ofJson(false);

    public static final String NOTION_API_URL = "https://api.notion.com";

    public static final String NOTION_API_VERSION = "2022-06-28";

    /**
     * Gets the base URL for Notion API, allowing override for testing
     */
    public static String getBaseUrl() {
        String overrideUrl = System.getProperty("notion.api.base.url");
        return overrideUrl != null ? overrideUrl : NOTION_API_URL;
    }

    public static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    // Notion API Endpoints
    public static final String PAGES_ENDPOINT = "/v1/pages";
    public static final String BLOCKS_ENDPOINT = "/v1/blocks";
    public static final String SEARCH_ENDPOINT = "/v1/search";

    /** Notion caps a single create/append request at 100 child blocks. */
    public static final int MAX_BLOCKS_PER_REQUEST = 100;

    @Schema(
        title = "Notion API token",
        description = "The Notion API integration token (Internal Secret connection)"
    )
    @PluginProperty(group = "connection", secret = true)
    private Property<String> apiToken;

    @Schema(title = "The HTTP client configuration")
    HttpConfiguration options;

    /**
     * Makes an HTTP call to the Notion API with proper error handling
     */
    public <T> T makeCall(RunContext runContext, HttpRequest.HttpRequestBuilder requestBuilder, Class<T> responseType) throws Exception {
        var logger = runContext.logger();

        try (HttpClient client = new HttpClient(runContext, options)) {
            HttpRequest request = requestBuilder.build();
            HttpResponse<T> response = client.request(request, responseType);

            return response.getBody();
        } catch (Exception e) {
            logger.error("Error making request to Notion API: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Adds authentication and required headers to the HTTP request
     */
    public void getAuthorizedRequest(
        RunContext runContext,
        HttpRequest.HttpRequestBuilder requestBuilder) throws IllegalVariableEvaluationException {

        var apiTokenRendered = runContext.render(this.apiToken).as(String.class);

        if (apiTokenRendered.isEmpty()) {
            throw new IllegalArgumentException("Missing required apiToken field");
        }

        requestBuilder
            .addHeader("Authorization", "Bearer " + apiTokenRendered.get())
            .addHeader("Notion-Version", NOTION_API_VERSION)
            .addHeader("Content-Type", JSON_CONTENT_TYPE);
    }

    /**
     * Builds the complete Notion API URL for the endpoint
     */
    protected String buildNotionURL() {
        return getBaseUrl() + getEndpoint();
    }

    /**
     * Builds URL for creating a new page
     */
    protected String buildCreatePageURL() {
        return getBaseUrl() + PAGES_ENDPOINT;
    }

    /**
     * Builds URL for getting/updating/deleting a specific page
     */
    protected String buildPageURL(String pageId) {
        return getBaseUrl() + PAGES_ENDPOINT + "/" + pageId;
    }

    /**
     * Builds URL for getting blocks/children of a page
     */
    protected String buildPageChildrenURL(String pageId) {
        return getBaseUrl() + BLOCKS_ENDPOINT + "/" + pageId + "/children";
    }

    /**
     * Builds URL for updating a specific block
     */
    protected String buildBlockURL(String blockId) {
        return getBaseUrl() + BLOCKS_ENDPOINT + "/" + blockId;
    }

    /**
     * Builds URL for search operations
     */
    protected String buildSearchURL() {
        return getBaseUrl() + SEARCH_ENDPOINT;
    }

    /**
     * Creates a GET request builder with authentication and headers
     */
    protected HttpRequest.HttpRequestBuilder buildGetRequest(RunContext runContext, String url) throws IllegalVariableEvaluationException {
        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("GET");

        getAuthorizedRequest(runContext, requestBuilder);
        return requestBuilder;
    }

    /**
     * Creates a POST request builder with authentication, headers, and JSON body
     */
    protected HttpRequest.HttpRequestBuilder buildPostRequest(RunContext runContext, String url, Object body) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build());

        getAuthorizedRequest(runContext, requestBuilder);
        return requestBuilder;
    }

    /**
     * Creates a PATCH request builder with authentication, headers, and JSON body
     */
    protected HttpRequest.HttpRequestBuilder buildPatchRequest(RunContext runContext, String url, Object body) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("PATCH")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build());

        getAuthorizedRequest(runContext, requestBuilder);
        return requestBuilder;
    }

    /**
     * Splits a block array into batches of at most {@link #MAX_BLOCKS_PER_REQUEST} and appends each
     * batch, in order, to a page/block via the children endpoint. Notion caps a single request at 100
     * child blocks, so longer content must be paginated.
     *
     * @param startIndex index of the first block to append — {@link #MAX_BLOCKS_PER_REQUEST} when the
     *        first batch was already sent inline with a create request, or 0 to append all
     */
    protected void appendBlocksInBatches(RunContext runContext, String blockId, ArrayNode blocks, int startIndex) throws Exception {
        if (blocks == null) {
            return;
        }
        var url = buildPageChildrenURL(blockId);
        for (var i = Math.max(startIndex, 0); i < blocks.size(); i += MAX_BLOCKS_PER_REQUEST) {
            var batch = mapper.createArrayNode();
            var end = Math.min(i + MAX_BLOCKS_PER_REQUEST, blocks.size());
            for (var j = i; j < end; j++) {
                batch.add(blocks.get(j));
            }
            var requestBuilder = buildPatchRequest(runContext, url, Map.of("children", batch));
            makeCall(runContext, requestBuilder, NotionResponse.class);
        }
    }

    /**
     * Returns the first {@link #MAX_BLOCKS_PER_REQUEST} blocks (or fewer) for inclusion in a
     * page-creation request; the remainder should be appended via
     * {@link #appendBlocksInBatches(RunContext, String, ArrayNode, int)}.
     */
    protected ArrayNode firstBlockBatch(ArrayNode blocks) {
        var batch = mapper.createArrayNode();
        if (blocks != null) {
            var end = Math.min(MAX_BLOCKS_PER_REQUEST, blocks.size());
            for (var i = 0; i < end; i++) {
                batch.add(blocks.get(i));
            }
        }
        return batch;
    }

    /**
     * Creates a DELETE request builder with authentication and headers
     */
    protected HttpRequest.HttpRequestBuilder buildDeleteRequest(RunContext runContext, String url) throws IllegalVariableEvaluationException {
        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("DELETE");

        getAuthorizedRequest(runContext, requestBuilder);
        return requestBuilder;
    }

    /**
     * Stores results to Kestra's internal storage
     */
    protected URI store(RunContext runContext, List<Map<String, Object>> results) throws IOException {
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var output = new BufferedWriter(new FileWriter(tempFile), FileSerde.BUFFER_SIZE)) {
            Flux<Map<String, Object>> recordFlux = Flux.fromIterable(results);
            FileSerde.writeAll(output, recordFlux).block();
            return runContext.storage().putFile(tempFile);
        }
    }

    /**
     * Returns the API endpoint path for this operation
     * Must be implemented by concrete classes
     */
    protected abstract String getEndpoint();
}