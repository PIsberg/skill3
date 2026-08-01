---
paths: ["**/HttpPageFetcher.java"]
---

<!-- VIBETAGS-START -->
# Rules for HttpPageFetcher

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: outbound page fetch egress for partly-untrusted URLs; SSRF guard must not be weakened
<!-- VIBETAGS-END -->
