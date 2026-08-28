# Stackframe Forge

Future Forge dedicated-server integration through the shared platform SPI.

## Owns

- Forge lifecycle and logging capture;
- Forge mod metadata, mappings, environment paths, and commands;
- Forge-specific diagnostic evidence and classifiers;
- Forge packaging and compatibility.

## Dependency boundary

Forge depends on core and renderer contracts plus the accepted platform SPI. It
does not import Fabric or copy Fabric internals into shared modules.

## Worker notes

- Do not stabilize the SPI from Forge assumptions alone.
- Reuse shared diagnostic meanings when evidence and operator impact match.
- Keep genuinely loader-specific behavior explicit.
- Run cross-loader contracts for fallback, redaction, and trace preservation.

See [`docs/COMPATIBILITY.md`](../docs/COMPATIBILITY.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
