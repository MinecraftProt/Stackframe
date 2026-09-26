package org.minecraftprot.stackframe.fabric.client.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import org.minecraftprot.stackframe.fabric.client.ClientCaptureBootstrap;
import org.minecraftprot.stackframe.fabric.client.ClientFailurePhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attach a passive observer to the result; return and recovery behavior stay vanilla. */
@Mixin(Minecraft.class)
abstract class ClientResourceReloadMixin {
    @Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"), require = 0)
    private void stackframeObserveResourceReload(
            boolean forced, GameLoadCookie cookie,
            CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        try {
            var result = callback.getReturnValue();
            if (result != null) {
                result.whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        ClientCaptureBootstrap.observe(failure, ClientFailurePhase.RESOURCE_RELOAD);
                    }
                });
            }
        } catch (Throwable ignored) {
            // Minecraft still receives and handles its original future.
        }
    }
}
