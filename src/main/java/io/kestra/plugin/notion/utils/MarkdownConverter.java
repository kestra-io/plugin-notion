package io.kestra.plugin.notion.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.kestra.core.serializers.JacksonMapper;

/**
 * Utility class for converting between Markdown and Notion blocks format.
 * Supports bidirectional conversion for common content types.
 *
 * <p>
 * The Markdown -> blocks direction parses with the CommonMark reference parser
 * (plus the GFM tables, strikethrough and task-list extensions) and walks the
 * resulting AST into Notion block objects. This renders block-level elements,
 * GFM tables, inline annotations (bold/italic/code/strikethrough/links) and
 * backslash escapes as real Notion structures rather than literal text.
 * </p>
 */
public class MarkdownConverter {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownConverter.class);
    private static final ObjectMapper mapper = JacksonMapper.ofJson();

    private static final List<Extension> EXTENSIONS = List.of(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create()
    );

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    /** Notion only supports heading levels 1-3; deeper Markdown headings are clamped. */
    private static final int MAX_HEADING_LEVEL = 3;

    /** Notion's code-block "language" is a closed enum; an unknown value is rejected by the API. */
    private static final Set<String> NOTION_CODE_LANGUAGES = Set.of(
        "abap", "agda", "arduino", "ascii art", "assembly", "bash", "basic", "bnf", "c", "c#",
        "c++", "clojure", "coffeescript", "coq", "css", "dart", "dhall", "diff", "docker", "ebnf",
        "elixir", "elm", "erlang", "f#", "flow", "fortran", "gherkin", "glsl", "go", "graphql",
        "groovy", "haskell", "hcl", "html", "idris", "java", "javascript", "json", "julia", "kotlin",
        "latex", "less", "lisp", "livescript", "llvm ir", "lua", "makefile", "markdown", "markup",
        "matlab", "mathematica", "mermaid", "nix", "notion formula", "objective-c", "ocaml", "pascal",
        "perl", "php", "plain text", "powershell", "prolog", "protobuf", "purescript", "python", "r",
        "racket", "reason", "ruby", "rust", "sass", "scala", "scheme", "scss", "shell", "smalltalk",
        "solidity", "sql", "swift", "toml", "typescript", "vb.net", "verilog", "vhdl", "visual basic",
        "webassembly", "xml", "yaml", "java/c/c++/c#"
    );

    /** Common Markdown fence tokens that differ from Notion's enum names. */
    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
        Map.entry("js", "javascript"), Map.entry("jsx", "javascript"), Map.entry("node", "javascript"),
        Map.entry("ts", "typescript"), Map.entry("tsx", "typescript"), Map.entry("py", "python"),
        Map.entry("rb", "ruby"), Map.entry("rs", "rust"), Map.entry("kt", "kotlin"),
        Map.entry("cs", "c#"), Map.entry("cpp", "c++"), Map.entry("cxx", "c++"),
        Map.entry("objc", "objective-c"), Map.entry("golang", "go"), Map.entry("sh", "shell"),
        Map.entry("zsh", "shell"), Map.entry("yml", "yaml"), Map.entry("md", "markdown"),
        Map.entry("dockerfile", "docker"), Map.entry("proto", "protobuf"), Map.entry("htm", "html"),
        Map.entry("text", "plain text"), Map.entry("plain", "plain text"),
        Map.entry("plaintext", "plain text"), Map.entry("txt", "plain text")
    );

    /**
     * Convert markdown content to Notion blocks array
     *
     * @param markdown The markdown content to convert
     * @return Array of Notion blocks
     */
    public static ArrayNode markdownToBlocks(String markdown) {
        var blocks = mapper.createArrayNode();

        if (markdown == null || markdown.trim().isEmpty()) {
            return blocks;
        }

        logger.debug("Converting markdown to Notion blocks: {} chars", markdown.length());

        var document = PARSER.parse(markdown);
        for (var node = document.getFirstChild(); node != null; node = node.getNext()) {
            appendBlock(blocks, node);
        }

        logger.debug("Created {} Notion blocks from markdown", blocks.size());
        return blocks;
    }

    /**
     * Map a single CommonMark block node to its Notion block(s) and append them to {@code out}.
     */
    private static void appendBlock(ArrayNode out, Node node) {
        switch (node) {
            case Heading heading -> out.add(headingBlock(heading));
            case Paragraph paragraph -> out.add(wrapParagraph(inlineRichText(paragraph)));
            case FencedCodeBlock code -> out.add(codeBlock(code.getLiteral(), code.getInfo()));
            case IndentedCodeBlock code -> out.add(codeBlock(code.getLiteral(), null));
            case BulletList list -> appendListItems(out, list, false);
            case OrderedList list -> appendListItems(out, list, true);
            case BlockQuote quote -> out.add(quoteBlock(quote));
            case ThematicBreak thematicBreak -> out.add(block("divider", mapper.createObjectNode()));
            case TableBlock table -> {
                var tableNode = tableBlock(table);
                if (tableNode != null) {
                    out.add(tableNode);
                }
            }
            default -> {
                // Fallback: surface any inline content as a paragraph so nothing is silently dropped.
                var richText = inlineRichText(node);
                if (!richText.isEmpty()) {
                    out.add(wrapParagraph(richText));
                }
            }
        }
    }

    private static void appendListItems(ArrayNode out, Node list, boolean ordered) {
        for (var item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (item instanceof ListItem listItem) {
                out.add(listItemBlock(listItem, ordered));
            }
        }
    }

    /**
     * Build a list item block. A {@code TaskListItemMarker} promotes the item to a Notion
     * {@code to_do} block; nested lists/blocks under the item become Notion children.
     */
    private static ObjectNode listItemBlock(ListItem item, boolean ordered) {
        var first = item.getFirstChild();

        // The task-list extension prepends a TaskListItemMarker as the list item's first child;
        // its presence turns the item into a Notion to_do block.
        TaskListItemMarker marker = null;
        if (first instanceof TaskListItemMarker taskMarker) {
            marker = taskMarker;
            first = first.getNext();
        }

        var hasParagraph = first instanceof Paragraph;
        var richText = hasParagraph ? inlineRichText(first) : mapper.createArrayNode();
        var childStart = hasParagraph ? first.getNext() : first;

        var children = mapper.createArrayNode();
        for (var child = childStart; child != null; child = child.getNext()) {
            appendBlock(children, child);
        }

        var type = marker != null ? "to_do" : (ordered ? "numbered_list_item" : "bulleted_list_item");

        var body = mapper.createObjectNode();
        body.set("rich_text", richText);
        if (marker != null) {
            body.put("checked", marker.isChecked());
        }
        if (!children.isEmpty()) {
            body.set("children", children);
        }

        return block(type, body);
    }

    private static ObjectNode quoteBlock(BlockQuote quote) {
        var richText = mapper.createArrayNode();
        var children = mapper.createArrayNode();

        var first = quote.getFirstChild();
        var childStart = first;
        if (first instanceof Paragraph) {
            richText = inlineRichText(first);
            childStart = first.getNext();
        }
        for (var child = childStart; child != null; child = child.getNext()) {
            appendBlock(children, child);
        }

        var body = mapper.createObjectNode();
        body.set("rich_text", richText);
        if (!children.isEmpty()) {
            body.set("children", children);
        }
        return block("quote", body);
    }

    private static ObjectNode tableBlock(TableBlock table) {
        var rowCells = new ArrayList<ArrayNode>();
        var width = 0;

        // A TableBlock contains a TableHead and (optionally) a TableBody, each holding TableRows.
        for (var section = table.getFirstChild(); section != null; section = section.getNext()) {
            for (var row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) {
                    continue;
                }
                var cells = mapper.createArrayNode();
                for (var cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (cell instanceof TableCell) {
                        cells.add(inlineRichText(cell));
                    }
                }
                width = Math.max(width, cells.size());
                rowCells.add(cells);
            }
        }

        if (width == 0) {
            // Degenerate table (e.g. a separator-only row) — Notion rejects table_width: 0.
            return null;
        }

        var children = mapper.createArrayNode();
        for (var cells : rowCells) {
            // Notion requires every row to have exactly table_width cells.
            while (cells.size() < width) {
                cells.add(mapper.createArrayNode());
            }
            var rowBody = mapper.createObjectNode();
            rowBody.set("cells", cells);
            children.add(block("table_row", rowBody));
        }

        var body = mapper.createObjectNode();
        body.put("table_width", width);
        body.put("has_column_header", true);
        body.put("has_row_header", false);
        body.set("children", children);
        return block("table", body);
    }

    private static ObjectNode headingBlock(Heading heading) {
        var type = "heading_" + Math.min(heading.getLevel(), MAX_HEADING_LEVEL);
        var body = mapper.createObjectNode();
        body.set("rich_text", inlineRichText(heading));
        return block(type, body);
    }

    private static ObjectNode wrapParagraph(ArrayNode richText) {
        var body = mapper.createObjectNode();
        body.set("rich_text", richText);
        return block("paragraph", body);
    }

    private static ObjectNode codeBlock(String content, String info) {
        var body = mapper.createObjectNode();

        var richText = mapper.createArrayNode();
        addText(richText, stripTrailingNewline(content), Ctx.EMPTY);
        body.set("rich_text", richText);

        body.put("language", notionLanguage(info));

        return block("code", body);
    }

    /**
     * Resolve a Markdown fence info string to a value Notion's code-block language enum accepts.
     * Notion rejects the whole request for an unknown language, so an unspecified or unrecognised fence
     * (including common aliases like js/ts/yml) falls back to "plain text", the enum's neutral value.
     */
    private static String notionLanguage(String info) {
        if (info == null || info.isBlank()) {
            return "plain text";
        }
        var token = info.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        token = LANGUAGE_ALIASES.getOrDefault(token, token);
        return NOTION_CODE_LANGUAGES.contains(token) ? token : "plain text";
    }

    private static String stripTrailingNewline(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static ObjectNode block(String type, ObjectNode body) {
        var block = mapper.createObjectNode();
        block.put("object", "block");
        block.put("type", type);
        block.set(type, body);
        return block;
    }

    /**
     * Build a Notion {@code rich_text} array from a block node's inline children.
     */
    private static ArrayNode inlineRichText(Node parent) {
        var out = mapper.createArrayNode();
        collectInlines(parent, out, Ctx.EMPTY);
        return out;
    }

    /**
     * Walk inline nodes left-to-right, carrying annotation/link context down through
     * nested emphasis and links so e.g. a bold link is both bold and a link.
     */
    private static void collectInlines(Node parent, ArrayNode out, Ctx ctx) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
            switch (child) {
                case Text text -> addText(out, text.getLiteral(), ctx);
                case StrongEmphasis strong -> collectInlines(child, out, ctx.withBold());
                case Emphasis emphasis -> collectInlines(child, out, ctx.withItalic());
                case Strikethrough strikethrough -> collectInlines(child, out, ctx.withStrike());
                case Code code -> addText(out, code.getLiteral(), ctx.withCode());
                case Link link -> {
                    var dest = link.getDestination();
                    collectInlines(child, out, dest == null || dest.isBlank() ? ctx : ctx.withHref(dest));
                }
                case Image image -> collectInlines(child, out, ctx); // render alt-text inlines as plain text
                case SoftLineBreak softBreak -> addText(out, "\n", ctx);
                case HardLineBreak hardBreak -> addText(out, "\n", ctx);
                case TaskListItemMarker marker -> {
                    /* consumed at the list-item level */ }
                default -> collectInlines(child, out, ctx); // unknown inline container: descend
            }
        }
    }

    private static void addText(ArrayNode out, String content, Ctx ctx) {
        if (content == null || content.isEmpty()) {
            return;
        }

        var item = mapper.createObjectNode();
        item.put("type", "text");

        var text = mapper.createObjectNode();
        text.put("content", content);
        if (ctx.href() != null) {
            var link = mapper.createObjectNode();
            link.put("url", ctx.href());
            text.set("link", link);
        }
        item.set("text", text);

        if (ctx.href() != null) {
            item.put("href", ctx.href());
        }
        item.set("annotations", annotations(ctx));

        out.add(item);
    }

    private static ObjectNode annotations(Ctx ctx) {
        var annotations = mapper.createObjectNode();
        annotations.put("bold", ctx.bold());
        annotations.put("italic", ctx.italic());
        annotations.put("strikethrough", ctx.strike());
        annotations.put("underline", false);
        annotations.put("code", ctx.code());
        annotations.put("color", "default");
        return annotations;
    }

    /**
     * Immutable inline formatting context threaded through {@link #collectInlines}.
     */
    private record Ctx(boolean bold, boolean italic, boolean code, boolean strike, String href) {

        static final Ctx EMPTY = new Ctx(false, false, false, false, null);

        Ctx withBold() {
            return new Ctx(true, italic, code, strike, href);
        }

        Ctx withItalic() {
            return new Ctx(bold, true, code, strike, href);
        }

        Ctx withCode() {
            return new Ctx(bold, italic, true, strike, href);
        }

        Ctx withStrike() {
            return new Ctx(bold, italic, code, true, href);
        }

        Ctx withHref(String url) {
            return new Ctx(bold, italic, code, strike, url);
        }
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
}
