# Structured diagnostic output (schema 1.0)

`StructuredDiagnosticRenderer` serializes a completed `DiagnosticDocument` as one
compact UTF-8 JSON object. Its JSON field names and types are specified by
[`diagnostic-1.0.schema.json`](schemas/diagnostic-1.0.schema.json). The object is
a projection of the same immutable, post-redaction model used by the plain and
ANSI renderers. It does not classify failures or inspect platform objects.

`toJson(document, observedAt)` returns an object without a trailing newline.
`emitNdjson(document, observedAt, sink)` builds that object and its trailing LF
before calling `sink.writeRecord(completeLine)` **once**. It does not call the
sink if serialization fails. The callback boundary receives one complete valid
record per event. A sink writing to a shared file, logger, or queue must
synchronize publication; a single callback alone does not guarantee atomic
filesystem or network writes, nor prevent a destination from partially writing
on I/O failure. A successful record has exactly one physical LF delimiter;
newlines inside text are JSON-escaped.

`observedAt` is a caller-supplied `Instant` encoded in UTC ISO-8601 form (with
Java's extended-year form for dates outside a four-digit year). It is
the event observation time, not the time rendering happened. The stable
`diagnosticId` identifies this diagnostic document; `correlationId` groups
related events. Both come from the completed model, and neither is synthesized
by the renderer. The renderer does not attach a wall-clock or regenerate IDs on
retry. JSON uses the media type `application/json`; an event stream uses
`application/x-ndjson`. Each NDJSON line is independently parseable and has no
array wrapper. The maximum encoded JSON object is 2,097,152 UTF-8 bytes,
excluding the NDJSON LF; an oversized result fails before delivery to the sink.

## Field semantics

- Every field from `DiagnosticDocument` and its nested model values is present.
  Optional values are represented by `null`; bounded collections always have
  `items` in producer order and `omittedCount` (including zero). The model's
  omission records retain their path, count, and reason.
- `root` and each `children.items[].diagnostic` contain the same diagnostic
  shape. `severity` is lowercase `error`, `warning`, or `note` and has the same
  meaning as the plain and ANSI label. Other enum tokens use lowercase snake
  case, for example `write_failed` and `server_sensitive`.
- `title` has a catalog `key` and selected display `value`. Every `DisplayText`
  has `value`, `origin`, `sensitivity`, `disposition`, and nullable `marker`.
  External text reaches this renderer only after the core redaction stage;
  `value` is the safe replacement or approved generalized text. `redactions`
  counts those transformations. Structured output cannot recover the original.
- `position` and label `range` use the model's one-based, end-exclusive source
  coordinates. Evidence IDs remain node-local. The trace summary is metadata,
  not a raw stack trace; `destination` is post-redaction `DisplayText`.

## Compatibility and consumers

`schemaVersion` versions the logical diagnostic contract independently of the
artifact version and diagnostic code. This renderer emits `1.0`. A minor
version may add optional fields or display-only enum values with a defined
fallback. A major version is required to remove or rename a field, change its
meaning or requiredness, change coordinates, or weaken privacy guarantees.
Consumers must reject unsupported major versions and may ignore unknown
optional fields in a supported major version. They must reject unknown values
for safety-sensitive `sensitivity`, `disposition`, and trace `state`; an unknown
display-only enum, including a future severity token, should use a generic
presentation without altering text or silently assigning a known severity.
These rules follow [`DIAGNOSTIC_MODEL.md`](DIAGNOSTIC_MODEL.md#schema-versioning).

Consumers should parse each full NDJSON line before acting on it and keep
redaction metadata with the diagnostic when forwarding or storing it. The
[`future-minor-unknown-fields.json`](../stackframe-renderer/src/test/resources/golden/structured/future-minor-unknown-fields.json)
fixture has version `1.1` and unknown optional fields at both document and
diagnostic level; the contract test shows that known fields retain their
meaning. The minimum/full and two-event golden files exercise current output.
