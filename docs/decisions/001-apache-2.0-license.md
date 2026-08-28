# ADR 001: License Stackframe under Apache-2.0

- **Status:** Proposed
- **Date:** 2026-08-28
- **Issue:** [#8](https://github.com/MinecraftProt/Stackframe/issues/8)
- **Owners:** Stackframe maintainers

## Context

Stackframe needs an approved open-source license before accepting contributions
or publishing artifacts. The project expects source and compiled mod jars to be
used by individuals, modpacks, hosting providers, and commercial server
operators. Those uses benefit from simple redistribution terms and should not
require unrelated server or modpack code to be published.

Stackframe will integrate with separately licensed platform APIs. Fabric API
uses Apache-2.0, while Minecraft Forge currently uses LGPL-2.1 for most of its
code. Stackframe's license does not relicense those projects, Minecraft, or any
other dependency. Each dependency and bundled component still needs its own
license and notice review.

Minecraft's EULA and Usage Guidelines are separate from an open-source license.
They permit independently developed Java Edition mods subject to their terms,
but the Stackframe license cannot grant rights to Minecraft code, assets, or
trademarks. Stackframe distributions must remain independent and comply with
the current Minecraft terms.

This record summarizes project policy, not legal advice.

## Decision

License Stackframe's original code and documentation under the Apache License,
Version 2.0, identified in metadata by the SPDX identifier `Apache-2.0`.
Approval and merge of the pull request containing this record is the
maintainer's explicit acceptance of this decision.

The exact unmodified license text is included in the root [`LICENSE`](../../LICENSE)
file. Future source and binary distributions must include it. Distributed
modified versions must preserve required notices and identify modified files.
If Stackframe later adds a `NOTICE` file, distributions must preserve its
applicable attribution notices as section 4 requires.

Apache-2.0 is permissive: recipients may use, modify, sublicense, and distribute
Stackframe, including in proprietary services or larger works, without a source
disclosure requirement. It also provides an express patent grant from
contributors and terminates that grant for a party bringing specified patent
claims about the work.

Contributions intentionally submitted for inclusion are licensed under the
same terms unless explicitly stated otherwise, as described by section 5.
Third-party material keeps its own license and may be included only after its
compatibility, attribution, and distribution obligations are documented.

## Alternatives considered

### MIT

MIT is shorter, permissive, and common in the Minecraft mod ecosystem. It was
not selected because it has no express patent license or contribution terms;
Apache-2.0 provides both while retaining permissive redistribution.

### Mozilla Public License 2.0

MPL-2.0 provides an express patent grant and file-level copyleft. It was not
selected because distributing modified covered files requires making their
source available under MPL-2.0. That obligation adds complexity for a mod
intended for broad repackaging and integration without materially advancing
Stackframe's current goals.

### GNU Lesser General Public License 3.0

LGPL-3.0 provides weak copyleft and an express patent grant. It was not selected
because its library, combined-work, source, and relinking obligations are more
complex for mod jars and their distributors. Stackframe does not currently need
copyleft to meet its contribution or availability goals.

## Consequences

### Positive

- Operators, modpacks, and hosting providers may use and redistribute
  Stackframe under predictable permissive terms.
- Contributors and recipients receive express copyright and patent grants.
- Future artifact metadata has one standard SPDX identifier.
- The license aligns with Fabric API without changing any dependency's terms.

### Negative

- Modified or proprietary forks do not have to publish their source.
- Redistributors must include the license, identify modified files, preserve
  applicable notices, and carry forward a future `NOTICE` file.
- Apache-2.0 text and obligations are longer than MIT's.

### Risks

- A dependency may impose obligations beyond Apache-2.0. Dependency review and
  generated license data remain release gates.
- Minecraft's terms may change independently. Release review must check the
  current EULA and Usage Guidelines and must not ship Minecraft code or assets.
- License metadata could drift. Release checks must use the `Apache-2.0` SPDX
  identifier and include the root license text.

## Validation

- Compare [`LICENSE`](../../LICENSE) byte-for-byte, allowing only line-ending
  normalization, with the
  [Apache Software Foundation text](https://www.apache.org/licenses/LICENSE-2.0.txt).
- Confirm `Apache-2.0` against the
  [SPDX license list](https://spdx.org/licenses/Apache-2.0.html).
- Review the current
  [Fabric license](https://github.com/FabricMC/fabric/blob/1.21.8/LICENSE),
  [Minecraft Forge license](https://github.com/MinecraftForge/MinecraftForge/blob/master/LICENSE.txt),
  [Minecraft EULA](https://www.minecraft.net/en-us/eula), and
  [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
  before publication.

## Follow-up

- Add `Apache-2.0` to Gradle and loader publication metadata when those files
  are introduced by issue #7.
- Generate dependency license and notice data before the first artifact is
  published.
