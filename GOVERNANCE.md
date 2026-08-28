# Governance

Stackframe currently uses lightweight maintainer governance appropriate for an
early project. This document can evolve as contributors and releases grow.

## Maintainer responsibilities

Maintainers:

- protect the fail-open and information-preservation guarantees;
- keep loader-independent contracts separate from platform adapters;
- triage issues, security reports, and compatibility claims;
- review diagnostic evidence, wording, fallback, and privacy impact;
- keep milestones, the roadmap board, and documents synchronized;
- approve architecture decisions and releases;
- manage repository permissions and publication credentials conservatively.

## Decisions

Routine changes are decided through issue and pull-request review. Significant
cross-cutting choices require an architecture decision record.

When reviewers disagree, decisions prioritize:

1. preservation of original server failures;
2. security and privacy;
3. correctness and evidence;
4. operator clarity and accessibility;
5. compatibility and maintainability;
6. implementation convenience.

Maintainers document unresolved tradeoffs rather than presenting consensus that
does not exist.

## Contributions

Anyone may propose issues, diagnostics, decisions, documentation, or code.
Merge and release permissions remain limited to trusted maintainers. Repeated
high-quality participation may lead to area ownership or maintainer status after
existing maintainers document the scope and access granted.

## Changes to governance

Governance changes use a public issue and pull request unless security or privacy
requires temporary private coordination. The reason, affected responsibilities,
and permission changes must be explicit.
