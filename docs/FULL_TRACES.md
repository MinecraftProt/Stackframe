# Full trace records

The core `TraceRecorder` stores the original Java throwable in a separate local
file before a concise diagnostic may say that full details were preserved. The
Fabric server and client capture adapters supply the source event and keep
ordinary logging or vanilla crash handling active. A record failure must leave
the original event on its path.

## Location and lookup

The default directory is `logs/stackframe-traces` relative to the process working
directory. Each successful write publishes one `<correlation-id>.trace` UTF-8
file with a `Stackframe trace v1` ownership header followed by the complete JDK
stack trace. The diagnostic's `CorrelationId` and trace `recordId` use that same short,
opaque token; its separate root `DiagnosticId` identifies the diagnostic
occurrence. Search for the correlation token in the diagnostic, then
open its matching file to see the complete JDK stack trace, including causes,
suppressed exceptions, and all frames. The file path is kept out of the
renderer-facing model; local code may use `TraceRecord.file()` for recovery.

Trace files are independent of `latest.log` and its Log4j rotation. Log
rotation cannot split a trace or invalidate its token. A record is marked
`PRESERVED` only after its temporary file is closed, flushed, and published.
An interrupted or failed write is `WRITE_FAILED` and must add the supplied
explanatory note to the diagnostic; it must not claim a complete trace exists.
Concurrent events receive independent files and IDs, so no two traces interleave.

## Privacy and retention

These are **raw local debug records**, with no configurable redaction mode for
trace files.
Throwable messages and frames may contain
tokens, player information, addresses, paths, and other private data. The
recorder does not send them to the console, structured output, a support bundle,
or a network service. Redacted operator output follows the separate policy in
[Security and privacy](SECURITY_AND_PRIVACY.md). Do not attach a raw trace to a
public issue; sanitize it first.

The recorder restricts its directory to the owner on POSIX filesystems and uses
an owner-only ACL on Windows filesystems that expose Java's ACL view. Each
record is created with owner-only access before its throwable is written.
Operators should keep the directory on a private filesystem and limit access to
the local operator. If the filesystem cannot provide private access, the
deployment must supply that protection or disable raw trace use before release.

Records do not follow `latest.log` rotation. The Fabric configuration defaults
to **manual** retention for the server adapter: nothing is automatically
deleted. A server operator may explicitly select bounded retention with both
an age and file-count limit; see [Fabric configuration](CONFIGURATION.md) for
the exact settings and failure behavior. The development client adapter uses
manual retention without a client configuration control. Bounded server cleanup
only considers Stackframe-named regular `.trace`
files carrying the ownership header, and it leaves older unmarked records and
`.partial` files alone. If cleanup was interrupted, partial files are sensitive
and may be deleted manually after Minecraft is stopped. Monitor available
disk space even with bounded retention; a warning means cleanup did not
complete.

## Producer contract

Call `TraceRecorder.record(originalThrowable)` before constructing a completed
diagnostic. Use `TraceRecord.summary(...)` for the diagnostic's `TraceSummary`.
When correlation runs before trace preservation, call
`TraceRecorder.newCorrelationId()` for a candidate, then
`record(originalThrowable, selectedCorrelationId)` only for an emitted
diagnostic. A collision with an existing trace ID returns `WRITE_FAILED` and
does not overwrite the old file or change the selected ID.
If the state is `WRITE_FAILED`, include a fixed failure note in that node's
notes and keep the original platform log or crash report. Never build a
`PRESERVED` summary from a failed result. Release the source throwable reference
after output; `TraceRecord` itself retains no throwable or exception message.
