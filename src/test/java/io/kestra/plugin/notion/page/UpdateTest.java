package io.kestra.plugin.notion.page;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Update functionality.
 * These tests focus on input validation, builder patterns, and inheritance
 * without requiring actual API calls.
 */
class UpdateTest {

    private static class TestUpdate extends Update {
        private String capturedPageId;
        private ArrayNode capturedBlocks;
        private int addBlocksCalls;

        @Override
        protected void addBlocksToPage(RunContext runContext, String pageId, ArrayNode blocks) {
            this.capturedPageId = pageId;
            this.capturedBlocks = blocks;
            this.addBlocksCalls++;
        }
    }

    private Update update;

    @BeforeEach
    void setUp() {
        update = Update.builder().build();
    }

    @Test
    void testBuilderPattern() {
        // Test that the builder pattern works correctly
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("Updated Title"))
            .content(Property.ofValue("Updated content"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getApiToken(), notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testBuilderWithMinimalFields() {
        // Test that builder works with just required fields
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        assertThat(task, notNullValue());
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), nullValue());
        assertThat(task.getContent(), nullValue());
    }

    @Test
    void testGetEndpoint() {
        // Test that the endpoint is correctly set
        assertThat(update.getEndpoint(), equalTo("/v1/pages"));
    }

    @Test
    void testInheritanceFromAbstractTask() {
        // Test that Update properly inherits from AbstractTask
        assertThat(update, instanceOf(AbstractTask.class));
        assertThat(update, instanceOf(NotionConnection.class));
    }

    @Test
    void testOptionalFieldsHandling() {
        // Test handling of optional fields
        Update task = Update.builder()
            .apiToken(Property.ofValue("test-token"))
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("Updated Title"))
            .content(Property.ofValue("# Updated Content\n\nThis is updated content."))
            .build();

        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
    }

    @Test
    void testTaskInheritanceStructure() {
        // Test that Update implements RunnableTask correctly
        assertThat(update, instanceOf(io.kestra.core.models.tasks.RunnableTask.class));

        // Test that it has the correct generic type (inherited from AbstractTask)
        Update task = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();
        assertThat(task, notNullValue());
    }

    @Test
    void testPropertyTypes() {
        // Test that properties are of correct Property<String> type
        Update task = Update.builder()
            .pageId(Property.ofValue("test-page-id"))
            .title(Property.ofValue("Test Title"))
            .content(Property.ofValue("Test content"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        // Verify properties can be accessed (they should not be null)
        assertThat(task.getPageId(), notNullValue());
        assertThat(task.getTitle(), notNullValue());
        assertThat(task.getContent(), notNullValue());
        assertThat(task.getApiToken(), notNullValue());
    }

    @Test
    void testOutputInheritance() {
        // Test that Update uses the common Output from AbstractTask
        // This test verifies the structure is properly inherited

        // Test output builder
        AbstractTask.Output output = AbstractTask.Output.builder()
            .pageId("test-page-id")
            .title("Updated Title")
            .content("Updated content")
            .message("Page updated successfully")
            .build();

        assertThat(output, notNullValue());
        assertThat(output.getPageId(), equalTo("test-page-id"));
        assertThat(output.getTitle(), equalTo("Updated Title"));
        assertThat(output.getContent(), equalTo("Updated content"));
        assertThat(output.getMessage(), equalTo("Page updated successfully"));
    }

    @Test
    void testFieldValidation() {
        // Test required field validation concepts
        Update task = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .build();

        // Test that required fields are properly set
        assertThat(task.getPageId(), notNullValue());

        // Optional fields should be null if not set
        assertThat(task.getTitle(), nullValue());
        assertThat(task.getContent(), nullValue());
    }

    @Test
    void testFieldCombinations() {
        // Test different combinations of optional fields

        // Title only
        Update titleOnly = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("New Title"))
            .build();

        assertThat(titleOnly.getTitle(), notNullValue());
        assertThat(titleOnly.getContent(), nullValue());

        // Content only
        Update contentOnly = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .content(Property.ofValue("New content"))
            .build();

        assertThat(contentOnly.getTitle(), nullValue());
        assertThat(contentOnly.getContent(), notNullValue());

        // Both title and content
        Update both = Update.builder()
            .pageId(Property.ofValue("12345678-1234-1234-1234-123456789abc"))
            .title(Property.ofValue("New Title"))
            .content(Property.ofValue("New content"))
            .build();

        assertThat(both.getTitle(), notNullValue());
        assertThat(both.getContent(), notNullValue());
    }

    @Test
    void testAppendPageContentAddsBlocks() throws Exception {
        TestUpdate task = new TestUpdate();

        task.appendPageContent(null, "test-page-id", "# Title\n\nParagraph");

        assertThat(task.addBlocksCalls, equalTo(1));
        assertThat(task.capturedPageId, equalTo("test-page-id"));
        assertThat(task.capturedBlocks, notNullValue());
        assertThat(task.capturedBlocks.size(), greaterThan(0));
    }

    @Test
    void testAppendPageContentSkipsBlankContent() throws Exception {
        TestUpdate task = new TestUpdate();

        task.appendPageContent(null, "test-page-id", "   ");
        task.appendPageContent(null, "test-page-id", null);

        assertThat(task.addBlocksCalls, equalTo(0));
    }

    @Test
    void testUpdateSchemaDescriptionMentionsAppend() {
        Schema schema = Update.class.getAnnotation(Schema.class);
        assertThat(schema, notNullValue());
        assertThat(schema.description(), containsString("append"));
        assertThat(schema.description(), not(containsString("replace")));
    }

    @Test
    void testContentSchemaDescriptionMentionsAppend() throws Exception {
        Field contentField = Update.class.getDeclaredField("content");
        Schema schema = contentField.getAnnotation(Schema.class);
        assertThat(schema, notNullValue());
        assertThat(schema.description(), containsString("append"));
        assertThat(schema.description(), not(containsString("replace")));
    }
}
