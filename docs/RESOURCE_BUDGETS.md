# Diagnostic resource budgets

Stackframe keeps its supplemental work bounded. The original Minecraft or
Log4j failure remains on its normal path even when a Stackframe budget is hit.
No budget silently turns a partial diagnostic into a complete one.

| Stage | Default bound | Overflow behavior |
| --- | --- | --- |
| Throwable normalization | 256 nodes, 64 levels, 256 frames per throwable, 64 suppressed children per throwable, 4,096 total frames, 262,144 candidate-text UTF-8 bytes, 262,144 scalar-work units | Records truncation and omission statistics in the normalized graph. Public `Throwable` accessors may allocate an array before Stackframe can inspect its size. |
| Completed diagnostic model | 64 diagnostic nodes, depth 8, 262,144 UTF-8 bytes per document; per-field and per-list limits in `ModelLimits` | Rejects an invalid document before rendering. |
| Plain/ANSI renderer | 16 MiB output, 262,144 logical lines, 32 Mi work units, 30 seconds elapsed; caller may choose smaller positive limits up to five minutes | Throws `RenderLimitException`. `renderToString` never publishes partial text; a streaming caller may have written a prefix and must treat the exception as incomplete output. |
| Terminal layout | Target at most 100 columns; unknown width uses 80 | Narrow layout and linear label annotations preserve meaning when positional layout is unavailable. Indivisible tokens may exceed the target. |
| Fabric handoff | 128 queued observations by default; constructors reject capacities above 4,096 | `offer` never waits for the worker. Overflow increments `dropped`; the worker reports skipped supplemental diagnostics, while the original Log4j event continues unchanged. |
| Correlation cache | 256 entries and 30 seconds by default; configured maximum 4,096 entries and five minutes | Evicts old entries and emits exact repeat summaries. Entries hold weak throwable references; the queue and one active worker observation are the bounded strong-retention path. |

The renderer checks elapsed time between its own work and output operations.
It cannot interrupt an `Appendable` that never returns. Fabric renders on a
daemon worker and waits at most two seconds for that worker during shutdown;
its logging observer does not wait for diagnostic formatting or trace storage.
The counters in `FabricDiagnosticPipeline.PipelineStats` expose accepted,
processed, dropped, queue occupancy, peak occupancy, and processing failures.

Full raw trace records are a separate, explicitly sensitive local channel.
They preserve the original throwable rather than applying renderer truncation;
see [full trace records](FULL_TRACES.md) and
[configuration](CONFIGURATION.md) for opt-in retention. An oversized or unprintable raw trace must
never be claimed as a successfully preserved complete trace.
