---
name: mcp
description: "Connect AI applications to external tools and data via the Model Context Protocol, including configuring servers, choosing transports, and securing connections in clients like Claude Code, VS Code, Cursor, and ChatGPT."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
## Overview

The Model Context Protocol (MCP) is an open-source, open standard introduced by Anthropic in November 2024 that standardises secure, two-way connections between AI applications and external tools, data sources, and services — without custom per-integration code. It is described as "a USB-C port for AI": any compliant host (Claude, ChatGPT, Cursor, VS Code Copilot) can plug into any compliant server and immediately discover and use its capabilities.

MCP solves the M×N integration problem: previously, M apps each needed custom code for N tools, with every integration inventing its own auth, sandboxing, and data-handling patterns. With MCP, tool providers expose one MCP interface and any MCP-enabled application can use it.

**Architecture (client-server):**

| Role | Description |
|------|-------------|
| Host | The AI application that coordinates clients and uses provided context (e.g., Claude Desktop, VS Code with Copilot, Cursor, ChatGPT). |
| Client | Instantiated by the host — one per server. Handles the dedicated connection, capability discovery, and primitive invocation. |
| Server | Exposes context to clients. Can be local (e.g., filesystem server on the same machine) or remote (a hosted service over HTTPS). |

To connect to multiple servers, one host opens and manages multiple clients.

**Recent developments:** GitHub now hosts an MCP Registry to integrate external tools. Cloudflare provides APIs to build remote MCP servers (`createMcpHandler`, `McpAgent`, `McpClient`) plus features for MCP governance and charging for MCP tools via x402. The Microsoft Learn MCP Server is a publicly available remote server (Streamable HTTP) that lets clients like GitHub Copilot search official documentation, fetch complete articles, and search code samples — with no authentication required and no charge.

## When to use

Use MCP when an AI model or agent needs to:

- Access databases, custom APIs, or specialized functionality beyond built-in capabilities (Roo Code).
- Provide real-time or external context (current financial data, pricing, user-specific data) that LLMs lack by default (Vercel).
- Implement features from issue trackers, analyze monitoring data, query databases, integrate designs, or automate workflows (Claude Code examples).

## Instructions

**1. Understand the primitives.** Servers expose three context types to clients, plus server-initiated capabilities:

| Primitive | Description |
|-----------|-------------|
| Tools | Functions the LLM can invoke to take actions or fetch live data. Each tool has a JSON Schema input definition the model uses to construct valid calls. |
| Resources | Structured, URI-addressed read-only data the model can consume as context — files, database records, API responses. The model reads but does not modify them. |
| Prompts | Reusable templates; in Claude Code these can be executed as commands. |

**2. Choose a transport.** MCP uses stateful JSON-RPC 2.0:

| Transport | How it works | Use case |
|-----------|--------------|----------|
| STDIO | Uses stdin/stdout; the host spawns the server as a child process on the same machine. | Local servers. |
| Streamable HTTP | HTTP POST + Server-Sent Events (SSE). Clients POST requests; servers stream responses. Supports multiple concurrent clients. | Remote/hosted servers. |
| SSE (legacy) | Listed as a legacy remote transport. | Older remote servers. |

**3. Know the session lifecycle:**
1. Client sends `initialize` with its protocol version and capabilities.
2. Server replies with its capabilities and server info.
3. Client confirms the session is ready with a one-way notification.
4. Client discovers capabilities then invokes them.

A session is established per connection and maintained for its duration. All messages are JSON-RPC request/response pairs or one-way notifications. The schema is defined TypeScript-first and also published as `schema.json` in the spec repo.

**4. Add a server to your client.** In Claude Code, choose a scope and transport:

| Scope | Visibility |
|-------|------------|
| Local | Current session/machine only. |
| Project | Shared via `.mcp.json` (supports environment variable expansion). |
| User | Across your projects. |

Claude Code supports adding a remote HTTP server, a remote SSE server, a local stdio server, or a remote WebSocket server, plus plugin-provided MCP servers, dynamic tool updates, automatic reconnection, OAuth authentication (fixed callback port, pre-configured credentials, custom metadata discovery, restricted scopes, dynamic headers), MCP output limits, and MCP Tool Search for scaling.

In VS Code, install an MCP server via the Extensions view (e.g., search `@mcp playwright`) and select **Install** to add it to your user profile, then use its tools in chat. Use the Agent Customizations editor (Preview) via **Chat: Open Customizations** in the Command Palette to manage customizations.

**5. Secure your connection.** Cloudflare provides guides for securing MCP servers, handling OAuth with MCP servers, and MCP governance. Microsoft Learn MCP Server requires no authentication but is subject to the Microsoft Learn Terms of Use, and contains only publicly available documentation — not training or user profile information.

## Examples

**Connect a remote, no-auth documentation server (Microsoft Learn):** A remote MCP server using Streamable HTTP that lets GitHub Copilot and other agents search Microsoft's official documentation, fetch a complete article, and search code samples. The endpoint is designed for programmatic MCP client access and may return `405 Method Not Allowed` if accessed manually from a browser. The underlying knowledge service refreshes incrementally after content updates and performs a full refresh once a day.

**Use Context7 for up-to-date library docs.** Install:

```
npx ctx7 setup
```

Invoke in prompts:

```
Create a Next.js middleware that checks for a valid JWT in cookies
and redirects unauthenticated users to `/login`. use context7
```

```
Configure a Cloudflare Worker script to cache
JSON API responses for five minutes. use context7
```

```
How do I set up Next.js 14 middleware? use context7
```

Target a specific library:

```
Implement basic authentication with Supabase. use library /supabase/supabase for API and docs.
```

Make it automatic by adding a rule:

```
Always use Context7 when I need library/API documentation, code generation, setup or configuration steps without me having to explicitly ask.
```

**Claude Code workflow prompts:**

- Implement features from issue trackers: "Add the feature described in JIRA issue ENG-4521 and create a PR on GitHub."
- Analyze monitoring data: "Check Sentry and Statsig to check the usage of the feature described in ENG-4521."
- Query databases: "Find emails of 10 random users who used feature ENG-4521, based on our PostgreSQL database."
- Integrate designs: "Update our standard email template based on the new Figma designs that were posted in Slack."

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
