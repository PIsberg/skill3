---
paths: ["**/PageFetcher.java"]
---

<!-- VIBETAGS-START -->
# Rules for PageFetcher

## Contract-Frozen Signature
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: Fetch seam. Keeping page retrieval behind it is what lets extraction, date parsing and scoring be tested against HTML fixtures with no network, and it is the boundary at which --input-file replaces the network entirely.
<!-- VIBETAGS-END -->
