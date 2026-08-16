package net.dutymod.fixerupper.common.mixin.perf.suspend_integrated_server_during_load;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import net.dutymod.fixerupper.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Mixin(IntegratedServer.class)
@ClientOnlyMixin
public abstract class IntegratedServerMixin extends MinecraftServer implements IDeferrableIntegratedServer {
    @Shadow
    private boolean paused;

    private int duty$numTickServerCalls = 0;
    private final AtomicBoolean duty$hasPrimaryClientJoined = new AtomicBoolean(false);

    public IntegratedServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes);
    }

    /**
     * @author embeddedt
     * @reason Wait to be finished processing all expensive packets (recipes, tags, etc.)
     * before continuing to tick the integrated server.
     */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z", ordinal = 0))
    private boolean preventTicks(Minecraft instance, Operation<Boolean> original) {
        return !duty$hasPrimaryClientJoined.get() || original.call(instance);
    }

    /**
     * @author embeddedt
     * @reason Keep our own tick count for the integrated server specifically, rather than relying on super
     * to increment.
     */
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void duty$countTicks(CallbackInfo ci) {
        this.duty$numTickServerCalls++;
    }

    /**
     * @author embeddedt
     * @reason If waiting for a client connection to exist, we only need to tick the server connection,
     * not the whole server as vanilla does. However, we must tick the whole server once to accommodate mods
     * that rely on the first tick to initialize state as a side effect. Not doing this causes issues like
     * <a href="https://github.com/embeddedt/FixerUpper/issues/639">#639</a>.
     */
    @WrapWithCondition(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V", ordinal = 0))
    private boolean preventRunningFullServerTick(MinecraftServer server, BooleanSupplier hasTimeLeft) {
        if (this.duty$numTickServerCalls >= 2 && this.paused && !duty$hasPrimaryClientJoined.get()) {
            var conn = this.getConnection();
            if (conn != null) {
                conn.tick();
            }
            return false;
        }
        return true;
    }

    @Override
    public void duty$markClientLoadFinished() {
        duty$hasPrimaryClientJoined.set(true);
    }
}
