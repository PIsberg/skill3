# Analysis: Using a Claude subscription as an alternative to an API key

**Branch:** `analyze-claude-subscription-auth`
**Question:** Can this project authenticate Claude synthesis with a Claude
subscription (Pro/Max) instead of an Anthropic API key — and keep both?

**Short answer:** Yes, it is technically feasible and now implemented as an
*additional* credential mode for the `anthropic` provider. The API-key path is
untouched. There are real licensing and token-lifecycle caveats, documented below.

---

## 1. How the two credentials differ

| | API key | Claude subscription |
|---|---|---|
| Format | `sk-ant-api...` | OAuth access token (`sk-ant-oat...`), short-lived |
| HTTP header | `x-api-key: <key>` | `Authorization: Bearer <token>` **+** `anthropic-beta: oauth-2025-04-20` |
| Obtained from | Anthropic Console | OAuth login flow (Claude Code / `claude` CLI `/login`, or `ant auth login`) |
| Lifetime | Long-lived, manual rotation | Minutes; refreshed by the login tool, **not** auto-refreshed when passed via env/flag |
| Billing | Per-token, pay-as-you-go | Counts against the subscription plan |

The Messages API rejects a subscription bearer token sent without the
`oauth-2025-04-20` beta header, and rejects a request that carries *both*
`x-api-key` and `Authorization` — so the two are mutually exclusive on one client.

## 2. SDK support (the enabling fact)

The project already depends on `com.anthropic:anthropic-java:2.43.0`. Its
`AnthropicOkHttpClient.Builder` natively supports both credential shapes:

- `.apiKey(String)` → sends `x-api-key`
- `.authToken(String)` → sends `Authorization: Bearer`
- `.putHeader("anthropic-beta", "oauth-2025-04-20")` → the required subscription beta header
- `fromEnv()` reads both `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN`

No new dependency, no raw-HTTP fallback, no version bump is needed. The bearer
path is a first-class SDK feature.

## 3. What was implemented (keeps both)

A subscription mode was added *alongside* the API-key mode. Selection is by
credential presence — nothing about the existing API-key behaviour changes.

- **`AnthropicChatModel`** — two static factories make the credential mode
  explicit: `withApiKey(...)` (unchanged behaviour) and `withSubscription(...)`
  (bearer token + `oauth-2025-04-20` header, retries preserved). The original
  constructors are retained for backward compatibility.
- **`LlmProviderFactory`** — the `anthropic` case now resolves a subscription
  token first (`--llm-auth-token` flag → `ANTHROPIC_AUTH_TOKEN` env). If present,
  it builds the subscription client; otherwise it falls back to the API key
  (`--llm-key` → `ANTHROPIC_API_KEY`), exactly as before. With neither, the error
  now names both credential options.
- **`LearnCommand`** — adds the `--llm-auth-token` option.
- **Tests** — `LlmProviderFactoryTest` covers the subscription path, the
  prefer-subscription-when-both behaviour, and the no-credentials error.

### Usage

```bash
# API key — unchanged
skill3 learn mcp --llm-provider anthropic --llm-model claude-opus-4-8 \
  --llm-key sk-ant-api...

# Claude subscription — new. Supply a current OAuth access token.
export ANTHROPIC_AUTH_TOKEN="$(ant auth print-credentials --access-token)"
skill3 learn mcp --llm-provider anthropic --llm-model claude-opus-4-8
```

`isCapable("anthropic")` is unchanged, so output verification still defaults on
for both modes.

## 4. Caveats (read before relying on this)

1. **Licensing / Terms of Service.** Claude Pro/Max subscription tokens are
   licensed for use with Anthropic's first-party surfaces (Claude apps, Claude
   Code). Driving a *custom* application with a subscription token may violate the
   Consumer Terms. This project provides the mechanism; whether your use is
   permitted is a Terms question, not a technical one. For unattended/server use,
   an API key (or Workload Identity Federation) is the sanctioned path.
2. **Token acquisition and refresh are out of scope.** This code does not run the
   OAuth login (PKCE) flow and does not refresh tokens. The caller must supply a
   *current* access token; subscription tokens expire in minutes, so a long
   `learn` run could outlast the token. Acquiring/refreshing it is the job of the
   `claude` CLI or `ant auth login`.
3. **Secret handling.** The subscription token is as sensitive as the API key.
   It must never be logged, echoed, or placed in fixtures — same rule as
   `BraveSearchClient.apiKey` / `LocalLlmClient.apiKey` in the project guardrails.
   The implementation passes it straight to the SDK and never stores or prints it.

## 5. Live test — Opus 4.8 against a Max subscription

Tested end-to-end with `claude-opus-4-8`, the subscription token, and a curated
input corpus (JDK 26 — see §7). Result:

- **Auth works.** The synthesis request reached Anthropic, authenticated, and
  returned a real `request_id`. A bad/expired token returns **401**; we never saw
  one.
- **The blocker is a rate limit, not auth.** Every synthesis attempt returned
  **HTTP 429 `rate_limit_error`** — a genuine quota cap on the Max plan. The SDK's
  built-in retries (3, with backoff) were not enough to clear the window.
- **Root cause: a shared plan quota.** The driving Claude Code session runs on the
  *same* Max subscription, so its own traffic consumes the plan's rate budget.
  While that session is active, the `learn` run keeps hitting the cap. This is
  caveat §4.1 / §4.2 made concrete: subscription tokens are metered against the
  plan and are not intended to drive a separate concurrent application.

### Was it parallelism? No.

A hypothesis was that the pipeline calls Claude concurrently. It does not. Every
`ChatModel.complete(...)` call site — `Synthesizer`, `Verifier`, the
self-correction fix loop, and `QueryPlanner` — runs **strictly sequentially**, one
request at a time. Input vetting uses SkillSpector (a Python tool), not Claude.
The pipeline's only concurrency is `RetrievalService` fanning out **page fetches**
(HTTP, or in-memory for a curated corpus) — never Claude. Running with
`--sequential` (§6) and `--no-verify` (a single, smallest-possible Claude call)
still 429'd immediately, confirming the cap is per-request quota, not concurrency.

### Producing the artifact under the rate limit

The synthesis just needs an uncontended quota. Either:

- **API key** (a separate quota pool): set `ANTHROPIC_API_KEY` and run without
  `--llm-auth-token`. Same code path, no contention. Cleanest for automation.
- **Subscription, with this Claude Code session closed/idle** so the Max window is
  free. PowerShell, from the repo root:

  ```powershell
  # Step 1 — load a current subscription token from the Claude Code login (no echo).
  $env:ANTHROPIC_AUTH_TOKEN = (Get-Content "$HOME\.claude\.credentials.json" -Raw |
      ConvertFrom-Json).claudeAiOauth.accessToken

  # Step 2 — run synthesis (sequential keeps it gentle on the plan's rate limit).
  .\gradlew run --args="learn jdk26 --input-file examples/JDK26-sources.corpus.txt --llm-provider anthropic --llm-model claude-opus-4-8 --sequential --output-dir skills/jdk26"

  # Step 3 — publish the generated skill under the examples convention.
  Copy-Item skills\jdk26\SKILL.md examples\SKILL-JDK26.md
  ```

  The token expires in minutes (§4.2); re-run step 1 if a run reports 401.

## 6. Synchronous / sequential mode (`--sequential`)

Added an opt-in flag, `--sequential` (alias `--synchronous`), that makes source
**page retrieval** run one URL at a time on the caller thread instead of fanning
out over virtual threads. Slower, but deterministic and gentle on a rate-limited
or fragile backend.

- `RetrievalService` gains a serial path next to the parallel default; the
  original constructor still defaults to concurrent, so nothing else changes. The
  `@AIThreadSafe`/IMMUTABLE invariant is preserved — serial execution only removes
  concurrency, it cannot weaken it (documented in the class note + `CLAUDE.md`).
- `LearnPipeline.Options` threads the flag; `LearnCommand` exposes it.
- Covered by `RetrievalServiceTest.sequentialModePreservesOrderAndRunsOnCallerThread`.

**Scope note (important):** this affects page fetching only. Model
synthesis/verification are *already* sequential, so `--sequential` does **not**
change Anthropic request volume and does **not** mitigate the §5 rate limit. It is
useful for the Brave-discovery path (many concurrent page fetches) and for
deterministic runs — not as a rate-limit workaround.

## 7. JDK 26 test corpus

`examples/JDK26-sources.txt` (free-form prose the user supplied) is not in the
pipeline's input-file format. It was converted to `examples/JDK26-sources.corpus.txt`
in the documented `=== SOURCE ===` layout (original left intact): two sources —
the JRebel "What's New in Java" article and the OpenJDK 26 JEP deep-dive. Input
vetting (prompt-injection / secret-leakage scan) passes clean on it. This is the
`--input-file` used by the commands above.

## 7b. Verification is now opt-out (`--no-verify`)

The accuracy gate (`Verifier`) used to default ON only for capable hosted
providers and OFF for local. It now defaults **ON for every provider** — accuracy
is the safer default — and is disabled with `--no-verify`. When verification runs
against a non-capable provider, the CLI prints an advisory (a weak model may
rewrite rather than re-ground) instead of silently skipping it.

Re-running the JDK 26 skill through the gate with the local model
(`qwen2.5:14b`) demonstrated the tradeoff the advisory warns about:

- **Fixed** — the fabricated `LazyConstant` constructor from the unverified run
  was corrected to `LazyConstant.of()` (grounded to the source), and JEP coverage
  went from ~6 to all 10.
- **Regressed** — the local verifier rewrote rather than surgically grounding, so
  it dropped the code examples and the `## Sources` list. The published
  `examples/SKILL-JDK26.md` is the verified output with the Sources section
  restored by hand (the one unambiguous, grounded loss).

Takeaway: opt-out verification is the right default, and a *capable* model
(Opus + `--verify`) is still the gold path — it grounds claims without discarding
structure. Local verification trades structure for accuracy.

## 8. Recommendation

Keep both modes (done). Use the **API key** for CI and any unattended pipeline —
it is the supported, non-expiring, ToS-clean path with its own quota. Treat the
**subscription mode** as a convenience for interactive local runs by a developer
who is already logged in to Claude and accepts the Terms caveat, and run it when
no other Claude client is contending for the plan's rate limit. If automated
subscription use is ever needed, the missing piece is an OAuth login+refresh
helper, not anything in the SDK.
