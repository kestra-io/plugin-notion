package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotionConnection functionality.
 * These tests focus on connection setup, URL building, and basic authentication logic
 * without requiring actual API calls or full Kestra framework initialization.
 */
class NotionConnectionTest {

    // Concrete implementation for testing
    @SuperBuilder
    @NoArgsConstructor
    public static class TestNotionConnection extends NotionConnection {
        @Override
        protected String getEndpoint() {
            return "/test";
        }
    }

    private TestNotionConnection connection;

    @BeforeEach
    void setUp() {
        connection = TestNotionConnection.builder().build();
    }

    @Test
    void testConstants() {
        // Test that all API constants are properly defined
        assertThat(NotionConnection.NOTION_API_URL, equalTo("https://api.notion.com"));
        assertThat(NotionConnection.NOTION_API_VERSION, equalTo("2022-06-28"));
        assertThat(NotionConnection.JSON_CONTENT_TYPE, equalTo("application/json; charset=UTF-8"));
        assertThat(NotionConnection.PAGES_ENDPOINT, equalTo("/v1/pages"));
        assertThat(NotionConnection.BLOCKS_ENDPOINT, equalTo("/v1/blocks"));
        assertThat(NotionConnection.SEARCH_ENDPOINT, equalTo("/v1/search"));
    }

    @Test
    void testBuildNotionURL() {
        String url = connection.buildNotionURL();

        assertThat(url, equalTo("https://api.notion.com/test"));
    }

    @Test
    void testBuildCreatePageURL() {
        String url = connection.buildCreatePageURL();

        assertThat(url, equalTo("https://api.notion.com/v1/pages"));
    }

    @Test
    void testBuildPageURL() {
        String pageId = "test-page-id-123";
        String url = connection.buildPageURL(pageId);

        assertThat(url, equalTo("https://api.notion.com/v1/pages/" + pageId));
    }

    @Test
    void testBuildPageChildrenURL() {
        String pageId = "test-page-id-123";
        String url = connection.buildPageChildrenURL(pageId);

        assertThat(url, equalTo("https://api.notion.com/v1/blocks/" + pageId + "/children"));
    }

    @Test
    void testBuildBlockURL() {
        String blockId = "test-block-id-456";
        String url = connection.buildBlockURL(blockId);

        assertThat(url, equalTo("https://api.notion.com/v1/blocks/" + blockId));
    }

    @Test
    void testBuildSearchURL() {
        String url = connection.buildSearchURL();

        assertThat(url, equalTo("https://api.notion.com/v1/search"));
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        TestNotionConnection connection = TestNotionConnection.builder()
            .apiToken(Property.ofValue("test-token"))
            .build();

        assertThat(connection, notNullValue());
        assertThat(connection.getApiToken(), notNullValue());
    }

    @Test
    void testBuilderWithoutToken() {
        // Test that builder works without token (which is valid for URL building methods)
        TestNotionConnection connection = TestNotionConnection.builder().build();

        assertThat(connection, notNullValue());
        // These methods should work without a token
        assertThat(connection.buildCreatePageURL(), notNullValue());
        assertThat(connection.buildSearchURL(), notNullValue());
    }

    @Test
    void testURLBuildingWithSpecialCharacters() {
        // Test URL building with various page/block IDs
        String pageIdWithDashes = "01234567-89ab-cdef-0123-456789abcdef";
        String blockIdWithSpecialChars = "block_123_test";

        assertThat(connection.buildPageURL(pageIdWithDashes),
            equalTo("https://api.notion.com/v1/pages/" + pageIdWithDashes));
        assertThat(connection.buildBlockURL(blockIdWithSpecialChars),
            equalTo("https://api.notion.com/v1/blocks/" + blockIdWithSpecialChars));
    }

    @Test
    void testEndpointAbstraction() {
        // Test that the abstract getEndpoint method is properly implemented
        assertThat(connection.getEndpoint(), equalTo("/test"));
        assertThat(connection.buildNotionURL(), containsString("/test"));
    }

    @Test
    void testBuilderWithHttpConfiguration() {
        // Test that HTTP configuration can be set through builder
        TestNotionConnection connectionWithConfig = TestNotionConnection.builder()
            .apiToken(Property.ofValue("test-token"))
            .options(null) // HTTP configuration would go here in real usage
            .build();

        assertThat(connectionWithConfig, notNullValue());
        assertThat(connectionWithConfig.getOptions(), nullValue());
    }
}
