# Error correlation and repeat summaries

Issue [#21](https://github.com/MinecraftProt/Stackframe/issues/21) adds a
loader-neutral correlation primitive in
`org.minecraftprot.stackframe.correlation` and connects it to the initial
Fabric Log4j capture pipeline. This is live for throwable-bearing `ERROR` and
`FATAL` observations after Fabric's capture hook is installed; failures before
that hook remain outside this adapter.

## Identity and decisions

`DiagnosticCorrelator.observe` accepts the original `Throwable` object, a
proposed opaque `CorrelationId`, and an `Importance`. It compares source objects
by Java reference identity (`==`), never by exception class, message, stack
trace, rendered line, or diagnostic code. The adapter must pass the same source
object at every observation point for one underlying failure. Separate throwable
objects are separate events even when their text is identical. If a platform
cannot identify a common source object, it must emit the observations separately;
guessing from message text risks hiding a distinct failure.

The first ordinary observation returns `EMIT_DIAGNOSTIC` and keeps its proposed
correlation ID. Later observations of that same object within the window return
`SUPPRESS_DIAGNOSTIC_ONLY` with the first ID. This flag applies only to
Stackframe's supplemental diagnostic. **Every original log event, crash report,
and vanilla error path continues independently.** The result contains no source
object or exception text. The correlator holds only weak source references plus
bounded IDs and counters.

`CRITICAL` always returns `EMIT_DIAGNOSTIC`, including for repeated observations.
The Fabric adapter marks `FATAL` events critical and `ERROR` events ordinary.
Other adapters must classify their own fatal startup or crash paths before
calling the correlator. A critical observation closes an ordinary window for the
same object and returns its pending repeat summary. It does not start a new
suppression window. This favors visible duplicate critical diagnostics over a
silently lost fatal event.

## Bounds and summary delivery

The default fixed window is 30 seconds from the first observation, with at most
256 active identities. Configuration accepts a positive window of at most five
minutes and 1-4,096 entries. The window does not slide on repeats. At the exact
expiry boundary, the old window closes and a fresh diagnostic may be emitted.
When full, the oldest active entry is evicted. A backward monotonic-clock sample
closes old windows rather than extending suppression. Calls are synchronized, so
simultaneous observations of one object have one leader and exact counts.
Processing is bounded by the configured entry count; there is no background
thread or unbounded queue.

`RepeatSummary` contains the first correlation ID and `repeatCount`, excluding
the first observation. `totalObservations()` is one plus that count. Pending
summaries are returned on expiry, capacity eviction, clock regression, critical
bypass, or `drainAll`. A platform must publish **all** summaries returned with an
observation, call `drainExpired` on a periodic tick for quiet windows, and call
`drainAll` before shutdown or configuration replacement. These methods return
immutable, renderer-independent values. Fabric publishes a plain, bounded
`[Stackframe] Failure <id> was observed <count> more times` log event (with
singular wording for one repeat).
It does not infer a new cause or claim that raw traces were merged. Abrupt
process termination or a shutdown deadline can prevent a final summary; the
original Log4j events remain available.

`Config.disabled()` makes every observation emit a diagnostic and retains no
identity state or repeat count. Invalid bounds fail validation; adapters must
surface configuration errors under the normal configuration policy. The Fabric
pipeline accepts a `Config` at construction; the operator-facing configuration
file and command wiring are part of [#14](https://github.com/MinecraftProt/Stackframe/issues/14).

## Trace and integration boundary

The proposed ID must be unique over the relevant trace retention period. Fabric
allocates a candidate ID before correlation and uses the chosen ID for the first
diagnostic and `TraceRecorder.record(Throwable, CorrelationId)`. A suppressed
observation writes no extra trace. A trace-ID collision fails safely without
overwriting the existing file or changing the diagnostic's ID; the diagnostic
reports trace storage failure. The completed `DiagnosticDocument` schema and
renderers are unchanged.

The bounded Fabric queue can still omit a supplemental diagnostic under
backpressure, and the two-second shutdown deadline can leave work unfinished.
Neither case consumes the original Log4j event. Real Minecraft server and
hosting-platform validation remains governed by the compatibility matrix.

The [contract tests](../stackframe-core/src/test/java/org/minecraftprot/stackframe/correlation/DiagnosticCorrelatorTest.java)
cover same-object repeats, equal-text distinct errors, fixed-window boundaries,
capacity eviction, concurrent observations, critical bypass, disabled mode,
and a regressed clock. Fabric pipeline tests cover trace and summary delivery.
[ADR 009](decisions/009-error-correlation.md) records the design and tradeoffs.
