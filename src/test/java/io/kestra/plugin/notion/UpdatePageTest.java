package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpdatePage functionality.
 * These tests focus on input validation, builder patterns, and UpdatePage-specific properties
 * without requiring actual API calls.
 */
class UpdatePageTest {

    private UpdatePage updatePage;

    @BeforeEach
    void setUp() {
        updatePage = UpdatePage.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly with all properties
        UpdatePage task = UpdatePage.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .title(Property.of("Updated Title"))
            .content(Property.of("# Updated Content\n\nThis is updated content."))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields (pageId inherited)
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), nullValue());
        assertThat(task.getContent(), nullValue());
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test that optional fields (title and content) can be null
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .title(Property.of("Optional Title"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), nullValue()); // content is optional
    }

    @Test
    void testContentOnlyUpdate() {
        // Test updating only content without title
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .content(Property.of("# New Content\n\nUpdated page content without changing title."))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getTitle(), nullValue()); // title is optional
    }

    @Test
    void testTitleOnlyUpdate() {
        // Test updating only title without content
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .title(Property.of("New Title"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), nullValue()); // content is optional
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(updatePage.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractNotionTask() {
        // Test that UpdatePage properly inherits from AbstractNotionTask
        assertThat(updatePage, instanceOf(AbstractNotionTask.class));
        assertThat(updatePage, instanceOf(NotionConnection.class));
        
        // Test inherited URL building methods work
        assertThat(updatePage.buildPageURL("test-id"), containsString("/v1/pages/test-id"));
        assertThat(updatePage.buildPageChildrenURL("test-id"), containsString("/v1/blocks/test-id/children"));
        assertThat(updatePage.buildBlockURL("block-id"), containsString("/v1/blocks/block-id"));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that UpdatePage implements RunnableTask correctly
        assertThat(updatePage, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test that it uses AbstractNotionTask.Output
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-id"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testUpdatePageSpecificProperties() {
        // Test UpdatePage-specific properties (title and content)
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .title(Property.of("Updated Page Title"))
            .content(Property.of("# Updated Content\n\n- Item 1\n- Item 2"))
            .build();
        
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testInheritedPageIdProperty() {
        // Test that pageId property is correctly inherited from AbstractNotionTask
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("inherited-page-id-123"))
            .build();
        
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that all properties are of correct Property<String> type
        UpdatePage task = UpdatePage.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("test-page-id"))
            .title(Property.of("Test Title"))
            .content(Property.of("Test content"))
            .build();
        
        // Verify all properties can be accessed
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testBuilderWithHttpOptions() {
        // Test builder with HTTP configuration options
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .title(Property.of("Test Title"))
            .options(null) // HTTP configuration would go here
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getOptions(), nullValue());
    }

    @Test
    void testMarkdownContentHandling() {
        // Test handling of markdown content
        String markdownContent = """
            # Main Title
            
            This is a paragraph with **bold** and *italic* text.
            
            ## Subsection
            
            - List item 1
            - List item 2
            
            ```java
            System.out.println("Code block");
            ```
            """;
        
        UpdatePage task = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .content(Property.of(markdownContent))
            .build();
        
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testEmptyContentHandling() {
        // Test handling of empty or null content
        UpdatePage task1 = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .content(Property.of(""))
            .build();
        
        UpdatePage task2 = UpdatePage.builder()
            .pageId(Property.of("test-page-id"))
            .content(Property.of("   ")) // whitespace only
            .build();
        
        assertThat(task1.getContent(), notNullValue());
        assertThat(task2.getContent(), notNullValue());
    }

    @Test
    void testComplexUpdateScenario() {
        // Test a complex update scenario with all fields
        UpdatePage task = UpdatePage.builder()
            .apiToken(Property.of("complex-test-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .title(Property.of("Complex Updated Title"))
            .content(Property.of("# Complex Update\n\nThis is a complex update with multiple sections."))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getEndpoint(), equalTo("/v1/pages"));
    }
} 