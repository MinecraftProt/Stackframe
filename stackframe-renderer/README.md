# Stackframe renderer

Terminal, plain-text, and structured output for completed diagnostics.

## Owns

- width-aware terminal layout and wrapping;
- excerpts, labels, notes, help, and trace summaries;
- ANSI styling and plain-text semantic equivalence;
- versioned JSON and NDJSON output;
- accessibility and localization rendering mechanics.

## Dependency boundary

Renderer depends on core contracts only. It does not import Fabric or Forge,
classify throwables, read server files, or bypass redaction.

## Worker notes

- Treat every displayed string as untrusted.
- Sanitize control characters and bound output.
- Calculate alignment by display width.
- Add focused golden files rather than one global snapshot.
- Prove that removing ANSI preserves all meaning.

See [`docs/DIAGNOSTIC_STYLE.md`](../docs/DIAGNOSTIC_STYLE.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
