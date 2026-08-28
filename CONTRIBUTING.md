# Contributing to Stackframe

Stackframe is currently in its design and bootstrap phase. Use a GitHub issue to
agree on behavior before starting a large implementation.

Read the [project design](docs/PROJECT.md), [diagnostic style guide](docs/DIAGNOSTIC_STYLE.md),
and [security and privacy model](docs/SECURITY_AND_PRIVACY.md) before changing
diagnostic behavior.

## Branches

- `main` contains stable, reviewed work.
- `dev` is the integration branch for upcoming releases.
- Feature branches should use short purpose-based names such as
  `diagnostic-renderer` or `fabric-log-capture`.
- Do not put personal names, usernames, or initials in branch names.

Open pull requests against `dev` unless a maintainer requests another base.
See [GitHub workflow](docs/GITHUB_WORKFLOW.md) for labels, milestones, and board
states.

## Parallel work

Follow [Parallel workstreams](docs/WORKSTREAMS.md) when several contributors or
agent sessions work at once.

- One issue maps to one branch, one active owner, and normally one pull request.
- Claim the issue on the roadmap before editing.
- Stay inside the workstream's owned paths unless the issue explicitly lists a
  cross-cutting contract change.
- Coordinate changes to root build files, shared models, public interfaces, and
  diagnostic-code meanings before implementation.
- Rebase or merge from `dev` before handing dependent work to another worker.
- Use the [handoff template](docs/HANDOFF.md) when work changes owner or unlocks a
  dependent issue.

## Development workflow

1. Choose or open an issue and describe the intended behavior.
2. Set its roadmap status to In progress and record the active owner.
3. Keep loader-independent logic out of Fabric- or Forge-specific modules.
4. Add focused tests, including a golden output fixture for rendering changes.
5. Confirm that plain output conveys everything shown with ANSI styling.
6. Document user-visible configuration or diagnostic-code changes.
7. Open a focused pull request and link the issue.

## Engineering expectations

- Never suppress an original error because formatting failed.
- Keep complete throwable data available even when console frames are collapsed.
- Do not infer a responsible mod or remediation without evidence.
- Bound traversal depth, context size, memory use, and formatting time.
- Avoid logging secrets or unnecessary personal data.
- Prefer typed exception and metadata checks over message matching.
- Keep the core independent of Minecraft loaders and logging implementations.

## Diagnostic changes

New specialized diagnostics should include:

- a stable code and ownership area;
- the exact evidence required to select it;
- operator-facing title, cause, and help text;
- a generic fallback path;
- fixtures for causal chains and malformed inputs;
- ANSI and plain-text snapshots;
- notes about sensitive values that require redaction.

## Commit and pull request scope

Use clear, imperative commit subjects. Keep unrelated refactors out of feature
pull requests. A pull request should explain the operator impact, technical
approach, fallback behavior, and validation performed.

Changes to a public contract, dependency direction, data handling policy, or
release process may require an architecture decision record. Copy the template
in [`docs/decisions`](docs/decisions/000-template.md), choose the next number,
and link the decision from the relevant issue and pull request.
