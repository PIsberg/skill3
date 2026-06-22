package se.deversity.skill3.skillspector;

/** Thrown when the SkillSpector CLI cannot be invoked (e.g. not installed). */
public class SkillSpectorUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    public SkillSpectorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
