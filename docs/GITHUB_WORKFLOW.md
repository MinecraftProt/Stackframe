# GitHub workflow

GitHub issues hold scoped requirements, milestones hold release outcomes, and the
[Stackframe Roadmap](https://github.com/orgs/MinecraftProt/projects/1) shows live
status across the repository.

## Issue requirements

Implementation starts from an issue containing:

- operator or maintainer problem;
- in-scope and out-of-scope behavior;
- evidence required for diagnostic classification;
- fallback and information-preservation behavior;
- security and privacy considerations;
- measurable acceptance criteria;
- milestone and area labels.

Bug reports use the bug form. New specialized console handling uses the
diagnostic request form. Broader product behavior uses the feature form.

## Milestones

| Milestone | Outcome |
| --- | --- |
| M0 - Foundation | Loader-independent contracts and reproducible build |
| M1 - Fabric MVP | Safe, usable Fabric alpha |
| M2 - Production readiness | Bounded, private, maintainable Fabric release |
| M3 - Forge support | Forge adapter with shared diagnostic semantics |

Dependencies, workstreams, and exit criteria are in [ROADMAP.md](ROADMAP.md).

## Labels

### Type

- `type: task`: concrete implementation, documentation, or maintenance work.
- `epic`: a larger result composed of multiple scoped issues.
- GitHub's `bug`, `enhancement`, and `documentation` labels remain available for
  community reports.

### Area

- `area: core`: diagnostic model, normalization, classification, enrichment.
- `area: renderer`: ANSI, plain, terminal layout, structured output.
- `area: fabric`: Fabric and Minecraft integration.
- `area: forge`: Forge integration.
- `area: tooling`: build, tests, CI, publication.
- `area: docs`: operator and contributor documentation.
- `area: security`: privacy, redaction, supply chain, unsafe input.

### Priority

- `priority: critical`: required to avoid swallowed errors, unsafe output, broken
  contracts, or an unusable milestone.
- `priority: high`: important milestone behavior with no safe reason to defer.
- `priority: medium`: planned behavior that does not block the current safe
  release.

Priority is not issue order. Dependencies and milestone exit criteria determine
which ready issue starts next.

## Board states

- **Todo:** accepted and scoped, but not actively implemented.
- **In progress:** assigned work with an active purpose-named branch or pull
  request.
- **Done:** acceptance criteria are met and the change is merged into its target
  branch.

Blocked work remains in progress only when someone is actively resolving the
block; otherwise return it to Todo and document the dependency.

## Pull requests

Pull requests normally target `dev`. They link the issue, explain operator
impact, document fallback behavior, and include focused evidence. A pull request
does not close a milestone issue until all acceptance criteria are met.

`main` receives reviewed release-ready changes from `dev` or an explicitly
approved urgent fix. Release tags are created only from `main`.

## Triage

Maintainers regularly:

1. remove duplicates without losing useful reproduction details;
2. request sanitization when reports expose protected data;
3. choose the earliest milestone whose exit criteria require the work;
4. apply type, area, and priority labels;
5. split issues that cannot be validated by one coherent acceptance set;
6. link decisions and dependencies;
7. keep project status aligned with actual work.

Security reports do not enter public triage until disclosure is safe.
