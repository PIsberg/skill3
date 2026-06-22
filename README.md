# Skill3 — a fully local AI Skill Relearner

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/PIsberg/skill3/actions/workflows/ci.yml/badge.svg)](https://github.com/PIsberg/skill3/actions/workflows/ci.yml)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://adoptium.net/)
[![Static analysis: Error Prone · PMD · SpotBugs · ArchUnit](https://img.shields.io/badge/static%20analysis-Error%20Prone%20%C2%B7%20PMD%20%C2%B7%20SpotBugs%20%C2%B7%20ArchUnit-success)](#development)

Skill3 is a lightweight Java CLI that **relearns a technical skill** for an AI
agent. It discovers documentation sources, scores them for **authority** and
**freshness** (anchored to a target model's knowledge cutoff), synthesizes them
with a **local LLM** into an Agent Skills `SKILL.md`, and vets the result with
NVIDIA's [SkillSpector](https://github.com/NVIDIA/SkillSpector).

The point: a model only knows what existed before its training cutoff. Skill3
gathers what changed **after** that cutoff and bakes it into a skill the agent
can load — so it stops emitting deprecated patterns.

- **General** — no skill is hardcoded; freshness is driven by a cutoff *date*.
- **Local-first** — synthesis runs on a local LLM (Ollama / any OpenAI-compatible
  endpoint). The **only** external service is discovery (Brave Search).
- **Cutoff-anchored** — the discovery search window starts at the target model's
  knowledge cutoff, so results favour material the model doesn't already know.
- **Quality-gated** — the build runs Error Prone, PMD, SpotBugs and ArchUnit, and
  ships compile-time AI guardrails via [VibeTags](https://github.com/PIsberg/vibetags).

See [docs/SPEC.md](docs/SPEC.md) for the full specification,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, and
[docs/PLAN.md](docs/PLAN.md) for the roadmap.

---

## Why it matters: MCP versioning

The cleanest illustration of the problem Skill3 solves is the **Model Context Protocol**.
MCP revisions are **date-versioned** (`2024-11-05` → `2025-03-26` → `2025-06-18`), and the
changes between them are not backward-compatible: a new HTTP transport, a required
`MCP-Protocol-Version` header, removed JSON-RPC batching, new primitives like elicitation.

A model trained before mid-2025 confidently emits the *old* protocol — wrong transport,
missing header, assumptions that silently break real integrations. That's exactly the
post-cutoff drift Skill3 targets: anchor discovery at the model's cutoff, pull what changed
since, and bake it into a skill the agent loads.

See the generated example: **[`examples/SKILL-mcp.md`](examples/SKILL-mcp.md)** — an MCP skill
centred on protocol versioning and revision negotiation.

---

## The pipeline: Brave → local LLM → SkillSpector

Skill3 is a linear pipeline (`LearnPipeline`) with three external/local touch-points —
**Brave** for discovery, a **local LLM** for synthesis, and **SkillSpector** for vetting:

```
                 ┌──────────────────── Phase 1: Discovery & Retrieval ────────────────────┐
  topic ───►  Brave Search API ──► (fallback) web scraper ──► extract dates ──► score authority
                 └──────────────────────────────────────────────────────────────┬─────────┘
                                                                                  │
                 ┌──────────────── Phase 2: Ranking ──────────────┐              ▼
                 │  freshness filter (anchored to model cutoff)    │   ranked ContextBundle
                 │  cross-source consensus (prune lonely code)     │
                 └──────────────────────────────┬─────────────────┘
                                                 ▼
                 ┌──────── Phase 3: Synthesis (LOCAL) ────────┐
                 │  local LLM drafts SKILL.md                  │
                 │  deterministic post-processor guarantees    │   ← never trusts the model
                 │  spec-compliant frontmatter                 │     for format compliance
                 └──────────────────────┬─────────────────────┘
                                        ▼
                 ┌──────── Phase 4: Vetting (LOCAL) ──────────┐
                 │  SkillSpector static scan → findings        │
                 │  self-correction loop revises until clean   │
                 └──────────────────────┬─────────────────────┘
                                        ▼
                        skills/<topic>/SKILL.md  (+ index.html preview)
```

| Stage | Component | Where | Notes |
|---|---|---|---|
| **Discover** | Brave Search API → web scraper fallback | Network | The only external service; needs an API key. |
| **Date / authority** | `DateExtractor`, `AuthorityScorer` | Local | Per-host trust + published-date extraction. |
| **Rank** | `FreshnessFilter`, `ConsensusValidator` | Local | Freshness is anchored to the model cutoff; code kept only with cross-source agreement. |
| **Synthesize** | local LLM + `SkillMdPostProcessor` | Local | The post-processor — not the model — guarantees valid frontmatter. |
| **Vet** | SkillSpector + `SelfCorrectionLoop` | Local | Re-drafts until the report is clean (bounded iterations). |

Only **Discover** leaves the machine. Synthesis, vetting, and the test suite are
fully local.

---

## Requirements

- **JDK 21+** (compiled with `--release 21`; the Gradle build daemon runs on JDK 21).
- A **local LLM** exposed over an OpenAI-compatible API (e.g. [Ollama](https://ollama.com)).
- **Python 3.12–3.14** (only for `setup`; SkillSpector's supported range).
- A **[Brave Search API](https://brave.com/search/api/) key** for discovery.

## Build

```bash
./gradlew build        # compile + full quality gate (analysis) + tests
./gradlew test         # tests only (JUnit + ArchUnit)
./gradlew run --args="..."   # run the CLI
```

`build` runs the complete quality gate — see [Development](#development).

---

## Setup

### 1. Install SkillSpector (one-time)

```bash
./gradlew run --args="setup"
```

This provisions a local Python venv and installs SkillSpector into it. `learn`
runs SkillSpector with `--no-llm` so vetting stays fully local (static analysis only).

### 2. Get a Brave Search key

Discovery uses the [Brave Search API](https://brave.com/search/api/) — the **only
external service `learn` needs**.

1. Create an account at <https://brave.com/search/api/>.
2. Subscribe to a plan. The **Free** tier (a few thousand queries/month) is enough
   to try Skill3; a card may be required for verification even on the free plan.
3. Create a subscription token (your API key).
4. Provide it one of two ways:

```bash
# Option A — environment variable (picked up automatically)
export BRAVE_SEARCH_API_KEY="your-token"

# Option B — per run
./gradlew run --args="learn mcp --llm-model qwen2.5-coder:7b --brave-key your-token"
```

The token is sent in the `X-Subscription-Token` header. If no key is found,
`learn` stops early with a clear message; the key is treated as a secret
(`@AIPrivacy` — never logged).

> You don't strictly need a key to evaluate the pipeline: the example in
> [`examples/`](examples/) was produced from seeded source URLs, and the tests
> stub discovery behind the `SearchClient` interface.

---

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
| `--llm-model <name>` | Local synthesis model. | **required** |
| `--llm-endpoint <url>` | OpenAI-compatible endpoint. | `http://localhost:11434` |
| `--brave-key <key>` | Brave Search key (or `BRAVE_SEARCH_API_KEY`). | env |
| `--output-dir <path>` | Where the skill is written. | `./skills/<skill-name>` |

Output: `./skills/<skill-name>/SKILL.md` (+ an `index.html` preview).

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

## Development

`./gradlew build` enforces a quality gate. Configuration lives in
[`config/`](config/) and [`build.gradle`](build.gradle).

| Tool | Scope | Config |
|---|---|---|
| **Error Prone** (`2.50.0`) | main sources, woven into `javac` | `build.gradle` |
| **PMD** (`7.24.0`) | main sources | [`config/pmd-ruleset.xml`](config/pmd-ruleset.xml) |
| **SpotBugs** (`6.5.8`, effort `Max`) | main classes | [`config/spotbugs-exclude.xml`](config/spotbugs-exclude.xml) |
| **ArchUnit** (`1.4.0`) | layering / cycles | [`ArchitectureTest`](src/test/java/se/deversity/skill3/ArchitectureTest.java) |
| **JSpecify** (`1.0.0`) | nullness | `@NullMarked` `package-info.java` per package |

ArchUnit keeps the layering honest: `model` is a dependency-free leaf, only the
`Skill3App` composition root touches `cli`, and the sub-packages stay acyclic.

### AI guardrails (VibeTags)

The codebase is annotated with [VibeTags](https://github.com/PIsberg/vibetags) —
compile-time, `SOURCE`-retention annotations (zero runtime cost) that mark intent
for AI tools, e.g.:

- `@AIPrivacy` on the Brave API key (never log/echo it),
- `@AICore` on `SkillMdPostProcessor` (guarantees spec compliance — change with care),
- `@AISecure` on `NameSanitizer` / `BraveSearchClient`,
- `@AIImmutable` on `ContextBundle`, `@AIContext` on `CutoffResolver`.

On every compile the processor regenerates the guardrail files (`CLAUDE.md`,
`llms.txt`, `llms-full.txt`) from these annotations. There's also a
[`vibetags-usage`](.claude/skills/vibetags-usage/SKILL.md) skill describing the
full annotation set.

---

## Example output

- [`examples/SKILL-json-rpc.md`](examples/SKILL-json-rpc.md) — a real skill produced
  by the pipeline: discovery seeded with the JSON-RPC spec + Wikipedia, synthesized
  locally, and vetted clean by SkillSpector (valid frontmatter, a proper one-sentence
  `description`, correct JSON-RPC 2.0 examples, real source URLs).
- [`examples/SKILL-mcp.md`](examples/SKILL-mcp.md) — an MCP skill centred on protocol
  versioning. Produced by the pipeline, then **edited for technical accuracy** (a small
  local synthesis model conflated unrelated tools); kept as the canonical illustration
  of post-cutoff drift. Output quality scales with the synthesis model.

Every generated skill ends with a provenance footer —
`_Created with [skill3](https://github.com/PIsberg/skill3)._` — stamped deterministically
by the generator (idempotently, even across self-correction revisions).

The generated `SKILL.md` follows the
[Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
standard. Skill3 deterministically guarantees format compliance (name charset,
reserved-word stripping, description limits) regardless of what the LLM emits.

## License

Apache-2.0 — see [LICENSE](LICENSE).
