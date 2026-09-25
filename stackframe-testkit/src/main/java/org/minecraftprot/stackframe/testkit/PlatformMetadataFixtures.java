package org.minecraftprot.stackframe.testkit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loader-neutral, synthetic metadata values for adapter tests. */
public final class PlatformMetadataFixtures {
    private PlatformMetadataFixtures() {
    }

    public record Mod(String id, String version, Optional<String> displayName,
            Optional<String> origin) {
        public Mod {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(origin, "origin");
        }
    }

    public record Snapshot(String loader, String gameVersion, Optional<String> mappingNamespace,
            List<Mod> mods) {
        public Snapshot {
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(gameVersion, "gameVersion");
            Objects.requireNonNull(mappingNamespace, "mappingNamespace");
            mods = List.copyOf(mods);
        }
    }
}
