---
name: mcp
description: "Covers the MCP 2026-07-28 stateless release candidate (SEP-2567/2575/2577 breaking changes, new headers, Tasks/MCP Apps extensions, deprecations), the priority-area 2026 roadmap, and the 2026 CVE/security incidents — use when working on MCP servers, clients, or transports after January 2026."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
## What changed

The largest revision of MCP since launch is the **2026-07-28 specification release candidate**, published as an RC ahead of the final spec dated **July 28, 2026**. It contains breaking changes. The headline: MCP becomes **stateless at the protocol layer**. The prior shipped spec was **2025-11-25**; no new version was cut between then and the RC.

### Stateless protocol (breaking)

Six SEPs combine to deliver statelessness; two are the core removals:

| Area | 2025-11-25 | 2026-07-28 RC |
|---|---|---|
| Session | `Mcp-Session-Id` header + protocol-level session (server-issued, pins client to one instance) | Removed (SEP-2567); any request can hit any instance |
| Handshake | `initialize`/`initialized` exchange negotiates protocol version, client info, capabilities once per session | Removed (SEP-2575); those values travel in `_meta` on **every** request |
| Capability discovery | Obtained during handshake | New `server/discover` method fetches server capabilities on demand |
| Routing | Required sticky sessions, shared session store, deep packet inspection of JSON-RPC body | Plain round-robin load balancer; route on headers |

A `tools/call` is now a single self-contained request that any server instance can handle. The practical effect: a remote server that previously needed sticky sessions and a shared session store can run behind an ordinary round-robin load balancer.

**Protocol state ≠ application state.** If your server needs cross-call state (shopping cart, browser session, running deployment), you mint an explicit handle and have the model pass it back as a regular tool argument — the application manages it, the way any HTTP API does. State becomes an explicit, visible handle rather than hidden in a protocol session.

### New required headers

Every Streamable HTTP request must now include:

- `Mcp-Method` — e.g. `tools/call` (the operation)
- `Mcp-Name` — the name of the tool or resource

This lets load balancers, gateways, and rate limiters route on the operation without buffering/parsing the JSON-RPC body (cheaper L7 routing). **Servers are required to reject requests where the headers and body disagree** (integrity check).

### Standardized client-side caching (new)

`tools/list`, `resources/list`, and `resources/read` responses now carry **`ttlMs`** and **`cacheScope`** fields. Clients learn how long a list is fresh and whether it is safe to share across users, cutting redundant polling.

### Authorization

The RC aligns authorization more closely with **OAuth and OpenID Connect** deployments, and introduces a **formal deprecation policy** (see below).

### Extensions framework

- **Tasks** graduates from an experimental primitive (originally SEP-1686) to a **first-class extension** for long-running work (CI pipelines, batch processing, human-in-the-loop approvals). Servers return a task handle; clients poll with `tasks/get`. Roadmap-noted gaps being closed: retry semantics on transient failure and result expiry/retention policy.
- **MCP Apps** (server-rendered interactive HTML UIs rendered in sandboxed iframes inside Claude, ChatGPT, VS Code, and Goose) landed in January 2026 and is now officially part of the extensions framework.

### 2026 roadmap (March 2026, lead maintainer David Soria Parra)

The roadmap dropped release-milestone framing for **four priority areas** driven by Working/Interest Groups, which now drive timelines. SEPs in these areas get expedited review:

1. **Transport evolution and scalability** — stateless/horizontal-scaling transport plus **MCP Server Cards**: structured server metadata served via a `.well-known` URL so registries/crawlers discover capabilities without a live connection. **Explicitly: no new official transports this cycle** — the existing Streamable HTTP transport is being evolved.
2. **Agent communication** — Tasks lifecycle, agent-to-agent tool-level primitives (note: Google's A2A handles higher-level inter-agent coordination), streaming/progressive results.
3. **Governance maturation** — a documented **contributor ladder** (community participant → WG contributor → facilitator → lead maintainer → core maintainer) and delegation so trusted Working Groups accept SEPs in their domain without full core review.
4. **Enterprise readiness** — audit trails, SSO-integrated auth, gateway behavior, config portability; least-defined, expected to land mostly as **extensions** rather than core changes. A dedicated Enterprise WG did not yet exist as of the roadmap.

### Ecosystem scale (post-cutoff figures)

TypeScript + Python SDKs reached **~97 million monthly downloads** (March 2026, up from ~2M at launch); **9,400–10,000+ public servers**; Claude reportedly processes **over 1 billion tool calls/month** via MCP. The AAIF held the **MCP Dev Summit North America** in New York City in April 2026 (~1,200 attendees).

## Deprecated or removed

Under the new **Feature Lifecycle Policy** (stages: Active → Deprecated → Removed, with **at least 12 months between each**), SEP-2577 marks the following **deprecated** in the RC. They still work today and cannot be removed for at least a year:

| Capability | Replacement guidance |
|---|---|
| Roots | Explicit mechanisms: tool parameters, resource URIs, or server configuration |
| Logging | `stderr` for stdio transports; OpenTelemetry for structured observability |
| Sampling | Servers should make **direct LLM provider API calls** instead of piggybacking on the client's LLM (cleaner separation of concerns) — the migration with the most friction |

Removed at the protocol layer in the RC: the `Mcp-Session-Id` header / protocol-level session (SEP-2567) and the `initialize`/`initialized` handshake (SEP-2575).

## When to use

Use this when building or operating MCP servers/clients after January 2026 — especially when designing for horizontal scaling, migrating off sessions/handshake/Roots/Sampling/Logging, configuring gateways for the new headers, planning around the deprecation timeline, or assessing the 2026 security incident landscape.

## Examples

**Migration checklist for the RC (before final spec, July 28, 2026):**
- Stop relying on `Mcp-Session-Id`; move any cross-call state into explicit handles passed as tool arguments.
- Drop the `initialize`/`initialized` flow; send protocol version, client info, and capabilities in `_meta` on each request; call `server/discover` when you need server capabilities up front.
- Add `Mcp-Method` and `Mcp-Name` headers to every Streamable HTTP request; ensure the server rejects header/body mismatches.
- Honor `ttlMs`/`cacheScope` on `tools/list`, `resources/list`, `resources/read` to cache and reduce polling.
- Replace Roots, Sampling, Logging usage per the table above — they still work, but plan within the ≥12-month lifecycle window.
- Reconfigure gateways: a plain round-robin load balancer with header-based L7 routing replaces sticky sessions, shared session stores, and JSON-RPC body inspection.

**Security hardening (2026 incidents):**
- Scan for exposed endpoints: query `/mcp` and `/sse` and check for `0.0.0.0` bindings (e.g., Snyk's `mcp-scan`). Trend Micro found 492 MCP servers exposed with zero authentication.
- Rotate credentials in agent config files (`.claude/settings.json` and similar plaintext configs); pin and review MCP server package versions; block auto-approval of MCP servers.
- Update **Claude Code to 2.0.65+** — patches CVE-2025-59536 and CVE-2026-21852.
- Patch **Microsoft MCP servers** for **CVE-2026-26118** (CVSS 8.8, fixed in the March 10, 2026 Patch Tuesday; tool-hijacking risk, no confirmed exploitation reported).
- Treat AI agent infrastructure as a supply-chain dependency; add behavioral monitoring of agent action sequences (static analysis misses sequence-level exfiltration chains).

## Sources

- https://blog.modelcontextprotocol.io/posts/2026-mcp-roadmap/
- https://blog.modelcontextprotocol.io/posts/2026-07-28-release-candidate/
- https://byteiota.com/mcp-goes-stateless-2026-release-candidate/
- https://aaif.io/blog/mcp-is-growing-up/
- https://thenewstack.io/model-context-protocol-roadmap-2026/
- https://toloka.ai/blog/the-future-of-mcp-enterprise-adoption/
- https://www.getknit.dev/blog/the-future-of-mcp-roadmap-enhancements-and-whats-next
- https://www.taskade.com/blog/mcp-servers
- https://truto.one/blog/what-is-an-mcp-server-the-2026-architecture-guide-for-saas-pms/
- https://en.wikipedia.org/wiki/Model_Context_Protocol
- https://blog.cyberdesserts.com/ai-agent-security-risks/
- https://www.pointguardai.com/ai-security-incidents/microsoft-mcp-server-vulnerability-opens-door-to-ai-tool-hijacking-cve-2026-26118
- https://dev.to/piiiico/mcp-security-vulnerabilities-in-2026-40-cves-and-counting-4pco
- https://www.digitalapplied.com/blog/ai-agent-protocol-ecosystem-map-2026-mcp-a2a-acp-ucp
- https://aimagazine.com/globenewswire/3312626

---

_Created with [skill3](https://github.com/PIsberg/skill3)._
