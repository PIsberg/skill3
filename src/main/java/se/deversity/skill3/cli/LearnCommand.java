package se.deversity.skill3.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import se.deversity.skill3.LearnPipeline;
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

    @Option(names = "--llm-model", required = true, description = "Local synthesis model name.")
    String llmModel;

    @Option(names = "--llm-endpoint", defaultValue = "http://localhost:11434",
            description = "OpenAI-compatible endpoint. Default: ${DEFAULT-VALUE}")
    String llmEndpoint;

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

        LearnPipeline pipeline = new LearnPipeline(
                new BraveSearchClient(key, freshness),
                new HttpPageFetcher(),
                new DateExtractor(),
                new LocalLlmClient(llmEndpoint, llmModel),
                new SkillSpectorRunner(Venv.bin("skillspector").toString()));

        try {
            System.out.println("Discovering and synthesizing '" + skillName + "' with " + llmModel + "...");
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
