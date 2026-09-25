# Stackframe documentation

This directory contains the product and engineering contracts for Stackframe.
The modules are pre-alpha. These documents and the issue tracker distinguish
implemented behavior from contracts that still need integration evidence.

## Start here

1. [Project design](PROJECT.md) explains the problem, principles, architecture,
   processing pipeline, and non-goals.
2. [Roadmap](ROADMAP.md) turns that design into milestones and release gates.
3. [Diagnostic model](DIAGNOSTIC_MODEL.md) defines the immutable,
   loader-independent contract shared by the pipeline and renderers.
4. [Diagnostic style](DIAGNOSTIC_STYLE.md) defines what readable output means.
5. [Compatibility](COMPATIBILITY.md) publishes the current evidence matrix and
   defines how support claims are earned.
6. [Security and privacy](SECURITY_AND_PRIVACY.md) defines data boundaries.
7. [Release process](RELEASES.md) defines version and publication rules.
   [Dependency and supply-chain review](SUPPLY_CHAIN.md) defines the checks and
   release triage path for build inputs and bundled components.
8. [GitHub workflow](GITHUB_WORKFLOW.md) explains how work moves through issues,
   the project board, branches, and pull requests.
9. [Parallel workstreams](WORKSTREAMS.md) assigns module and file ownership so
   several workers can contribute without unnecessary conflicts.
10. [Dependency waves](DEPENDENCIES.md) identifies which issues can start together
   and which contracts must land first.
11. [Worker handoff](HANDOFF.md) defines the context required when work changes
   sessions or unlocks a dependent task.
12. [Diagnostic extensions](EXTENSIONS.md) defines the public contribution API,
    namespace and privacy boundaries, and failure isolation.
13. [Error correlation](CORRELATION.md) defines identity-based duplicate
    suppression, repeat summaries, and Fabric delivery boundaries.
14. [Support bundle staging](SUPPORT_BUNDLES.md) defines bounded local export
    from completed diagnostics and the remaining operator-flow boundary.

## Operator help

- [Diagnostic catalog](diagnostics/README.md) lists registered codes and links
  to operator guidance.
- [Troubleshooting](TROUBLESHOOTING.md) covers trace lookup, plain output,
  configuration, hosting panels, and sanitized reports.
- [Full trace records](FULL_TRACES.md) explains local raw-record recovery and
  retention.

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
| Diagnostic code or operator guidance | `diagnostics/`, `TROUBLESHOOTING.md`, generated registry catalog |
| Supported environment | `COMPATIBILITY.md`, release notes |
| Sensitive data handling | `SECURITY_AND_PRIVACY.md`, threat fixtures |
| Milestone or release scope | `ROADMAP.md`, GitHub milestone/project |
| Versioning or publication | `RELEASES.md`, release workflow |
| Workstream or ownership boundary | `WORKSTREAMS.md`, module README |

The live issue tracker is authoritative for implementation status. Documents are
authoritative for cross-cutting contracts and policies.
