package io.kestra.plugin.notion.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarkdownConverter utility functionality.
 * These tests cover markdown to Notion blocks conversion, Notion blocks to markdown conversion,
 * and various edge cases without requiring actual API calls.
 */
class MarkdownConverterTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // =========================
    // Markdown to Blocks Tests
    // =========================

    @Test
    void testEmptyMarkdownToBlocks() {
        // Test empty string
        ArrayNode result = MarkdownConverter.markdownToBlocks("");
        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(0));

        // Test whitespace only
        result = MarkdownConverter.markdownToBlocks("   \n  \t  ");
        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(0));
    }

    @Test
    void testNullMarkdownToBlocks() {
        // Test null input
        ArrayNode result = MarkdownConverter.markdownToBlocks(null);
        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(0));
    }

    @Test
    void testSimpleParagraphToBlocks() {
        String markdown = "This is a simple paragraph.";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(1));

        JsonNode block = result.get(0);
        assertThat(block.path("type").asText(), equalTo("paragraph"));
        assertThat(block.path("object").asText(), equalTo("block"));
        assertThat(block.has("paragraph"), equalTo(true));
    }

    @Test
    void testHeadersToBlocks() {
        String markdown = "# Header 1\n## Header 2\n### Header 3\n#### Header 4";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(4));

        // Test H1
        JsonNode h1 = result.get(0);
        assertThat(h1.path("type").asText(), equalTo("heading_1"));
        assertThat(h1.has("heading_1"), equalTo(true));

        // Test H2
        JsonNode h2 = result.get(1);
        assertThat(h2.path("type").asText(), equalTo("heading_2"));
        assertThat(h2.has("heading_2"), equalTo(true));

        // Test H3
        JsonNode h3 = result.get(2);
        assertThat(h3.path("type").asText(), equalTo("heading_3"));
        assertThat(h3.has("heading_3"), equalTo(true));

        // Test H4+ (should be converted to H3)
        JsonNode h4 = result.get(3);
        assertThat(h4.path("type").asText(), equalTo("heading_3"));
        assertThat(h4.has("heading_3"), equalTo(true));
    }

    @Test
    void testBulletedListToBlocks() {
        String markdown = "- Item 1\n- Item 2\n* Item 3\n+ Item 4";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(4));

        for (int i = 0; i < 4; i++) {
            JsonNode block = result.get(i);
            assertThat(block.path("type").asText(), equalTo("bulleted_list_item"));
            assertThat(block.has("bulleted_list_item"), equalTo(true));
        }
    }

    @Test
    void testNumberedListToBlocks() {
        String markdown = "1. First item\n2. Second item\n3. Third item";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(3));

        for (int i = 0; i < 3; i++) {
            JsonNode block = result.get(i);
            assertThat(block.path("type").asText(), equalTo("numbered_list_item"));
            assertThat(block.has("numbered_list_item"), equalTo(true));
        }
    }

    @Test
    void testCodeBlockToBlocks() {
        String markdown = "```java\nSystem.out.println(\"Hello\");\n```";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(1));

        JsonNode block = result.get(0);
        assertThat(block.path("type").asText(), equalTo("code"));
        assertThat(block.has("code"), equalTo(true));

        JsonNode codeBlock = block.path("code");
        assertThat(codeBlock.path("language").asText(), equalTo("java"));
    }

    @Test
    void testCodeBlockWithoutLanguage() {
        String markdown = "```\nSome code without language\n```";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(1));

        JsonNode block = result.get(0);
        assertThat(block.path("type").asText(), equalTo("code"));
        assertThat(block.has("code"), equalTo(true));
    }

    @Test
    void testMixedContentToBlocks() {
        String markdown = """
            # Main Title

            This is a paragraph with some content.

            ## Subsection

            - List item 1
            - List item 2

            1. Numbered item 1
            2. Numbered item 2

            ```python
            print("Hello, World!")
            ```

            Another paragraph.
            """;

        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), greaterThan(5)); // Should have multiple blocks

        // Check that different block types are present
        boolean hasHeading = false;
        boolean hasParagraph = false;
        boolean hasBulletList = false;
        boolean hasNumberedList = false;
        boolean hasCode = false;

        for (JsonNode block : result) {
            String type = block.path("type").asText();
            switch (type) {
                case "heading_1", "heading_2", "heading_3" -> hasHeading = true;
                case "paragraph" -> hasParagraph = true;
                case "bulleted_list_item" -> hasBulletList = true;
                case "numbered_list_item" -> hasNumberedList = true;
                case "code" -> hasCode = true;
            }
        }

        assertThat(hasHeading, equalTo(true));
        assertThat(hasParagraph, equalTo(true));
        assertThat(hasBulletList, equalTo(true));
        assertThat(hasNumberedList, equalTo(true));
        assertThat(hasCode, equalTo(true));
    }

    // =========================
    // Blocks to Markdown Tests
    // =========================

    @Test
    void testNullBlocksToMarkdown() {
        String result = MarkdownConverter.blocksToMarkdown(null);
        assertThat(result, equalTo(""));
    }

    @Test
    void testEmptyBlocksToMarkdown() {
        ArrayNode emptyBlocks = mapper.createArrayNode();
        String result = MarkdownConverter.blocksToMarkdown(emptyBlocks);
        assertThat(result, equalTo(""));
    }

    @Test
    void testParagraphBlockToMarkdown() {
        ObjectNode block = createSimpleParagraphBlock("This is a test paragraph.");
        ArrayNode blocks = mapper.createArrayNode();
        blocks.add(block);

        String result = MarkdownConverter.blocksToMarkdown(blocks);
        assertThat(result, containsString("This is a test paragraph."));
    }

    @Test
    void testHeaderBlocksToMarkdown() {
        ArrayNode blocks = mapper.createArrayNode();

        blocks.add(createSimpleHeaderBlock("heading_1", "Header 1"));
        blocks.add(createSimpleHeaderBlock("heading_2", "Header 2"));
        blocks.add(createSimpleHeaderBlock("heading_3", "Header 3"));

        String result = MarkdownConverter.blocksToMarkdown(blocks);

        assertThat(result, containsString("# Header 1"));
        assertThat(result, containsString("## Header 2"));
        assertThat(result, containsString("### Header 3"));
    }

    @Test
    void testListBlocksToMarkdown() {
        ArrayNode blocks = mapper.createArrayNode();

        blocks.add(createSimpleListBlock("bulleted_list_item", "Bullet item 1"));
        blocks.add(createSimpleListBlock("bulleted_list_item", "Bullet item 2"));
        blocks.add(createSimpleListBlock("numbered_list_item", "Numbered item 1"));
        blocks.add(createSimpleListBlock("numbered_list_item", "Numbered item 2"));

        String result = MarkdownConverter.blocksToMarkdown(blocks);

        assertThat(result, containsString("- Bullet item 1"));
        assertThat(result, containsString("- Bullet item 2"));
        assertThat(result, containsString("1. Numbered item 1"));
        assertThat(result, containsString("1. Numbered item 2"));
    }

    @Test
    void testCodeBlockToMarkdown() {
        ObjectNode block = createSimpleCodeBlock("java", "System.out.println(\"Hello\");");
        ArrayNode blocks = mapper.createArrayNode();
        blocks.add(block);

        String result = MarkdownConverter.blocksToMarkdown(blocks);

        assertThat(result, containsString("```java"));
        assertThat(result, containsString("System.out.println(\"Hello\");"));
        assertThat(result, containsString("```"));
    }

    @Test
    void testUnsupportedBlockTypeToMarkdown() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "unsupported_type");

        ObjectNode unsupportedData = mapper.createObjectNode();
        ArrayNode richText = createSimpleRichText("Some unsupported content");
        unsupportedData.set("rich_text", richText);
        block.set("unsupported_type", unsupportedData);

        ArrayNode blocks = mapper.createArrayNode();
        blocks.add(block);

        String result = MarkdownConverter.blocksToMarkdown(blocks);
        assertThat(result, containsString("Some unsupported content"));
    }

    // =========================
    // Rich Text Formatting Tests
    // =========================

    @Test
    void testRichTextWithFormattingToMarkdown() {
        ArrayNode richText = mapper.createArrayNode();

        // Bold text
        richText.add(createFormattedRichText("Bold text", true, false, false, null));

        // Italic text
        richText.add(createFormattedRichText("Italic text", false, true, false, null));

        // Code text
        richText.add(createFormattedRichText("Code text", false, false, true, null));

        // Link text
        richText.add(createFormattedRichText("Link text", false, false, false, "https://example.com"));

        ObjectNode block = createParagraphBlockWithRichText(richText);
        ArrayNode blocks = mapper.createArrayNode();
        blocks.add(block);

        String result = MarkdownConverter.blocksToMarkdown(blocks);

        assertThat(result, containsString("**Bold text**"));
        assertThat(result, containsString("*Italic text*"));
        assertThat(result, containsString("`Code text`"));
        assertThat(result, containsString("[Link text](https://example.com)"));
    }

    // =========================
    // Edge Cases and Error Handling
    // =========================

    @Test
    void testMalformedJsonToMarkdown() {
        ObjectNode malformedBlock = mapper.createObjectNode();
        malformedBlock.put("type", "paragraph");
        // Missing the paragraph object

        ArrayNode blocks = mapper.createArrayNode();
        blocks.add(malformedBlock);

        String result = MarkdownConverter.blocksToMarkdown(blocks);
        // Should handle gracefully and not crash
        assertThat(result, notNullValue());
    }

    @Test
    void testVeryLongContentHandling() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is line ").append(i).append(" of very long content. ");
        }

        String markdown = longContent.toString();
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), greaterThan(0));
    }

    @Test
    void testSpecialCharactersInMarkdown() {
        String markdown = "# Header with special chars: éñüç\n\nParagraph with symbols: @#$%^&*()";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), equalTo(2));
    }

    @Test
    void testNestedCodeBlocksInMarkdown() {
        String markdown = """
            Here's some code:

            ```markdown
            # This is markdown inside a code block
            - List item
            ```

            And more content.
            """;

        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result, notNullValue());
        assertThat(result.size(), greaterThan(1));

        // Should have at least one code block
        boolean hasCodeBlock = false;
        for (JsonNode block : result) {
            if ("code".equals(block.path("type").asText())) {
                hasCodeBlock = true;
                break;
            }
        }
        assertThat(hasCodeBlock, equalTo(true));
    }

    // =========================
    // Helper Methods for Test Data Creation
    // =========================

    private ObjectNode createSimpleParagraphBlock(String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "paragraph");

        ObjectNode paragraph = mapper.createObjectNode();
        paragraph.set("rich_text", createSimpleRichText(text));
        block.set("paragraph", paragraph);

        return block;
    }

    private ObjectNode createSimpleHeaderBlock(String level, String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", level);

        ObjectNode header = mapper.createObjectNode();
        header.set("rich_text", createSimpleRichText(text));
        block.set(level, header);

        return block;
    }

    private ObjectNode createSimpleListBlock(String type, String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", type);

        ObjectNode listItem = mapper.createObjectNode();
        listItem.set("rich_text", createSimpleRichText(text));
        block.set(type, listItem);

        return block;
    }

    private ObjectNode createSimpleCodeBlock(String language, String content) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "code");

        ObjectNode code = mapper.createObjectNode();
        code.set("rich_text", createSimpleRichText(content));
        if (language != null) {
            code.put("language", language);
        }
        block.set("code", code);

        return block;
    }

    private ArrayNode createSimpleRichText(String text) {
        ArrayNode richText = mapper.createArrayNode();

        ObjectNode richTextItem = mapper.createObjectNode();
        richTextItem.put("type", "text");
        richTextItem.put("plain_text", text);

        ObjectNode textObject = mapper.createObjectNode();
        textObject.put("content", text);
        richTextItem.set("text", textObject);

        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("bold", false);
        annotations.put("italic", false);
        annotations.put("code", false);
        richTextItem.set("annotations", annotations);

        richText.add(richTextItem);
        return richText;
    }

    private ObjectNode createFormattedRichText(String text, boolean bold, boolean italic, boolean code, String href) {
        ObjectNode richTextItem = mapper.createObjectNode();
        richTextItem.put("type", "text");
        richTextItem.put("plain_text", text);

        ObjectNode textObject = mapper.createObjectNode();
        textObject.put("content", text);
        richTextItem.set("text", textObject);

        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("bold", bold);
        annotations.put("italic", italic);
        annotations.put("code", code);
        richTextItem.set("annotations", annotations);

        if (href != null) {
            richTextItem.put("href", href);
        }

        return richTextItem;
    }

    private ObjectNode createParagraphBlockWithRichText(ArrayNode richText) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "paragraph");

        ObjectNode paragraph = mapper.createObjectNode();
        paragraph.set("rich_text", richText);
        block.set("paragraph", paragraph);

        return block;
    }
}