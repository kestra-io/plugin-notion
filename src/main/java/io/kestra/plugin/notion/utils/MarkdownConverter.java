package io.kestra.plugin.notion.utils;

import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for converting between Markdown and Notion blocks format.
 * Supports bidirectional conversion for common content types.
 */
public class MarkdownConverter {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownConverter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // Markdown patterns
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^[\\s]*[-*+]\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("^[\\s]*\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    /**
     * Convert markdown content to Notion blocks array
     * 
     * @param markdown The markdown content to convert
     * @return Array of Notion blocks
     */
    public static ArrayNode markdownToBlocks(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return mapper.createArrayNode();
        }

        logger.debug("Converting markdown to Notion blocks: {} chars", markdown.length());

        ArrayNode blocks = mapper.createArrayNode();
        String[] lines = markdown.split("\n");

        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();
        String codeBlockLanguage = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Handle code blocks
            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    // Starting code block
                    inCodeBlock = true;
                    codeBlockLanguage = line.substring(3).trim();
                    if (codeBlockLanguage.isEmpty()) {
                        codeBlockLanguage = null;
                    }
                    codeBlockContent.setLength(0);
                } else {
                    // Ending code block
                    inCodeBlock = false;
                    blocks.add(createCodeBlock(codeBlockContent.toString(), codeBlockLanguage));
                    codeBlockLanguage = null;
                }
                continue;
            }

            if (inCodeBlock) {
                if (codeBlockContent.length() > 0) {
                    codeBlockContent.append("\n");
                }
                codeBlockContent.append(line);
                continue;
            }

            // Handle headers
            if (line.startsWith("#")) {
                blocks.add(createHeaderBlock(line));
                continue;
            }

            // Handle list items
            if (line.trim().matches("^[-*+]\\s+.+")) {
                blocks.add(createBulletedListBlock(line));
                continue;
            }

            if (line.trim().matches("^\\d+\\.\\s+.+")) {
                blocks.add(createNumberedListBlock(line));
                continue;
            }

            // Handle empty lines
            if (line.trim().isEmpty()) {
                continue;
            }

            // Handle paragraphs
            blocks.add(createParagraphBlock(line));
        }

        logger.debug("Created {} Notion blocks from markdown", blocks.size());
        return blocks;
    }

    /**
     * Convert Notion blocks to markdown content
     * 
     * @param blocks The Notion blocks to convert
     * @return Markdown content
     */
    public static String blocksToMarkdown(JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return "";
        }

        logger.debug("Converting {} Notion blocks to markdown", blocks.size());

        StringBuilder markdown = new StringBuilder();

        for (JsonNode block : blocks) {
            String blockType = block.path("type").asText();
            JsonNode blockData = block.path(blockType);

            switch (blockType) {
                case "paragraph":
                    markdown.append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n\n");
                    break;

                case "heading_1":
                    markdown.append("# ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n\n");
                    break;

                case "heading_2":
                    markdown.append("## ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n\n");
                    break;

                case "heading_3":
                    markdown.append("### ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n\n");
                    break;

                case "bulleted_list_item":
                    markdown.append("- ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n");
                    break;

                case "numbered_list_item":
                    markdown.append("1. ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n");
                    break;

                case "code":
                    String language = blockData.path("language").asText("");
                    String caption = richTextToMarkdown(blockData.path("caption"));
                    String codeContent = richTextToMarkdown(blockData.path("rich_text"));

                    markdown.append("```").append(language).append("\n");
                    markdown.append(codeContent);
                    markdown.append("\n```");
                    if (!caption.isEmpty()) {
                        markdown.append("\n*").append(caption).append("*");
                    }
                    markdown.append("\n\n");
                    break;

                case "quote":
                    markdown.append("> ").append(richTextToMarkdown(blockData.path("rich_text")));
                    markdown.append("\n\n");
                    break;

                case "divider":
                    markdown.append("---\n\n");
                    break;

                default:
                    // For unsupported block types, try to extract plain text
                    String plainText = richTextToMarkdown(blockData.path("rich_text"));
                    if (!plainText.isEmpty()) {
                        markdown.append(plainText).append("\n\n");
                    }
            }
        }

        // Clean up extra newlines at the end
        String result = markdown.toString().replaceAll("\\n\\n+$", "\n").trim();

        logger.debug("Converted to markdown: {} chars", result.length());
        return result;
    }

    /**
     * Convert rich text array to plain markdown text
     */
    private static String richTextToMarkdown(JsonNode richTextArray) {
        if (richTextArray == null || !richTextArray.isArray()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (JsonNode richText : richTextArray) {
            String plainText = richText.path("plain_text").asText("");
            JsonNode annotations = richText.path("annotations");
            String href = richText.path("href").asText(null);

            String text = plainText;

            // Apply formatting
            if (annotations.path("bold").asBoolean(false)) {
                text = "**" + text + "**";
            }
            if (annotations.path("italic").asBoolean(false)) {
                text = "*" + text + "*";
            }
            if (annotations.path("code").asBoolean(false)) {
                text = "`" + text + "`";
            }

            // Apply link
            if (href != null && !href.isEmpty()) {
                text = "[" + text + "](" + href + ")";
            }

            result.append(text);
        }

        return result.toString();
    }

    /**
     * Create a paragraph block from markdown text
     */
    private static ObjectNode createParagraphBlock(String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", "paragraph");

        ObjectNode paragraph = mapper.createObjectNode();
        paragraph.set("rich_text", createRichTextFromMarkdown(text));
        block.set("paragraph", paragraph);

        return block;
    }

    /**
     * Create a header block from markdown header line
     */
    private static ObjectNode createHeaderBlock(String headerLine) {
        int level = 0;
        for (char c : headerLine.toCharArray()) {
            if (c == '#')
                level++;
            else
                break;
        }

        String text = headerLine.substring(level).trim();
        String blockType = "heading_" + Math.min(level, 3); // Notion supports heading_1, heading_2, heading_3

        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", blockType);

        ObjectNode heading = mapper.createObjectNode();
        heading.set("rich_text", createRichTextFromMarkdown(text));
        block.set(blockType, heading);

        return block;
    }

    /**
     * Create a bulleted list item block
     */
    private static ObjectNode createBulletedListBlock(String listLine) {
        String text = listLine.trim().replaceFirst("^[-*+]\\s+", "");

        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", "bulleted_list_item");

        ObjectNode listItem = mapper.createObjectNode();
        listItem.set("rich_text", createRichTextFromMarkdown(text));
        block.set("bulleted_list_item", listItem);

        return block;
    }

    /**
     * Create a numbered list item block
     */
    private static ObjectNode createNumberedListBlock(String listLine) {
        String text = listLine.trim().replaceFirst("^\\d+\\.\\s+", "");

        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", "numbered_list_item");

        ObjectNode listItem = mapper.createObjectNode();
        listItem.set("rich_text", createRichTextFromMarkdown(text));
        block.set("numbered_list_item", listItem);

        return block;
    }

    /**
     * Create a code block
     */
    private static ObjectNode createCodeBlock(String content, String language) {
        ObjectNode block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", "code");

        ObjectNode code = mapper.createObjectNode();
        code.set("rich_text", createRichTextArray(content));
        if (language != null && !language.isEmpty()) {
            code.put("language", language);
        }
        block.set("code", code);

        return block;
    }

    /**
     * Create rich text array from markdown text with basic formatting
     */
    private static ArrayNode createRichTextFromMarkdown(String text) {
        ArrayNode richText = mapper.createArrayNode();

        if (text == null || text.isEmpty()) {
            return richText;
        }

        // For now, create simple rich text without parsing inline formatting
        // This can be enhanced later to handle bold, italic, links, etc.
        ObjectNode richTextItem = mapper.createObjectNode();
        richTextItem.put("type", "text");

        ObjectNode textObject = mapper.createObjectNode();
        textObject.put("content", text);
        richTextItem.set("text", textObject);

        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("bold", false);
        annotations.put("italic", false);
        annotations.put("strikethrough", false);
        annotations.put("underline", false);
        annotations.put("code", false);
        annotations.put("color", "default");
        richTextItem.set("annotations", annotations);

        richText.add(richTextItem);

        return richText;
    }

    /**
     * Create simple rich text array for plain text
     */
    private static ArrayNode createRichTextArray(String text) {
        ArrayNode richText = mapper.createArrayNode();

        if (text == null || text.isEmpty()) {
            return richText;
        }

        ObjectNode richTextItem = mapper.createObjectNode();
        richTextItem.put("type", "text");
        richTextItem.put("plain_text", text);

        ObjectNode textObject = mapper.createObjectNode();
        textObject.put("content", text);
        richTextItem.set("text", textObject);

        ObjectNode annotations = mapper.createObjectNode();
        annotations.put("bold", false);
        annotations.put("italic", false);
        annotations.put("strikethrough", false);
        annotations.put("underline", false);
        annotations.put("code", false);
        annotations.put("color", "default");
        richTextItem.set("annotations", annotations);

        richText.add(richTextItem);

        return richText;
    }
}