# Stackframe testkit

Reusable fixtures and verification utilities for every production module.

## Owns

- throwable graph and hostile-input fixtures;
- fake platform metadata and environment adapters;
- focused golden-output helpers;
- dedicated-server integration scenarios;
- compatibility and performance evidence utilities.

## Dependency boundary

Testkit may depend on production modules in test scope. No production module
depends on testkit. Loader-specific fixtures remain separated by platform.

## Worker notes

- Keep fixtures deterministic and independent of machine paths.
- Print reproducible seeds for generated failures.
- Bound process and server timeouts.
- Retain sanitized evidence on CI failures.
- Prefer small area-specific snapshots over one shared file.

See [`docs/WORKSTREAMS.md`](../docs/WORKSTREAMS.md).
