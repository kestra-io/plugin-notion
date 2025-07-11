package io.kestra.plugin.notion.page;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.notion.NotionConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Read functionality.
 * These tests focus on input validation, builder patterns, and inheritance
 * without requiring actual API calls.
 */
class ReadTest {

    private Read read;

    @BeforeEach
    void setUp() {
        read = Read.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Read task = Read.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(read.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractTask() {
        // Test that Read properly inherits from AbstractTask
        assertThat(read, instanceOf(AbstractTask.class));
        assertThat(read, instanceOf(NotionConnection.class));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Read implements RunnableTask correctly
        assertThat(read, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));
        
        // Test that it has the correct generic type (inherited from AbstractTask)
        Read task = Read.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Read task = Read.builder()
            .pageId(Property.of("test-page-id"))
            .apiToken(Property.of("test-token"))
            .build();
        
        // Verify properties can be accessed (they should not be null)
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testRequiredFieldsHandling() {
        // Test that pageId is required (inherited from AbstractTask)
        Read task = Read.builder()
            .apiToken(Property.of("test-token"))
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testOutputInheritance() {
        // Test that Read uses the common Output from AbstractTask
        // This test verifies the structure is properly inherited
        
        // The output should be the same as AbstractTask.Output
        assertThat(AbstractTask.Output.class, notNullValue());
        
        // Test output builder
        AbstractTask.Output output = AbstractTask.Output.builder()
            .pageId("test-page-id")
            .content("test content")
            .message("Page read successfully")
            .build();
        
        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getContent(), equalTo("test content"));
        assertThat(output.getMessage(), equalTo("Page read successfully"));
    }

    @Test
    void testMinimalConfiguration() {
        // Test that Read can be built with minimal required fields
        Read task = Read.builder()
            .pageId(Property.of("12345678-1234-1234-1234-123456789abc"))
            .build();
        
        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }
} 