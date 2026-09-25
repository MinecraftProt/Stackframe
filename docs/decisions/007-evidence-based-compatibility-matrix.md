# ADR 007: Evidence-based compatibility matrix and release links

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** #45
- **Owners:** compatibility and release maintainers

## Context

The selected Minecraft 26.2, Java 25, and Fabric Loader 0.19.3 baseline is a
build target. The development Fabric artifact and CI build do not establish
dedicated-server compatibility. Later Fabric, Forge, and client artifacts will
have different exact combinations. Operators need to distinguish a tested row
from an inferred version range, see known failures, and recover the support
claim that accompanied the binary they downloaded.

## Decision

The public compatibility matrix uses stable row IDs and the statuses
**Supported**, **Tested**, **Expected-compatible**, **Degraded**,
**Unsupported**, and **Unknown**. A positive or degraded row records the exact
artifact and checksum, source revision, Minecraft, Java vendor/patch, loader,
API, operating system, output destination, relevant mods, a UTC last-tested
date, immutable test or reviewed manual evidence, and limitations. Missing
versions remain explicitly unknown; nearby versions do not inherit a claim.
Supported requires a released artifact and a passing release matrix. A build or
startup smoke test alone cannot establish it.

Evidence is **Stale** when a relevant version or tested behavior changes, its
link becomes unavailable, or its last runtime test is more than 90 days old.
Stale positive rows show **Unknown (stale)** until revalidated and retain their
historical evidence and date. Unsupported policy rows stay unsupported until the
policy and implementation change. Reproducible regressions link to an issue and
update the affected current rows according to observed behavior.

Each released binary maps to its filename, SHA-256, source commit, applicable row
IDs, and a commit-SHA permalink to the matrix file included in its annotated
release tag. The release page carries this mapping and tagged-source test
evidence. A moving branch or generic project link cannot identify the original
claim. Past release matrices remain historical while the current matrix can
show new failures and staleness.

## Alternatives considered

### Treat selected pins as a supported version range

This would be simple to publish, but would assign untested Java vendors, loader
patches, host environments, and mod combinations a support claim without
evidence.

### Link every release to the live matrix

This gives operators the newest information but loses the exact claim attached
to an older downloaded binary. The current matrix remains discoverable for
regressions; the release record also pins its original revision.

### Stale only after a version change

Unchanged version strings can conceal changed hosting services, operating-system
patches, or missing evidence. The 90-day review boundary makes old evidence
visible without rewriting a historical release record.

## Consequences

### Positive

- Operators can distinguish released support, tested development rows, and
  untested candidates.
- Regression issues can point to exact affected rows and versions.
- Every binary's release record can identify the matrix revision it shipped with.

### Negative

- Each release needs per-artifact evidence review and a verified permalink.
- Supported rows require retesting or an explicit downgrade when evidence ages.

### Risks

- A release process might omit a binary or use a branch URL. Publication must
  check every downloadable filename, checksum, source commit, row ID, and
  commit-SHA link before approval.
- A stale positive claim might remain visible as current. Matrix maintenance
  must run after relevant dependency, platform, or behavior changes and before
  every release; regressions trigger immediate row review.

## Validation

Review the live matrix and release-entry template against all required fields.
Before a release, verify the tag resolves to the artifact source commit, every
matrix permalink opens at that SHA, each artifact checksum matches, and every
supported row links runtime evidence for its exact combination. Runtime tests
remain owned by the Fabric, Forge, client, and compatibility workstreams.

## Follow-up

- #16, #17, and #24 supply server, logging/hosting, and mod evidence.
- #14 and #10 provide the first configured Fabric diagnostic output path.
- The first Fabric release publishes per-artifact matrix links under
  [the release process](../RELEASES.md).
