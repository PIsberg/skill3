---
paths: ["**/NameSanitizer.java"]
---

<!-- VIBETAGS-START -->
# Rules for NameSanitizer

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: output sanitization: reserved-word stripping must never be weakened

### Rules for method sanitize
- **Rule**: Must remain a pure function. Forbid state modifications and side effects.
<!-- VIBETAGS-END -->
