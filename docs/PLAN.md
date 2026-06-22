# Skill3 - Project Roadmap & Plan

Step-by-step plan to implement **Skill3: a fully local AI Skill Relearner**.
The tool discovers technical sources, scores them for authority + freshness
(anchored to a target model's knowledge cutoff), synthesizes a `SKILL.md` with a
**local LLM**, and vets it with NVIDIA's SkillSpector. No cloud provider / API key.

## Phase 1: Setup & Initialization
- [ ] Initialize the Gradle project (`build.gradle`, `settings.gradle`) with
      required dependencies (picocli, HTTP client, HTML parser, JSON).
- [ ] Create the core package structures:
  - `skill3` (main)
  - `skill3.cli` (Picocli subcommands)
  - `skill3.pipeline` (discovery + evaluative modules)
  - `skill3.llm` (local OpenAI-compatible LLM client)
  - `skill3.skillspector` (SkillSpector CLI integration)
  - `skill3.web` (HTML preview generator)
- [ ] Build a `Skill3App` CLI skeleton accepting `setup` and `learn`.

## Phase 2: Discovery & Evaluative Ingestion
- [ ] Implement `RetrievalService` with **Brave Search API** support.
- [ ] Implement the **Scraper Fallback** to parse official documentation pages
      when search results are insufficient (respect `robots.txt` / rate limits).
- [ ] Implement `DateExtractor` (HTML meta → sitemap `lastmod` → repo dates;
      undated fallback). **Critical path** for freshness.
- [ ] Implement the **target-model → cutoff** table + `--cutoff-time` override;
      error clearly on unknown model with no override.
- [ ] Build `AuthorityScorer` with heuristic/config-driven domain weighting
      (`<0.5` never overrides `1.0`).
- [ ] Build `FreshnessFilter`: combined `authority × recency` scoring anchored to
      the cutoff; pre-cutoff cannot override post-cutoff authoritative content;
      `--strict-cutoff` flag for hard filtering.
- [ ] Build `ConsensusValidator`: cross-source agreement among N independent
      high-authority sources (schema-free); discard conflicting snippets.

## Phase 3: Local LLM Synthesizer
- [ ] Implement `LocalLlmClient` against an OpenAI-compatible chat endpoint
      (default Ollama `http://localhost:11434`); stream long generations.
- [ ] Author synthesis prompt templates: build only from supplied context, prefer
      post-cutoff authoritative content, carry through stated deprecations,
      delimit scraped text as untrusted data.
- [ ] Generate `SKILL.md` with YAML frontmatter (name, description, metadata:
      version, learned-date, target-model, cutoff).

## Phase 4: SkillSpector & Self-Correction
- [ ] Implement `SetupCommand`: verify Python, clone SkillSpector, create `.venv`,
      install, and **record the actual CLI subcommands/flags + report format**
      (resolve venv binary path per platform).
- [ ] Implement `SkillSpectorRunner` via `ProcessBuilder` (explicit binary path,
      no shell string); parse the report (JSON/SARIF as discovered).
- [ ] Implement the hybrid self-correction loop: deterministic sanitization
      (strip secrets / drop flagged lines) → local-LLM revision → rescan,
      up to 3 iterations; emit + warn on residual findings.

## Phase 5: Web Preview Generator (optional / deferred)
- [ ] Create `WebPreviewGenerator` to render `SKILL.md` into a responsive
      `index.html` (Inter font, HSL dark gradients, glassmorphism).

## Phase 6: Validation & Verification
- [ ] Unit tests for `AuthorityScorer`, `FreshnessFilter`, `ConsensusValidator`,
      and `DateExtractor`, with mocked network + LLM.
- [ ] Integration runs: `learn mcp` and `learn jdk26` against a chosen
      `--target-model` / `--cutoff-time`.
- [ ] Verify generated skills are coherent, carry cutoff metadata, and pass the
      local SkillSpector security checks.
