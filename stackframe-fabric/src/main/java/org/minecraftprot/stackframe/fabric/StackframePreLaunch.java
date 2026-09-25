package org.minecraftprot.stackframe.fabric;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Early hook for startup failures that occur before regular mod initialization. */
public final class StackframePreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        FabricCaptureBootstrap.install();
    }
}
