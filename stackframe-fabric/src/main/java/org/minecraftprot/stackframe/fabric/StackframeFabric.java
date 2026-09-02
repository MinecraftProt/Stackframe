package org.minecraftprot.stackframe.fabric;

import net.fabricmc.api.ModInitializer;

public final class StackframeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[Stackframe] Loaded Stackframe dedicated-server bootstrap.");
    }
}
