# Security policy

Stackframe is pre-alpha and has no supported runtime release yet. Security fixes
will target maintained releases once artifacts are published.

## Report privately

Use a [private GitHub security advisory](https://github.com/MinecraftProt/Stackframe/security/advisories/new)
for suspected vulnerabilities, including:

- secrets or personal data exposed by diagnostics or support bundles;
- terminal escape, log, or structured-output injection;
- file access outside approved server paths;
- denial of service through exception processing or error storms;
- swallowed, altered, or misdirected original errors;
- unsafe extension, update, build, or publication behavior.

Do not include live credentials or unnecessary personal data. Provide a minimal
reproduction, affected versions or commits, expected impact, and any known
mitigation.

## Response

Maintainers will acknowledge the report when available, investigate impact,
coordinate a fix and disclosure, and credit reporters who want attribution.
Exact timelines cannot be guaranteed before the project has a staffed release
process.

The detailed threat model and data policy are in
[`docs/SECURITY_AND_PRIVACY.md`](docs/SECURITY_AND_PRIVACY.md).
