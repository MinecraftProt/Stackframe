# Stackframe

Stackframe is a Minecraft diagnostics mod that turns noisy failures into concise,
structured diagnostics inspired by the Rust compiler. Development starts with
dedicated servers; a separate client edition is planned after the server
foundation is production-ready.

Instead of forcing an administrator to decode a wall of stack frames, Stackframe
will identify the failure, show the useful context, explain the likely cause,
and suggest the next action. Full technical details remain available in the log
file so information is never lost.

```text
error[SF1001]: failed to load the overworld
  --> world/level.dat
   |
   = cause: level metadata is unreadable
   = help: restore level.dat_old or a recent backup
   = trace: 14 internal stack frames collapsed
```

Stackframe presents each failure at two levels:

- an operator view with the relevant cause, context, and safe next action;
- a complete correlated debug record for developers and advanced support.

It does not delete stack traces, rewrite normal chat or informational logs, or
claim that a mod is responsible without evidence.

## Goals

- Make production server errors readable and actionable.
- Preserve the original exception and stack trace for debugging.
- Work in plain terminals, ANSI-capable terminals, files, and CI logs.
- Start with Fabric, while keeping the core independent enough for Forge later.
- Reuse the same diagnostic language in a future Fabric-first client edition.
- Remain useful when an error is unknown by providing a safe generic diagnostic.
- Fail open: if formatting breaks, emit the original server error unchanged.
- Protect secrets and personal data before output leaves the diagnostic pipeline.

Stackframe is in the design and bootstrap phase. The selected foundation baseline
is a Minecraft 26.2 dedicated Fabric server on Java 25; this is not yet a support
claim. See the [compatibility policy](docs/COMPATIBILITY.md) for exact pins and
evidence requirements. Stackframe is not ready for production use yet.

## Planned architecture

```text
stackframe-core       loader-independent diagnostic model and pipeline
stackframe-renderer   terminal, plain-text, and structured output
stackframe-fabric     Fabric/Minecraft integration
stackframe-forge      Forge/Minecraft integration after the shared SPI stabilizes
stackframe-fabric-client  planned Fabric client integration and in-game view
stackframe-forge-client   planned Forge client adapter after the shared SPI
stackframe-testkit    fixtures, snapshots, and integration-test utilities
```

## Documentation

| Document | Purpose |
| --- | --- |
| [Documentation index](docs/README.md) | Entry point for all project documents |
| [Project design](docs/PROJECT.md) | Product principles, architecture, and pipeline |
| [Roadmap](docs/ROADMAP.md) | Milestones, critical path, and release gates |
| [Diagnostic style](docs/DIAGNOSTIC_STYLE.md) | Message grammar, layout, and examples |
| [Compatibility](docs/COMPATIBILITY.md) | Support definitions and test expectations |
| [Security and privacy](docs/SECURITY_AND_PRIVACY.md) | Threat model and redaction boundaries |
| [Release process](docs/RELEASES.md) | Versioning, channels, and publication gates |
| [GitHub workflow](docs/GITHUB_WORKFLOW.md) | Issues, labels, project board, and branches |
| [Parallel workstreams](docs/WORKSTREAMS.md) | Module ownership and multi-worker boundaries |
| [Dependency waves](docs/DEPENDENCIES.md) | Safe order for parallel issue execution |
| [Worker handoff](docs/HANDOFF.md) | Standard context transfer between sessions |
| [Contributing](CONTRIBUTING.md) | Contributor workflow and engineering expectations |
| [Support](SUPPORT.md) | Getting help and preparing a useful report |
| [Governance](GOVERNANCE.md) | Decision-making and maintainer responsibilities |

## Roadmap

1. **Foundation:** build setup, diagnostic model, renderer, and test fixtures.
2. **Fabric MVP:** safe log capture, exception normalization, readable output,
   configuration, and compatibility testing.
3. **Production readiness:** performance, redaction, structured output,
   documentation, packaging, and release automation.
4. **Forge support:** stabilize the loader API and add a Forge adapter.
5. **Client support:** ship a separate Fabric client edition, then add a Forge
   client adapter after the shared platform contracts are stable.

Use the [public roadmap board](https://github.com/orgs/MinecraftProt/projects/1)
for status and the [issue tracker](https://github.com/MinecraftProt/Stackframe/issues)
for scoped requirements and acceptance criteria.

## Status and license

The project is pre-alpha. Stackframe is licensed under the
[Apache License 2.0](LICENSE), a permissive license that allows use,
modification, and redistribution while preserving its notices. The rationale
and alternatives are recorded in
[ADR 001](docs/decisions/001-apache-2.0-license.md).
