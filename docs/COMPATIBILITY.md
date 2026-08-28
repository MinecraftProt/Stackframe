# Compatibility policy

Stackframe must earn compatibility claims with repeatable evidence. Similar
versions are not automatically treated as supported.

## Status definitions

| Status | Meaning |
| --- | --- |
| Supported | Covered by the release test matrix and eligible for fixes |
| Tested | A specific combination passed documented tests |
| Expected-compatible | Not fully tested; no known incompatible contract |
| Degraded | Works with documented missing or altered behavior |
| Unsupported | Outside policy or known not to work |
| Unknown | No reliable compatibility evidence |

`Expected-compatible` and `unknown` are not synonyms for supported.

## Compatibility dimensions

Each release records exact values for:

- Minecraft dedicated server version;
- Java runtime and vendor assumptions;
- Fabric Loader and, when required, Fabric API;
- Forge version after Forge support exists;
- Stackframe artifact and configuration schema;
- operating system families;
- terminal/output mode and hosting environment;
- tested logging, crash-report, and representative server mods.

## Selected foundation baseline

The first Fabric implementation targets this exact baseline, selected on
2026-08-28:

| Dimension | Selected value | Boundary |
| --- | --- | --- |
| Environment | Dedicated server | Client-only operation and client-only crashes are outside the first release |
| Minecraft Java Edition | `26.2` | Exact version; snapshots, release candidates, and other stable lines are not implied compatible |
| Java runtime | Java `25` | Minimum runtime; use a current patched Java 25 build |
| Java toolchain | Java `25`, `--release 25` | Eclipse Temurin is the reference CI vendor; other conforming Java 25 vendors require their own evidence |
| Fabric Loader | `0.19.3` | Minimum candidate for the first test row; later Loader versions are not implied compatible |
| Fabric API | `0.158.0+26.2` | Approved pin when the Fabric module uses Fabric API; it is not an operator requirement if the artifact has no Fabric API dependency |
| Fabric Loom | `1.17.20` | Build-time pin in the Fabric-recommended `1.17` line |
| Gradle Wrapper | `9.5.1` | Build-time pin; use the committed wrapper rather than a system Gradle |
| Minecraft names | Native Mojang names | Minecraft 26.2 is unobfuscated; do not add Yarn or legacy Intermediary mappings |

[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) is the authoritative,
Gradle-readable location for the numeric pins. Build configuration introduced by
issue #7 consumes those values. The Gradle wrapper URL necessarily repeats the
Gradle version during bootstrap and must be checked against the catalog.

Java 25 is both the minimum server runtime and compilation target: Mojang's 26.2
metadata declares Java major version 25. Stackframe does not promise compatibility
with older bytecode targets because the dedicated server itself cannot run there.
The baseline chooses a Java feature version rather than one vendor patch so
operators can apply Java 25 security updates. Each test record still names the
exact vendor and patch used.

Fabric API remains conditional because Fabric Loader does not include it and a
server operator should not install an unnecessary mod. If implementation imports
Fabric API, the artifact metadata must declare it and the exact version above
becomes part of the tested combination.

### Initial claim state

No executable Stackframe release or dedicated-server harness exists yet. The
selected baseline is therefore **unknown**, not supported or tested. Selection
means implementation work may build against the row; it does not mean operators
should deploy Stackframe.

| Combination | Current status | Reason |
| --- | --- | --- |
| Exact selected baseline | Unknown | No Stackframe artifact or dedicated-server evidence exists |
| Java 25 vendors other than the recorded test vendor | Unknown | JVM conformance is not a substitute for Stackframe evidence |
| Java 25 vendor or patch combinations without evidence | Unknown | JVM conformance is not a substitute for Stackframe evidence |
| Later Loader or Fabric API patch versions | Unknown | Patch compatibility must be demonstrated, not inferred |
| Minecraft versions other than `26.2` | Unsupported | Minecraft compatibility is exact-version by default |
| Java versions below 25 | Unsupported | The Minecraft 26.2 dedicated server requires Java 25 |
| Minecraft snapshots or release candidates | Unsupported | Pre-releases are not release targets |
| Client-only environments or failures | Unsupported | Stackframe's first release is dedicated-server-only |
| Forge or another loader | Unsupported | Fabric is the only initial loader |

An exact row becomes **tested** only after its evidence is recorded. It becomes
**supported** only when a Stackframe release includes that row in its release
matrix and maintainers accept fixes for it. Untested Java 25 vendor changes and
accepted dependency patches may be marked **expected-compatible** after build and
startup review, but that status never replaces dedicated-server tests.

## Loader policy

Fabric is the first supported loader. Forge work begins only after Fabric proves
the loader-independent contracts and the platform SPI is stabilized.

Loader modules own lifecycle hooks, platform metadata, commands, and environment
paths. Core diagnostic meanings and renderer behavior remain shared.

## Minecraft version policy

Version-specific integrations should be isolated behind small adapters. A new
Minecraft version is supported only when:

- the project builds against selected mappings and loader versions;
- dedicated-server startup and shutdown pass;
- capture, generic fallback, and full-trace preservation pass;
- representative specialized diagnostics pass;
- configuration migration behavior is known;
- limitations and exact test date are published.

One Stackframe platform artifact targets one exact Minecraft line. Minecraft and
Fabric types stay in `stackframe-fabric`; core and renderer contracts remain
version-neutral. Within the Fabric module:

- lifecycle, logging, metadata, and other Minecraft touchpoints are kept behind a
  small platform-adapter boundary rather than scattered through diagnostics;
- native Mojang names are used for 26.2, without a remapping compatibility layer;
- a second source set or subproject is added only when supporting a second
  Minecraft line requires source-incompatible code;
- runtime version checks and reflection are not used to turn an untested line into
  an expected-compatible or supported claim;
- shared behavior is proved with the same core contracts and dedicated-server
  scenarios for every adapter.

This policy avoids speculative multi-version machinery in the first
implementation while preserving a clear extraction point when another line earns
support.

## Logging and hosting environments

Automatic ANSI output must account for redirected output, CI logs, containers,
service managers, custom Log4j appenders, and hosting panels. When capability is
unknown, Stackframe falls back to plain text.

Compatibility requires that existing intended appenders and crash reports still
receive complete errors. Merely producing a pretty console message is not enough.

## Mod compatibility

Testing focuses on integration risk rather than popularity alone:

- mods that install logging appenders;
- crash and diagnostics tools;
- performance mods that alter threading or logging;
- permissions and command frameworks;
- world, datapack, and lifecycle managers;
- mods that produce custom exceptions or Stackframe extensions.

A mod is named incompatible only with a reproducible minimal case. The matrix
records exact versions and whether the failure belongs to Stackframe, the other
mod, or an unresolved interaction.

## Claim lifecycle

Every matrix entry includes an evidence source and last-tested date. A claim
becomes stale when a relevant Stackframe, loader, Minecraft, Java, or other-mod
version changes. Stale entries are visibly downgraded until retested.

Release notes link the matrix revision used for that artifact.

## Upgrade and deprecation policy

- Review Java 25 patches and Fabric Loader, Fabric API, Loom, and Gradle releases
  at least monthly and promptly after a relevant security advisory.
- Accept a dependency update only through a reviewed pin change. Runtime updates
  require dependency resolution, build, and dedicated-server startup/shutdown
  evidence before promotion to tested.
- Evaluate each stable Minecraft release after stable Fabric tooling is available.
  Snapshots and release candidates may inform research but never create a support
  claim.
- Do not deprecate the current Minecraft line until a replacement line is tested
  and published in a Stackframe release.
- Announce removal at least 90 days and one Stackframe release in advance. During
  that window, document whether the old line receives full fixes or
  security/critical fixes only.
- A severe vulnerability or unavailable upstream dependency may shorten the
  window. The release notes must name the reason, impact, and safe migration.
- Minecraft has no LTS designation in this policy. Age or popularity alone does
  not extend a line's support.

Changing the selected Minecraft or Java feature version is a compatibility
decision and requires an ADR. Patch pin updates do not require a new ADR unless
they alter a compatibility or release guarantee.

## Baseline sources

Sources were checked on 2026-08-28. Dynamic metadata is cited alongside dated
release guidance so a future update can distinguish what was selected from what
is current then.

| Decision | Primary source |
| --- | --- |
| Minecraft 26.2 is the current stable release and requires Java 25 | [Mojang version manifest and per-version metadata](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) |
| Java 25 is an LTS release | [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) |
| Temurin reference builds | [Adoptium Java 25 HotSpot API](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&heap_size=normal&image_type=jdk&os=linux&vendor=eclipse) |
| Fabric recommends Loader 0.19.3, Loom 1.17, and Gradle 9.5.1 for 26.2 | [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html) |
| Loader 0.19.3 is stable for 26.2 | [Fabric Meta loader data](https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.3) |
| Fabric API 0.158.0+26.2 is a stable release | [Fabric API release](https://github.com/FabricMC/fabric-api/releases/tag/0.158.0%2B26.2) |
| Loom 1.17.20 is the selected stable patch in the 1.17 line | [Fabric Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.20/fabric-loom-1.17.20.module) and [Loom 1.17 release](https://github.com/FabricMC/fabric-loom/releases/tag/1.17) |
| Gradle 9.5.1 supports running and toolchains on Java 25 | [Gradle Java compatibility matrix](https://docs.gradle.org/9.5.1/userguide/compatibility.html) |
| Pins match Fabric's maintained 26.2 example | [Fabric example mod 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2) |
