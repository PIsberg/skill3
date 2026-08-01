# Example output

A real `learn` run, start to finish.

## Example output

A skill3 output is a **delta**, not a primer: it covers only what changed *after* the target
model's cutoff and explicitly tells the model to rely on existing knowledge for the rest.

**MCP — `claude-opus-4-8`** ([`examples/SKILL-mcp-claude.md`](../examples/SKILL-mcp-claude.md)):
the `QueryPlanner`'s protocol-focused queries (spec release, roadmap, security) surface the
actual changelog, so the skill is a true protocol delta — the **2026-07-28 stateless release
candidate** (SEP-2567/2575 remove the session header and `initialize` handshake; new
`Mcp-Method`/`Mcp-Name` headers; `ttlMs`/`cacheScope` caching), the SEP-2577 deprecation of
Roots/Sampling/Logging, the 2026 roadmap, and 2026 CVEs — with the pre-cutoff fundamentals
treated as already known. ([`examples/SKILL-mcp.md`](../examples/SKILL-mcp.md) is the older
local-model run, hand-edited for accuracy — kept for the model-quality contrast.)

**Current events — `claude-opus-4-8`** ([`examples/SKILL-trump-claude.md`](../examples/SKILL-trump-claude.md)):
proves the same machinery works for a non-technical topic. The `QueryPlanner` expanded
`trump` into six facet queries (latest news, executive orders, tariffs, foreign policy, legal
rulings, midterms), so the skill spans the full post-cutoff picture — the Iran war, the
Venezuela strike, the Supreme Court striking down IEEPA tariffs and the Section 122/301/232
pivot, ICE detention litigation, midterms — not just one story, all from sources dated after
the cutoff. "When to use" points the model back to its existing knowledge for the baseline.
The [local-model version](../examples/SKILL-trump.md) is kept alongside it.

![Why the Trump demo is honest: with a 2026-01 cutoff, the only legitimate way to produce SKILL-trump.md is to run the real pipeline — Brave fetches post-cutoff sources, the local LLM synthesizes them, no current events fabricated from memory.](../assets/trump-demo-note.png)

- **Caveat:** these are *raw, unverified* model summaries of post-cutoff pages — included to
  demonstrate the pipeline, not as fact-checked references. Judge claims against the sources.

- [`examples/SKILL-json-rpc.md`](../examples/SKILL-json-rpc.md) — an earlier locally-synthesized
  skill, vetted clean by SkillSpector.

Every generated skill ends with a provenance footer —
`_Created with [skill3](https://github.com/PIsberg/skill3)._` — stamped deterministically
by the generator (idempotently, even across self-correction revisions).

The generated `SKILL.md` follows the
[Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
standard. Skill3 deterministically guarantees format compliance (name charset,
reserved-word stripping, description limits) regardless of what the LLM emits.

[← back to the README](../README.md)
