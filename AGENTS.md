# Kestra Notion Plugin

## What

description = 'Notion plugin for Kestra Exposes 4 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with Notion, allowing orchestration of Notion-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
