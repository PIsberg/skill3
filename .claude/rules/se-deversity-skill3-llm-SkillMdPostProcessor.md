---
paths: ["**/SkillMdPostProcessor.java"]
---

<!-- VIBETAGS-START -->
# Rules for SkillMdPostProcessor

## Core Functionality
- **Sensitivity**: High
- **Note**: Deterministically guarantees SKILL.md spec compliance; model output is never trusted. Changes risk emitting invalid frontmatter — keep the parsing and frontmatter synthesis covered by SkillMdPostProcessorTest.

### Rules for method render
- **Rule**: This operation is idempotent. Calling it multiple times must produce the same result as calling it once.
- **Reason**: SelfCorrectionLoop re-runs render() on its own output, so a revised draft passes through repeatedly. Every guarantee here must converge: exactly one frontmatter block and exactly one provenance footer, no matter how many revision rounds ran.
<!-- VIBETAGS-END -->
