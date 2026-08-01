# Skill3 — a fully local AI Skill Relearner

<p align="center">
  <img src="docs/demo.gif" alt="Skill3 learning a skill end-to-end: offline discovery, input-corpus vetting with a live progress bar (secret redaction + SkillSpector), local synthesis, and a clean SkillSpector pass." width="100%">
</p>

> A real, fully-offline `learn` run over a made-up corpus (`examples/demo-corpus.txt`).
> Recorded from the real run by [`docs/record-demo.py`](docs/record-demo.py) (asciicast +
> [`agg`](https://github.com/asciinema/agg)); the idle synthesis wait is time-compressed.
> A [VHS](https://github.com/charmbracelet/vhs) recipe is in [`docs/demo.tape`](docs/demo.tape).

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/PIsberg/skill3/actions/workflows/ci.yml/badge.svg)](https://github.com/PIsberg/skill3/actions/workflows/ci.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://adoptium.net/)
[![Static analysis: Error Prone · PMD · SpotBugs · ArchUnit](https://img.shields.io/badge/static%20analysis-Error%20Prone%20%C2%B7%20PMD%20%C2%B7%20SpotBugs%20%C2%B7%20ArchUnit-success)](docs/DEVELOPMENT.md)
[![Donate](https://img.shields.io/badge/Donate-PayPal-0079C1?logo=paypal&logoColor=white)](https://paypal.me/isbergpeter)

Skill3 is a lightweight Java CLI that **relearns a technical skill** for an AI
agent. It discovers documentation sources, scores them for **authority** and
**freshness** (anchored to a target model's knowledge cutoff), synthesizes them
with a **local LLM** into an Agent Skills `SKILL.md`, and vets the result with
NVIDIA's [SkillSpector](https://github.com/NVIDIA/SkillSpector).

The point: a model only knows what existed before its training cutoff. Skill3
gathers what changed **after** that cutoff and bakes it into a skill the agent
can load — so it stops emitting deprecated patterns.

- **General** — no skill is hardcoded; the model plans the searches and freshness is
  driven by a cutoff *date*, so it works for any topic (technical or not).
- **Delta, not primer** — a generated skill covers only what changed *after* the
  cutoff and tells the model to rely on existing knowledge for the rest.
- **Local-first** — by default synthesis runs on a local LLM (Ollama), so the only
  external service is discovery (Brave Search). A hosted provider (any
  OpenAI-compatible gateway, or Claude via the native SDK) is opt-in.
- **Cutoff-anchored** — the discovery window is bounded by the target model's cutoff
  (below) and today (above), so results are the slice the model doesn't already know.
- **Quality-gated** — the build runs Error Prone, PMD, SpotBugs and ArchUnit, and
  ships compile-time AI guardrails via [VibeTags](https://github.com/PIsberg/vibetags).

See [docs/SPEC.md](docs/SPEC.md) for the full specification,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, and
[docs/PLAN.md](docs/PLAN.md) for the roadmap.

---

## What is a knowledge cutoff — and why it's the whole idea

A large language model is trained on a snapshot of the world that ends on a fixed date:
its **knowledge cutoff**. Everything before that date the model may know; everything
**after** it the model has simply never seen. Claude Opus 4.8, for example, has a cutoff
of **January 2026** — ask it about anything from February 2026 onward and it will either
say it doesn't know, or (worse) confidently answer using stale, pre-cutoff information.

The cutoff is not a bug to be patched; it's a hard property of how the model was trained.
You can't retrain the model, but you *can* hand it the missing slice of the world at
runtime. That is the entire premise of Skill3:

> **Take a topic and a model's cutoff date, gather only what changed *after* that date,
> and compile it into a `SKILL.md` the agent loads — so it answers from current reality
> instead of stale memory.**

Concretely, the cutoff date drives a date-bounded web search: discovery starts at the
cutoff and ends today (`2026-01-01to<today>`), so the pipeline spends its effort on
material the model could not possibly already know, rather than re-summarising what it
learned in training. Everything else — authority scoring, freshness ranking, local
synthesis, vetting — exists to turn that fresh slice into something an agent can trust.

This works for **any** topic, not just code (see the [examples](docs/EXAMPLE-OUTPUT.md) — a
software protocol *and* current events). The cutoff is the dial; the skill is the output.

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

## The pipeline: plan → Brave → synthesize → (verify) → vet

Skill3 is a linear pipeline (`LearnPipeline`). The synthesis model (local Ollama by
default, or a hosted provider) is used at four points — to **plan** the searches, to
**synthesize** the skill, optionally to **verify** it, and to **revise** it during
vetting. **Brave** does discovery and **SkillSpector** does safety vetting.

```
                 ┌──────── Phase 0: Plan (model) ─────────┐
  topic + cutoff ►  QueryPlanner → N post-cutoff queries  │   topic-agnostic; no per-topic logic
                 └──────────────────┬─────────────────────┘
                                    ▼
                 ┌──────────── Phase 1: Discovery & Retrieval ─────────────┐
  per query ─► Brave Search ─► fetch pages (parallel) ─► extract dates ─► score authority
                 └───────────────────────────────────────────┬───────────┘
                                                              ▼
                 ┌──────────────── Phase 2: Ranking ──────────────┐
                 │  consensus (prune lonely code blocks)           │   ranked ContextBundle
                 │  freshness: cutoff ≤ published ≤ today           │   (future-dated dropped)
                 │  authoritative hosts ranked first               │
                 └──────────────────────────────┬─────────────────┘
                                                 ▼
                 ┌──────── Phase 3: Synthesis (model) ────────┐
                 │  model drafts a post-cutoff DELTA           │   ← post-processor, not the model,
                 │  deterministic post-processor guarantees    │     guarantees the frontmatter
                 │  spec-compliant frontmatter + footer        │
                 └──────────────────────┬─────────────────────┘
                                        ▼
                 ┌──── Phase 3b: Verify (model, --verify) ────┐
                 │  re-ground each claim against the sources;  │   optional accuracy gate
                 │  demote future-dated releases to "planned"  │
                 └──────────────────────┬─────────────────────┘
                                        ▼
                 ┌──────── Phase 4: Vetting (SkillSpector) ────┐
                 │  static scan → findings                     │
                 │  self-correction loop revises (bounded)     │
                 └──────────────────────┬─────────────────────┘
                                        ▼
                        skills/<topic>/SKILL.md  (+ index.html preview)
```

| Stage | Component | Where | Notes |
|---|---|---|---|
| **Plan** | `QueryPlanner` (the synthesis model) | model | Topic-agnostic: the model expands the topic into up to 6 post-cutoff facet queries (it already knows the topic, so it knows what might have changed). No hardcoded per-topic logic. |
| **Discover** | Brave Search API → web scraper fallback, **or** `--input-file` (`FileCorpus`) | Network / **offline** | Runs every planned query (freshness-windowed); needs an API key. Or skip the network entirely and supply a user-curated corpus file — see [Offline discovery](docs/USAGE.md#offline-discovery-with---input-file). |
| **Fetch** | `RetrievalService` over virtual threads | Network | Merges/de-dupes URLs across queries, then fetches **concurrently** (one virtual thread per URL); results merged on the caller thread. |
| **Date / authority** | `DateExtractor`, `AuthorityScorer` | Local | Published-date extraction + per-host trust; `--authoritative` hosts rank first. |
| **Rank** | `IngestionPipeline` (`ConsensusValidator`, `FreshnessFilter`) | Local | Code kept only with cross-source agreement; freshness anchored to the cutoff **and bounded above by today** (future-dated sources dropped). |
| **Synthesize** | synthesis model + `SkillMdPostProcessor` | model + local | Model drafts a post-cutoff delta; the post-processor — not the model — guarantees valid frontmatter and stamps the footer. |
| **Verify** *(opt-in)* | `Verifier` (`--verify`) | model | Re-grounds every claim against the sources; removes unsupported claims, demotes future-dated releases. Worthwhile only with a capable model. |
| **Vet** | `SkillSpectorRunner` + `SelfCorrectionLoop` | Local | Static safety scan; re-drafts on findings (bounded iterations). "Clean" means safe, not necessarily accurate — hence Verify. |

With the default **local** provider, only **Discover** leaves the machine — planning,
synthesis, verification, and vetting are all local. Choosing `--llm-provider openai`
or `anthropic` moves the model calls to a hosted endpoint.

---

## Documentation

| Guide | What is in it |
|---|---|
| [Install](docs/INSTALL.md) | Requirements, build, SkillSpector setup, Brave key |
| [Usage](docs/USAGE.md) | Every `learn` flag, `--input-file`, model choice, the cutoff window |
| [Architecture](docs/ARCHITECTURE.md) | How it is structured and why — with generated diagrams |
| [Specification](docs/SPEC.md) | Full behavioural spec |
| [Development](docs/DEVELOPMENT.md) | Build gates, releases, AI guardrails |
| [Example output](docs/EXAMPLE-OUTPUT.md) | A complete run, start to finish |
| [Roadmap](docs/PLAN.md) | What is planned next |

---

## 💛 Support skill3

skill3 is built and maintained by a single developer in his spare time. If it saves you time, a small donation helps keep the project alive:

- **[Donate via PayPal](https://paypal.me/isbergpeter)** — every contribution, however small, is appreciated.
- Don't have PayPal yet? **[Sign up with this invite link](https://www.paypal.com/webapps/mch/cmd/?v=3.0&t=1784460151&fdata=OBcGAzRHBBYcHAQeSFRMKk90PRgwNE9jVWhoGjAsS0gtRmZpYAd8bEJTbQlgWnlVaFVQX3cFTEdaUUwTRBFMSy50aF1xaV11Q357XWt5UlFdUX5sbQtpdFdGdFcnAS9HcCRJR3QDVVVORltJHUVcU19nfFtzZF9jV2poTjYhDkhMJ2Z5bwZwZkNRYwFhWHpfYFZdV3UCXEdYU0xRTlRMKk90BiQWGDoHV2hqTng4BAgAAmZ5GBNpOBUOOwImCScKNBAfAyENHhMUHQwCVE9XBw88J1B.a09jVWhoHzUhDkhMJ2Z5aQV4ZURWdBlySWoFOQVJRwMWTCk3IyQkaFRMSU90Kgs1cE8CV2h5TnhrS0gICSM8LBNpFVVGZABiWHheYVdcVmIWTkdYEwwZSVRMKk90fl59Yll0QHFwV2F4XlBfVXNhbBNpdlVGIUg9AS9HcCRJR3UCXl5NRFVAHkVeWVthfFNyaFljV2poTi9pSylMRnR2aBNpdlVGIUtwSQtHcFVfXncDW1ZIRVxRDFZMSwc7PRwgDgcmV2gJTnh-Ul9VV3JgaQZ-ZEVTZw1kUX9WcEVLR2JeAxIPFTITQhEIS08VaEsLNBY2VgssHC1oKwoZDig2eRNrdFUKJl8OAzsPcEUoR2J-IDYrNT4jZDojOU90aktkMA02HyYnMDAsS0gtRmZvaQN8YUFQZgxmX3lfZFBbV3METEdaUUwRTgEEBQAKKgUhNE9jNmhoPTwuDxsfBisHMVw-PAACClkODjkPNAoMR2IUTEcQHhkVXyoeDx8KOw82NBpjVwloTj8pBhoIRmZ7eRMhOwAGNkwiDTpHcCRJR3MZXUdYU0xRThoYBBonMEtkEU9jJQxoTnppSxweAiMHPUo8MAYJNFQ9EWpHEUVJACJbHgNYUU5RDDw-NS0ZACkOBSYQI2hoL3hpDAgBFCJ5eRFpdAQVMEs0BioSOAsGOTdOHQNYUS1RDENYWVZhfV98YF5xT3h9Wm17Xl5MRmR5eUYvJx0DdBkRSWpRZVxbUXYAWVJJR11JGUNfU11laEtmcE8vHT0uHTw5Aw1MRgd5eVZ7NBFQZwBlWnJSY1BdBXZWXlZBFV4UFRYIDgxmeFohcE9hV2gkBC0vGAwLDiN5eXJpdBBUNF1mWnNSY11cVHcCDlMYQ11ISEYJUg0wLQh2YF4mV2hqTng-GR0EA2Z5GBNpMUcGMA9jUH9UaFBaUnZUWAdKQFUVHhFVCQsxK1l0YQpjV2poTispBAJMRgd5eQN8dFVEdBkiHSk5MgwGR2J3TEc2Pj45eTBMS010aA8zNAA2KScoAjxpSylMRjc9KkEnOxULPEIwHCIJPzsHCDBeGQNYUU5RDAAeDxwKLh8sNU9jNmhoXDx6W1BYXn9pYVR-YUMCZQplDH9eMAFeACYGVQVOFFxRDFZMSxsmLBgaIgsxBSAmAQYvHwAJRmYYeRN.NEUEZllmWnpfN1NbUHoAXVVIEgxASRYLDAgzLwwjNE9jVWhoGSo8Aw1MRgd5eQUpZBdUNA9jWXIAZldeX3QHXlcbEV0UThMLDAgzLwwgcE9hV2g.HC06Aw1MRgd5eQEtZ0VeYAFpWXIAZ1BfA3MFWQJNSAwVGxMIW1Y2fg50cE9hV2g7Cj8hDkhMJ2Z5bwZwZkNRYwFhWHpfYFZdV3UCXEdYU0xRSA0dAxwsFh42cE8CV2h4WGF8Xl9cV3JpeRNrdFUCIRlwKGpHEighJQgWTEVYURkXWVRMKk90IR4xIR1nRQhsXR9tWC8aEDB2KFMxJRULe1s-BW5UFwcPFmYFKwseHUhCawcIDAsnOw83dF0EHyc9Cjc8T1opCiwsPxd6YwAUJ1s0TXgiaQYJCi8&cks=ZjgxYjY4YjRlNDZjYWM1NWM4NTU1YWViMmI3YThmYjI&e=1.0)** — free for you, and PayPal gives the project a referral bonus.

## License

Apache-2.0 — see [LICENSE](LICENSE).
