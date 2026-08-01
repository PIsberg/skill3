# Developing Skill3

Build gates, the release process, and how the compile-time AI guardrails are
maintained. For the design behind the code, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Development

`./gradlew build` enforces a quality gate. Configuration lives in
[`config/`](../config/) and [`build.gradle`](../build.gradle).

| Tool | Scope | Config |
|---|---|---|
| **Error Prone** (`2.50.0`) | main sources, woven into `javac` | `build.gradle` |
| **PMD** (`7.24.0`) | main sources | [`config/pmd-ruleset.xml`](../config/pmd-ruleset.xml) |
| **SpotBugs** (`6.5.8`, effort `Max`) | main classes | [`config/spotbugs-exclude.xml`](../config/spotbugs-exclude.xml) |
| **ArchUnit** (`1.4.0`) | layering / cycles | [`ArchitectureTest`](../src/test/java/se/deversity/skill3/ArchitectureTest.java) |
| **JSpecify** (`1.0.0`) | nullness | `@NullMarked` `package-info.java` per package |
| **async-test-lib** (`1.7.0-RC1`) | concurrency stress tests (`@AsyncTest`) | [`ConcurrencySafetyTest`](../src/test/java/se/deversity/skill3/ConcurrencySafetyTest.java) |
| **JaCoCo** coverage gate | `check` fails below 75% instruction / 65% branch | `build.gradle` |

Tests run on JUnit Jupiter 6; the hosted Claude provider uses the official
`anthropic-java` SDK.

ArchUnit keeps the layering honest: `model` is a dependency-free leaf, only the
`Skill3App` composition root touches `cli`, and the sub-packages stay acyclic.

### Releases

Push a `v*` tag to cut a release: CI runs the full gate, builds the application
distribution (`./gradlew build` → `build/distributions/skill3-<version>.zip|tar`,
launch scripts included), and publishes a GitHub Release with auto-generated notes
and the distribution attached — see [`release.yml`](../.github/workflows/release.yml).

```bash
git tag v0.1.0 && git push origin v0.1.0
```

### AI guardrails (VibeTags)

The codebase is annotated with [VibeTags](https://github.com/PIsberg/vibetags) —
compile-time, `SOURCE`-retention annotations (zero runtime cost) that mark intent
for AI tools, e.g.:

- `@AIPrivacy` on the Brave API key (never log/echo it),
- `@AICore` on `SkillMdPostProcessor` (guarantees spec compliance — change with care),
- `@AISecure` on `NameSanitizer` / `BraveSearchClient`,
- `@AIImmutable` on `ContextBundle`, `@AIContext` on `CutoffResolver`,
- `@AIContract` on the three seams that make the pipeline testable without a network or a
  model — `ChatModel`, `SearchClient`, `PageFetcher`,
- `@AILoadBearing` on `FileCorpus` (it implements *both* discovery seams on purpose) and on
  `InputVetter` (quarantine is mitigation, not amnesty; redaction is unconditional),
- `@AISchemaSafe` on `RunManifest` — its component names are the `run.json` field names,
- `@AIIdempotent` on `SkillMdPostProcessor.render()`, which the self-correction loop re-runs
  on its own output,
- `@AIDomainModel` + `@AIArchitecture` on `Source`, mirroring the layering `ArchitectureTest`
  already enforces.

On every compile the processor regenerates the guardrail regions in `CLAUDE.md`,
`llms.txt` and `llms-full.txt`. **Never hand-edit between the `VIBETAGS-START` and
`VIBETAGS-END` markers** — that region is rewritten from the annotations on the next
compile. Everything outside them, including the hand-written briefing at the top of
`CLAUDE.md`, survives untouched; change an annotation, not the generated text.

Because [`.claude/rules/`](../.claude/rules/) exists, the layout is the *indexed* one: the
root `CLAUDE.md` keeps only the always-on safety tier inline (privacy, core, security) and
indexes the per-element detail into scoped rule files that a host tool loads when you open a
matching source file. Delete that directory and the detail moves back inline. There is also a
[`vibetags-usage`](../.claude/skills/vibetags-usage/SKILL.md) skill describing the full
annotation set.

### Diagrams

`./gradlew diagrams` re-renders the SVGs under [`diagrams/`](diagrams/) from the source with
[code-karta](https://github.com/PIsberg/codekarta), pinned from Maven Central. The renderer is
deterministic, so re-running it with unchanged source is a no-op in `git status`. Regenerate
and commit whenever a change moves the structure the diagrams describe — see
[ARCHITECTURE.md](ARCHITECTURE.md#generated-structure-diagrams).

---

[← back to the README](../README.md)
