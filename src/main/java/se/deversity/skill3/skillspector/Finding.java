package se.deversity.skill3.skillspector;

/** A single SkillSpector finding. Fields are best-effort across report formats. */
public record Finding(String category, String severity, String message, String file, int line) {
}
