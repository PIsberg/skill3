---
paths: ["**/DiscoveryProvider.java"]
---

<!-- VIBETAGS-START -->
# Rules for DiscoveryProvider

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: forwards the Brave subscription token to the search client; must not log it
<!-- VIBETAGS-END -->
