---
paths: ["**/RunManifest.java"]
---

<!-- VIBETAGS-START -->
# Rules for RunManifest

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.
- **Reason**: Serialized verbatim to run.json with a default ObjectMapper — the component names ARE the on-disk field names. Renaming, reordering into a different shape, or introducing a type that needs a Jackson module silently changes or breaks the provenance file that answers 'what produced this SKILL.md?'.
<!-- VIBETAGS-END -->
