package org.minecraftprot.stackframe.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/** Physical-client bootstrap with log-only, fail-open failure observation. */
public final class StackframeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCaptureBootstrap.install();
        System.out.println("[Stackframe Fabric Client] Loaded client bootstrap.");
    }
}
