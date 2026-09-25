# Release compatibility entry template

Copy this entry into each GitHub Release description. This file is a template,
not evidence or a published compatibility claim. Replace every placeholder before
publishing; do not publish a release with a missing artifact row.

| Artifact file | SHA-256 | Source commit | Exact matrix revision | Applicable row IDs and statuses | Tagged-source test evidence |
| --- | --- | --- | --- | --- | --- |
| `ARTIFACT_NAME.jar` | `SHA256` | `COMMIT_SHA` | [Matrix at COMMIT_SHA](https://github.com/MinecraftProt/Stackframe/blob/COMMIT_SHA/docs/COMPATIBILITY.md#current-public-matrix) | `ROW_ID: STATUS (last tested YYYY-MM-DD UTC)` or `none: Unknown` | Immutable CI run and manual report links, or `none (untested)` |

For each artifact, state its **Supported**, **Tested**, **Expected-compatible**,
**Degraded**, **Unsupported**, or **Unknown** rows exactly as they appear in that
matrix revision, including the last-tested UTC dates and known limitations.
`none` for applicable row IDs means **no support claim**, not general support.
Link each open regression issue beside the affected row. Include the exact
Minecraft, Java vendor/patch, loader/API, OS, output destination, and relevant
mod versions from the matrix rather than replacing them with version ranges.

The matrix URL must use the release tag's resolved commit SHA, not a branch name
or the tag name. Resolve an annotated tag with `git rev-parse <tag>^{commit}`.
Compare the resolved commit with the artifact source commit and verify that the
linked file exists there. The release page maps each downloadable binary to its
own checksum and matrix row; one generic compatibility link for a multi-artifact
release is insufficient.
