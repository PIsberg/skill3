# Using Skill3

Every flag of `learn`, offline discovery with `--input-file`, choosing a synthesis
model, and how the cutoff drives the search window. To install first, see
[INSTALL.md](INSTALL.md); for why the pipeline is shaped this way, see
[ARCHITECTURE.md](ARCHITECTURE.md).

## Usage

### Learn a skill

```bash
./gradlew run --args="learn mcp \
  --target-model claude-opus-4-8 \
  --llm-model qwen2.5-coder:7b \
  --brave-key $BRAVE_SEARCH_API_KEY"
```

Common options for `learn`:

| Option | Meaning | Default |
|---|---|---|
| `--target-model <id>` | Model the skill is *for*; used only to look up a knowledge cutoff. | `claude-opus-4-8` |
| `--cutoff-time <yyyy-MM>` | Explicit cutoff override (wins over `--target-model`). | — |
| `--strict-cutoff` | Hard-exclude sources at/before the cutoff. | off |
| `--llm-model <name>` | Synthesis model name. | **required** |
| `--llm-provider <p>` | `local` \| `openai` \| `anthropic`. | `local` |
| `--llm-endpoint <url>` | OpenAI-compatible endpoint (local/openai). | `http://localhost:11434` |
| `--llm-key <key>` | Key for hosted providers (`openai`: `LLM_API_KEY`; `anthropic`: `ANTHROPIC_API_KEY`). | env |
| `--max-tokens <n>` | Max output tokens for synthesis. | `8192` |
| `--temperature <t>` | Sampling temperature (local/openai only). | server default |
| `--rich-context` | Feed more sources/excerpts to the model (suits big-context models). | off |
| `--authoritative <hosts>` | Comma-separated hosts ranked first (e.g. `modelcontextprotocol.io,github.com`). | — |
| `--verify` / `--no-verify` | Re-ground every claim against the sources (accuracy gate, one extra model call). | on for `openai`/`anthropic`, off for `local` |
| `--brave-key <key>` | Brave Search key (or `BRAVE_SEARCH_API_KEY`). | env |
| `--input-file <path>` | Offline discovery: a user-curated corpus file used instead of Brave (no key/network). See [Offline discovery](#offline-discovery-with---input-file). | — |
| `--dry-run` | Stop after discovery + ranking; print the sources, dates and scores; write nothing. | off |
| `--no-cache` | Bypass the on-disk cache of search results and fetched pages (`~/.skill3/cache`, 7-day TTL). | off |
| `--output-dir <path>` | Where the skill is written. | `./skills/<skill-name>` |

Output: `./skills/<skill-name>/SKILL.md` (+ an `index.html` preview and a `run.json`
provenance manifest recording the queries, the exact sources and scores that backed the
skill, the verify/vet outcome, and per-phase timings).

Discovery and model calls retry transient failures (connection errors, `429`, `5xx`) with
exponential backoff (honoring `Retry-After`), and search results + fetched pages are cached
under `~/.skill3/cache` (7-day TTL) so re-running a topic skips the network — pass `--no-cache`
to force fresh fetches.

### Offline discovery with `--input-file`

`--input-file` replaces Brave with a **user-curated corpus file** you fill in
yourself — the same role Brave plays (supplying source documents), but offline:
no key, no network, fully reproducible. It slots in behind the same
`SearchClient`/`PageFetcher` seams (`FileCorpus`), so everything downstream —
date extraction, authority scoring, consensus, freshness, synthesis and vetting —
runs exactly as it would for live pages.

```bash
./gradlew run --args="learn mcp \
  --llm-model qwen2.5-coder:7b \
  --input-file ./my-sources.txt"
```

**File format.** Documents are separated by a line that reads exactly
`=== SOURCE ===`. Each starts with `key: value` headers (`url` required;
`title` and `date` as `yyyy-MM-dd` optional), then a blank line, then the body.
The body may be plain text, Markdown (fenced ```` ``` ```` code blocks and `#`
headings are recognised), or raw HTML:

````
=== SOURCE ===
url: https://modelcontextprotocol.io/specification
title: MCP Specification (2026-03 revision)
date: 2026-03-01

# Resources
The _meta field is now accepted on every request in the 2026-03 revision.

```
client.call("tools/list");
```

=== SOURCE ===
url: https://github.com/org/repo/releases
date: 2026-04-01

Release notes describing the new behaviour and flags...
````

The whole file is treated as the curated result set (every document is used —
the model's planned queries don't filter it down). Anything before the first
`=== SOURCE ===` marker is ignored, so you can keep a comment at the top. A
ready-to-copy template lives at
[`examples/input-corpus-sample.txt`](../examples/input-corpus-sample.txt).

### Choosing a synthesis model

Synthesis is the quality bottleneck (see the examples below — the *same* sources, very
different skills). Three providers, in order of fidelity to the local-first design:

1. **Bigger local model (default, keeps the no-key design).** Just pull a stronger Ollama
   model — no code, no key:
   ```bash
   ./gradlew run --args="learn mcp --llm-model qwen2.5-coder:32b --brave-key $BRAVE_SEARCH_API_KEY"
   ```
2. **Any OpenAI-compatible gateway** (OpenRouter, Together, Groq, …) — opt-in, breaks the
   no-key property only when you use it:
   ```bash
   ./gradlew run --args="learn mcp --llm-provider openai \
     --llm-endpoint https://openrouter.ai/api --llm-model <model> \
     --llm-key $LLM_API_KEY --rich-context --brave-key $BRAVE_SEARCH_API_KEY"
   ```
3. **Claude (native Anthropic SDK)** — highest quality. Uses the official
   `anthropic-java` SDK and the Messages API (not an OpenAI shim):
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-...
   ./gradlew run --args="learn mcp --llm-provider anthropic \
     --llm-model claude-opus-4-8 --rich-context --brave-key $BRAVE_SEARCH_API_KEY"
   ```
   `--temperature` is ignored for `anthropic` (Opus 4.8 rejects sampling parameters).

### How the cutoff drives the search window

The resolved cutoff (from `--target-model`, or `--cutoff-time` if given) becomes
the **start** of the Brave discovery window; today is the end. For
`claude-opus-4-8` (cutoff `2026-01`) a run today searches:

```
Cutoff: claude-opus-4-8 (2026-01)
Search window: 2026-01-01to2026-06-22
```

So discovery skips what the model already knows and surfaces only what's new since
its cutoff. Widen it for a given run with `--cutoff-time` (e.g. `--cutoff-time 2024-01`).

> **Output quality scales with the synthesis model.** A small model (e.g.
> `qwen2.5:3b`) hallucinates and conflates unrelated tools; a capable coder model
> (e.g. `qwen2.5-coder:7b` or larger) produces accurate content from the same sources.

---

[← back to the README](../README.md)
