package org.minecraftprot.stackframe.testkit.platform.fabric;

import java.util.List;
import java.util.Optional;
import org.minecraftprot.stackframe.testkit.PlatformMetadataFixtures.Mod;
import org.minecraftprot.stackframe.testkit.PlatformMetadataFixtures.Snapshot;

/** Synthetic Fabric server metadata, with optional values present or absent. */
public final class FabricMetadataFixtures {
    private FabricMetadataFixtures() {
    }

    public static Snapshot completeServer() {
        return new Snapshot(
                "fabric",
                "26.2",
                Optional.of("intermediary"),
                List.of(
                        new Mod("fabricloader", "0.19.3", Optional.of("Fabric Loader"),
                                Optional.empty()),
                        new Mod("example-addon", "1.2.0", Optional.of("Example Addon"),
                                Optional.of("mods/example-addon.jar"))));
    }

    public static Snapshot sparseServer() {
        return new Snapshot(
                "fabric",
                "26.2",
                Optional.empty(),
                List.of(new Mod("example-addon", "1.2.0", Optional.empty(), Optional.empty())));
    }
}
