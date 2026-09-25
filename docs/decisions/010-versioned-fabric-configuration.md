# ADR 010: Strict versioned Fabric server configuration

- **Status:** Proposed
- **Date:** 2026-09-25
- **Issue:** [#14](https://github.com/MinecraftProt/Stackframe/issues/14)
- **Owners:** Fabric integration and privacy maintainers

## Context

Fabric needs operator controls for supplemental output, trace storage,
filtering, and correlation. A permissive parser could treat a typo or a
newer-release setting as a default, changing privacy or retention behavior
without the operator noticing. Several requested concepts, including
verbosity, frame collapse, and redaction modes, have no live Fabric behavior
yet. Log4j also fans one diagnostic event to potentially different appenders,
so the adapter cannot assert that automatic terminal detection applies to all
destinations.

## Decision

Use one UTF-8 `config/stackframe.properties` file under the server working
directory, with `schema_version=1` required first. The adapter loads it at
capture installation. Missing means documented safe defaults; any present but
invalid or unreadable file produces a location-aware, value-free error and
disables supplemental capture for that attempt. Original platform logging
remains independent. Changes require a restart.

Schema 1 accepts only implemented settings: plain/ANSI/conservative-auto
output, an in-root trace directory, manual or bounded trace retention, filters
for the currently emitted `SF0001`, and bounded correlation window/capacity.
Unknown keys and future schema versions fail closed. No schema 0 migration is
inferred because no prior released configuration exists. Settings that would
have no effect in a selected mode are rejected.

Manual trace retention is the default. Opt-in bounded cleanup checks only
complete, Stackframe-named regular `.trace` files with an ownership header in
the configured directory,
with age and count limits plus a scan cap. It skips known links, unrelated
files, and partial writes; failures surface in safe output. It never cleans
other server logs or arbitrary directories. The raw trace content is not
redacted by the operator renderer.

No `redaction=off`, verbosity, frame-collapse, or JSON output field is exposed
until the adapter can implement and verify that behavior. Explicit ANSI is an
operator override; auto remains plain for the Fabric Log4j fan-out path until
destinations can be assessed independently.

## Alternatives considered

### Ignore unknown fields or fall back to defaults

This would make a typo or future-version file look accepted while changing
diagnostic delivery, retention, or privacy. Strict rejection provides a clear
repair path and leaves the original server failure visible.

### Use `java.util.Properties` without a parser

It silently accepts duplicate keys and loses useful source locations. A small
bounded parser can report the exact line and expected form without logging an
untrusted value.

### Automatically delete old traces by default

Complete records may be the only local debugging evidence. Manual retention
avoids surprise deletion, while the explicit bounded mode serves operators
who need a cap.

## Consequences

- A malformed file disables only Stackframe's supplemental capture; the server
  still logs the original event through its existing appenders.
- Operators must restart after correction. There is no live reload or silent
  migration.
- The first schema deliberately has a narrow vocabulary. Deferred fields need
  code, tests, and a schema decision before they can be accepted.
- Retention is destructive only after explicit opt-in, and cleanup failures
  require operator attention. It cannot promise protection from hostile
  filesystem races or other processes manipulating the trace directory.
- Isolated parser and pipeline tests do not establish dedicated-server
  compatibility. The public matrix remains evidence-driven.
