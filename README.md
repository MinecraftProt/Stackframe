# Stackframe

Stackframe is a server-side Minecraft mod that turns noisy console failures into
concise, structured diagnostics inspired by the Rust compiler.

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
   = details: 14 internal stack frames collapsed
```

## Goals

- Make production server errors readable and actionable.
- Preserve the original exception and stack trace for debugging.
- Work in plain terminals, ANSI-capable terminals, files, and CI logs.
- Start with Fabric, while keeping the core independent enough for Forge later.
- Remain useful when an error is unknown by providing a safe generic diagnostic.

Stackframe is in the design and bootstrap phase. The first release will target
Fabric servers; it is not ready for production use yet.

## Planned architecture

```text
stackframe-core       loader-independent diagnostic model and pipeline
stackframe-renderer   terminal, plain-text, and structured output
stackframe-fabric     Fabric/Minecraft integration
stackframe-testkit    fixtures, snapshots, and integration-test utilities
```

See [Project design](docs/PROJECT.md) for the product and technical plan, and
[Contributing](CONTRIBUTING.md) for the proposed workflow.

## Roadmap

1. **Foundation:** build setup, diagnostic model, renderer, and test fixtures.
2. **Fabric MVP:** safe log capture, exception normalization, readable output,
   configuration, and compatibility testing.
3. **Production readiness:** performance, redaction, structured output,
   documentation, packaging, and release automation.
4. **Forge support:** stabilize the loader API and add a Forge adapter.

The GitHub issue tracker is the source of truth for scoped work.

## Status and license

The project is pre-alpha. A license has not been selected yet, so no permission
to use, modify, or redistribute the code is granted until a license is added.
