package se.deversity.skill3.skillspector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSpectorReportTest {

    @Test
    void parsesRealJsonIssuesShape() {
        // Mirrors SkillSpector `--format json`: findings under "issues", nested location.
        String json = """
                {
                  "skill": {"name": "mcp"},
                  "risk_assessment": {"score": 40, "severity": "MEDIUM"},
                  "issues": [
                    {
                      "id": "PI001",
                      "category": "prompt-injection",
                      "severity": "HIGH",
                      "confidence": 0.9,
                      "location": {"file": "SKILL.md", "start_line": 12, "end_line": 12},
                      "explanation": "Possible injection vector."
                    }
                  ]
                }
                """;
        SkillSpectorReport report = SkillSpectorReport.parse(json);

        assertFalse(report.clean());
        assertEquals(1, report.findings().size());
        Finding f = report.findings().get(0);
        assertEquals("prompt-injection", f.category());
        assertEquals("HIGH", f.severity());
        assertEquals("Possible injection vector.", f.message());
        assertEquals("SKILL.md", f.file());
        assertEquals(12, f.line());
    }

    @Test
    void emptyIssuesIsClean() {
        assertTrue(SkillSpectorReport.parse("{\"issues\": []}").clean());
    }

    @Test
    void sarifResultsFallback() {
        String sarif = "{\"runs\":[{\"results\":[{\"ruleId\":\"X\",\"level\":\"warning\",\"message\":\"m\"}]}]}";
        SkillSpectorReport report = SkillSpectorReport.parse(sarif);
        assertEquals(1, report.findings().size());
    }

    @Test
    void realSarifShapeYieldsCategoryMessageAndLocation() {
        // Actual SARIF: camelCase ruleId, message as {"text": ...}, location nested under
        // locations[0].physicalLocation. The flattened fixture above hides all three.
        String sarif = """
                {"runs":[{"results":[{
                  "ruleId": "PI001",
                  "level": "error",
                  "message": {"text": "Possible injection vector."},
                  "locations": [{"physicalLocation": {
                    "artifactLocation": {"uri": "SKILL.md"},
                    "region": {"startLine": 12}
                  }}]
                }]}]}
                """;
        SkillSpectorReport report = SkillSpectorReport.parse(sarif);

        assertEquals(1, report.findings().size());
        Finding f = report.findings().get(0);
        assertEquals("PI001", f.category());
        assertEquals("error", f.severity());
        assertEquals("Possible injection vector.", f.message());
        assertEquals("SKILL.md", f.file());
        assertEquals(12, f.line());
        assertEquals(1, report.highSeverityFindings().size()); // level "error" blocks
    }

    @Test
    void highSeverityFindingsKeepOnlyBlockingSeverities() {
        SkillSpectorReport report = new SkillSpectorReport(List.of(
                new Finding("a", "LOW", "m", "f", 1),
                new Finding("b", "MEDIUM", "m", "f", 2),
                new Finding("c", "HIGH", "m", "f", 3),
                new Finding("d", "critical", "m", "f", 4),
                new Finding("e", "error", "m", "f", 5),     // SARIF level mapped into severity
                new Finding("f", "warning", "m", "f", 6),
                new Finding("g", null, "m", "f", 7)), "raw");
        assertEquals(3, report.highSeverityFindings().size()); // HIGH, critical, error
    }

    @Test
    void nonJsonOutputIsCleanButRawPreserved() {
        SkillSpectorReport report = SkillSpectorReport.parse("INFO: scanning...\ndone");
        assertTrue(report.clean());
        assertTrue(report.raw().contains("scanning"));
    }
}
