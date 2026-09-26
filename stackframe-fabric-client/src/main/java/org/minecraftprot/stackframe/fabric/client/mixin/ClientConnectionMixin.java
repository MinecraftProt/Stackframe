package org.minecraftprot.stackframe.fabric.client.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.minecraftprot.stackframe.fabric.client.ClientCaptureBootstrap;
import org.minecraftprot.stackframe.fabric.client.ClientFailurePhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only the physical client's receiving connection, before vanilla disconnect logic. */
@Mixin(Connection.class)
abstract class ClientConnectionMixin {
    @Inject(method = "exceptionCaught", at = @At("HEAD"), require = 0)
    private void stackframeObserveConnectionFailure(
            ChannelHandlerContext context, Throwable failure, CallbackInfo callback) {
        try {
            if (((Connection) (Object) this).getSending() == PacketFlow.SERVERBOUND) {
                ClientCaptureBootstrap.observe(failure, ClientFailurePhase.CONNECTION);
            }
        } catch (Throwable ignored) {
            // Vanilla exceptionCaught still handles the original connection and throwable.
        }
    }
}
