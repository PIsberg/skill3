package se.deversity.skill3.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import se.deversity.skill3.LearnPipeline;
import se.deversity.skill3.llm.AnthropicChatModel;
import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.llm.LocalLlmClient;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.pipeline.BraveSearchClient;
import se.deversity.skill3.pipeline.CutoffResolver;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.HttpPageFetcher;
import se.deversity.skill3.skillspector.Finding;
import se.deversity.skill3.skillspector.SkillSpectorRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Runs the full relearn pipeline for one skill (thin CLI over {@link LearnPipeline}). */
@Command(name = "learn",
        mixinStandardHelpOptions = true,
        description = "Discover, evaluate, synthesize and vet a SKILL.md for a topic.")
public class LearnCommand implements Callable<Integer> {

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

    @Option(names = "--max-tokens", defaultValue = "8192",
            description = "Max output tokens for synthesis. Default: ${DEFAULT-VALUE}")
    int maxTokens;

    @Option(names = "--temperature",
            description = "Sampling temperature (local/openai only; ignored for anthropic).")
    Double temperature;

    @Option(names = "--rich-context",
            description = "Feed more sources/excerpts to the model (suits big-context models).")
    boolean richContext;

    @Option(names = "--brave-key", description = "Brave Search key (or env BRAVE_SEARCH_API_KEY).")
    String braveKey;

    @Option(names = "--output-dir", description = "Output dir. Default: ./skills/<skill-name>")
    String outputDir;

    @Override
    public Integer call() {
        String key = braveKey != null ? braveKey : System.getenv("BRAVE_SEARCH_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("No Brave Search key. Pass --brave-key or set BRAVE_SEARCH_API_KEY.");
            return 2;
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

        String freshness = cutoff.freshnessRange(LocalDate.now(ZoneId.systemDefault()));
        System.out.println("Search window: " + freshness);

        final ChatModel chat;
        String provider = llmProvider == null ? "local" : llmProvider.toLowerCase(Locale.ROOT);
        switch (provider) {
            case "anthropic" -> {
                String akey = llmKey != null ? llmKey : System.getenv("ANTHROPIC_API_KEY");
                if (akey == null || akey.isBlank()) {
                    System.err.println("anthropic provider needs a key: pass --llm-key or set ANTHROPIC_API_KEY.");
                    return 2;
                }
                chat = new AnthropicChatModel(akey, llmModel, maxTokens);
            }
            case "openai" -> {
                String okey = llmKey != null ? llmKey : System.getenv("LLM_API_KEY");
                if (okey == null || okey.isBlank()) {
                    System.err.println("openai provider needs a key: pass --llm-key or set LLM_API_KEY.");
                    return 2;
                }
                chat = new LocalLlmClient(llmEndpoint, llmModel, maxTokens, okey, temperature);
            }
            case "local" -> chat = new LocalLlmClient(llmEndpoint, llmModel, maxTokens, llmKey, temperature);
            default -> {
                System.err.println("Unknown --llm-provider '" + provider + "' (use local | openai | anthropic).");
                return 2;
            }
        }

        LearnPipeline pipeline = new LearnPipeline(
                new BraveSearchClient(key, freshness),
                new HttpPageFetcher(),
                new DateExtractor(),
                chat,
                new SkillSpectorRunner(Venv.bin("skillspector").toString()),
                richContext);

        try {
            System.out.println("Discovering and synthesizing '" + skillName + "' with "
                    + provider + ":" + llmModel + "...");
            LearnPipeline.Result res = pipeline.run(
                    new LearnPipeline.Request(skillName, targetModel, cutoff, strictCutoff, outDir));

            reportVetting(res);
            System.out.println("Done. Wrote:");
            System.out.println("  " + res.skillFile());
            System.out.println("  " + res.htmlFile());
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
