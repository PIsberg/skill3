<!-- VIBETAGS-START -->
# Rules for BraveSearchClient

### Rules for field apiKey
- **Rule**: Never log or expose runtime values of this element.
- **Reason**: Brave Search subscription token — never log, echo, or include in errors/fixtures

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: external-API credential handling and the only network egress with a secret token
<!-- VIBETAGS-END -->
