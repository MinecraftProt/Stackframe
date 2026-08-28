# Security and privacy

Stackframe processes exception messages, paths, mod metadata, network
information, configuration excerpts, and environment details. Treat all of it as
untrusted and potentially sensitive.

## Security goals

- Never hide an original server failure because Stackframe failed.
- Prevent terminal control injection and structured-output injection.
- Keep enrichment inside approved server paths.
- Redact protected values before operator, structured, or bundle output.
- Avoid retaining throwable graphs and server objects after output.
- Perform no automatic upload or destructive remediation.
- Keep CI, publication, and update automation least-privileged.

## Threat model

Inputs may be controlled by a malicious mod, malformed world or datapack,
untrusted configuration, player-triggered action, compromised dependency, or
unexpected platform behavior.

An attacker may try to:

- place credentials or private data in exception text;
- inject ANSI escapes, fake log lines, or JSON structure;
- reference paths outside the server directory;
- create cycles or huge graphs that exhaust memory or time;
- trigger repeated errors that block the server;
- make Stackframe blame another mod without evidence;
- abuse support bundles to collect unrelated files.

The diagnostic pipeline must remain bounded and treat every external string,
path, throwable, metadata object, and extension as untrusted.

## Data classes

| Class | Examples | Default behavior |
| --- | --- | --- |
| Public technical | diagnostic code, loader version | May be displayed |
| Server-sensitive | absolute paths, mod list, internal address | Minimize or redact |
| Personal | usernames, player UUIDs, chat content | Redact unless essential and approved |
| Secret | tokens, passwords, session IDs, private keys | Always redact |
| World data | player files, chunks, inventories | Never include as context or bundles by default |

Classification occurs before rendering. A renderer cannot opt out of redaction.

## Redaction rules

- Replace a value with a typed marker such as `<redacted:token>`.
- Redact all occurrences, including causes, suppressed exceptions, excerpts,
  labels, structured fields, and extension-provided data.
- Canonicalize paths before deciding whether they are permitted.
- Prefer relative server paths when useful.
- Do not reveal a secret's length, prefix, or hash by default.
- Bound redaction work to resist pathological input while preserving safe
  fallback behavior.
- Record that redaction occurred without logging the removed value.

False negatives are security bugs. False positives are usability defects and
should be fixed without weakening protection broadly.

## Full debug records

Full traces are operationally necessary but are not automatically safe. They may
contain sensitive exception messages and environment data.

- Store them locally by default.
- Use restrictive file permissions where the platform supports them.
- Document destination, retention, rotation, and deletion.
- Never upload them automatically.
- Apply a separately documented redaction mode rather than assuming raw means
  safe.
- If writing fails, notify the operator and retain the original console fallback.

## Context and file access

Enrichment may read only explicitly approved categories under canonical server
roots. Symlinks, junctions, traversal segments, and race conditions must be
considered. An exception-provided path is not authorization to read a file.

Excerpts have byte and line limits. Binary files, world data, private keys, and
known secret stores are denied by default.

## Support bundles

Bundles are local, explicit, bounded, and previewable. The operator must see:

- exact files and generated records included;
- time range and diagnostic count;
- values and categories redacted;
- bundle location and deletion guidance.

World data, player data, credentials, and arbitrary configuration files are
excluded by default. Partial bundles are removed or clearly marked after failure.

## External communication

The initial product has no telemetry and no automatic upload. Any future network
feature requires an architecture decision, explicit opt-in, documented endpoint
and retention, threat review, and a way to inspect the exact payload before
sending.

## Reporting vulnerabilities

Do not open a public issue for a suspected vulnerability. Follow
[`SECURITY.md`](../SECURITY.md) and use a private GitHub security advisory.
