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

## Worker notes

- Coordinate public model changes before implementation.
- Allocate stable codes through the registry rather than local constants.
- Include cycle, depth, size, malformed-input, and fallback fixtures.
- Never retain a throwable or server instance beyond completed output.

See [`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
