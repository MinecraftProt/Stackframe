package org.minecraftprot.stackframe.fabric.client;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Starts the passive observer before the regular client mod initializer. */
public final class StackframeClientPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        ClientCaptureBootstrap.install();
    }
}
