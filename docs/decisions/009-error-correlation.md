# ADR 009: Identity-based bounded error correlation

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** [#21](https://github.com/MinecraftProt/Stackframe/issues/21)
- **Owners:** core diagnostic and platform integration maintainers

## Context

One underlying failure can be seen by several capture points and produce
repeated Stackframe diagnostics. Matching on text would hide distinct errors
with the same wording, while unbounded identity state would retain private
throwables or grow under an error storm. A crash must remain visible even when
similar observations are suppressed. Fabric's initial capture path already
preserves original Log4j events and writes separate trace files.

## Decision

Use original `Throwable` reference identity for duplicate recognition. A
trusted adapter chooses the same source object for repeated observations of one
failure; no class, message, stack trace, or code fingerprint is involved. The
core correlator stores a weak reference, opaque correlation ID, first-observed
monotonic time, and exact repeat count. It never returns raw throwable data.

The first ordinary observation emits a supplemental diagnostic. Further
observations of the same object within a fixed window suppress only the
supplemental diagnostic and reuse the first correlation ID. The original
platform event is never suppressed by this API. A critical observation always
emits, ending any ordinary window for that object and returning its pending
summary. The adapter must classify criticality; diagnostic `Severity.ERROR`
alone does not identify a fatal path.

The window starts at first observation and does not slide. A configured maximum
entry count bounds state, with oldest-entry eviction. Expired, evicted,
collected, and drained entries return summaries when they have repeats. A
backward clock sample retires existing windows. Synchronized operations give
one leader and exact counts under concurrent calls. Disabled mode bypasses
suppression and retains no state.

The Fabric adapter publishes summaries returned by observations, polls
`drainExpired`, and calls `drainAll` at shutdown. It allocates a candidate
correlation ID before observing, and the trace recorder accepts the selected
ID for the first emitted diagnostic. Suppressed observations write no extra
trace. The Log4j observer passes `FATAL` as critical and `ERROR` as ordinary;
its original appenders remain independent of the supplemental queue.

## Alternatives considered

### Group by exception message, stack fingerprint, or diagnostic code

Unrelated failures often share text and frames. A heuristic key could silently
hide a distinct error, violating the original-error contract.

### Keep strong references to source throwables

This would simplify identity lookup but retain potentially large, sensitive
exception graphs for the full window. Weak references keep the source eligible
for collection while preserving identity whenever the same object is still
reachable for another observation.

### Suppress critical duplicates too

A deferred repeat summary may never be published if the process stops during a
fatal path. Always emitting critical diagnostics avoids silent loss.

### Use a sliding window

A high-frequency failure could suppress diagnostics indefinitely. A fixed
window emits a new leader after the bound and keeps operator visibility.

## Consequences

- Equal-text but distinct throwable objects are never merged.
- Identity sharing across wrappers is the adapter's responsibility. Without it,
  duplicate-looking observations remain separate.
- Summary counts are exact for observations handled by one correlator instance;
  they are not reconstructed from unrelated raw logs or across process restarts.
- A quiet expired entry remains in bounded state until the adapter polls or
  another observation arrives. Periodic draining is required for timely
  summaries and time-based retirement.
- An abrupt process stop can lose pending ordinary summaries. Critical events
  always emit, and the original platform event remains untouched.
- A caller-selected trace ID can collide with an existing file. That attempt
  fails without overwriting the trace, and the diagnostic reports the failure.
- Operator-facing configuration wiring and real server compatibility evidence
  remain separate from the core and isolated pipeline tests.

## Validation

Core tests prove distinct identity, exact repeat counts, concurrent one-leader
behavior, window boundaries, capacity eviction, critical bypass, disabled
mode, and clock regression. Fabric pipeline tests prove chosen trace IDs match
emitted diagnostics, summaries are published, duplicate traces are avoided,
`FATAL` bypasses suppression, and original Log4j events continue. Real server
compatibility remains a separate release gate.
