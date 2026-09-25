# Dependency and supply-chain review

The build fails on an unapproved download, a changed wrapper JAR, an unlocked
version, or an unreviewed dependency included in the Fabric artifact. These
checks verify identity and reviewed license metadata. A separate pull-request
dependency review checks introduced versions against GitHub advisories; it is
not a substitute for reviewing the existing dependency baseline before release.

## Reproducible checks

From a clean checkout with Java 25 available, run:

```shell
./gradlew --no-daemon --stacktrace --dependency-verification=strict clean build verifyModuleBoundaries
```

The committed wrapper pins Gradle's distribution SHA-256. The root build also
checks the wrapper JAR against its reviewed SHA-256, and CI runs the independently
pinned Gradle wrapper-validation action before executing the JAR. Strict Gradle
verification checks downloaded build and runtime artifacts against
`gradle/verification-metadata.xml`; module lockfiles pin resolved versions. The
only trusted generated artifacts are Loom's exact local Minecraft server and
merged client mappings. Their source downloads are verified, their generated
POMs are checksum-pinned, and their repository paths are checked by the build.
See [Building](BUILDING.md) for that exception's limits.

`stackframe-fabric:generateEmbeddedDependencyReport` resolves the Fabric
`includeInternal` configuration and writes a sorted UTF-8 inventory to
`stackframe-fabric/build/generated/supply-chain/dependencies.tsv`. The same file
is included at `META-INF/stackframe/dependencies.tsv` in the Fabric JAR. Each row
names an embedded component, its path inside the JAR, its reviewed SPDX license,
and the included license text. `verifyEmbeddedDependencyReport`, run by `check`,
opens the final JAR and checks that each reported dependency and license exists.
An added embedded component fails the build until its license is reviewed and
added to the root build's allowlist. The same verification applies to the
separately packaged Fabric client bootstrap. The inventories cover only embedded
components. Minecraft and Fabric Loader are platform-provided rather than
bundled by either artifact.

CI grants only `contents: read` to the build job. The checkout does not persist
credentials. Pull-request builds have no publication token or write permission.
The pinned [dependency-review workflow](../.github/workflows/dependency-review.yml)
runs for pull requests into `dev` with only `contents: read`. It blocks newly
introduced dependencies with high or critical published advisories, while lower
severity findings remain in the review output for triage. GitHub's dependency
review API and advisory coverage determine what it can detect; an absent finding
does not prove a dependency is safe or that every transitive component was
resolved into the dependency graph. The check does not auto-upgrade packages or
grant release credentials.
The tag-only [release-artifact workflow](RELEASE_ARTIFACTS.md) keeps its build job
read-only and gives only its separate attestation job OIDC and attestation write
permissions. A signed attestation establishes source/build provenance, not
vulnerability or license clearance.

## Reviewing updates

[Dependabot version updates](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configure-version-updates)
are configured weekly for Gradle and GitHub Actions, targeting `dev`. The
configuration must also reach the repository's default branch before GitHub
starts those scheduled checks. These pull requests are proposals: there is no
automatic merge or automatic dependency upgrade at release time. A proposed
update may initially fail CI because lockfiles and verification metadata must
be reviewed together.

For each update, identify the direct and transitive changes and read the
upstream release notes and license. Regenerate affected lockfiles with
`--write-locks` and verification hashes with
`--write-verification-metadata sha256`, then inspect every new checksum and
repository origin before committing. Do not accept a new hash merely to make
CI pass. Recheck the embedded license allowlist and notice text when an included
component changes. Run the strict clean build, focused tests, and any claimed
server compatibility checks before merging. Wrapper updates also require a
new distribution checksum, wrapper JAR review, and CI wrapper validation.

## Vulnerability triage before release

Maintainers review the resolved dependency inventory, build plugins, and CI
actions against [GitHub's Advisory Database](https://github.com/advisories) and
repository Dependabot alerts before each release candidate. The repository's
vulnerability-alerts API returned enabled on 2026-09-25, with no open alerts at
that time; verify the live setting and findings again for each release. The
pull-request dependency review detects newly introduced advisories only. If
alerts or dependency-graph coverage are unavailable, record the manual advisory
search and date in the release review.

For each finding, record its advisory ID, affected component and version,
dependency path, affected artifact or build job, reachable behavior, severity,
available fix or mitigation, reviewer, date, and disposition. A finding that may
expose an actual Stackframe release is handled through the private process in
[`SECURITY.md`](../SECURITY.md). Do not auto-merge a major upgrade or suppress a
finding without a written, time-bounded reason. Hold the release when an
applicable finding has no accepted mitigation. A false positive or unreachable
development-only path still needs an explicit recorded decision.

The release review also checks that every embedded component has its license
text in the artifact and that platform-provided dependency obligations are
handled by the platform or release documentation. Keep the inventory, review
record, and artifact checksum with the release evidence.
