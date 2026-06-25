package se.deversity.skill3.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import se.deversity.skill3.LearnPipeline;
import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.llm.LlmProviderFactory;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;
import se.deversity.skill3.pipeline.CutoffResolver;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.DiscoveryProvider;
import se.deversity.skill3.pipeline.DiskCache;
import se.deversity.skill3.pipeline.PageFetcher;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.Finding;
import se.deversity.skill3.skillspector.SkillSpectorRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Runs the full relearn pipeline for one skill (thin CLI over {@link LearnPipeline}). */
@Command(name = "learn",
        mixinStandardHelpOptions = true,
        description = "Discover, evaluate, synthesize and vet a SKILL.md for a topic.")
public class LearnCommand implements Callable<Integer> {

    /** Days a cached search result or fetched page stays valid before it's re-fetched. */
    private static final int CACHE_TTL_DAYS = 7;

    @Parameters(index = "0", description = "Skill/topic to learn (e.g. mcp, jdk26).")
    String skillName;

    @Option(names = "--target-model", defaultValue = "claude-opus-4-8",
            description = "Model the skill is for; used only as a cutoff lookup. Default: ${DEFAULT-VALUE}")
    String targetModel;

    @Option(names = "--cutoff-time", description = "Explicit cutoff (yyyy-MM); overrides --target-model.")
    String cutoffTime;

    @Option(names = "--strict-cutoff", description = "Exclude sources at/before the cutoff.")
    boolean strictCutoff;

    @Option(names = "--llm-model", required = true, description = "Synthesis model name.")
    String llmModel;

    @Option(names = "--llm-provider", defaultValue = "local",
            description = "Synthesis provider: local | openai | anthropic. Default: ${DEFAULT-VALUE}")
    String llmProvider;

    @Option(names = "--llm-endpoint", defaultValue = "http://localhost:11434",
            description = "OpenAI-compatible endpoint (local/openai). Default: ${DEFAULT-VALUE}")
    String llmEndpoint;

    @Option(names = "--llm-key",
            description = "API key for hosted providers (openai: env LLM_API_KEY; anthropic: env ANTHROPIC_API_KEY).")
    String llmKey;

    @Option(names = "--llm-auth-token",
            description = "Claude subscription (Pro/Max) OAuth access token for anthropic (or env "
                    + "ANTHROPIC_AUTH_TOKEN). Sent as a Bearer token; preferred over --llm-key when set. "
                    + "Supply a current token (e.g. from your Claude CLI login); it is not refreshed here.")
    String llmAuthToken;

    @Option(names = "--max-tokens", defaultValue = "8192",
            description = "Max output tokens for synthesis. Default: ${DEFAULT-VALUE}")
    int maxTokens;

    @Option(names = "--temperature",
            description = "Sampling temperature (local/openai only; ignored for anthropic).")
    Double temperature;

    @Option(names = "--rich-context",
            description = "Feed more sources/excerpts to the model (suits big-context models).")
    boolean richContext;

    @Option(names = {"--sequential", "--synchronous"},
            description = "Fetch source pages one at a time instead of concurrently. Slower, but "
                    + "gentler on rate-limited backends and fully deterministic. Note: model "
                    + "synthesis/verification are already sequential, so this only affects page "
                    + "retrieval.")
    boolean sequential;

    @Option(names = "--authoritative", split = ",",
            description = "Comma-separated authoritative hosts ranked first (e.g. modelcontextprotocol.io,github.com).")
    java.util.List<String> authoritative;

    @Option(names = "--verify", negatable = true,
            description = "Re-ground every claim against the sources after synthesis (accuracy gate, "
                    + "one extra model call). ON by default for every provider; pass --no-verify to "
                    + "opt out. A capable model grounds best — a weak local model may rewrite rather "
                    + "than ground, but accuracy is the safer default.")
    Boolean verify;

    @Option(names = "--dry-run",
            description = "Stop after discovery + ranking: print the planned queries and the ranked "
                    + "sources with their dates and scores, then exit without synthesis or writing files.")
    boolean dryRun;

    @Option(names = "--brave-key", description = "Brave Search key (or env BRAVE_SEARCH_API_KEY).")
    String braveKey;

    @Option(names = "--input-file",
            description = "Offline discovery: a user-curated corpus file (see docs) used instead of "
                    + "Brave. When set, no Brave key or network is needed for discovery.")
    String inputFile;

    @Option(names = "--no-cache",
            description = "Bypass the on-disk cache of search results and fetched pages "
                    + "(default cache: ~/.skill3/cache, " + CACHE_TTL_DAYS + "-day TTL).")
    boolean noCache;

    @Option(names = "--output-dir", description = "Output dir. Default: ./skills/<skill-name>")
    String outputDir;

    @Option(names = "--no-fail-on-findings",
            description = "Do NOT exit non-zero when high-severity (HIGH/CRITICAL) SkillSpector findings "
                    + "remain after input + output vetting. The gate is ON by default (exit 3); the "
                    + "skill is written either way.")
    boolean noFailOnFindings;

    @Override
    public Integer call() {
        boolean fileMode = inputFile != null && !inputFile.isBlank();

        String key = null;
        if (!fileMode) {
            key = braveKey != null ? braveKey : System.getenv("BRAVE_SEARCH_API_KEY");
            if (key == null || key.isBlank()) {
                System.err.println("No Brave Search key. Pass --brave-key or set BRAVE_SEARCH_API_KEY, "
                        + "or use --input-file for offline discovery.");
                return 2;
            }
        }

        final Cutoff cutoff;
        try {
            cutoff = new CutoffResolver().resolve(targetModel, cutoffTime);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        System.out.println("Cutoff: " + cutoff.label());

        Path outDir = outputDir != null ? Path.of(outputDir) : Path.of("skills", skillName);

        final DiscoveryProvider.Sources sources;
        if (fileMode) {
            try {
                sources = DiscoveryProvider.fromInputFile(Path.of(inputFile));
            } catch (IOException e) {
                System.err.println("Cannot read --input-file '" + inputFile + "': " + e.getMessage());
                return 2;
            }
            System.out.println("Discovery: input file " + inputFile + " (offline, no Brave)");
        } else {
            String freshness = cutoff.freshnessRange(LocalDate.now(ZoneId.systemDefault()));
            System.out.println("Search window: " + freshness);
            DiskCache cache = null;
            if (!noCache) {
                Path cacheDir = Path.of(System.getProperty("user.home"), ".skill3", "cache");
                cache = new DiskCache(cacheDir, Duration.ofDays(CACHE_TTL_DAYS));
                System.out.println("Cache: " + cacheDir + " (--no-cache to bypass)");
            }
            sources = DiscoveryProvider.brave(key, freshness, cache);
        }
        SearchClient search = sources.search();
        PageFetcher fetcher = sources.fetcher();

        String provider = llmProvider == null ? "local" : llmProvider.toLowerCase(Locale.ROOT);
        final ChatModel chat;
        try {
            chat = LlmProviderFactory.create(new LlmProviderFactory.Config(
                    provider, llmEndpoint, llmModel, maxTokens, llmKey, temperature, llmAuthToken));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        java.util.Set<String> authoritativeHosts = authoritative == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(authoritative);

        // Verification is the accuracy gate, and accuracy is the safer default — so it is ON for
        // every provider unless the caller explicitly opts out with --no-verify. When shipping
        // unverified, warn loudly so "looks clean" is never mistaken for "checked against sources";
        // when verifying with a non-capable provider, note that a weak model may rewrite rather
        // than ground (the original reason local used to default off — now an advisory, not a default).
        boolean capableProvider = LlmProviderFactory.isCapable(provider);
        boolean effectiveVerify = verify != null ? verify : true;
        if (!effectiveVerify) {
            System.out.println("WARNING: shipping UNVERIFIED synthesis — claims are NOT re-grounded "
                    + "against the sources (you passed --no-verify). Drop --no-verify to enable the "
                    + "accuracy gate.");
        } else if (!capableProvider) {
            System.out.println("Note: verification is ON with a non-capable provider ('" + provider
                    + "'); a weak model may rewrite rather than re-ground. Use a capable provider for "
                    + "the strongest accuracy gate, or --no-verify to skip it.");
        }

        LearnPipeline.Options options = new LearnPipeline.Options(
                5, 2, 3, richContext, authoritativeHosts, effectiveVerify, sequential);

        LearnPipeline pipeline = new LearnPipeline(
                search,
                fetcher,
                new DateExtractor(),
                chat,
                new SkillSpectorRunner(Venv.bin("skillspector").toString()),
                options);

        LearnPipeline.Request request =
                new LearnPipeline.Request(skillName, targetModel, cutoff, strictCutoff, outDir);

        if (dryRun) {
            try {
                LearnPipeline.Discovery discovery = pipeline.discover(request);
                List<Source> ranked = discovery.ranked();
                System.out.println("Dry run — " + discovery.queries().size() + " quer(ies), "
                        + ranked.size() + " ranked source(s), best first:");
                for (Source s : ranked) {
                    System.out.printf(Locale.ROOT,
                            "  [score %.2f] %s%n"
                                    + "    published=%s postCutoff=%s authority=%.2f recency=%.2f consensus=%d%n",
                            s.combinedScore, s.url, s.published, s.postCutoff,
                            s.authority, s.recencyWeight, s.consensusCount);
                }
                System.out.println("Dry run complete — no synthesis, no files written.");
                return 0;
            } catch (IllegalStateException e) {
                System.err.println(e.getMessage());
                return 1;
            } catch (IOException e) {
                System.err.println("discovery failed: " + e.getMessage());
                return 1;
            }
        }

        try {
            System.out.println("Discovering and synthesizing '" + skillName + "' with "
                    + provider + ":" + llmModel + "...");
            LearnPipeline.Result res = pipeline.run(request);

            reportVetting(res);
            System.out.println("Done. Wrote:");
            System.out.println("  " + res.skillFile());
            System.out.println("  " + res.htmlFile());
            System.out.println("  " + res.manifestFile());

            if (!noFailOnFindings) {
                List<Finding> blocking = res.blockingFindings();
                if (!blocking.isEmpty()) {
                    System.err.println("FAILED: " + blocking.size() + " high-severity SkillSpector "
                            + "finding(s) remain after vetting (input + output). The skill was written "
                            + "but flagged; pass --no-fail-on-findings to emit it without failing.");
                    return 3;
                }
            }
            return 0;
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("learn failed: " + e.getMessage());
            return 1;
        }
    }

    private static void reportVetting(LearnPipeline.Result res) {
        if (!res.vetted()) {
            System.out.println("SkillSpector unavailable; skipping vetting. Run `setup` to enable it.");
        } else if (res.clean()) {
            System.out.println("SkillSpector: clean.");
        } else {
            System.out.println("WARNING: " + res.report().findings().size() + " finding(s) remain:");
            for (Finding f : res.report().findings()) {
                System.out.println("  - [" + f.severity() + "] " + f.category() + ": " + f.message());
            }
        }
    }
}
