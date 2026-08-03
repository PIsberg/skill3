<!-- VIBETAGS-START -->
# Rules for LocalLlmClient

### Rules for field apiKey
- **Rule**: Never log or expose runtime values of this element.
- **Reason**: LLM provider API key — never log, echo, or include in errors/fixtures

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: outbound LLM-provider credential (Bearer token) handling
<!-- VIBETAGS-END -->
