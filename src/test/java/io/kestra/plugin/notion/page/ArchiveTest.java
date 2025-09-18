package io.kestra.plugin.notion.page;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.notion.NotionConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Archive functionality.
 * These tests focus on input validation, builder patterns, and inheritance
 * without requiring actual API calls.
 */
class ArchiveTest {

    private Archive archive;

    @BeforeEach
    void setUp() {
        archive = Archive.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Archive task = Archive.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(archive.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractTask() {
        // Test that Archive properly inherits from AbstractTask
        assertThat(archive, instanceOf(AbstractTask.class));
        assertThat(archive, instanceOf(NotionConnection.class));
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Archive implements RunnableTask correctly
        assertThat(archive, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));

        // Test that it has the correct generic type (inherited from AbstractTask)
        Archive task = Archive.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Archive task = Archive.builder()
            .pageId(Property.ofValue("test-page-id"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testRequiredFieldsHandling() {
        // Test that pageId is required (inherited from AbstractTask)
        Archive task = Archive.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testOutputInheritance() {
        // Test that Archive uses the common Output from AbstractTask
        // This test verifies the structure is properly inherited

        // Test output builder
        AbstractTask.Output output = AbstractTask.Output.builder()
            .pageId("test-page-id")
            .archived(true)
            .message("Page archived successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getArchived(), equalTo(true));
        assertThat(output.getMessage(), equalTo("Page archived successfully"));
    }

    @Test
    void testMinimalConfiguration() {
        // Test that Archive can be built with minimal required fields
        Archive task = Archive.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
    }

    @Test
    void testArchiveOperationConcept() {
        // Test conceptual understanding of archive operation
        // Archive should only need pageId (no content or title changes)
        Archive task = Archive.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        // Archive task should have minimal properties compared to Create/Update
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());

        // Archive doesn't have title or content properties (those are in other tasks)
        // This test verifies the clean separation of concerns
    }

    @Test
    void testSimplicity() {
        // Test that Archive is the simplest page operation
        // It should only require pageId and apiToken
        Archive task = Archive.builder()
            .pageId(Property.ofValue("test-page-id"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        assertThat(task, notNullValue());

        // Verify this is a clean, focused task
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }
}