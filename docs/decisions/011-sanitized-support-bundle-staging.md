# ADR 011: Stage sanitized support bundles from completed diagnostics

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** #36
- **Owners:** renderer and privacy maintainers

## Context

Operators need reviewable, bounded evidence for support requests. Raw logs and
trace files retain secrets and world/player data, while `DiagnosticDocument` is
the renderer-safe post-redaction boundary. An export path must not infer consent
from a diagnostic event or read arbitrary files.

## Decision

The renderer module plans a local ZIP from explicitly selected completed
diagnostics and post-policy metadata. The plan serializes and fixes all entry
bytes before exposing an exact file/size/time/redaction preview. Export writes
only those bytes, refuses overwrite, cleans a failed temporary file, and never
uploads. Entry names and count/size/time limits are fixed. Raw traces, logs,
worlds, player data, and configuration contents are excluded; an optional trace
mode needs a separately reviewed sanitizer and operator flow.

The API depends on core's completed document and the structured renderer. It
does not add a dependency from core to platform or renderer code. An adapter
must present the preview and require an explicit operator action before calling
export. This PR establishes the staging contract, not that UI or command.

## Alternatives considered

### Zip a diagnostic directory and let operators remove private files

This would start from raw data and makes omission mistakes likely. It was
rejected in favor of an allowlisted set of generated entries.

### Recompute files after preview

Events or metadata might change between preview and export. Freezing serialized
bytes makes the preview exact, at the cost of a bounded in-memory plan.

## Consequences

- Safe default bundles can be inspected and exported without file-system reads.
- A bundle is limited to 64 events, 24 hours, and 2 MiB uncompressed content.
- The operator-facing selection/confirmation flow and sanitized optional full
  traces remain open work in #36.
- A producer misclassifying external text can still leak through a completed
  document; redaction validation and manual review remain necessary.

## Validation

Tests compare previewed names and byte sizes with real ZIP entries, parse every
NDJSON record, verify redaction counts and raw exclusion, and exercise count,
time, size, and existing-target failure bounds.
