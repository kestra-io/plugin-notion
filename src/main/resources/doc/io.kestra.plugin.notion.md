# How to use the Notion plugin

Read and write Notion pages and database records from Kestra flows.

## Authentication

Set `apiToken` to your Notion integration token (created at [notion.so/my-integrations](https://www.notion.so/my-integrations)). The integration must be shared with any pages or databases it needs to access. Store the token in a [secret](https://kestra.io/docs/concepts/secret) and set it on each task.

## Tasks

Page tasks operate on individual Notion pages: `page.Create` creates a new page with `title` and optional Markdown `content` under a `parentPageId`. `page.Read` fetches a page's content and metadata by `pageId`. `page.Update` modifies a page's `title` or appends `content`. `page.Archive` moves a page to trash.

Database tasks operate on Notion databases: `database.Query` queries a database by `databaseId` with optional `filter`, `sorts`, and `pageSize` — use `fetchType` to return rows as a list (`FETCH`), a single row (`FETCH_ONE`), or a stored file (`STORE`). `database.CreateItem` adds a row with `title` and a `properties` map. `database.UpdateItem` modifies a row's `properties` or sets `archived`. `database.DeleteItem` archives a row by `pageId`. `database.Update` renames a database or changes its `databaseDescription`.
