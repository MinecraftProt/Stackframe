# Stackframe Fabric

Fabric and Minecraft dedicated-server integration.

## Owns

- lifecycle and logging capture;
- Fabric mod metadata and version evidence;
- mappings, environment paths, and commands;
- Fabric-backed built-in diagnostic classifiers;
- Fabric packaging and server compatibility.

## Dependency boundary

Fabric depends on core and renderer contracts. Fabric types remain in this
module and do not leak into shared public models. This module never depends on
Forge.

## Worker notes

- Preserve existing appenders, crash reports, and original event ordering.
- Guard recursive logging and duplicate observation.
- Keep client-only code out of dedicated-server paths.
- Use verified metadata before naming a mod.
- Cover early startup, runtime, reload, shutdown, and formatter failure.

See [`docs/COMPATIBILITY.md`](../docs/COMPATIBILITY.md) and
[`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
