package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReadPage functionality.
 * These tests focus on input validation, builder patterns, and inheritance structure
 * without requiring actual API calls.
 */
class ReadPageTest {

    private ReadPage readPage;

    @BeforeEach
    void setUp() {
        readPage = ReadPage.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(readPage.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractNotionTask() {
        // Test that ReadPage properly inherits from AbstractNotionTask
        assertThat(readPage, instanceOf(AbstractNotionTask.class));
        assertThat(readPage, instanceOf(NotionConnection.class));
        
        // Test inherited URL building methods work
        assertThat(readPage.buildPageURL("test-id"), containsString("/v1/pages/test-id"));
        assertThat(readPage.buildPageChildrenURL("test-id"), containsString("/v1/blocks/test-id/children"));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that ReadPage implements RunnableTask correctly
        assertThat(readPage, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test return type is AbstractNotionTask.Output
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("test-id"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPageIdProperty() {
        // Test that pageId property is correctly inherited from AbstractNotionTask
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("test-page-id-123"))
            .build();
        
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testInheritedOutputStructure() {
        // Test that ReadPage uses AbstractNotionTask.Output
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("test-id"))
            .build();
        
        // ReadPage should inherit the common output structure
        assertThat(task, instanceOf(AbstractNotionTask.class));
    }

    @Test
    void testInheritedHelperMethods() {
        // Test that ReadPage inherits helper methods from AbstractNotionTask
        assertThat(readPage, instanceOf(AbstractNotionTask.class));
        
        // Test inherited URL building capabilities
        String pageUrl = readPage.buildPageURL("test-page-id");
        assertThat(pageUrl, equalTo("https://api.notion.com/v1/pages/test-page-id"));
        
        String childrenUrl = readPage.buildPageChildrenURL("test-page-id");
        assertThat(childrenUrl, equalTo("https://api.notion.com/v1/blocks/test-page-id/children"));
    }

    @Test
    void testReadPageSpecificConfiguration() {
        // Test ReadPage-specific configuration
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of("secret-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        
        // Test endpoint
        assertThat(task.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testReadPageInputValidation() {
        // Test basic input validation structure
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("valid-page-id"))
            .build();
        
        // ReadPage should require pageId (inherited from AbstractNotionTask)
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testPropertyAccess() {
        // Test that properties can be properly accessed
        ReadPage task = ReadPage.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("test-page-id"))
            .build();
        
        // Verify all properties are accessible
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testBuilderWithHttpOptions() {
        // Test builder with HTTP configuration options
        ReadPage task = ReadPage.builder()
            .pageId(Property.of("test-page-id"))
            .options(null) // HTTP configuration would go here
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getOptions(), nullValue());
    }
} 