# ADR 003: Loader-independent diagnostic model

- **Status:** Proposed
- **Date:** 2026-08-28
- **Issue:** [#6](https://github.com/MinecraftProt/Stackframe/issues/6)
- **Owners:** core diagnostic engine maintainers

## Context

Capture adapters, classifiers, enrichers, redaction, terminal renderers, and
structured output need one contract before their implementation can proceed in
parallel. If each layer invents its own representation, diagnostic meaning,
privacy guarantees, omission behavior, and trace correlation will drift.

The model must remain independent from loader, game, and logging implementation
types. It must also cross a strict redaction boundary: renderers should never
receive a raw secret or retain the throwable and server objects from which a
diagnostic was derived.

The repository does not yet contain the Gradle scaffold from
[issue #7](https://github.com/MinecraftProt/Stackframe/issues/7), so the decision
must be precise enough to implement later without pretending that executable
acceptance currently exists.

## Decision

Adopt the immutable, bounded value graph specified in
[`DIAGNOSTIC_MODEL.md`](../DIAGNOSTIC_MODEL.md).

One `DiagnosticDocument` carries schema version, occurrence and correlation
identifiers, a root diagnostic, redaction notices, and explicit omissions. Each
diagnostic contains severity, stable code, trusted title, ordered locations,
bounded excerpts and labels, notes, help, trace state, evidence and confidence
references, and nested diagnostics.

Raw external values and renderer-safe values are different static types.
Completed documents contain only trusted catalog text and post-policy
`DisplayText`; they cannot contain raw protected values. Diagnostic trees have
fixed count, depth, text, and byte limits, and every omission is observable.

Classifier arbitration is not part of this decision. The model stores evidence
and the resulting confidence assessment through a neutral interface;
[issue #41](https://github.com/MinecraftProt/Stackframe/issues/41) owns how that
result is selected. Diagnostic code allocation remains owned by issue #5,
redaction policy by issue #27, trace storage by issue #13, renderer presentation
by issue #42, and concrete JSON/NDJSON encoding by issue #30.

Schema version `1.0` versions the logical and structured field meanings
independently from artifact versions and diagnostic codes. Compatible optional
additions use a minor version; semantic or safety-breaking changes use a major
version.

## Alternatives considered

### Renderer-specific models

Separate terminal, plain, and structured DTOs would simplify each renderer but
allow facts, nesting, omissions, and redaction state to diverge. This conflicts
with semantic equivalence and was rejected.

### A mutable cross-module diagnostic builder

A shared mutable object would make incremental enrichment convenient, but it
would expose partially redacted state, complicate thread safety, and allow source
objects to escape. Mutable builders may exist only as short-lived core
implementation details.

### Plain strings plus renderer-side redaction

Plain strings are easy to serialize but cannot prove whether a value is trusted,
sensitive, transformed, or safe. Renderer-side redaction also permits one output
path to bypass policy. Distinct pre-redaction and display-safe text types were
selected instead.

### A flat list of diagnostics

A flat list avoids recursive types but loses cause, suppressed, related, and
aggregate relationships or recreates them with fragile external indexes. A
strictly bounded immutable tree keeps those relationships explicit.

## Consequences

### Positive

- Core, renderers, adapters, and structured output share one semantic contract.
- Platform objects and logging implementations cannot leak through public model
  types.
- Redaction is enforced before rendering rather than requested from renderers.
- Limits and omissions are deterministic, inspectable, and testable.
- Evidence can evolve under the independent arbitration policy without changing
  presentation contracts.

### Negative

- Producers must construct explicit bounded lists, local references, and safe
  text wrappers instead of passing strings directly.
- Schema changes require compatibility review even when Java source compatibility
  would otherwise be preserved.
- Nested diagnostics and byte budgets require more validation than a simple DTO.

### Risks

- Implementations could accidentally duplicate the logical model in renderer or
  JSON modules. Dependency tests and cross-renderer fixtures must prevent this.
- A future policy might need a new sensitivity or evidence category. Minor
  evolution permits additions only when older consumers have a safe fallback;
  otherwise a major version is required.
- Documentation can drift before executable types exist. Issue #7 is an explicit
  dependency for compiled contract tests, and dependent workers must use this
  specification rather than unpublished assumptions.

## Validation

For this decision-only change:

- validate all relative documentation links;
- parse code and schema examples where applicable;
- review terminology against `PROJECT.md`, `DIAGNOSTIC_STYLE.md`, and
  `SECURITY_AND_PRIVACY.md`; and
- confirm the contract introduces no loader, game, or logging implementation
  type.

After issue #7, executable acceptance must cover immutability, references,
boundedness, malformed input, nested diagnostics, redaction type separation,
fallback, and equivalent ANSI/plain/structured consumption.

## Follow-up

- [#7](https://github.com/MinecraftProt/Stackframe/issues/7): provide the Gradle
  scaffold needed for compiled core types and tests.
- [#1](https://github.com/MinecraftProt/Stackframe/issues/1): map normalized
  throwable data into the bounded contract without retaining throwable objects.
- [#3](https://github.com/MinecraftProt/Stackframe/issues/3): consume only
  completed documents in terminal rendering.
- [#5](https://github.com/MinecraftProt/Stackframe/issues/5): implement stable
  diagnostic code allocation.
- [#13](https://github.com/MinecraftProt/Stackframe/issues/13): implement trace
  preservation and correlation generation.
- [#19](https://github.com/MinecraftProt/Stackframe/issues/19): implement bounded
  enrichment.
- [#27](https://github.com/MinecraftProt/Stackframe/issues/27): implement the
  redaction policy behind the safe text boundary.
- [#30](https://github.com/MinecraftProt/Stackframe/issues/30): define the
  concrete JSON/NDJSON projection and compatibility fixtures.
- [#41](https://github.com/MinecraftProt/Stackframe/issues/41): define how
  evidence produces confidence and deterministic classifier selection.
