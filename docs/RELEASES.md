# Release process

Stackframe uses conservative pre-release channels until diagnostic and loader
contracts are proven in real servers.

## Versioning

Use semantic versions for published artifacts:

- `0.y.z` while public contracts may still change;
- `-alpha.n` for incomplete milestones and compatibility testing;
- `-beta.n` after feature scope is complete but production evidence is limited;
- release candidates only when all release gates pass.

Diagnostic-code meanings, structured schema, configuration schema, extension
API, and loader SPI are versioned public contracts even before `1.0.0`.

## Branch flow

- `main`: stable reviewed repository state and release tags.
- `dev`: integration branch for the next release.
- purpose-named feature branches: focused work targeting `dev`.
- release stabilization occurs through reviewed changes, not an untracked
  long-lived personal branch.

No branch name contains a person's name, username, or initials.

## Fabric alpha gates

- Foundation and Fabric MVP milestone exit criteria pass.
- A clean checkout produces the artifact with the documented toolchain.
- Dedicated-server tests cover every claimed support combination.
- The public compatibility matrix records exact versions, test dates, evidence,
  limitations, and freshness for every claimed combination.
- Capture never swallows an injected original error.
- Full traces and correlation IDs are recoverable.
- Installation, configuration, privacy limitations, and known issues are clear.
- License files and artifact metadata use the `Apache-2.0` SPDX identifier.

## Production-ready Fabric gates

- Redaction and hostile-input tests pass.
- Backpressure and error-storm behavior are measured.
- JSON schema and configuration migrations are documented.
- Compatibility claims include evidence and dates.
- Vulnerability, dependency, and license review is complete.
- Release automation produces checksums and provenance.
- Every stable code has operator documentation.

## Forge alpha gates

- The platform SPI is approved and used by Fabric.
- Cross-loader contract tests pass.
- Forge dedicated-server scenarios pass for every claimed version.
- Shared codes retain identical meanings.
- Forge-specific limitations and artifact identity are unambiguous.

## Fabric client alpha gates

- The client scope, UX, threading, privacy, and artifact contract is approved.
- Client and dedicated-server artifacts have unambiguous names and metadata.
- Fabric client capture preserves vanilla logs and crash reports on every
  formatter failure path.
- The in-game view passes keyboard, narration, scaling, redaction, and fallback
  scenarios.
- Exact supported Minecraft, Java, Fabric, operating-system, and relevant
  graphics assumptions have client-specific evidence.
- No server compatibility claim is reused as client evidence.

## Forge client alpha gates

- Fabric client contracts are proven and the shared loader SPI is stable.
- Forge server and client adapters pass shared fallback, redaction, and
  trace-preservation tests.
- Shared diagnostic codes retain the same meaning across side and loader.
- Forge client artifact identity and exact compatibility rows are published.

## Publication

Release automation should:

1. Build from an annotated version tag on an approved commit.
2. Use the committed Gradle wrapper and selected Java toolchain.
3. Run the complete release test matrix.
4. Include the root `LICENSE`, identify Stackframe as `Apache-2.0`, and generate
   checksums, dependency/license data, and provenance. The Fabric JAR already
   embeds a verified dependency/license inventory for its bundled components;
   maintainers review platform-provided dependencies separately using
   [the supply-chain procedure](SUPPLY_CHAIN.md).
5. Produce release notes from reviewed issues and pull requests.
6. For **each** downloadable artifact, record its filename, SHA-256, source
   commit, applicable matrix row IDs, and a permalink to the exact
   `docs/COMPATIBILITY.md` revision at the release tag's commit SHA. Use the
   [release-entry template](compatibility/RELEASE_ENTRY_TEMPLATE.md); verify each
   link and matching artifact/source revision before publication.
7. Link build and runtime evidence for the tagged source revision. If an exact
   row lacks qualifying evidence, publish it as `Unknown` or a narrower status,
   never as `Supported`. Record open regressions and stale rows visibly.
8. Require explicit maintainer approval before external publication.
9. Never grant publication credentials to pull-request workflows.

Partial publication is reported and recovered explicitly. Existing artifacts are
not overwritten to hide a failed release.

## Release notes

Notes include:

- exact supported Minecraft, Java, and loader versions;
- added, changed, and deprecated diagnostic codes;
- configuration or structured-schema migration steps;
- privacy or retention changes;
- fixed compatibility problems;
- known limitations, regression issues, last-tested dates, and per-artifact links
  to the exact compatibility matrix commit;
- artifact checksums and source revision.

## Rollback

Artifacts are immutable. A defective release is marked clearly and followed by a
new version. Security-sensitive releases follow `SECURITY.md`; operators receive
specific upgrade or mitigation guidance without exposing an unpatched flaw.
