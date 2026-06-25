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
    // Markdown Link Parsing Tests
    // =========================

    @Test
    void testMarkdownLinkInParagraph() {
        var markdown = "Check out [Kestra](https://kestra.io) for orchestration.";
        var result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result.size(), equalTo(1));

        var richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(richText.size(), equalTo(3));

        // Plain text before link
        var before = richText.get(0);
        assertThat(before.path("text").path("content").asText(), equalTo("Check out "));
        assertThat(before.path("text").has("link"), equalTo(false));
        assertThat(before.has("href"), equalTo(false));

        // Link segment
        var link = richText.get(1);
        assertThat(link.path("text").path("content").asText(), equalTo("Kestra"));
        assertThat(link.path("text").path("link").path("url").asText(), equalTo("https://kestra.io"));
        assertThat(link.path("href").asText(), equalTo("https://kestra.io"));

        // Plain text after link
        var after = richText.get(2);
        assertThat(after.path("text").path("content").asText(), equalTo(" for orchestration."));
        assertThat(after.path("text").has("link"), equalTo(false));
    }

    @Test
    void testMarkdownMultipleLinks() {
        var markdown = "[A](https://a.com) and [B](https://b.com)";
        var result = MarkdownConverter.markdownToBlocks(markdown);

        var richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(richText.size(), equalTo(3));

        assertThat(richText.get(0).path("text").path("content").asText(), equalTo("A"));
        assertThat(richText.get(0).path("href").asText(), equalTo("https://a.com"));

        assertThat(richText.get(1).path("text").path("content").asText(), equalTo(" and "));

        assertThat(richText.get(2).path("text").path("content").asText(), equalTo("B"));
        assertThat(richText.get(2).path("href").asText(), equalTo("https://b.com"));
    }

    @Test
    void testMarkdownLinkOnly() {
        var markdown = "[Docs](https://docs.example.com)";
        var result = MarkdownConverter.markdownToBlocks(markdown);

        var richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(richText.size(), equalTo(1));

        var link = richText.get(0);
        assertThat(link.path("text").path("content").asText(), equalTo("Docs"));
        assertThat(link.path("text").path("link").path("url").asText(), equalTo("https://docs.example.com"));
        assertThat(link.path("href").asText(), equalTo("https://docs.example.com"));
    }

    @Test
    void testMarkdownNoLinks() {
        var markdown = "Plain text without any links.";
        var result = MarkdownConverter.markdownToBlocks(markdown);

        var richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(richText.size(), equalTo(1));
        assertThat(richText.get(0).path("text").path("content").asText(), equalTo("Plain text without any links."));
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
    // Issue #56: GFM tables, inline formatting, backslash escapes
    // =========================

    @Test
    void testGfmTableToBlocks() {
        String markdown = """
            | Feature | Value |
            |---------|-------|
            | Tables  | yes   |
            | Bold    | also  |
            """;

        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result.size(), equalTo(1));
        JsonNode table = result.get(0);
        assertThat(table.path("type").asText(), equalTo("table"));

        JsonNode tableData = table.path("table");
        assertThat(tableData.path("table_width").asInt(), equalTo(2));
        assertThat(tableData.path("has_column_header").asBoolean(), equalTo(true));
        assertThat(tableData.path("has_row_header").asBoolean(), equalTo(false));

        JsonNode rows = tableData.path("children");
        assertThat(rows.size(), equalTo(3)); // header + 2 body rows

        JsonNode headerRow = rows.get(0);
        assertThat(headerRow.path("type").asText(), equalTo("table_row"));
        JsonNode headerCells = headerRow.path("table_row").path("cells");
        assertThat(headerCells.size(), equalTo(2));
        assertThat(headerCells.get(0).get(0).path("text").path("content").asText(), equalTo("Feature"));
        assertThat(headerCells.get(1).get(0).path("text").path("content").asText(), equalTo("Value"));

        JsonNode firstBodyCell = rows.get(1).path("table_row").path("cells").get(0).get(0);
        assertThat(firstBodyCell.path("text").path("content").asText(), equalTo("Tables"));
    }

    @Test
    void testInlineFormattingAnnotations() {
        String markdown = "A paragraph with **bold**, *italic*, `code`, and ~~struck~~ text.";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result.size(), equalTo(1));
        JsonNode richText = result.get(0).path("paragraph").path("rich_text");

        assertThat(annotationFor(richText, "bold").path("bold").asBoolean(), equalTo(true));
        assertThat(annotationFor(richText, "italic").path("italic").asBoolean(), equalTo(true));
        assertThat(annotationFor(richText, "code").path("code").asBoolean(), equalTo(true));
        assertThat(annotationFor(richText, "struck").path("strikethrough").asBoolean(), equalTo(true));
    }

    @Test
    void testBoldLinkCombination() {
        String markdown = "**[Kestra](https://kestra.io)**";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        JsonNode richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(richText.size(), equalTo(1));

        JsonNode item = richText.get(0);
        assertThat(item.path("text").path("content").asText(), equalTo("Kestra"));
        assertThat(item.path("text").path("link").path("url").asText(), equalTo("https://kestra.io"));
        assertThat(item.path("href").asText(), equalTo("https://kestra.io"));
        assertThat(item.path("annotations").path("bold").asBoolean(), equalTo(true));
    }

    @Test
    void testBackslashEscapesAreResolved() {
        String markdown = "A price of \\$500 and a literal \\*asterisk\\*.";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        String text = concatRichText(result.get(0).path("paragraph").path("rich_text"));
        assertThat(text, equalTo("A price of $500 and a literal *asterisk*."));
        assertThat(text, not(containsString("\\")));
    }

    @Test
    void testTaskListBecomesTodoBlocks() {
        String markdown = "- [ ] Review proposal\n- [x] Schedule follow-up";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(result.size(), equalTo(2));

        JsonNode first = result.get(0);
        assertThat(first.path("type").asText(), equalTo("to_do"));
        assertThat(first.path("to_do").path("checked").asBoolean(), equalTo(false));
        assertThat(concatRichText(first.path("to_do").path("rich_text")).trim(), equalTo("Review proposal"));

        JsonNode second = result.get(1);
        assertThat(second.path("type").asText(), equalTo("to_do"));
        assertThat(second.path("to_do").path("checked").asBoolean(), equalTo(true));
    }

    @Test
    void testCodeBlockWithoutLanguageDefaultsToPlainText() {
        String markdown = "```\nsome code\n```";
        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        JsonNode code = result.get(0);
        assertThat(code.path("type").asText(), equalTo("code"));
        assertThat(code.path("code").path("language").asText(), equalTo("plain text"));
    }

    @Test
    void testIssue56Repro() {
        String markdown = """
            ## Repro

            A paragraph with **bold**, *italic*, `code`, a [link](https://kestra.io), and a price of \\$500.

            | Feature | Value |
            |---|---|
            | Tables | should render |
            | Bold   | **yes**       |
            """;

        ArrayNode result = MarkdownConverter.markdownToBlocks(markdown);

        assertThat(blockTypes(result), hasItems("heading_2", "paragraph", "table"));

        JsonNode paragraph = firstBlockOfType(result, "paragraph");
        String paraText = concatRichText(paragraph.path("paragraph").path("rich_text"));
        assertThat(paraText, containsString("$500")); // escape resolved
        assertThat(paraText, not(containsString("\\$")));

        JsonNode table = firstBlockOfType(result, "table");
        assertThat(table.path("table").path("table_width").asInt(), equalTo(2));

        // Bold inside a table cell carries the annotation.
        JsonNode boldCell = table.path("table").path("children").get(2)
            .path("table_row").path("cells").get(1).get(0);
        assertThat(boldCell.path("text").path("content").asText(), equalTo("yes"));
        assertThat(boldCell.path("annotations").path("bold").asBoolean(), equalTo(true));
    }

    @Test
    void testCodeFenceLanguageAliasesMapToNotionEnum() {
        assertThat(codeLanguage("```js\nx\n```"), equalTo("javascript"));
        assertThat(codeLanguage("```ts\nx\n```"), equalTo("typescript"));
        assertThat(codeLanguage("```yml\nx\n```"), equalTo("yaml"));
        assertThat(codeLanguage("```Java\nx\n```"), equalTo("java"));
    }

    @Test
    void testUnknownCodeFenceLanguageFallsBackToPlainText() {
        assertThat(codeLanguage("```no-such-lang\nx\n```"), equalTo("plain text"));
    }

    private String codeLanguage(String markdown) {
        return MarkdownConverter.markdownToBlocks(markdown).get(0).path("code").path("language").asText();
    }

    @Test
    void testEmptyLinkDestinationRendersAsPlainText() {
        ArrayNode result = MarkdownConverter.markdownToBlocks("see [here]() now");

        JsonNode richText = result.get(0).path("paragraph").path("rich_text");
        assertThat(concatRichText(richText), equalTo("see here now"));
        for (JsonNode item : richText) {
            assertThat(item.path("text").has("link"), equalTo(false));
            assertThat(item.has("href"), equalTo(false));
        }
    }

    private JsonNode annotationFor(JsonNode richText, String content) {
        for (JsonNode item : richText) {
            if (content.equals(item.path("text").path("content").asText())) {
                return item.path("annotations");
            }
        }
        throw new AssertionError("No rich text item with content: " + content);
    }

    private String concatRichText(JsonNode richText) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : richText) {
            sb.append(item.path("text").path("content").asText());
        }
        return sb.toString();
    }

    private java.util.List<String> blockTypes(ArrayNode blocks) {
        java.util.List<String> types = new java.util.ArrayList<>();
        for (JsonNode block : blocks) {
            types.add(block.path("type").asText());
        }
        return types;
    }

    private JsonNode firstBlockOfType(ArrayNode blocks, String type) {
        for (JsonNode block : blocks) {
            if (type.equals(block.path("type").asText())) {
                return block;
            }
        }
        throw new AssertionError("No block of type: " + type);
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