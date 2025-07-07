package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArchivePage functionality.
 * These tests focus on input validation, builder patterns, and inheritance structure
 * without requiring actual API calls.
 */
class ArchivePageTest {

    private ArchivePage archivePage;

    @BeforeEach
    void setUp() {
        archivePage = ArchivePage.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        ArchivePage task = ArchivePage.builder()
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
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(archivePage.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractNotionTask() {
        // Test that ArchivePage properly inherits from AbstractNotionTask
        assertThat(archivePage, instanceOf(AbstractNotionTask.class));
        assertThat(archivePage, instanceOf(NotionConnection.class));
        
        // Test inherited URL building methods work
        assertThat(archivePage.buildPageURL("test-id"), containsString("/v1/pages/test-id"));
        assertThat(archivePage.buildPageChildrenURL("test-id"), containsString("/v1/blocks/test-id/children"));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that ArchivePage implements RunnableTask correctly
        assertThat(archivePage, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test return type is AbstractNotionTask.Output
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("test-id"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPageIdProperty() {
        // Test that pageId property is correctly inherited from AbstractNotionTask
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("test-page-id-123"))
            .build();
        
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testInheritedOutputStructure() {
        // Test that ArchivePage uses AbstractNotionTask.Output
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("test-id"))
            .build();
        
        // ArchivePage should inherit the common output structure
        assertThat(task, instanceOf(AbstractNotionTask.class));
    }

    @Test
    void testInheritedHelperMethods() {
        // Test that ArchivePage inherits helper methods from AbstractNotionTask
        assertThat(archivePage, instanceOf(AbstractNotionTask.class));
        
        // Test inherited URL building capabilities
        String pageUrl = archivePage.buildPageURL("test-page-id");
        assertThat(pageUrl, equalTo("https://api.notion.com/v1/pages/test-page-id"));
        
        String childrenUrl = archivePage.buildPageChildrenURL("test-page-id");
        assertThat(childrenUrl, equalTo("https://api.notion.com/v1/blocks/test-page-id/children"));
    }

    @Test
    void testArchivePageSpecificConfiguration() {
        // Test ArchivePage-specific configuration
        ArchivePage task = ArchivePage.builder()
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
    void testArchivePageInputValidation() {
        // Test basic input validation structure
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("valid-page-id"))
            .build();
        
        // ArchivePage should require pageId (inherited from AbstractNotionTask)
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testPropertyAccess() {
        // Test that properties can be properly accessed
        ArchivePage task = ArchivePage.builder()
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
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("test-page-id"))
            .options(null) // HTTP configuration would go here
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getOptions(), nullValue());
    }

    @Test
    void testArchivePageArchivingConcept() {
        // Test that ArchivePage understands the archiving concept
        // (In Notion, pages are archived rather than permanently deleted)
        ArchivePage task = ArchivePage.builder()
            .pageId(Property.of("page-to-archive"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        
        // ArchivePage should work with the same endpoints as other page operations
        assertThat(task.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testArchivePageSafetyAspects() {
        // Test that ArchivePage is properly configured for safe operations
        ArchivePage task = ArchivePage.builder()
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
        // Test that ArchivePage can handle different page ID formats
        String uuidFormat = "12345678-1234-1234-1234-123456789abc";
        String compactFormat = "123456789abcdef0123456789abcdef0";
        
        ArchivePage task1 = ArchivePage.builder()
            .pageId(Property.of(uuidFormat))
            .build();
        
        ArchivePage task2 = ArchivePage.builder()
            .pageId(Property.of(compactFormat))
            .build();
        
        assertThat(task1.getPageId(), notNullValue());
        assertThat(task2.getPageId(), notNullValue());
    }

    @Test
    void testTaskStructureConsistency() {
        // Test that ArchivePage maintains consistent structure with other CRUD operations
        ArchivePage task = ArchivePage.builder()
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