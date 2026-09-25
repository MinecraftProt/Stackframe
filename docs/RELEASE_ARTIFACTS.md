# Tag build and release-artifact runbook

This page describes the automated candidate bundle and the separate, manual
publication step. A successful build establishes a JAR's source and dependency
identity; it does **not** establish dedicated-server compatibility or authorize
public release. Apply the gates in [RELEASES.md](RELEASES.md) first.

## Before a tag

Merge reviewed changes to `main`, including accurate compatibility rows and
release notes. Verify the full release test matrix and the supply-chain review.
Create an **annotated** tag such as `v0.1.0-alpha.1` on the approved `main`
commit and push that tag. The version without `v` becomes the Gradle project
version, Fabric mod version, JAR filename, and JAR manifest
`Implementation-Version`. The JAR's `Build-Revision` holds the full commit SHA.
Do not move or reuse a release tag.

Repository maintainers must configure tag protection/rulesets for `v*` so only
approved publishers can create or update tags. They should enable GitHub's
immutable releases setting before the first public release. These repository
settings are outside this pull request and are **not verified by the workflow**.
The existing branch protection and release approval process must ensure the
tagged source passed review. Do not push a tag merely to test this workflow.

## What the workflow produces

The [tag-only workflow](../.github/workflows/release-artifacts.yml) checks out
the tagged commit with full history and no persisted credentials. It rejects
invalid or lightweight tags, source not reachable from `origin/main`, and a JAR
whose metadata differs from the tag. It runs the committed Gradle wrapper with
Java 25, strict dependency verification, `clean build`, and architecture checks.
No pull-request job can create a release.

Each successful run uploads one Actions artifact named for the tag, commit,
workflow run ID, and attempt. The bundle contains:

| File | Meaning |
| --- | --- |
| `stackframe-fabric-VERSION.jar` | Remapped dedicated-server Fabric binary; embedded mod version and manifest identify version and source commit |
| `LICENSE` | Stackframe's Apache-2.0 license text |
| `embedded-dependencies.tsv` | Exact reviewed bundled-component inventory, SPDX license IDs, and license paths; checked against the JAR's embedded copy |
| `runtime-dependencies.txt` | Resolved Fabric runtime classpath tree, including platform-provided components; this is inventory data, **not** license clearance |
| `CHANGELOG.md` | Commit-subject draft for maintainer editing, **not** approved release notes |
| `provenance.json` | Tag, full source revision, run attempt, build command, wrapper identity, Java version, binary digest, and immutable compatibility-matrix link |
| `SHA256SUMS` | SHA-256 of every release-bound bundle file; the draft changelog is excluded |

The separate attestation job downloads that exact run bundle, verifies its
checksums, and signs build provenance for the Fabric JAR using GitHub's OIDC
attestation service. It holds
`id-token: write` and `attestations: write`; the build job has only
`contents: read`. Neither job holds `contents: write` or an external release
credential. An artifact upload is **not** a public GitHub Release, and Actions
artifacts expire after 90 days. The whole workflow, including attestation, must
be green before publication.

Only a Fabric server binary is assembled today. Forge and client artifacts must
get distinct names, metadata validation, inventories, checksums, and
compatibility rows before joining this workflow.

## Review and publish

Download the artifact from the successful workflow run, keeping its run URL in
the release review record. On a Unix shell, validate the bundle and attestation:

```sh
cd downloaded-bundle
sha256sum --check SHA256SUMS
gh attestation verify stackframe-fabric-VERSION.jar -R MinecraftProt/Stackframe
```

Replace `VERSION` with the exact version. Compare the tag's resolved commit
(`git rev-parse 'vVERSION^{commit}'`) with `provenance.json`, the JAR manifest,
and the compatibility-matrix permalink. Review the bundled license inventory,
platform-provided dependency obligations, advisory triage, test evidence, and
every public compatibility claim. Prepare final release notes using the
[per-artifact template](compatibility/RELEASE_ENTRY_TEMPLATE.md); the draft
changelog alone is insufficient.

After explicit maintainer approval, a person with GitHub release write access
checks whether a release for this tag already exists. Create a **draft** release
from the existing tag and attach the reviewed files and final notes. With GitHub
CLI, `--verify-tag` refuses to create a missing tag and `--draft` prevents
immediate publication:

```sh
gh release view vVERSION -R MinecraftProt/Stackframe
# If a release already exists, stop and inspect it. Do not run create again.
gh release create vVERSION ./stackframe-fabric-VERSION.jar ./LICENSE \
  ./embedded-dependencies.tsv ./runtime-dependencies.txt ./provenance.json \
  ./SHA256SUMS --draft --verify-tag --title 'vVERSION' \
  --notes-file ./approved-release-notes.md -R MinecraftProt/Stackframe
```

The publisher must explicitly check that the first command reports **not
found** before running the second. Review draft assets and per-artifact SHA,
source commit, compatibility rows, and tagged-source evidence once more, then
publish through GitHub's release UI. The publisher's own credential is used;
no personal access token or repository secret is stored in this workflow.

## Failures and retries

Build failures are visible as a failed run; available verification reports are
uploaded under a run/attempt-specific name. An attestation failure also leaves
the workflow red even if the bundle uploaded. Fix source problems in a new
reviewed commit and **new version tag**. For an infrastructure outage with
unchanged source, rerun the same workflow attempt through GitHub; the new
attempt gets a distinct bundle name and must pass in full. Never publish assets
from a red run.

If draft creation or asset upload partly succeeds, inspect the draft and its
asset list. Add only genuinely missing, checksum-verified files to that draft;
`gh release upload` without `--clobber` refuses an existing asset. Never use
`--clobber`. If a public release is already published, leave it intact and
publish a corrected version under a new tag. Keep the failed run or partial
draft visible in the release review record so the recovery is auditable.

This workflow does not exercise the dedicated-server compatibility matrix,
configure tag rulesets, enable immutable releases, approve release notes, or
publish a GitHub Release. Those remain release gates, not inferred outcomes of
a green artifact job.
