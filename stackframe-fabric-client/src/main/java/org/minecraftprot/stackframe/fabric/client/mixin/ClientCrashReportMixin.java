package org.minecraftprot.stackframe.fabric.client.mixin;

import net.minecraft.CrashReport;
import org.minecraftprot.stackframe.fabric.client.ClientCaptureBootstrap;
import org.minecraftprot.stackframe.fabric.client.ClientFailurePhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also observes startup crashes that Main reports before a Minecraft instance exists. */
@Mixin(CrashReport.class)
abstract class ClientCrashReportMixin {
    @Inject(method = "forThrowable", at = @At("RETURN"), require = 0)
    private static void stackframeObserveCrashReport(
            Throwable failure, String description,
            CallbackInfoReturnable<CrashReport> callback) {
        try {
            ClientCaptureBootstrap.observe(failure, ClientFailurePhase.CRASH_REPORT);
        } catch (Throwable ignored) {
            // Construction and saving of the vanilla report continue unchanged.
        }
    }
}
