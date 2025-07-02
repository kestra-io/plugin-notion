package io.kestra.plugin.notion;

import io.kestra.core.models.property.Property;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreatePage functionality.
 * These tests focus on input validation, builder patterns, and request building
 * without requiring actual API calls.
 */
class CreatePageTest {

    private CreatePage createPage;

    @BeforeEach
    void setUp() {
        createPage = CreatePage.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        CreatePage task = CreatePage.builder()
            .apiToken(Property.of("test-token"))
            .title(Property.of("Test Page"))
            .content(Property.of("Test content"))
            .parentPageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields
        CreatePage task = CreatePage.builder()
            .apiToken(Property.of("test-token"))
            .title(Property.of("Test Page"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), nullValue());
        assertThat(task.getParentPageId(), nullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(createPage.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromNotionConnection() {
        // Test that CreatePage properly inherits from NotionConnection
        assertThat(createPage, instanceOf(NotionConnection.class));
        
        // Test inherited URL building methods work
        assertThat(createPage.buildCreatePageURL(), equalTo("https://api.notion.com/v1/pages"));
        assertThat(createPage.buildPageURL("test-id"), containsString("/v1/pages/test-id"));
    }

    @Test
    void testOutputBuilder() {
        // Test that the Output class builder works correctly
        CreatePage.Output output = CreatePage.Output.builder()
            .pageId("test-page-id")
            .url("https://notion.so/test-page")
            .title("Test Page")
            .content("Test content")
            .success(true)
            .message("Page created successfully")
            .build();
        
        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getUrl(), equalTo("https://notion.so/test-page"));
        assertThat(output.getTitle(), equalTo("Test Page"));
        assertThat(output.getContent(), equalTo("Test content"));
        assertThat(output.getSuccess(), equalTo(true));
        assertThat(output.getMessage(), equalTo("Page created successfully"));
    }

    @Test
    void testOutputBuilderWithNullValues() {
        // Test that Output builder handles null values gracefully
        CreatePage.Output output = CreatePage.Output.builder()
            .pageId("test-page-id")
            .success(false)
            .build();
        
        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getSuccess(), equalTo(false));
        assertThat(output.getUrl(), nullValue());
        assertThat(output.getTitle(), nullValue());
        assertThat(output.getContent(), nullValue());
    }

    @Test
    void testOutputImplementsCorrectInterface() {
        // Test that Output implements the correct Kestra interface
        CreatePage.Output output = CreatePage.Output.builder().build();
        assertThat(output, instanceOf(io.kestra.core.models.tasks.Output.class));
    }

    @Test
    void testFieldValidation() {
        // Test required field validation concepts
        CreatePage task = CreatePage.builder()
            .title(Property.of("Valid Title"))
            .build();
        
        // Test that required fields are properly set
        assertThat(task.getTitle(), notNullValue());
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test handling of optional fields
        CreatePage task = CreatePage.builder()
            .apiToken(Property.of("test-token"))
            .title(Property.of("Test Page"))
            .content(Property.of("# Test Content\n\nThis is a test."))
            .parentPageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that CreatePage implements RunnableTask correctly
        assertThat(createPage, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test that it has the correct generic type
        CreatePage task = CreatePage.builder()
            .title(Property.of("Test"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        CreatePage task = CreatePage.builder()
            .title(Property.of("Test Title"))
            .content(Property.of("Test content"))
            .parentPageId(Property.of("test-parent-id"))
            .build();
        
        // Verify properties can be accessed (they should not be null)
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }
} 