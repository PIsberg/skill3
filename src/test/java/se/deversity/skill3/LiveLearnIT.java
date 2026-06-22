package se.deversity.skill3;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.deversity.skill3.cli.Venv;
import se.deversity.skill3.llm.LocalLlmClient;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.HttpPageFetcher;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.SkillSpectorRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live end-to-end run against a real local LLM (Ollama) and the installed
 * SkillSpector. Discovery is seeded with real doc URLs in place of Brave search;
 * everything downstream is real. Gated on SKILL3_LIVE so it never runs in CI.
 */
class LiveLearnIT {

    @Test
    void liveLearnJsonRpc() throws Exception {
        Assumptions.assumeTrue(System.getenv("SKILL3_LIVE") != null, "set SKILL3_LIVE=1 to run");

        SearchClient seed = (q, n) -> List.of(
                "https://www.jsonrpc.org/specification",
                "https://en.wikipedia.org/wiki/JSON-RPC");

        SkillSpectorRunner spector = Files.exists(Venv.bin("skillspector"))
                ? new SkillSpectorRunner(Venv.bin("skillspector").toString())
                : null;

        String model = System.getenv().getOrDefault("SKILL3_MODEL", "qwen2.5:0.5b");
        LearnPipeline pipeline = new LearnPipeline(
                seed, new HttpPageFetcher(), new DateExtractor(),
                new LocalLlmClient("http://localhost:11434", model), spector);

        Path out = Path.of("build", "live", "json-rpc");
        LearnPipeline.Result res = pipeline.run(new LearnPipeline.Request(
                "json-rpc", "claude-opus-4-8", new Cutoff(YearMonth.of(2026, 1), "test"), false, out));

        System.out.println("=== VETTED=" + res.vetted() + " CLEAN=" + res.clean() + " ===");
        if (res.report() != null) {
            System.out.println("findings=" + res.report().findings().size());
        }
        System.out.println("=== SKILL.md ===");
        System.out.println(Files.readString(res.skillFile()));

        assertTrue(Files.exists(res.skillFile()));
    }
}
