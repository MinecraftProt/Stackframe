# Loader-independent diagnostic model

This document is the normative contract shared by capture, classification,
enrichment, redaction, rendering, and structured output. It defines the completed
diagnostic value that crosses module boundaries. Presentation choices belong to
the renderer and classifier selection rules belong to
[issue #41](https://github.com/MinecraftProt/Stackframe/issues/41).

The terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative.

## Design goals

The model:

- is an immutable value graph with no callbacks or retained platform objects;
- has no Fabric, Forge, Minecraft, or logging implementation types;
- carries the same meaning to ANSI, plain-text, and structured renderers;
- distinguishes trusted catalog text from untrusted external values;
- cannot expose an unredacted protected value to a renderer;
- represents multiple locations, bounded excerpts, labels, nested diagnostics,
  evidence, confidence, help, and trace preservation;
- makes truncation and omission explicit; and
- evolves through an independently versioned structured schema.

The contract describes logical types. The Java 25 implementation is exposed from
`stackframe-core` in package `org.minecraftprot.stackframe.diagnostic`.
Serialization annotations remain outside the model and belong to issue #30.

## Completed diagnostic

`DiagnosticDocument` is the only value accepted by a renderer:

| Field | Type | Required | Contract |
| --- | --- | --- | --- |
| `schemaVersion` | `SchemaVersion` | yes | Version of this logical and structured model. |
| `diagnosticId` | `DiagnosticId` | yes | Identifies this emitted root diagnostic occurrence. |
| `correlationId` | `CorrelationId` | yes | Joins operator output, structured output, and the preserved debug record. |
| `root` | `Diagnostic` | yes | Root operator-facing diagnostic. |
| `redactions` | `BoundedList<RedactionNotice>` | yes | Categories and counts transformed before rendering; it never contains removed values. |
| `omissions` | `BoundedList<Omission>` | yes | Document-wide data omitted to satisfy safety limits. |

A completed document contains only `CatalogText` and `DisplayText`.
Externally sourced text is always `DisplayText`; raw throwable messages, external
metadata, paths, and excerpts use a separate pre-redaction input type and MUST
NOT implement or be assignable to `DisplayText`.

### Diagnostic

| Field | Type | Required | Contract |
| --- | --- | --- | --- |
| `severity` | `Severity` | yes | Impact of this diagnostic. |
| `code` | `DiagnosticCode` | yes | Stable meaning and documentation key. |
| `title` | `CatalogText` | yes | Short verified operation statement, never raw exception text. |
| `locations` | `BoundedList<Location>` | yes | Ordered most actionable first. May be empty. |
| `notes` | `BoundedList<Note>` | yes | Ordered facts and context. May be empty. |
| `help` | `BoundedList<Help>` | yes | Evidence-backed actions. May be empty. |
| `trace` | `TraceSummary` | yes | Preservation state and bounded frame summary. |
| `evidence` | `BoundedList<EvidenceReference>` | yes | Local evidence index used by claims in this node. |
| `confidence` | `ConfidenceReference` | yes | Classifier assessment supplied under the #41 contract. |
| `children` | `BoundedList<RelatedDiagnostic>` | yes | Nested causes, suppressed failures, related failures, or aggregate items. |
| `omissions` | `BoundedList<Omission>` | yes | Node-local data omitted to satisfy a limit. |

Required collections are present even when empty. Optional scalar values are
represented by absence, not by `null`, blank text, sentinel numbers, or empty
identifiers.

## Scalar value types

### Severity

`Severity` is a closed enum:

| Value | Meaning |
| --- | --- |
| `ERROR` | An operation failed or cannot continue safely. |
| `WARNING` | The operation continued with meaningful risk or degraded behavior. |
| `NOTE` | Supporting information that is meaningful as a nested diagnostic. |

Severity describes impact. It does not encode color, glyph, log level, exit
status, or classifier confidence.

### Diagnostic code

`DiagnosticCode` is exactly six ASCII characters matching `SF[0-9]{4}`.

- A code identifies one stable semantic meaning, not an exception class,
  classifier, title wording, or renderer layout.
- Reassigning a code to a different meaning is a breaking change.
- Wording and additional non-contradictory evidence may evolve without changing
  the code.
- Allocation and uniqueness belong to the registry delivered by
  [issue #5](https://github.com/MinecraftProt/Stackframe/issues/5).

### Identifiers

`DiagnosticId` is an opaque, case-sensitive ASCII token of 8-64 characters
matching `[A-Za-z0-9][A-Za-z0-9._:-]*`. It is unique within one process
retention window and MUST NOT contain a timestamp, host name, path, user
identifier, secret, or semantic diagnosis.

`CorrelationId` is an opaque uppercase Crockford Base32 token of 6-26 characters
matching `[0-9A-HJKMNP-TV-Z]+`. It MUST be unique within the configured trace
retention window and MUST NOT encode sensitive data. Every nested diagnostic in
one document uses the root correlation ID. Generation, collision handling,
retention, and cross-event grouping are owned by
[issue #13](https://github.com/MinecraftProt/Stackframe/issues/13).

Local reference identifiers (`LocationId`, `EvidenceId`) match
`[a-z][a-z0-9-]{0,31}` and are unique within their diagnostic node. They are not
stable API identifiers and are never used to correlate separate documents.

## Text safety boundary

The core implementation MUST use distinct static types on either side of
redaction:

- `CandidateText` contains bounded but potentially untrusted or sensitive input.
  It is accepted only by enrichment and redaction.
- `CatalogText` is trusted project-owned wording selected by stable key. It is
  sanitized and bounded but never constructed from external input.
- `DisplayText` is the only externally sourced text a completed diagnostic may
  contain. It contains no raw protected value.

`DisplayText` has this logical shape:

| Field | Type | Contract |
| --- | --- | --- |
| `value` | string | Sanitized visible text or a typed replacement marker. |
| `origin` | `CATALOG`, `EXTERNAL`, or `GENERATED` | Provenance category, not a platform object. |
| `sensitivity` | `PUBLIC`, `SERVER_SENSITIVE`, `PERSONAL`, `SECRET`, or `WORLD_DATA` | Classification before transformation. |
| `disposition` | `VISIBLE`, `REDACTED`, `GENERALIZED`, or `OMITTED` | Transformation applied before crossing the renderer boundary. |
| `marker` | `RedactionMarker` or absent | Required for `REDACTED`, `GENERALIZED`, and `OMITTED`; absent for `VISIBLE`. |

`CatalogText` contains a stable ASCII `key` and its resolved `value`. The key
matches `[a-z][a-z0-9.-]{0,95}` and the value follows the same Unicode,
sanitization, and length rules as `DisplayText`. Localization may replace the
resolved value but cannot change the diagnostic code or field semantics.

The following invariants apply:

1. `VISIBLE` is valid only when the post-policy value is classified `PUBLIC`.
2. `SECRET` and `WORLD_DATA` MUST never use `VISIBLE`.
3. A non-visible value contains only its replacement or generalized text. The
   original value is not retained anywhere in the completed graph.
4. `RedactionMarker` contains a stable category such as `TOKEN`, `PATH`,
   `PERSON`, `ADDRESS`, or `WORLD_DATA`; it contains no prefix, suffix, hash,
   length, or other derivative of the removed value.
5. All values are valid Unicode, use `\n` for logical line boundaries, and contain
   no terminal escape, bidirectional override, or disallowed control characters.
6. Sanitization and redaction occur before construction of
   `DiagnosticDocument`. A renderer cannot disable either operation.

The exact detectors, allowlists, and policy configuration are owned by
[issue #27](https://github.com/MinecraftProt/Stackframe/issues/27). That work may
add marker categories but cannot weaken these boundary invariants.

## Locations, excerpts, and labels

`Location` has:

| Field | Type | Required | Contract |
| --- | --- | --- | --- |
| `id` | `LocationId` | yes | Node-local reference key. |
| `kind` | `LocationKind` | yes | `FILE`, `CONFIGURATION`, `RESOURCE`, `COMPONENT`, `SOURCE`, `LIFECYCLE`, or `OTHER`. |
| `display` | `DisplayText` | yes | Operator-actionable location after canonicalization and redaction. |
| `position` | `SourceRange` | no | Exact range only when verified. |
| `excerpt` | `Excerpt` | no | Bounded text read through approved enrichment policy. |
| `evidenceIds` | `BoundedList<EvidenceId>` | yes | Evidence supporting this location. |

`SourceRange` uses one-based line and column numbers and an end-exclusive end
position. Its start MUST precede its end. A single point is represented by an
absent range, not a zero-width range. Columns count Unicode code points in the
logical source text; renderers calculate display width separately.

`Excerpt` has a one-based `startLine`, an ordered `BoundedList<ExcerptLine>`, and
an ordered `BoundedList<Label>`. Each `ExcerptLine` has its absolute `lineNumber`
and `DisplayText text`. Line numbers are strictly increasing by one. Newline
characters are not stored in line text.

`Label` has a required `SourceRange range`, `LabelStyle` (`PRIMARY` or
`SECONDARY`), required `DisplayText message`, and evidence references. Its range
MUST fall within the excerpt. Overlapping labels are valid and retain insertion
order. Color, carets, wrapping, and label layout are renderer policy owned by
issue #42.

Excerpt and label columns refer to the final post-redaction `DisplayText`, never
to removed source text. Redaction MUST recompute affected ranges against that
final text. If a range cannot be remapped without exposing a removed value's
length or position, the range or label is omitted with reason
`REDACTION_POLICY`. Location positions that would disclose equivalent protected
information follow the same rule.

A location or range is included only when verified. Exception text that resembles
a path or line number is not verification and never authorizes file access.

## Notes and help

`Note` contains:

- `kind`: `CAUSE`, `CONTEXT`, or `NOTE`;
- required `DisplayText text`; and
- an ordered `BoundedList<EvidenceId>`.

Notes state facts. Their evidence IDs MUST resolve in the same diagnostic node.
Cause notes are ordered from operator-facing cause toward lower-level technical
cause.

`Help` contains:

- required `DisplayText text`;
- `kind`: `ACTION`, `INSPECT`, or `COLLECT_EVIDENCE`; and
- an ordered, non-empty `BoundedList<EvidenceId>`.

Help is optional at the diagnostic level but, when present, MUST identify the
evidence supporting it. Whether an assessment permits a particular help kind,
claim, or named responsibility is determined by issue #41; the model only
preserves the selected result and its evidence references.

## Evidence and confidence references

`EvidenceReference` records why a claim is present without retaining the source
object:

| Field | Type | Contract |
| --- | --- | --- |
| `id` | `EvidenceId` | Node-local unique key. |
| `kind` | `TYPED_FAILURE`, `STRUCTURED_METADATA`, `VALIDATED_CONTENT`, `MAPPED_SOURCE`, `MESSAGE_PATTERN`, or `OTHER` | Loader-neutral evidence category. |
| `summary` | `DisplayText` | Bounded safe statement of the observed fact. |
| `source` | `DisplayText` or absent | Safe source description; never an object reference or unrestricted path. |

Evidence is descriptive. It does not name platform classes, own blame, or decide
which classifier wins.

`ConfidenceReference` contains:

- optional `assessmentId`, an opaque identifier whose vocabulary and meaning are
  defined by issue #41;
- `classifierId`: an opaque stable ASCII identifier or absent for fallback;
- an ordered `BoundedList<EvidenceId>`; and
- optional `policyId`, identifying the arbitration policy version.

Referenced evidence MUST resolve within the same node. The model stores the
result; an absent `assessmentId` means only that no assessment was supplied and
has no implied rank. Issue #41 owns the allowed identifiers and defines
confidence derivation, thresholds, deterministic priority, ties, aggregation,
help eligibility, responsibility claims, and development-only explanations.
Renderers MUST treat the reference as metadata and MUST NOT recompute or
reinterpret confidence.

## Trace summary

`TraceSummary` contains:

| Field | Type | Required | Contract |
| --- | --- | --- | --- |
| `state` | `PRESERVED`, `WRITE_FAILED`, or `NOT_APPLICABLE` | yes | Whether complete details can be recovered. |
| `totalFrames` | non-negative integer or absent | no | Known normalized frame count. |
| `shownFrames` | non-negative integer | yes | Frames represented directly in this diagnostic. |
| `omittedFrames` | non-negative integer | yes | Frames collapsed or excluded from this diagnostic. |
| `omittedCauses` | non-negative integer | yes | Cause nodes not represented here. |
| `destination` | `DisplayText` or absent | no | Safe local destination description. |
| `recordId` | `DiagnosticId` or absent | no | Preserved-record identifier when different from the root ID. |

When `totalFrames` is present, `shownFrames + omittedFrames` MUST equal it.
`PRESERVED` requires a destination or record ID. `WRITE_FAILED` requires a
node-local note describing that failure and MUST NOT claim that complete details
are available. `NOT_APPLICABLE` is reserved for diagnostics with no originating
trace, such as a configuration validation result.

The full throwable and its objects are not fields in this model. Preservation
storage, retention, and failure behavior are delivered by issue #13.

## Nested diagnostics

`RelatedDiagnostic` contains a `relation` and one immutable `Diagnostic` child.
The relation is one of:

- `CAUSE`: a lower-level failure needed to explain the parent;
- `SUPPRESSED`: a suppressed or concurrent failure retained for completeness;
- `RELATED`: a relevant independent diagnostic;
- `AGGREGATE_ITEM`: one item in a multi-error validation result.

The graph MUST be a finite tree: no shared child instances, cycles, parent
references, callbacks, or lazy suppliers. Child order is semantically meaningful
and deterministic. Nesting does not replace the complete normalized throwable;
it is the bounded operator-facing relationship selected from it.

## Explicit boundedness

Every repeated field uses `BoundedList<T>`:

| Field | Type | Contract |
| --- | --- | --- |
| `items` | immutable ordered list | Retained values. |
| `omittedCount` | non-negative integer | Number excluded by the producer. |

An omitted count greater than zero requires a matching `Omission`. `Omission`
contains the affected `ModelPath`, omitted count, and reason:
`COUNT_LIMIT`, `DEPTH_LIMIT`, `TEXT_LIMIT`, `BYTE_BUDGET`, `REDACTION_POLICY`, or
`INVALID_INPUT`. It never contains omitted content.

`ModelPath` is a logical path rooted at `$`. A field segment is `.` followed by
one of the field names defined in this contract; an item segment is a zero-based
index in the retained `items` list, written as `[n]`. For example,
`$.root.locations.items[0].excerpt.lines` identifies the bounded excerpt-line
list in the first retained location. A path MUST resolve in the completed
document, MUST identify the field whose values were omitted, and MUST NOT point
into removed content. Exactly one omission record matches each non-zero
`omittedCount`, and their counts MUST agree.

`INVALID_INPUT` applies only when a malformed external item in an aggregate can
be excluded without changing any retained claim. A malformed model value or
unresolved reference still fails construction.

Hard limits for schema version 1 are:

| Limit | Maximum |
| --- | ---: |
| Diagnostic nodes per document | 64 |
| Diagnostic nesting depth, root counted as 1 | 8 |
| Locations per node | 16 |
| Excerpt lines per location | 32 |
| Labels per excerpt | 64 |
| Notes per node | 32 |
| Help entries per node | 16 |
| Evidence references per node | 64 |
| Redaction notices per document | 32 |
| Omission records per document or node | 32 |
| Title length | 200 Unicode code points |
| Location display length | 1,024 Unicode code points |
| Excerpt line length | 4,096 Unicode code points |
| Any other text value | 4,096 Unicode code points |
| Completed document string data | 262,144 UTF-8 bytes |
| Any frame or omission count | 2,147,483,647 |

Producers MAY apply smaller documented operational budgets. They MUST NOT exceed
these limits, silently truncate, wrap counters, or substitute invalid values.
The document byte budget is the sum of the UTF-8 encoded byte lengths of every
string scalar, including identifiers and markers; structural field names are not
counted. Each occurrence is counted even when equal strings are interned.
Truncation happens at a Unicode code-point boundary and is represented by a safe
generated marker plus an `Omission`. If a valid bounded document cannot be
constructed, the pipeline emits the generic fallback diagnostic. If even that
cannot be rendered, the original server event is emitted unchanged.

## Redaction notices

`RedactionNotice` contains a `RedactionMarker` category, transformation
(`REDACTED`, `GENERALIZED`, or `OMITTED`), and positive occurrence count.
Notices are aggregate metadata only. They MUST NOT contain source strings,
locations, hashes, prefixes, suffixes, or secret lengths.

The sum is informational because one source value may be transformed in several
places. It is not an audit proof and renderers must not infer hidden content from
it.

## Immutability and construction invariants

Implementations MUST enforce all of the following at construction:

1. Every object is final and deeply immutable; collections are defensively
   copied and expose no mutable view.
2. No value references a throwable, logger, class loader, platform metadata
   object, server instance, path handle, input stream, or callback.
3. Required identifiers, titles, location displays, notes, help, evidence
   summaries, and replacement markers are non-blank after normalization.
   `ExcerptLine.text` MAY be empty or whitespace-only so source line numbering
   remains faithful. Identifiers are ASCII and match their grammar.
4. Local IDs are unique and every reference resolves in the same node.
5. Collections preserve deterministic producer order and contain no `null`.
6. Ranges, frame counts, omission counts, list sizes, nesting, and byte budget
   satisfy their limits.
7. The completed graph contains only `CatalogText` and `DisplayText`, never
   `CandidateText`.
8. Invalid input produces an explicit validation failure. Constructors MUST NOT
   silently discard, clamp, or rewrite invalid values.

Builders MAY be mutable and short-lived inside core, but they are not public
cross-module contracts and MUST discard references to source objects after
building or failing.

## Schema versioning

`SchemaVersion` is an ASCII `MAJOR.MINOR` pair. This contract starts at `1.0`.
It versions the logical model and its structured field meanings independently
from the Stackframe artifact version and diagnostic codes.

- A minor version may add optional fields or enum values with defined fallback
  handling. It cannot change existing field meaning, requiredness, limits, or
  privacy guarantees.
- A major version is required to remove or rename a field, make an optional field
  required, change a field meaning or coordinate system, loosen a privacy
  invariant, or make an old document invalid.
- Structured output MUST include `schemaVersion` at the document root and map
  every required model field without deriving new diagnostic meaning.
- Consumers MUST reject an unsupported major version explicitly. Consumers MAY
  ignore unknown optional fields in a supported major version but MUST preserve
  the known fields' meaning.
- Unknown enum values are treated as unsupported for fields whose meaning affects
  safety (`sensitivity`, `disposition`, trace state). For display-only categories,
  consumers use an `OTHER`/generic presentation without changing the underlying
  text.

The concrete JSON/NDJSON encoding, media type, escaping, and compatibility
fixtures belong to [issue #30](https://github.com/MinecraftProt/Stackframe/issues/30).
That encoding must be a faithful projection of this model rather than a second
diagnostic contract.

## Consumer obligations

- Capture adapters translate platform values into core inputs and release their
  platform references.
- Classifiers and enrichers produce claims and evidence but do not render.
- Redaction converts every external value into `DisplayText` and is the final
  authority before the document becomes renderable.
- Renderers accept only `DiagnosticDocument`, do not inspect exceptions or
  platform APIs, do not perform classification, and do not bypass redaction.
- ANSI and plain renderers differ only in presentation. Structured output
  preserves the same ordered facts, relationships, identifiers, omissions, and
  trace state.

## Executable acceptance

Issue #6 established this normative provider contract before the Java/Gradle
scaffold existed. Issue #68 supplies the compiled immutable model and its focused
contract tests in `stackframe-core`.

Executable model acceptance MUST:

1. encode these logical types in `stackframe-core` without platform or logging
   dependencies;
2. add constructor/property tests for every invariant and hard limit;
3. add cycle, depth, count, text, byte-budget, malformed-reference, and fallback
   fixtures;
4. prove that ANSI, plain, and structured test consumers read the same completed
   model without platform callbacks; and
5. verify that completed models cannot contain `CandidateText` or retain source
   throwables and server objects.

Renderer-specific semantic-equivalence fixtures remain with the ANSI/plain and
structured renderer work in issues #3 and #30; those consumers MUST use this
completed model rather than defining parallel values.
