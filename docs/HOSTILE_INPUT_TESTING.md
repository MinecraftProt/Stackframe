# Hostile-input corpus

The default test run executes 64 deterministic cases from seed `0x43f00d5eed`
in both `HostileThrowableCorpusTest` and `HostileRenderingCorpusTest`. The shared
generator lives in `stackframe-testkit` and derives each case independently from
the base seed and case index. The CI corpus includes cycles and shared throwable
nodes, unreadable accessors, null/malformed frames, oversized text, malformed
UTF-16, Unicode clusters, terminal controls, JSON-looking strings, paths,
excerpts, labels, and widths from 1 to 256 columns. Per-case timeouts and model,
normalization, rendering, and structured-output budgets keep a failure bounded.

Normalization tests assert deterministic copied graphs and every global work,
frame, text, and byte limit. They intentionally do **not** treat candidate text
as display-safe. Rendering tests start from completed `DiagnosticDocument`
values and check deterministic plain output, ANSI/plain equivalence, absence of
injected terminal controls, bounded output, parseable JSON, and exactly one
complete parseable NDJSON record per callback. Minimal rejected examples for ESC,
CR, tab, bidi controls, unpaired surrogates, excess path length, and multiline
source excerpts are permanent tests. Any new failure found by the generated
corpus should be reduced to a small fixed fixture before changing production
code, then retained alongside its seed.

For a longer local run in PowerShell, use a supported JDK and force Gradle to
rerun the test tasks because environment-selected corpus sizes are not Gradle
cache inputs:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:STACKFRAME_FUZZ_SEED = '0x5EED43BEEF'
$env:STACKFRAME_FUZZ_CASES = '2000'
.\gradlew.bat --no-daemon --rerun-tasks :stackframe-core:test --tests '*HostileThrowableCorpusTest'
.\gradlew.bat --no-daemon --rerun-tasks :stackframe-renderer:test --tests '*HostileRenderingCorpusTest'
```

`STACKFRAME_FUZZ_CASES` accepts 1 to 5,000 and defaults to 64.
`STACKFRAME_FUZZ_START` defaults to 0 and selects the first index; a failure
prints its base seed, exact index, derived case seed, and the settings for a
single-case replay (`STACKFRAME_FUZZ_START=<index>` and
`STACKFRAME_FUZZ_CASES=1`). Clear these environment variables to return to the
CI corpus. A 512-case alternate-seed run on both modules passed when this suite
was introduced; this run found no production defect requiring a regression fix.
