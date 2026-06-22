---
name: mcp
description: "Connect AI applications to external tools, data, and services using the Model Context Protocol when integrating LLMs with databases, APIs, or specialized functionality across compatible clients."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
## Overview

The Model Context Protocol (MCP) is an open standard, introduced by Anthropic in November 2024, that standardizes secure, two-way connections between AI applications and external tools, data sources, and services—without custom per-integration code. It is often described as a "USB-C port for AI": any compliant host (such as Claude, ChatGPT, Cursor, or VS Code Copilot) can plug into any compliant server and immediately discover and use its capabilities.

MCP uses a client-server architecture:

- **Host** — The AI application that coordinates clients and uses provided context. Examples: Claude Desktop, VS Code with Copilot, Cursor, ChatGPT.
- **Client** — Instantiated by the host, one per server. Handles the dedicated connection, capability discovery, and primitive invocation. To connect to multiple servers, one host opens and manages multiple clients.
- **Server** — Exposes context to clients. Can be local (e.g., a filesystem server on the same machine) or remote (a hosted service over HTTPS).

Before MCP, every AI app needed custom code for every tool (M apps × N tools = M×N integrations to build and maintain), each integration invented its own auth and sandboxing patterns, and LLMs could not reliably discover available tools without bespoke prompt engineering. MCP solves this: developers implement a single MCP integration, and tool providers expose an MCP interface once to be accessed by any MCP-enabled application.

## When to use

Use MCP when you need to:

- Connect an LLM to real-time or external data (financial data, pricing, user-specific data) that it does not have access to by default.
- Give an AI agent the ability to take actions or fetch live data via tools (e.g., querying a PostgreSQL database, connecting to GitHub for code reviews, monitoring errors with Sentry).
- Provide read-only structured context such as files, database records, or API responses to a model.
- Integrate once and interoperate with any MCP-compatible system rather than building bespoke integrations per tool.
- Surface up-to-date documentation or knowledge to coding agents (e.g., Context7, Microsoft Learn MCP Server).

## Instructions

1. **Choose host and server.** Identify the MCP host (your AI application or IDE) and the MCP server(s) exposing the capabilities you need. Servers may be local or remote.

2. **Pick a transport:**
   - **stdio** — Uses stdin/stdout. The host spawns the server as a child process on the same machine. Best for local servers.
   - **Streamable HTTP** — Uses HTTP POST plus Server-Sent Events (SSE). Clients POST requests; servers stream responses. Supports multiple concurrent clients. Best for remote servers.

3. **Establish the session.** MCP uses stateful JSON-RPC 2.0; a session is established per connection and maintained for its duration. The lifecycle is:
   - Client sends `initialize` with its protocol version and capabilities.
   - Server replies with its capabilities and server info.
   - Client confirms the session is ready with a one-way notification.
   - Client discovers capabilities, then invokes them.

4. **Use the server's primitives:**
   - **Tools** — Functions the LLM can invoke to take actions or fetch live data. Each tool has a JSON Schema input definition the model uses to construct valid calls.
   - **Resources** — Structured, URI-addressed read-only data the model can consume as context (files, database records, API responses). The model reads but does not modify resources.
   - **Prompts** — Reusable prompt templates that some hosts can execute as commands.

5. **Configure in your host.** For example:
   - **VS Code:** Open the Extensions view and search `@mcp <name>` (e.g., `@mcp playwright`), then select Install to add the server to your user profile. Use the Agent Customizations editor (Preview) via `Chat: Open Customizations` to manage all customizations in one place.
   - **Claude Code:** Add a remote HTTP server, remote SSE server, local stdio server, or remote WebSocket server. Configure installation scopes (local, project, user) with defined precedence; project-scoped servers live in `.mcp.json`, which supports environment variable expansion.

6. **Handle authentication where required.** Remote servers may require OAuth. For Claude Code you can use a fixed OAuth callback port, pre-configured OAuth credentials, override OAuth metadata discovery, restrict OAuth scopes, or use dynamic headers for custom authentication. Some remote servers (e.g., Microsoft Learn MCP Server) require no authentication.

7. **Secure your connection.** Because each integration historically invented its own auth, sandboxing, and data-handling patterns, follow consistent security practices: review tool approvals, restrict scopes, and validate server trust before enabling.

## Modern vs deprecated

Roo Code's documentation distinguishes remote transports as **Streamable HTTP** (current) and **legacy SSE**. Prefer Streamable HTTP for remote servers and treat plain SSE as the legacy mechanism.

## Examples

**Use a documentation server (Context7):** Append a directive to a coding prompt so the agent pulls up-to-date library docs:

```
Create a Next.js middleware that checks for a valid JWT in cookies
and redirects unauthenticated users to `/login`. use context7
```

```
How do I set up Next.js 14 middleware? use context7
```

You can target a specific library and set up Context7 with its CLI:

```
Implement basic authentication with Supabase. use library /supabase/supabase for API and docs.
```

```
npx ctx7 setup
```

To make the behavior automatic, add a rule to your host:

```
Always use Context7 when I need library/API documentation, code generation, setup or configuration steps without me having to explicitly ask.
```

**Agentic workflows with tool servers (Claude Code):**

- Implement features from issue trackers: "Add the feature described in JIRA issue ENG-4521 and create a PR on GitHub."
- Analyze monitoring data: "Check Sentry and Statsig to check the usage of the feature described in ENG-4521."
- Query databases: "Find emails of 10 random users who used feature ENG-4521, based on our PostgreSQL database."
- Integrate designs: "Update our standard email template based on the new Figma designs that were posted in Slack."

**Connect to a remote documentation server (Microsoft Learn):** The Microsoft Learn MCP Server is a remote MCP server using Streamable HTTP that lets clients like GitHub Copilot search documentation, fetch complete articles, and search code samples. It requires no authentication and is publicly available at no charge; its endpoint is for programmatic MCP client access and may return `405 Method Not Allowed` if accessed directly from a browser.

## Sources

- https://developers.cloudflare.com/agents/model-context-protocol/
- https://docs.roocode.com/features/mcp/overview
- https://www.webfuse.com/mcp-cheat-sheet
- https://code.visualstudio.com/docs/agent-customization/mcp-servers
- https://github.com/modelcontextprotocol
- https://github.com/upstash/context7
- https://github.com/modelcontextprotocol/modelcontextprotocol
- https://vercel.com/docs/mcp
- https://learn.microsoft.com/en-us/training/support/mcp
- https://code.claude.com/docs/en/mcp

---

_Created with [skill3](https://github.com/PIsberg/skill3)._
