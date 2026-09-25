# Stackframe testkit

Reusable fixtures and verification utilities for every production module.

## Owns

- throwable graph and hostile-input fixtures;
- fake platform metadata and environment adapters;
- focused golden-output helpers;
- dedicated-server integration scenarios;
- compatibility and performance evidence utilities.

## Dependency boundary

Production modules use testkit through `testImplementation` only. It is absent
from production compile and runtime classpaths and from the Fabric artifact.
Loader-specific fixtures remain separated by platform.

## Available fixtures

- `ThrowableFixtures` creates fresh wrapper/suppressed graphs, cycles, shared
  references, deep cause chains, large stacks, unusual Unicode, absent metadata,
  synthetic secrets, and common server startup failure shapes. Every frame and
  path is fixed test data; no current machine path is captured.
- `PlatformMetadataFixtures` holds immutable, loader-neutral metadata snapshots.
  `platform.fabric.FabricMetadataFixtures` supplies complete and sparse Fabric
  server cases. The values are test data, not an adapter API contract.
- `GoldenSnapshots.assertMatches(path, output)` compares UTF-8 files while
  accepting CRLF checkouts and reporting the first differing line.

Core, renderer, and Fabric tests consume these fixtures through their own test
configurations. For example, core can normalize
`ThrowableFixtures.causeAndSuppressedCycle()`, and renderer tests can compare a
plain rendering to an area-specific golden file plus its ANSI-stripped form.

## Updating renderer snapshots

Run the dedicated task, then inspect and commit only the intended golden files:

```powershell
.\gradlew.bat :stackframe-renderer:updateGoldenSnapshots
git diff -- stackframe-renderer/src/test/resources/golden
```

Ordinary `test` and `check` tasks never update snapshots. The update task runs
only tests tagged `golden` and writes UTF-8 with LF line endings. Renderer tests
fix output mode and width explicitly, so terminal settings do not affect files.

## Worker notes

- Keep fixtures deterministic and independent of machine paths.
- Print reproducible seeds for generated failures.
- Bound process and server timeouts.
- Retain sanitized evidence on CI failures.
- Prefer small area-specific snapshots over one shared file.

See [`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
