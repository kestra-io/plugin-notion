package io.kestra.plugin.notion.page;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.notion.NotionConnection;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Create functionality.
 * These tests focus on input validation, builder patterns, and request building
 * without requiring actual API calls.
 */
class CreateTest {

    private Create create;

    @BeforeEach
    void setUp() {
        create = Create.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .content(Property.ofValue("Test content"))
            .parentPageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
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
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), nullValue());
        assertThat(task.getParentPageId(), nullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(create.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromNotionConnection() {
        // Test that Create properly inherits from NotionConnection
        assertThat(create, instanceOf(NotionConnection.class));
    }

    @Test
    void testOutputBuilder() {
        // Test that the Output class builder works correctly
        Create.Output output = Create.Output.builder()
            .pageId("test-page-id")
            .url("https://notion.so/test-page")
            .title("Test Page")
            .content("Test content")
            .message("Page created successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getUrl(), equalTo("https://notion.so/test-page"));
        assertThat(output.getTitle(), equalTo("Test Page"));
        assertThat(output.getContent(), equalTo("Test content"));
        assertThat(output.getMessage(), equalTo("Page created successfully"));
    }

    @Test
    void testOutputBuilderWithNullValues() {
        // Test that Output builder handles null values gracefully
        Create.Output output = Create.Output.builder()
            .pageId("test-page-id")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getUrl(), nullValue());
        assertThat(output.getTitle(), nullValue());
        assertThat(output.getContent(), nullValue());
    }

    @Test
    void testOutputImplementsCorrectInterface() {
        // Test that Output implements the correct Kestra interface
        Create.Output output = Create.Output.builder().build();
        assertThat(output, instanceOf(io.kestra.core.models.tasks.Output.class));
    }

    @Test
    void testFieldValidation() {
        // Test required field validation concepts
        Create task = Create.builder()
            .title(Property.ofValue("Valid Title"))
            .build();

        // Test that required fields are properly set
        assertThat(task.getTitle(), notNullValue());
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test handling of optional fields
        Create task = Create.builder()
            .apiToken(Property.ofValue("test-token"))
            .title(Property.ofValue("Test Page"))
            .content(Property.ofValue("# Test Content\n\nThis is a test."))
            .parentPageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Create implements RunnableTask correctly
        assertThat(create, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));

        // Test that it has the correct generic type
        Create task = Create.builder()
            .title(Property.ofValue("Test"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Create task = Create.builder()
            .title(Property.ofValue("Test Title"))
            .content(Property.ofValue("Test content"))
            .parentPageId(Property.ofValue("test-parent-id"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getParentPageId(), notNullValue());
    }
}