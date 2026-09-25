package org.minecraftprot.stackframe.matrix.mixin;

import net.minecraft.server.MinecraftServer;
import org.minecraftprot.stackframe.matrix.FailureFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pins every phase trigger to an actual Minecraft 26.2 server method. */
@Mixin(MinecraftServer.class)
public abstract class ServerPhaseMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void stackframeMatrixWorld(CallbackInfo callback) {
        FailureFixture.emit("world");
    }

    @Inject(method = "registryAccess", at = @At("HEAD"))
    private void stackframeMatrixRegistry(CallbackInfoReturnable<?> callback) {
        FailureFixture.emit("registry");
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void stackframeMatrixDatapack(CallbackInfoReturnable<?> callback) {
        FailureFixture.emit("datapack");
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void stackframeMatrixRuntime(CallbackInfo callback) {
        FailureFixture.emit("runtime");
        FailureFixture.throwFromMixin();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void stackframeMatrixShutdown(CallbackInfo callback) {
        FailureFixture.emit("shutdown");
    }
}
