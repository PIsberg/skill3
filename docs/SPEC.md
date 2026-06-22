# Skill3 - Technical Specification

Skill3 is a lightweight, **fully local** Java CLI utility. It ingests technical
document sources, filters them for **authority** and **freshness** (anchored to a
target model's knowledge cutoff), synthesizes them with a **local LLM** into a
structured AI agent skill (`SKILL.md`), and validates the result against security
risks using NVIDIA's `SkillSpector`.

Design principles:

- **General.** No skill is hardcoded. Freshness is driven by a cutoff *date*, not
  per-topic keyword lists, so the tool works for any technology or concept.
- **Local-first.** Synthesis runs on a local LLM (Ollama / any OpenAI-compatible
  endpoint). No cloud provider, no API key. The only network egress is
  discovery (Brave Search + web scraping).

---

## 1. System Architecture

```mermaid
graph TD
    CLI[Picocli CLI /learn] --> Pipeline[Evaluative Ingestion Pipeline]

    subgraph "Phase 1: Discovery & Retrieval"
        Pipeline --> Search[Brave Search API]
        Search -- If insufficient results --Fallback--> Scraper[Direct Web Scraper]
    end

    subgraph "Phase 2: Evaluative Ingestion"
        Search --> Dates[Publication-Date Extractor]
        Scraper --> Dates
        Dates --> Scorer[Authority Scorer]
        Dates --> Fresh[Freshness Filter - cutoff anchored]
        Dates --> Consensus[Consensus Cross-Referencer]
    end

    subgraph "Phase 3: Synthesis & Vetting"
        Scorer & Fresh & Consensus --> Synth[Local LLM Synthesizer]
        Synth --> Draft[SKILL.md Draft]
        Draft --> Spector[SkillSpector Runner]
        Spector -- Findings? --Sanitize + LLM revise--> Synth
        Spector -- Clean --> Output[Final SKILL.md + index.html Preview]
    end
```

---

## 2. CLI Interface

Arguments are parsed with `picocli`. Two commands are exposed.

### `setup`
Prepares the local environment for verification.
*   **Actions:**
    1. Locate a compatible Python by probing interpreters (`py -3.14/-3.13/-3.12`
       on Windows, `python3.x`/`python3` on POSIX). SkillSpector requires
       `>=3.12,<3.15`; do not assume `python` on PATH is new enough.
    2. Clone NVIDIA's SkillSpector from `https://github.com/NVIDIA/SkillSpector.git`
       to local `skillspector-src`.
    3. Initialize a Python virtual environment (`.venv`) inside the `skill3`
       directory.
    4. Install dependencies into the venv (`pip install -e skillspector-src`),
       resolving the venv binary path per platform (`Scripts/` on Windows,
       `bin/` on POSIX).
    5. Verify by invoking the installed `skillspector` CLI and recording its
       actual subcommands/flags (do not assume them — see §4.2).

### `learn <skill-name>`
Runs the evaluative pipeline to learn a new skill.
*   **Argument:**
    *   `<skill-name>`: technology or concept to learn (e.g. `mcp`, `jdk26`).
*   **Options:**
    *   `--target-model <id>`: the model the skill is *for*. Used **only** to look
        up a knowledge-cutoff date (no inference call). Default: `claude-opus-4-8`.
    *   `--cutoff-time <iso-date>`: explicit cutoff override; wins over
        `--target-model`. Use for unknown models or when the built-in table is
        stale.
    *   `--strict-cutoff`: hard-filter sources published at/before the cutoff
        (see §3B). Off by default (weighted ranking).
    *   `--llm-model <name>`: local synthesis model (e.g. `qwen2.5-coder`).
    *   `--llm-endpoint <url>`: OpenAI-compatible local endpoint.
        Default `http://localhost:11434` (Ollama).
    *   `--brave-key <key>`: Brave Search API key. If omitted, checks
        `BRAVE_SEARCH_API_KEY`.
    *   `--output-dir <path>`: output location. Default `./skills/<skill-name>`.

> Note: there is no `--provider` or `--api-key`. Synthesis is local-only.

### Target-model → cutoff table

A small **built-in, overridable** table maps known model IDs to cutoff dates,
e.g. `claude-opus-4-8 → 2026-01`. The table is expected to go stale; entries
should be sourced from model documentation and are always overridable with
`--cutoff-time`. An unknown `--target-model` without `--cutoff-time` is an error
that names the override flag.

---

## 3. Evaluative Ingestion Pipeline

Scraped data is processed through four general (skill-agnostic) modules.

### A. Authority Scorer
Assigns trust multipliers to URLs before ingestion. Domain classification is
heuristic/config-driven — **not** hardcoded per skill.
*   **Trust Weights (illustrative):**
    *   Official documentation domains: `1.0`
    *   Official SDK / language repositories: `1.0`
    *   Verified developer portals / standard repositories: `0.7`
    *   Unverified blogging platforms, `medium.com`, `dev.to`, personal gists: `0.2`
*   **Rule:** content scoring `< 0.5` is used for structural reference only and
    never overrides constraints from official `1.0`-weighted sources.

### B. Freshness Filter (cutoff-anchored)
Replaces all per-topic deprecation lists. The cutoff date (from `--target-model`
or `--cutoff-time`) is the freshness anchor: the value of relearning a skill is
the content published *after* the target model's training cutoff.
*   **Combined scoring (default):** each source is scored `authority × recency`,
    where `recency` is a multiplier for post-cutoff publication. Pre-cutoff
    content remains usable for baseline structure but **can never override a
    post-cutoff authoritative claim** (same override semantics as §3A).
*   **`--strict-cutoff`:** promotes the soft floor to a hard filter — sources
    published at/before the cutoff are excluded entirely.
*   Depends on reliable publication dates (§3D).
*   **Scope of the promise:** the filter *surfaces* fresh authoritative content;
    it does not *infer* deprecations. "X is now deprecated" is captured only when
    a fresh authoritative source states it (and the synthesizer carries it
    through), not by the filter itself.

### C. Consensus Cross-Referencing
General, schema-free. A snippet or claim is trusted when it recurs consistently
across **N independent high-authority sources**. Snippets that conflict with the
high-authority consensus are discarded. (No per-topic JSON-RPC schemas.)

### D. Publication-Date Extractor
Critical path — the entire freshness mechanism depends on it. Extracts a date per
source from, in order:
1. HTML metadata (`article:published_time`, OpenGraph, `<time>` elements).
2. Sitemap `lastmod`.
3. Repository signals (latest release / relevant commit date) for repo sources.
*   **Fallback when no date is found:** mark the source *undated* — excluded under
    `--strict-cutoff`, given a low recency weight otherwise.

---

## 4. Synthesis & Vetting Loop

### 4.1 Synthesis (local LLM)

Output target is the **Agent Skills `SKILL.md`** standard (used by Claude Code,
the Claude apps, and the API Skills feature). A skill is a *directory* containing
`SKILL.md` plus any bundled reference files.

#### 4.1.1 Synthesizer input contract
The pipeline hands the local LLM a structured **context bundle**, not raw HTML:

```jsonc
{
  "skill_name": "mcp",
  "target_model": "claude-opus-4-8",
  "cutoff": "2026-01",
  "sources": [                       // ranked, highest combined score first
    {
      "url": "https://modelcontextprotocol.io/...",
      "authority": 1.0,
      "published": "2026-05-21",
      "post_cutoff": true,
      "recency_weight": 1.0,
      "combined_score": 1.0,
      "consensus_count": 3,          // independent high-authority sources agreeing
      "excerpts": ["..."],           // extracted prose, headings, API signatures
      "code_blocks": ["..."]         // fenced snippets that survived consensus
    }
  ]
}
```

The synthesis prompt enforces:
*   Build **only** from `sources` (reduces hallucination; limits prompt-injection
    impact — scraped text is delimited as untrusted data, see §6).
*   Prefer `post_cutoff: true` content; carry through deprecations those sources
    explicitly state. Where fresh and stale content conflict, the fresh
    authoritative source wins (mirrors §3A/§3B override rules).
*   Cite provenance: each non-obvious claim/snippet traces to a source URL,
    surfaced in a **Sources** section.

#### 4.1.2 Required output structure

```markdown
---
name: <sanitized-kebab-name>          # ≤64 chars; [a-z0-9-]; no "anthropic"/"claude"
description: <what it does + when to use it>   # non-empty, ≤1024 chars, no XML tags
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
# <Skill Title>

## Overview / When to use
## Instructions            # step-by-step, current patterns
## Modern vs deprecated    # only when fresh sources state a deprecation
## Examples                # concrete, from consensus-validated code blocks
## Sources                 # provenance URLs
```

#### 4.1.3 Generator-enforced validation (deterministic, around the LLM call)
The LLM drafts; the generator **guarantees** format compliance — it does not
trust the model to obey the field rules:
*   **`name`:** lowercase, `[a-z0-9-]` only, ≤64 chars, strip/replace the reserved
    words `anthropic` and `claude`. (`learn claude` must **not** yield
    `name: claude` — derive a compliant name like `claude-mcp` → rejected too;
    use a neutral fallback such as `<topic>-skill` when the raw name collides
    with a reserved word.)
*   **`description`:** non-empty, ≤1024 chars, no XML tags; truncate/regenerate if
    violated.
*   **Frontmatter:** valid YAML; all fenced code blocks closed.
*   On violation: apply the deterministic fix, or re-prompt the local model once,
    then fail loudly rather than emit a malformed skill.

> Keep the client generic (OpenAI-compatible chat completions). Stream long
> generations.

### 4.2 Security Vetting
Run the installed SkillSpector CLI against the generated folder. Confirmed
against the NVIDIA/SkillSpector source **and a live run**: the command is
`skillspector scan <path> --no-llm --format json` (Typer CLI; formats
`terminal|json|markdown|sarif`). Skill3 passes **`--no-llm`** so SkillSpector runs
its static analysis only — without it the CLI exits 2 demanding an LLM API key,
which would break the fully-local design. SkillSpector exits non-zero when
findings exist; the runner ignores the exit code and parses stdout. With `--format json` the report is a JSON object whose findings live
under an **`issues[]`** array; each finding carries `id`, `category`, `severity`,
`confidence`, `explanation` (the message), and a nested
`location.{file,start_line}`. The report is written to stdout (logs go to
stderr). SkillSpector's optional stage-2 LLM semantic analysis is out of scope
unless explicitly wired to the local endpoint later.

### 4.3 Self-Correction (hybrid)
If SkillSpector reports findings (prompt-injection vectors, excessive authority,
credential exposure, etc.):
1.  **Deterministic pre-pass:** mechanically strip detected secrets and drop
    flagged instruction lines where the fix is unambiguous.
2.  **Local-LLM revision pass:** send the current draft + extracted findings to
    the local model to revise the offending sections.
3.  **Rescan.**
4.  Iterate up to **3 times**. If findings remain, emit the file and **warn** the
    user in the CLI with the residual findings.

---

## 5. Web Preview Specification (optional / deferred)

Generated after the pipeline succeeds; not required for the core value path.
The `index.html` preview should follow:
- **Typography:** Google Fonts "Inter" or "Outfit".
- **Color Scheme (HSL Dark Theme):**
  - Background: deep gray-blue `hsl(220, 20%, 10%)`
  - Secondary cards: translucent `hsla(220, 15%, 16%, 0.7)` with glassmorphism
    backdrop blur.
  - Accents: electric teal `hsl(180, 100%, 45%)` and neon magenta
    `hsl(320, 100%, 60%)`.
- **Micro-Animations:** hover states on rule selectors, copy-to-clipboard on code
  blocks, expand/collapse for deprecated-vs-modern comparisons.

---

## 6. Security Considerations

- **Prompt injection from scraped content.** Scraped pages flow into the local
  synthesizer; a malicious page could attempt to steer synthesis. Mitigations:
  treat scraped text as data (clearly delimited in the prompt), constrain the
  synthesizer to the supplied context, and rely on the SkillSpector vetting pass
  on the *output*. Risk is bounded by the local-only execution (no data egress).
- **Process execution.** SkillSpector runs via `ProcessBuilder` against the
  cloned venv; invoke the recorded binary path explicitly, never a shell string.
- **Scraper etiquette.** Respect `robots.txt` and rate limits in the fallback
  scraper.
