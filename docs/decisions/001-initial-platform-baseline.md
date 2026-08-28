# ADR 001: Initial platform baseline

- **Status:** Proposed
- **Date:** 2026-08-28
- **Issue:** #2
- **Owners:** compatibility and Fabric maintainers

## Context

Stackframe needs one reproducible platform target before Gradle bootstrap, Fabric
integration, and dedicated-server tests can begin. The target must reflect current
upstream support without claiming compatibility that has not been tested.

On 2026-08-28, Mojang identifies Minecraft Java Edition 26.2 as the current stable
release and its metadata requires Java 25. Fabric's 26.2 guidance recommends
Fabric Loader 0.19.3, Loom 1.17, and Gradle 9.5.1. Fabric API
0.158.0+26.2 is the current non-prerelease API release for that Minecraft line.
Java 25 is an LTS release and Gradle 9.5.1 supports running on it.

Stackframe is dedicated-server-first. A client launch or successful compilation
cannot establish support because capture, original-error preservation, startup,
reload, and shutdown behavior must be exercised on a server.

## Decision

The initial target is Minecraft 26.2 on Java 25 with Fabric Loader 0.19.3. Use
Fabric Loom 1.17.20 and the Gradle 9.5.1 wrapper for builds. Use native Mojang
names because Minecraft 26.2 is unobfuscated.

Fabric API 0.158.0+26.2 is the approved pin if the Fabric implementation consumes
the API. It is not a minimum operator dependency when the artifact does not use
it; artifact metadata and installation guidance must agree.

The numeric values live in `gradle/libs.versions.toml` for issue #7 to consume.
The full support, upgrade, deprecation, and evidence rules live in
`docs/COMPATIBILITY.md`.

The selected row starts as unknown. Only exact, recorded dedicated-server results
can make it tested, and only a published Stackframe release matrix can make it
supported.

Minecraft-specific code remains behind a small adapter boundary inside the Fabric
module. The first implementation does not add multi-version source sets,
reflection, or runtime dispatch. A source set or subproject is introduced when a
second tested Minecraft line actually requires source-incompatible code.

## Alternatives considered

### Target Minecraft 1.21.11 and Java 21

Minecraft 1.21.11 is a mature Fabric line and the last obfuscated Minecraft
release. It was rejected because it is no longer the current stable server line,
would begin Stackframe on an older Java baseline, and would require an additional
mapping/remapping transition when moving to 26.x.

### Follow every latest upstream version automatically

Automatically tracking Minecraft, Java, Loader, API, Loom, or Gradle minimizes
pin maintenance. It was rejected because upstream availability is not
Stackframe compatibility evidence and would make builds and operator requirements
non-reproducible.

### Support multiple Minecraft lines in the first release

Multiple lines could serve more existing servers. It was rejected for the first
release because each line multiplies adapter, packaging, and dedicated-server test
work before the capture contracts are proven. Additional lines can be proposed
with evidence after the initial vertical slice works.

## Consequences

### Positive

- Gradle bootstrap and Fabric work receive one coherent, current set of pins.
- Java runtime and compilation requirements match the dedicated server.
- Operators can distinguish a selected target from an earned support claim.
- Native names avoid introducing a legacy mapping layer into new code.

### Negative

- Servers remaining on 1.21.11 are outside the first target.
- Operators need Java 25 and may need Fabric API when implementation uses it.
- Exact-version support requires deliberate retesting for upstream updates.

### Risks

- A pin may become stale before the first release. Monthly and security-driven
  review detects this; updates remain explicit and reviewed.
- Fabric API may be added unnecessarily. Artifact metadata must require it only
  when production code imports it.
- Version-specific code may leak into shared modules. Dependency checks and
  dedicated adapter ownership enforce the boundary.

## Validation

Issue #7 must show that a clean checkout uses the catalog pins and the committed
Gradle wrapper. Before any support claim, the integration harness must start and
stop a Minecraft 26.2 dedicated server on the recorded Java 25 vendor and patch,
exercise capture and fallback, and preserve the complete original error.

## Follow-up

- #7 consumes the pins and bootstraps the modules and wrapper.
- #17 records exact dedicated-server evidence.
- #34 may publish a Fabric alpha only for rows that have earned support.
