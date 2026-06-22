---
name: mcp
description: "The Model Context Protocol (MCP) is an open, JSON-RPC 2.0 standard for connecting AI applications to external tools, data, and prompts; use it when building or integrating MCP clients/servers and especially when you must target a specific date-versioned protocol revision."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---

The Model Context Protocol (MCP) is an open standard for connecting AI applications to external tools, data sources, and prompts over a uniform JSON-RPC 2.0 interface.

## Overview

MCP defines a client–server protocol: a **host** (an AI application) runs one **client** per connection to an MCP **server**, and servers expose capabilities the model can use. Every message is JSON-RPC 2.0. Servers expose three core primitives — **tools** (callable functions), **resources** (readable context), and **prompts** (reusable templates) — while clients may expose **sampling**, **roots**, and (in newer revisions) **elicitation**.

## When to use

Use this skill when writing or integrating an MCP client or server, choosing a transport, or — most importantly — when you must pin or negotiate a specific protocol revision. MCP is **date-versioned**, and revisions are not backward-compatible by default, so the wrong assumption about the version silently breaks integrations.

## Protocol versioning

MCP protocol revisions are identified by a **date string `YYYY-MM-DD`**, not semver. Known revisions:

| Revision | Notable changes |
|---|---|
| `2024-11-05` | First public spec. stdio and HTTP+SSE transports. |
| `2025-03-26` | Added **Streamable HTTP** transport (replacing the older HTTP+SSE), OAuth 2.1-based authorization, tool annotations, and audio content. |
| `2025-06-18` | Structured tool output, **elicitation**, resource links in tool results, removal of JSON-RPC batching, and a required `MCP-Protocol-Version` header for HTTP. |

**Negotiation happens in `initialize`.** The client sends the latest revision it supports as `protocolVersion`; the server replies with the version it will actually use (its latest supported revision ≤ the client's, or its own latest if it can't match). The client must disconnect if it cannot accept the server's chosen version.

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": { "name": "example-client", "version": "1.0.0" }
  }
}
```

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": { "tools": {}, "resources": {} },
    "serverInfo": { "name": "example-server", "version": "1.0.0" }
  }
}
```

## Modern vs deprecated

- **Transport:** Prefer **Streamable HTTP** (since `2025-03-26`) for remote servers. The original **HTTP+SSE** transport is superseded — do not build new servers on it. **stdio** remains the standard for local subprocess servers.
- **Header (HTTP):** From `2025-06-18`, clients must send the negotiated revision on every subsequent HTTP request as `MCP-Protocol-Version: 2025-06-18`. Servers that receive no header should assume `2025-03-26` for backward compatibility.
- **Batching:** JSON-RPC batching was removed in `2025-06-18`; do not rely on it when targeting that revision.

## Instructions

1. Pick the **SDK**, not raw JSON-RPC, unless you have a reason: TypeScript `@modelcontextprotocol/sdk`, Python `mcp`.
2. **Negotiate explicitly:** send the newest revision you support in `initialize.protocolVersion`; branch behaviour on the version the server returns rather than assuming.
3. For HTTP, **echo the negotiated version** back via the `MCP-Protocol-Version` header on every request (required from `2025-06-18`).
4. **Feature-gate** capabilities by revision (e.g. only use elicitation/structured output when the negotiated version is `2025-06-18` or later).

```bash
npm install @modelcontextprotocol/sdk
# or: pip install mcp
```

## Sources

- [Model Context Protocol — specification](https://modelcontextprotocol.io/specification)
- [MCP specification revisions](https://modelcontextprotocol.io/specification/versioning)
- [TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk)

---

_Created with [skill3](https://github.com/PIsberg/skill3)._
