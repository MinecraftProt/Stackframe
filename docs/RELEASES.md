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
- Capture never swallows an injected original error.
- Full traces and correlation IDs are recoverable.
- Installation, configuration, privacy limitations, and known issues are clear.
- License and artifact metadata are complete.

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

## Publication

Release automation should:

1. Build from an annotated version tag on an approved commit.
2. Use the committed Gradle wrapper and selected Java toolchain.
3. Run the complete release test matrix.
4. Generate artifacts, checksums, dependency/license data, and provenance.
5. Produce release notes from reviewed issues and pull requests.
6. Require explicit maintainer approval before external publication.
7. Never grant publication credentials to pull-request workflows.

Partial publication is reported and recovered explicitly. Existing artifacts are
not overwritten to hide a failed release.

## Release notes

Notes include:

- exact supported Minecraft, Java, and loader versions;
- added, changed, and deprecated diagnostic codes;
- configuration or structured-schema migration steps;
- privacy or retention changes;
- fixed compatibility problems;
- known limitations and links to the compatibility matrix;
- artifact checksums and source revision.

## Rollback

Artifacts are immutable. A defective release is marked clearly and followed by a
new version. Security-sensitive releases follow `SECURITY.md`; operators receive
specific upgrade or mitigation guidance without exposing an unpatched flaw.
