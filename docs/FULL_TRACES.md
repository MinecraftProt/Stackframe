# Full trace records

The core `TraceRecorder` stores the original Java throwable in a separate local
file before a concise diagnostic may say that full details were preserved. The
Fabric capture adapter will supply the source event and keep its ordinary log
appenders active. A record failure must leave the original event on that path.

## Location and lookup

The default directory is `logs/stackframe-traces` relative to the server working
directory. Each successful write publishes one `<correlation-id>.trace` UTF-8
file. The diagnostic's `CorrelationId` and trace `recordId` use that same short,
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

These are **raw local debug records**, with no redaction mode implemented yet.
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
server administrators. If the filesystem cannot provide private access, the
deployment must supply that protection or disable raw trace use before release.

Records do not follow `latest.log` rotation and are **not automatically deleted**
in this pre-alpha implementation. Administrators delete old `.trace` files under
the dedicated directory according to their local retention policy. Incomplete
`.partial` files, if cleanup was interrupted, are sensitive and may be deleted
after the server is stopped. Backpressure and automated retention belong to the
production-hardening work; until then, monitor available disk space.

## Producer contract

Call `TraceRecorder.record(originalThrowable)` before constructing a completed
diagnostic. Use `TraceRecord.summary(...)` for the diagnostic's `TraceSummary`.
If the state is `WRITE_FAILED`, include `TraceRecord.failureNote()` in that node's
notes and keep the original server error in the normal log. Never build a
`PRESERVED` summary from a failed result. Release the source throwable reference
after output; `TraceRecord` itself retains no throwable or exception message.
