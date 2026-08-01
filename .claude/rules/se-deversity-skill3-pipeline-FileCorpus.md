---
paths: ["**/FileCorpus.java"]
---

<!-- VIBETAGS-START -->
# Rules for FileCorpus

## Load-Bearing Oddity
- **Rule**: This looks removable but is deliberate. Refactor only while the invariant holds.
- **Invariant**: FileCorpus implements BOTH discovery seams — SearchClient and PageFetcher — and LearnCommand injects the same instance into both slots. That is the design, not a layering slip: it is what makes an offline --input-file run take the identical downstream path as a live Brave run, so the two modes cannot diverge.
- **Breaks if changed**: the class is split into two collaborators, or either interface is dropped — offline runs then follow a different path from live ones and stop proving anything about the real pipeline
<!-- VIBETAGS-END -->
