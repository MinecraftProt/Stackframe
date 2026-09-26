package org.minecraftprot.stackframe.fabric.client.mixin;

import java.io.File;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import org.minecraftprot.stackframe.fabric.client.ClientCaptureBootstrap;
import org.minecraftprot.stackframe.fabric.client.ClientFailurePhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the report's throwable before Minecraft saves the original crash report. */
@Mixin(Minecraft.class)
abstract class ClientCrashMixin {
    @Inject(method = "crash", at = @At("HEAD"), require = 0)
    private static void stackframeObserveCrash(
            Minecraft minecraft, File gameDirectory, CrashReport report,
            int exitCode, CallbackInfo callback) {
        try {
            ClientCaptureBootstrap.observe(report.getException(), ClientFailurePhase.CRASH);
        } catch (Throwable ignored) {
            // The original crash/report path always retains control.
        }
    }
}
