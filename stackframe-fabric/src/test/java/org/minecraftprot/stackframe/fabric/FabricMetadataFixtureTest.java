package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.loader.api.Version;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.testkit.platform.fabric.FabricMetadataFixtures;

class FabricMetadataFixtureTest {
    @Test
    void completeFixtureUsesValidFabricVersionsAndPortableOrigins() throws Exception {
        var snapshot = FabricMetadataFixtures.completeServer();
        assertEquals("fabric", snapshot.loader());
        assertEquals("26.2", Version.parse(snapshot.gameVersion()).getFriendlyString());
        for (var mod : snapshot.mods()) {
            assertEquals(mod.version(), Version.parse(mod.version()).getFriendlyString());
            mod.origin().ifPresent(origin -> assertFalse(origin.contains(":")));
        }
    }

    @Test
    void sparseFixtureLeavesOptionalMetadataAbsent() {
        var snapshot = FabricMetadataFixtures.sparseServer();
        assertTrue(snapshot.mappingNamespace().isEmpty());
        assertTrue(snapshot.mods().getFirst().displayName().isEmpty());
        assertTrue(snapshot.mods().getFirst().origin().isEmpty());
    }
}
