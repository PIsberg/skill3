---
paths: ["**/RetrievalService.java"]
---

<!-- VIBETAGS-START -->
# Rules for RetrievalService

## Thread-Safety Guarantee
- **Strategy**: IMMUTABLE
- **Note**: Collaborators (PageFetcher/HttpClient, DateExtractor, AuthorityScorer) are stateless/immutable; each fetch task builds its own Source and results are merged on the caller thread. Keep it that way — do not share mutable state between fetch tasks. The opt-in `sequential` mode only removes concurrency (fetches run on the caller thread); it cannot weaken the invariant — serial execution is strictly safer than the parallel default it replaces.
<!-- VIBETAGS-END -->
