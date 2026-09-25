package org.minecraftprot.stackframe.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/** Physical-client bootstrap. Failure capture is introduced by issue #61. */
public final class StackframeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[Stackframe Fabric Client] Loaded client bootstrap.");
    }
}
