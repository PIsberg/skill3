---
paths: ["**/InputVetter.java"]
---

<!-- VIBETAGS-START -->
# Rules for InputVetter

## Load-Bearing Oddity
- **Rule**: This looks removable but is deliberate. Refactor only while the invariant holds.
- **Invariant**: A quarantined source is dropped from the set handed to the synthesizer, but its finding is still recorded and still trips the run gate. Redaction runs FIRST and unconditionally, so a secret never reaches the model even when SkillSpector is unavailable — and when it is unavailable nothing is gated, because absence of findings is observed, never asserted.
- **Breaks if changed**: quarantining is treated as resolving the finding, redaction is made conditional on the scanner being present, or a skipped scan is reported as clean
<!-- VIBETAGS-END -->
