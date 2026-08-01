---
paths: ["**/SearchClient.java"]
---

<!-- VIBETAGS-START -->
# Rules for SearchClient

## Contract-Frozen Signature
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: Discovery seam. BraveSearchClient (live) and FileCorpus (--input-file) both implement it, and isCuratedCorpus() is what tells the pipeline to skip LLM query planning. Removing the default method, or changing what it returns, silently re-enables planning for a corpus that is already the curated result set.
<!-- VIBETAGS-END -->
