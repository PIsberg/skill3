---
name: mcp
description: "Covers post-2026-01 MCP ecosystem additions including the GitHub MCP Registry, Claude Code's MCP Tool Search and channel push messages, Cloudflare's Code Mode and MCP governance, and x402 paid MCP tools; use when working with newer MCP client/server features."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
## What changed

The sources marked as post-cutoff contain mostly ecosystem documentation rather than protocol-level changes. The core MCP architecture (JSON-RPC 2.0 over stdio or Streamable HTTP, the initialize/notification session lifecycle, tools/resources/prompts primitives, host/client/server roles) is unchanged from what you already know. The material new-or-emphasized items below are scoped to specific platforms; treat them as platform features, not protocol revisions.

### GitHub MCP Registry
GitHub now lists an **MCP Registry** ("New") in its platform navigation, positioned as a way to "integrate external tools." This is presented as a discovery surface for MCP servers within the GitHub platform. (Details beyond its existence are not provided by the sources.)

### Claude Code MCP features
Claude Code documentation now describes several capabilities to be aware of:

| Feature | Purpose |
|---|---|
| MCP Tool Search ("Scale with MCP Tool Search") | Defers tool loading so large tool sets scale; server authors can configure tool search or exempt a server from deferral |
| Push messages with channels | Servers can push messages to Claude Code via channels |
| Dynamic tool updates | Tools can update at runtime without reconnect |
| Automatic reconnection | Client auto-reconnects to servers |
| Remote WebSocket server (Option 4) | Adds a WebSocket transport option alongside remote HTTP, remote SSE, and local stdio |
| MCP output limits and warnings | Per-tool output limits can be raised individually |
| OAuth controls | Fixed OAuth callback port, pre-configured OAuth credentials, override OAuth metadata discovery, restrict OAuth scopes, dynamic headers for custom auth |
| MCP resources / prompts | Reference MCP resources; use MCP prompts as commands |
| Plugin-provided MCP servers | MCP servers can be supplied by plugins |
| claude.ai connectors | Use MCP servers from Claude.ai; option to disable claude.ai connectors |

Installation scopes are local, project, and user, with a defined scope hierarchy/precedence and environment-variable expansion supported in `.mcp.json`.

### Cloudflare Agents MCP additions
Cloudflare's Agents docs expose an MCP API surface and several newer concepts:
- APIs: `createMcpHandler`, `McpAgent`, `McpClient`.
- **Code Mode** and **MCP governance** are listed as distinct protocol/operational topics.
- **MCP server portals** for Cloudflare.
- **x402** agentic payments, including **Charge for MCP tools** and an **MPP (Machine Payments Protocol)** — paying for tool invocations from the Agents SDK or coding tools.

### Other ecosystem notes
- Roo Code documents transports as **STDIO, Streamable HTTP, and legacy SSE**, explicitly framing SSE as legacy in favor of Streamable HTTP.
- Microsoft Learn MCP Server is a remote, no-auth, free, Streamable-HTTP server for searching/fetching official docs; it returns 405 on browser access and refreshes incrementally (full refresh daily). Updates tracked via its Release Notes.

## When to use

Use this skill when you are configuring or building MCP integrations on Claude Code, Cloudflare Agents, VS Code, Roo Code, or Vercel after early 2026, and need the platform-specific features (Tool Search, channels, Code Mode, x402 paid tools, the GitHub MCP Registry) that postdate your training. Do not use it as an MCP primer — the protocol fundamentals are unchanged.

## Examples

Add a remote WebSocket MCP server in Claude Code (Option 4, newer transport choice alongside HTTP/SSE/stdio):
```
# Claude Code supports four install transports:
# remote HTTP, remote SSE, local stdio, and remote WebSocket
```

Context7 invocation patterns (still current usage):
```
How do I set up Next.js 14 middleware? use context7
```
```
Implement basic authentication with Supabase. use library /supabase/supabase for API and docs.
```
```
npx ctx7 setup
```

Connect to the Microsoft Learn MCP Server (remote, Streamable HTTP, no authentication required) from an MCP client such as VS Code, Visual Studio, or MCP Inspector. Note that browser access returns `405 Method Not Allowed`.

Cloudflare paid MCP tools via x402: charge for individual MCP tool invocations and pay from the Agents SDK or coding tools using the MPP (Machine Payments Protocol) flow described in the Cloudflare Agentic Payments docs.

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
