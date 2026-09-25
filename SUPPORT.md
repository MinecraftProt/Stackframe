# Support

Stackframe is pre-alpha and has no usable mod artifact yet. Current support is
limited to project design, contribution, and roadmap questions.

Check the [current compatibility matrix](docs/COMPATIBILITY.md#current-public-matrix)
before reporting an environment as supported. Its `Unknown` rows are untested,
even when the development artifact builds or starts.

## Before opening an issue

1. Search existing issues and the public roadmap.
2. Read the [diagnostic catalog](docs/diagnostics/README.md) and
   [troubleshooting guide](docs/TROUBLESHOOTING.md).
3. Recover the complete trace using the diagnostic correlation ID.
4. Reproduce with the smallest relevant mod and configuration set when safe.
5. Remove tokens, public addresses, player data, and private paths.

## Choose the right report

- **Incorrect Stackframe behavior:** use the bug report form.
- **Unreadable generic stack trace:** use the new diagnostic request form.
- **New output or integration behavior:** use the feature request form.
- **Vulnerability or secret exposure:** use a private security advisory; do not
  open a public issue.

## Useful environment information

Include exact Stackframe, Minecraft, Java, loader, and relevant mod versions.
Also include output mode, operating system family, hosting panel or service
manager, and whether output was redirected. If a matrix row already describes the
combination, include its row ID and any linked regression issue.

Prefer a sanitized support bundle when that feature exists. Until then, inspect
every pasted line manually. Do not upload a complete server directory, world,
player data, credentials, or unsanitized configuration.

## Scope

Stackframe aims to explain server failures. It cannot provide general support for
Minecraft, Java, hosting providers, or every mod. A report may be redirected
upstream when evidence shows Stackframe captured and represented the failure
correctly.
