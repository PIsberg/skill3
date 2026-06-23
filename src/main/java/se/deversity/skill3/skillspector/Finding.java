package se.deversity.skill3.skillspector;

/** A single SkillSpector finding. Fields are best-effort across report formats. */
public record Finding(String category, String severity, String message, String file, int line) {

    /**
     * Whether the run gate treats this finding as blocking. Matches SkillSpector's
     * {@code HIGH}/{@code CRITICAL} severities and SARIF's {@code error} level
     * (case-insensitively); {@code MEDIUM}/{@code LOW}/{@code warning} are advisory.
     */
    public boolean isHighSeverity() {
        if (severity == null) {
            return false;
        }
        String s = severity.trim();
        return s.equalsIgnoreCase("high") || s.equalsIgnoreCase("critical") || s.equalsIgnoreCase("error");
    }
}
