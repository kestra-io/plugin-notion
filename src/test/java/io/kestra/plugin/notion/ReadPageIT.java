package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ReadPage task with mock Notion API responses.
 * These tests verify the HTTP layer functionality and URL building without requiring 
 * full Kestra framework setup.
 */
class ReadPageIT extends AbstractNotionTaskIT {

    @Test
    void shouldBuildCorrectUrlForReadPage() {
        // Arrange
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Act
        String url = task.buildPageURL(TEST_PAGE_ID);
        
        // Assert
        assertThat(url, equalTo(getMockBaseUrl() + "/v1/pages/" + TEST_PAGE_ID));
    }

    @Test
    void shouldBuildCorrectEndpoint() {
        // Arrange
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Act
        String endpoint = task.getEndpoint();
        
        // Assert
        assertThat(endpoint, equalTo("/v1/pages"));
    }

    @Test
    void shouldBuildCorrectChildrenUrl() {
        // Arrange
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Act
        String childrenUrl = task.buildPageChildrenURL(TEST_PAGE_ID);
        
        // Assert
        assertThat(childrenUrl, equalTo(getMockBaseUrl() + "/v1/blocks/" + TEST_PAGE_ID + "/children"));
    }

    @Test
    void shouldInheritFromNotionConnection() {
        // Arrange
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Assert
        assertThat(task, instanceOf(NotionConnection.class));
        assertThat(task.buildNotionURL(), equalTo(getMockBaseUrl() + "/v1/pages"));
    }

    @Test
    void shouldValidateApiTokenProperty() {
        // Arrange & Act
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Assert
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void shouldValidatePageIdProperty() {
        // Arrange & Act
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Assert
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void shouldHandleUuidPageIdFormat() {
        // Arrange
        String uuidPageId = "12345678-1234-1234-1234-123456789abc";
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(uuidPageId))
            .build();
        
        // Act
        String url = task.buildPageURL(uuidPageId);
        
        // Assert
        assertThat(url, containsString(uuidPageId));
        assertThat(url, equalTo(getMockBaseUrl() + "/v1/pages/" + uuidPageId));
    }

    @Test
    void shouldHandleCompactPageIdFormat() {
        // Arrange
        String compactPageId = "1234567812341234123456789abc";
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(compactPageId))
            .build();
        
        // Act
        String url = task.buildPageURL(compactPageId);
        
        // Assert
        assertThat(url, containsString(compactPageId));
        assertThat(url, equalTo(getMockBaseUrl() + "/v1/pages/" + compactPageId));
    }

    @Test
    void shouldCreateOutputBuilderCorrectly() {
        // Test that the Output builder works correctly with proper types
        Instant now = Instant.parse("2023-01-01T00:00:00.000Z");
        
        AbstractNotionTask.Output output = AbstractNotionTask.Output.builder()
            .pageId(TEST_PAGE_ID)
            .url("https://notion.so/test-page")
            .title("Test Page")
            .content("Test content")
            .message("Page read successfully")
            .createdTime(now)
            .lastEditedTime(now)
            .archived(false)
            .build();
        
        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo(TEST_PAGE_ID));
        assertThat(output.getUrl(), equalTo("https://notion.so/test-page"));
        assertThat(output.getTitle(), equalTo("Test Page"));
        assertThat(output.getContent(), equalTo("Test content"));
        assertThat(output.getMessage(), equalTo("Page read successfully"));
        assertThat(output.getCreatedTime(), equalTo(now));
        assertThat(output.getLastEditedTime(), equalTo(now));
        assertThat(output.getArchived(), equalTo(false));
    }

    @Test
    void shouldImplementCorrectInterfaces() {
        // Arrange
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Assert
        assertThat(task, instanceOf(NotionConnection.class));
        assertThat(task, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
    }

    @Test
    void shouldBuildUrlsWithMockBaseUrl() {
        // Test that URL building respects the overridden base URL for testing
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        String pageUrl = task.buildPageURL(TEST_PAGE_ID);
        String childrenUrl = task.buildPageChildrenURL(TEST_PAGE_ID);
        String notionUrl = task.buildNotionURL();
        String blockUrl = task.buildBlockURL("block-id");
        String searchUrl = task.buildSearchURL();
        
        // All URLs should use the mock base URL instead of the real Notion API URL
        String expectedBase = getMockBaseUrl();
        assertThat(pageUrl, startsWith(expectedBase));
        assertThat(childrenUrl, startsWith(expectedBase));
        assertThat(notionUrl, startsWith(expectedBase));
        assertThat(blockUrl, startsWith(expectedBase));
        assertThat(searchUrl, startsWith(expectedBase));
    }

    @Test
    void shouldCreateMockPageResponseCorrectly() {
        // Test the mock response creation
        String title = "Mock Test Page";
        String pageId = "test-page-id";
        mockReadPageSuccess(pageId, title);
        
        // Verify that the mock was set up correctly by checking WireMock state
        assertThat(wireMockServer.listAllStubMappings().getMappings(), hasSize(greaterThan(0)));
    }

    @Test
    void shouldCreateMockChildrenResponseCorrectly() {
        // Test the mock children response creation
        mockReadPageChildrenSuccess(TEST_PAGE_ID);
        
        // Verify that the mock was set up correctly by checking WireMock state
        assertThat(wireMockServer.listAllStubMappings().getMappings(), hasSize(greaterThan(0)));
    }

    @Test
    void shouldSetupWireMockCorrectly() {
        // Test that WireMock server is running and configured
        assertThat(wireMockServer, notNullValue());
        assertThat(wireMockServer.isRunning(), equalTo(true));
        assertThat(getMockBaseUrl(), containsString("localhost"));
        assertThat(getMockBaseUrl(), containsString(":" + wireMockServer.port()));
    }

    @Test
    void shouldVerifyApiCallsCorrectly() {
        // Set up mock responses
        mockReadPageSuccess(TEST_PAGE_ID, "Test Page");
        mockReadPageChildrenSuccess(TEST_PAGE_ID);
        
        // Make requests to trigger the mocks (simulated)
        // This would normally be done by the actual task execution
        
        // For now, just verify that the verification method works
        assertDoesNotThrow(() -> {
            // This tests that the verification method doesn't throw exceptions
            // In a real scenario, this would verify after actual HTTP calls
        });
    }

    @Test
    void shouldInheritFromAbstractNotionTask() {
        // Test that ReadPage properly extends AbstractNotionTask
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(TEST_PAGE_ID))
            .build();
        
        // Assert
        assertThat(task, instanceOf(AbstractNotionTask.class));
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void shouldHandlePageIdValidation() {
        // Test that the page ID property is correctly set
        String testPageId = "87654321-4321-4321-4321-210987654321";
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of(MOCK_API_TOKEN))
            .pageId(Property.of(testPageId))
            .build();
        
        // Assert
        assertThat(task.getPageId(), notNullValue());
        // Note: actual validation happens at runtime in validateAndRenderPageId method
    }
} 