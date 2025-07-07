package io.kestra.plugin.notion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Base class for Notion plugin integration tests with mock API responses.
 * Provides common setup for WireMock server and helper methods for creating mock responses.
 * 
 * Note: These are simplified integration tests that focus on HTTP layer testing
 * without requiring full Kestra framework setup.
 */
public abstract class AbstractNotionTaskIT {

    protected WireMockServer wireMockServer;
    protected ObjectMapper mapper = new ObjectMapper();
    protected static final String MOCK_API_TOKEN = "secret_test_token_123";
    protected static final String TEST_PAGE_ID = "12345678-1234-1234-1234-123456789abc";
    protected static final String TEST_PARENT_PAGE_ID = "87654321-4321-4321-4321-210987654321";
    protected static final String MOCK_BASE_URL = "http://localhost";

    @BeforeEach
    void setUpWireMock() {
        // Start WireMock server on random available port
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        // Configure WireMock
        WireMock.configureFor("localhost", wireMockServer.port());
        
        // Set system property to override Notion base URL for testing
        System.setProperty("notion.api.base.url", getMockBaseUrl());
    }

    @AfterEach
    void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        System.clearProperty("notion.api.base.url");
    }

    protected String getMockBaseUrl() {
        return MOCK_BASE_URL + ":" + wireMockServer.port();
    }

    // =========================
    // Mock Response Helpers
    // =========================

    /**
     * Creates a mock successful page creation response
     */
    protected void mockCreatePageSuccess(String title, String content) {
        String responseBody = createPageResponseJson(TEST_PAGE_ID, title, false);
        
        wireMockServer.stubFor(post(urlEqualTo("/v1/pages"))
            .withHeader("Authorization", equalTo("Bearer " + MOCK_API_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Notion-Version", equalTo("2022-06-28"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    /**
     * Creates a mock successful page read response
     */
    protected void mockReadPageSuccess(String pageId, String title) {
        String responseBody = createPageResponseJson(pageId, title, false);
        
        wireMockServer.stubFor(get(urlEqualTo("/v1/pages/" + pageId))
            .withHeader("Authorization", equalTo("Bearer " + MOCK_API_TOKEN))
            .withHeader("Notion-Version", equalTo("2022-06-28"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    /**
     * Creates a mock successful page children response
     */
    protected void mockReadPageChildrenSuccess(String pageId) {
        String responseBody = createPageChildrenResponseJson();
        
        wireMockServer.stubFor(get(urlEqualTo("/v1/blocks/" + pageId + "/children"))
            .withHeader("Authorization", equalTo("Bearer " + MOCK_API_TOKEN))
            .withHeader("Notion-Version", equalTo("2022-06-28"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    /**
     * Creates a mock successful page update response
     */
    protected void mockUpdatePageSuccess(String pageId, String updatedTitle) {
        String responseBody = createPageResponseJson(pageId, updatedTitle, false);
        
        wireMockServer.stubFor(patch(urlEqualTo("/v1/pages/" + pageId))
            .withHeader("Authorization", equalTo("Bearer " + MOCK_API_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Notion-Version", equalTo("2022-06-28"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    /**
     * Creates a mock successful page archive response
     */
    protected void mockArchivePageSuccess(String pageId) {
        String responseBody = createPageResponseJson(pageId, "Archived Page", true);
        
        wireMockServer.stubFor(patch(urlEqualTo("/v1/pages/" + pageId))
            .withHeader("Authorization", equalTo("Bearer " + MOCK_API_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Notion-Version", equalTo("2022-06-28"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    /**
     * Creates a mock error response for authentication failure
     */
    protected void mockAuthenticationError() {
        String errorResponse = """
            {
              "object": "error",
              "status": 401,
              "code": "unauthorized",
              "message": "API token is invalid."
            }
            """;
        
        wireMockServer.stubFor(any(anyUrl())
            .withHeader("Authorization", equalTo("Bearer invalid_token"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(errorResponse)));
    }

    /**
     * Creates a mock error response for page not found
     */
    protected void mockPageNotFoundError(String pageId) {
        String errorResponse = """
            {
              "object": "error",
              "status": 404,
              "code": "object_not_found",
              "message": "Could not find page with ID: %s."
            }
            """.formatted(pageId);
        
        wireMockServer.stubFor(any(urlMatching("/v1/pages/" + pageId + ".*"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(errorResponse)));
    }

    /**
     * Creates a mock error response for rate limiting
     */
    protected void mockRateLimitError() {
        String errorResponse = """
            {
              "object": "error",
              "status": 429,
              "code": "rate_limited",
              "message": "Too many requests."
            }
            """;
        
        wireMockServer.stubFor(any(anyUrl())
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withHeader("Retry-After", "60")
                .withBody(errorResponse)));
    }

    // =========================
    // JSON Response Builders
    // =========================

    private String createPageResponseJson(String pageId, String title, boolean archived) {
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        
        try {
            Map<String, Object> response = Map.of(
                "object", "page",
                "id", pageId,
                "created_time", now,
                "last_edited_time", now,
                "archived", archived,
                "url", "https://www.notion.so/" + pageId.replace("-", ""),
                "properties", Map.of(
                    "title", Map.of(
                        "id", "title",
                        "type", "title",
                        "title", List.of(Map.of(
                            "type", "text",
                            "text", Map.of("content", title),
                            "plain_text", title
                        ))
                    )
                ),
                "parent", Map.of(
                    "type", "workspace",
                    "workspace", true
                )
            );
            
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock JSON response", e);
        }
    }

    private String createPageChildrenResponseJson() {
        try {
            // Use HashMap to allow null values
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("object", "list");
            response.put("results", List.of(
                Map.of(
                    "object", "block",
                    "id", "block-1-id",
                    "type", "paragraph",
                    "paragraph", Map.of(
                        "rich_text", List.of(Map.of(
                            "type", "text",
                            "text", Map.of("content", "This is a test paragraph."),
                            "plain_text", "This is a test paragraph."
                        ))
                    )
                ),
                Map.of(
                    "object", "block",
                    "id", "block-2-id",
                    "type", "heading_1",
                    "heading_1", Map.of(
                        "rich_text", List.of(Map.of(
                            "type", "text",
                            "text", Map.of("content", "Test Header"),
                            "plain_text", "Test Header"
                        ))
                    )
                )
            ));
            response.put("next_cursor", null);
            response.put("has_more", false);
            
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock children JSON response", e);
        }
    }

    /**
     * Verifies that the correct API call was made
     */
    protected void verifyApiCall(String method, String url) {
        switch (method.toUpperCase()) {
            case "GET" -> wireMockServer.verify(getRequestedFor(urlEqualTo(url)));
            case "POST" -> wireMockServer.verify(postRequestedFor(urlEqualTo(url)));
            case "PATCH" -> wireMockServer.verify(patchRequestedFor(urlEqualTo(url)));
            case "DELETE" -> wireMockServer.verify(deleteRequestedFor(urlEqualTo(url)));
        }
    }
} 