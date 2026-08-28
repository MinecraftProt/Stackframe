# Stackframe documentation

This directory contains the product and engineering contracts for Stackframe.
Until executable modules exist, these documents and the issue tracker define the
expected behavior.

## Start here

1. [Project design](PROJECT.md) explains the problem, principles, architecture,
   processing pipeline, and non-goals.
2. [Roadmap](ROADMAP.md) turns that design into milestones and release gates.
3. [Diagnostic style](DIAGNOSTIC_STYLE.md) defines what readable output means.
4. [Compatibility](COMPATIBILITY.md) defines how support claims are earned.
5. [Security and privacy](SECURITY_AND_PRIVACY.md) defines data boundaries.
6. [Release process](RELEASES.md) defines version and publication rules.
7. [GitHub workflow](GITHUB_WORKFLOW.md) explains how work moves through issues,
   the project board, branches, and pull requests.
8. [Parallel workstreams](WORKSTREAMS.md) assigns module and file ownership so
   several workers can contribute without unnecessary conflicts.
9. [Dependency waves](DEPENDENCIES.md) identifies which issues can start together
   and which contracts must land first.
10. [Worker handoff](HANDOFF.md) defines the context required when work changes
   sessions or unlocks a dependent task.

## Decision records

Significant decisions live in [`decisions/`](decisions/README.md). An
architecture decision record is required when a change alters a public contract,
module dependency direction, loader boundary, diagnostic-code meaning, privacy
policy, or release guarantee.

## Document ownership

| Area | Documents that must change with behavior |
| --- | --- |
| Diagnostic model or pipeline | `PROJECT.md`, relevant decision record |
| Console message or layout | `DIAGNOSTIC_STYLE.md`, golden fixtures |
| Supported environment | `COMPATIBILITY.md`, release notes |
| Sensitive data handling | `SECURITY_AND_PRIVACY.md`, threat fixtures |
| Milestone or release scope | `ROADMAP.md`, GitHub milestone/project |
| Versioning or publication | `RELEASES.md`, release workflow |
| Workstream or ownership boundary | `WORKSTREAMS.md`, module README |

The live issue tracker is authoritative for implementation status. Documents are
authoritative for cross-cutting contracts and policies.
