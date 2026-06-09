# Kestra Notion Plugin

## What

- Provides plugin components under `io.kestra.plugin.notion`.
- Includes classes such as `NotionConnection`, `NotionResponse`, `Archive`, `Create`.

## Why

- What user problem does this solve? Teams need to create, read, update, and archive Notion pages, and query and manage Notion database items from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Notion steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Notion.

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
- `io.kestra.plugin.notion.page.UpdateTrigger` — polling trigger that fires when Notion pages are updated

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
