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

## Initial matrix

No executable release exists yet, so all runtime combinations are **unknown**.
Issue #2 selects the initial Java, Minecraft, and Fabric targets. The published
matrix must not be populated with support claims before the integration harness
provides evidence.

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
