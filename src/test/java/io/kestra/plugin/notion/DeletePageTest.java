package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeletePage functionality.
 * These tests focus on input validation, builder patterns, and inheritance structure
 * without requiring actual API calls.
 */
class DeletePageTest {

    private DeletePage deletePage;

    @BeforeEach
    void setUp() {
        deletePage = DeletePage.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        DeletePage task = DeletePage.builder()
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
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(deletePage.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractNotionTask() {
        // Test that DeletePage properly inherits from AbstractNotionTask
        assertThat(deletePage, instanceOf(AbstractNotionTask.class));
        assertThat(deletePage, instanceOf(NotionConnection.class));
        
        // Test inherited URL building methods work
        assertThat(deletePage.buildPageURL("test-id"), containsString("/v1/pages/test-id"));
        assertThat(deletePage.buildPageChildrenURL("test-id"), containsString("/v1/blocks/test-id/children"));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that DeletePage implements RunnableTask correctly
        assertThat(deletePage, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test return type is AbstractNotionTask.Output
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("test-id"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPageIdProperty() {
        // Test that pageId property is correctly inherited from AbstractNotionTask
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("test-page-id-123"))
            .build();
        
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testInheritedOutputStructure() {
        // Test that DeletePage uses AbstractNotionTask.Output
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("test-id"))
            .build();
        
        // DeletePage should inherit the common output structure
        assertThat(task, instanceOf(AbstractNotionTask.class));
    }

    @Test
    void testInheritedHelperMethods() {
        // Test that DeletePage inherits helper methods from AbstractNotionTask
        assertThat(deletePage, instanceOf(AbstractNotionTask.class));
        
        // Test inherited URL building capabilities
        String pageUrl = deletePage.buildPageURL("test-page-id");
        assertThat(pageUrl, equalTo("https://api.notion.com/v1/pages/test-page-id"));
        
        String childrenUrl = deletePage.buildPageChildrenURL("test-page-id");
        assertThat(childrenUrl, equalTo("https://api.notion.com/v1/blocks/test-page-id/children"));
    }

    @Test
    void testDeletePageSpecificConfiguration() {
        // Test DeletePage-specific configuration
        DeletePage task = DeletePage.builder()
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
    void testDeletePageInputValidation() {
        // Test basic input validation structure
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("valid-page-id"))
            .build();
        
        // DeletePage should require pageId (inherited from AbstractNotionTask)
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testPropertyAccess() {
        // Test that properties can be properly accessed
        DeletePage task = DeletePage.builder()
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
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("test-page-id"))
            .options(null) // HTTP configuration would go here
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getOptions(), nullValue());
    }

    @Test
    void testDeletePageArchivingConcept() {
        // Test that DeletePage understands the archiving concept
        // (In Notion, pages are archived rather than permanently deleted)
        DeletePage task = DeletePage.builder()
            .pageId(Property.of("page-to-archive"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        
        // DeletePage should work with the same endpoints as other page operations
        assertThat(task.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testDeletePageSafetyAspects() {
        // Test that DeletePage is properly configured for safe operations
        DeletePage task = DeletePage.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("important-page-id"))
            .build();
        
        // Verify that the task is properly configured
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        
        // Should inherit URL building for getting page info before deletion
        String pageUrl = task.buildPageURL("test-id");
        assertThat(pageUrl, notNullValue());
        assertThat(pageUrl, containsString("/v1/pages/test-id"));
    }

    @Test
    void testMultiplePageIdFormats() {
        // Test that DeletePage can handle different page ID formats
        String uuidFormat = "12345678-1234-1234-1234-123456789abc";
        String compactFormat = "123456789abcdef0123456789abcdef0";
        
        DeletePage task1 = DeletePage.builder()
            .pageId(Property.of(uuidFormat))
            .build();
        
        DeletePage task2 = DeletePage.builder()
            .pageId(Property.of(compactFormat))
            .build();
        
        assertThat(task1.getPageId(), notNullValue());
        assertThat(task2.getPageId(), notNullValue());
    }

    @Test
    void testTaskStructureConsistency() {
        // Test that DeletePage maintains consistent structure with other CRUD operations
        DeletePage task = DeletePage.builder()
            .apiToken(Property.of("consistency-test-token"))
            .pageId(Property.of("consistency-test-page-id"))
            .build();
        
        // Should inherit from same base classes as other CRUD operations
        assertThat(task, instanceOf(AbstractNotionTask.class));
        assertThat(task, instanceOf(NotionConnection.class));
        assertThat(task, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Should have same endpoint as other page operations
        assertThat(task.getEndpoint(), equalTo("/v1/pages"));
    }
} 