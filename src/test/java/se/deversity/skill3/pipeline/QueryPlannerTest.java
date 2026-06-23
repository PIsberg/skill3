package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.model.Cutoff;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryPlannerTest {

    private static final Cutoff CUTOFF = new Cutoff(YearMonth.of(2026, 1), "test");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @Test
    void stripsListMarkersAndQuotesButKeepsLeadingYears() {
        ChatModel model = (system, user) ->
                "1. Trump Iran strike 2026\n- Trump tax bill 2026\n\"Trump executive orders\"\n2026 Trump recap\n";
        List<String> queries = new QueryPlanner(model).plan("trump", CUTOFF, TODAY);
        assertEquals(List.of(
                "Trump Iran strike 2026",
                "Trump tax bill 2026",
                "Trump executive orders",
                "2026 Trump recap"), queries);
    }

    @Test
    void dedupesAndCapsToMaxQueries() {
        ChatModel model = (system, user) -> "a\na\nb\nc\nd\ne";
        assertEquals(List.of("a", "b", "c"), new QueryPlanner(model, 3).plan("x", CUTOFF, TODAY));
    }

    @Test
    void prependsTopicToQueriesThatLackIt() {
        ChatModel model = (system, user) ->
                "what is new in the 2026 revision\nmcp breaking changes\nlatest spec updates";
        List<String> queries = new QueryPlanner(model).plan("mcp", CUTOFF, TODAY);
        assertEquals(List.of(
                "mcp what is new in the 2026 revision",
                "mcp breaking changes",
                "mcp latest spec updates"), queries);
    }

    @Test
    void fallsBackToTopicWhenEmpty() {
        assertEquals(List.of("trump"), new QueryPlanner((s, u) -> "\n  \n").plan("trump", CUTOFF, TODAY));
    }

    @Test
    void fallsBackToTopicOnError() {
        ChatModel boom = (s, u) -> {
            throw new IOException("model down");
        };
        assertEquals(List.of("trump"), new QueryPlanner(boom).plan("trump", CUTOFF, TODAY));
    }
}
