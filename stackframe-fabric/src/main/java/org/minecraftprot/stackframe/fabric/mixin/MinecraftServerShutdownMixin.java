package org.minecraftprot.stackframe.fabric.mixin;

import net.minecraft.server.MinecraftServer;
import org.minecraftprot.stackframe.fabric.FabricCaptureBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drains diagnostics at the end of graceful stop, while server logging is live. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerShutdownMixin {
    @Inject(method = "stopServer", at = @At("TAIL"))
    private void stackframeDrainDiagnostics(CallbackInfo callback) {
        FabricCaptureBootstrap.shutdown();
    }
}
