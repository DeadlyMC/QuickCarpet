package quickcarpet.mixin.tickSpeed;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quickcarpet.helper.TickSpeed;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow @Final static Logger LOGGER;
    @Shadow private volatile boolean running;
    @Shadow private long timeReference;
    @Shadow private long lastTimeReference;
    @Shadow private Profiler profiler;
    @Shadow private volatile boolean loading;
    @Shadow private boolean waitingForNextTick;
    @Shadow private long nextTickTimestamp;
    @Shadow private boolean needsDebugSetup;
    @Shadow private int ticks;
    @Shadow @Nullable private MinecraftServer.DebugStart debugStart;

    @Shadow protected abstract boolean shouldKeepTicking();
    @Shadow protected abstract void runTasksTillTickEnd();
    @Shadow public abstract void tick(BooleanSupplier booleanSupplier);

    @Shadow protected abstract void startTickMetrics();
    @Shadow protected abstract void endTickMetrics();

    // Cancel a while statement
    @Redirect(method = "runServer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;running:Z"))
    private boolean cancelRunLoop(MinecraftServer server) {
        return false;
    }

    // Replaced the above cancelled while statement with this one
    // could possibly just inject that mspt selection at the beginning of the loop, but then adding all mspt's to
    // replace 50L will be a hassle
    @Inject(method = "runServer", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/MinecraftServer;setFavicon(Lnet/minecraft/server/ServerMetadata;)V"))
    private void modifiedRunLoop(CallbackInfo ci) {
        TickSpeed tickSpeed = TickSpeed.getServerTickSpeed();
        float partialTimeReference = 0;
        while (this.running) {
            //long long_1 = SystemUtil.getMeasuringTimeMs() - this.timeReference;
            //CM deciding on tick speed
            float mspt = 0;
            long behind = 0L;
            if (tickSpeed.tickWarpStartTime != 0 && tickSpeed.continueWarp()) {
                //making sure server won't flop after the warp or if the warp is interrupted
                this.timeReference = this.lastTimeReference = Util.getMeasuringTimeMs();
            } else {
                mspt = tickSpeed.msptGoal; // regular tick
                behind = Util.getMeasuringTimeMs() - this.timeReference;
            }
            //end tick deciding
            //smoothed out delay to include mspt component. With 50L gives defaults.
            if (behind > /*2000L*/1000L + 20 * mspt && this.timeReference - this.lastTimeReference >= /*15000L*/10000L + 100 * mspt) {
                float ticks = behind / mspt;//50L;
                LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", behind, ticks);
                this.timeReference += ticks * mspt;//50L;
                this.lastTimeReference = this.timeReference;
            }

            if (this.needsDebugSetup) {
                this.needsDebugSetup = false;
                this.debugStart = new MinecraftServer.DebugStart(Util.getMeasuringTimeNano(), this.ticks);
            }

            partialTimeReference += mspt - (long) mspt;
            this.timeReference += (long) mspt;//50L;
            if (partialTimeReference > 1) {
                partialTimeReference--;
                timeReference++;
            }
            this.startTickMetrics();
            this.profiler.push("tick");
            this.tick(this::shouldKeepTicking);
            this.profiler.swap("nextTickWait");
            this.waitingForNextTick = true;
            this.nextTickTimestamp = Math.max(Util.getMeasuringTimeMs() + (long) mspt, this.timeReference);
            this.runTasksTillTickEnd();
            this.profiler.pop();
            this.endTickMetrics();
            this.loading = true;
        }

    }

}
