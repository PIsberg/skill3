<!-- VIBETAGS-START -->
# Rules for ChatModel

## Contract-Frozen Signature
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: The single seam every model-driven stage binds to — QueryPlanner, Synthesizer, Verifier and the self-correction Reviser all take this one interface, which is what lets one --llm-provider choice apply uniformly. Test fakes implement it directly, so changing the signature breaks every unit test that avoids a live model.
<!-- VIBETAGS-END -->
