# Stackframe core

Loader-independent diagnostic processing.

## Owns

- diagnostic and normalized throwable models;
- diagnostic-code registry and classifier arbitration;
- correlation, deduplication, and generic fallback;
- enrichment and redaction contracts;
- bounded processing and extension isolation.

## Dependency boundary

Core may use the JDK and explicitly approved loader-neutral libraries. It must
not import Fabric, Forge, Minecraft, or Log4j implementation types.

Platform adapters translate their data into core contracts. Renderers receive a
completed, redacted diagnostic and do not call back into platform APIs.

The normative model is defined in
[`docs/DIAGNOSTIC_MODEL.md`](../docs/DIAGNOSTIC_MODEL.md) and its rationale in
[ADR 003](../docs/decisions/003-loader-independent-diagnostic-model.md).

## Java diagnostic contract

Package `org.minecraftprot.stackframe.diagnostic` implements schema `1.0` as
deeply immutable Java 25 values. `DiagnosticDocument` is the sole completed
renderer input. Construction validates local references, post-redaction text,
source coordinates, trace accounting, finite tree identity, hard collection and
text limits, exact omission paths and counts, and the UTF-8 document budget.

`CandidateText` exists only on the pre-redaction side of the ownership boundary.
Redacted and omitted `DisplayText` factories derive canonical typed markers
without accepting protected originals. Redaction policy, throwable
normalization, arbitration, trace storage, diagnostic-code allocation, and
rendering remain separate provider contracts.

## Diagnostic-code registry

Package `org.minecraftprot.stackframe.diagnostic.registry` owns accepted
`SF0xxx`-`SF5xxx` ranges, immutable allocation snapshots, evidence and fallback
requirements, remediation ceilings, and the active `SF0001` generic fallback.
The canonical declaration, generated searchable catalog, compatibility baseline,
and intentional migration process are documented in
[`docs/diagnostic-registry/`](../docs/diagnostic-registry/README.md).

## Worker notes

- Coordinate public model changes before implementation.
- Allocate stable codes through the registry rather than local constants.
- Include cycle, depth, size, malformed-input, and fallback fixtures.
- Never retain a throwable or server instance beyond completed output.

See [`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
Classifier evidence, confidence, arbitration, and fallback behavior are specified
by [`ADR 004`](../docs/decisions/004-classifier-arbitration.md).
