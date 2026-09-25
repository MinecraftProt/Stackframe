# Operator diagnostic catalog

Search by the stable `SF####` code in a diagnostic header. This directory adds
operator guidance to the [generated registry catalog](../diagnostic-registry/catalog.md),
which is the authoritative list of allocated codes, evidence contracts, fallback
rules, and remediation ceilings. The generated catalog includes reserved codes;
an operator page is required for every active or deprecated code. Reserved codes
are not emitted and do not need an operator page until activation.

| Code | Meaning | Registry state | Release state |
| --- | --- | --- | --- |
| [SF0001](SF0001.md) | Unexpected operation; no safe specialized diagnosis | Active | Unreleased; development Fabric adapter emits a generic fallback |

An active registry allocation is a contract, not proof that a released artifact
emits it. The development Fabric adapter observes severe Log4j events with
throwables, but has no published support claim. See
[Troubleshooting](../TROUBLESHOOTING.md) for trace lookup, plain output, hosting
panels, configuration questions, and safe reporting.

## How the catalog stays in sync

The canonical registry is `CanonicalDiagnosticRegistry`. Its generated
[catalog](../diagnostic-registry/catalog.md) and compatibility baseline are
checked by the build. `DiagnosticGuideCoverageTest` checks that every active or
deprecated registry entry has exactly one page here, the page's registry
identity and contract fields match, and the index links to it. Do not edit the
generated catalog by hand.

When adding or changing a code:

1. Update the canonical registry and regenerate its catalog under the
   [registry governance rules](../diagnostic-registry/README.md).
2. Add or update this operator page with evidence, safe checks, recovery limits,
   full-trace guidance, and an ANSI-free example. Update the table above.
3. Run the core test suite. Record runtime evidence before claiming that a
   platform adapter emits the code or that an environment is supported.

Each released artifact's documentation is the snapshot at its release tag.
Pages on `dev` may describe unreleased contracts and must label that state.
Stable code meanings are never reassigned; deprecated pages stay searchable and
link to replacements. Release notes and the compatibility matrix, when
published, identify the artifact version and tested behavior. Examples here
are illustrative until an integration test proves the corresponding emission
path.
