# Installing Skill3

Requirements, build, and the one-time setup for SkillSpector and a Brave Search key.
For what to run once it is installed, see [USAGE.md](USAGE.md).

## Requirements

- **JDK 25** (compiled with `--release 25`). Gradle provisions the JDK 25 toolchain
  automatically (auto-detected or downloaded via the Foojay resolver), so you don't
  need JDK 25 on `JAVA_HOME` — any JDK that runs Gradle will do.
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
> [`examples/`](../examples/) was produced from seeded source URLs, and the tests
> stub discovery behind the `SearchClient` interface. For a real run with **no
> key and no network at all**, supply your own sources with `--input-file` (see
> [Offline discovery](#offline-discovery-with---input-file)).

---

[← back to the README](../README.md)
