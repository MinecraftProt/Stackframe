# Architecture decisions

Architecture decision records (ADRs) preserve why a significant choice was made
and which constraints it protects. They supplement issues and pull requests;
they do not replace implementation acceptance criteria.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [001](001-apache-2.0-license.md) | Accepted | License Stackframe under Apache-2.0 |
| [002](002-initial-platform-baseline.md) | Proposed | Initial Minecraft, Java, Fabric, Loom, and Gradle baseline |
| [003](003-loader-independent-diagnostic-model.md) | Proposed | Loader-independent diagnostic model |

Create an ADR when changing:

- module boundaries or dependency direction;
- the loader platform SPI;
- diagnostic model, code meaning, or structured schema;
- capture, fallback, or full-trace guarantees;
- privacy, redaction, file access, or external communication;
- compatibility and release guarantees;
- a decision already recorded by an ADR.

## Process

1. Copy [`000-template.md`](000-template.md).
2. Rename it to the next three-digit number and a short kebab-case title.
3. Start with status `Proposed` and link the driving issue.
4. Record real alternatives and consequences.
5. Merge an accepted ADR before or with the implementing change.
6. Never rewrite an accepted decision substantially; supersede it with a new ADR.

Statuses are `Proposed`, `Accepted`, `Rejected`, `Deprecated`, and `Superseded`.
