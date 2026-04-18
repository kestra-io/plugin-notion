# Kestra Notion Plugin

## What

- Provides plugin components under `io.kestra.plugin.notion`.
- Includes classes such as `NotionConnection`, `NotionResponse`, `Archive`, `Create`.

## Why

- This plugin integrates Kestra with Notion Databases.
- It provides tasks that query and manage items in Notion databases.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `notion`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.notion.page.Archive`
- `io.kestra.plugin.notion.page.Create`
- `io.kestra.plugin.notion.page.Read`
- `io.kestra.plugin.notion.page.Update`

### Project Structure

```
plugin-notion/
├── src/main/java/io/kestra/plugin/notion/utils/
├── src/test/java/io/kestra/plugin/notion/utils/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
