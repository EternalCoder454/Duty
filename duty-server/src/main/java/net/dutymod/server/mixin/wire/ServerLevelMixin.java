package net.dutymod.server.mixin.wire;

import net.dutymod.server.wire.RedstoneWire;
import java.util.List;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.server.wire.interfaces.IServerLevel;
import net.dutymod.server.wire.WireHandler;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements IServerLevel {

	private WireHandler wireHandler;

	@Inject(
		method = "<init>",
		at = @At(
			value = "TAIL"
		)
	)
	private void duty$parseConfig(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage, ServerLevelData data, ResourceKey<Level> key, LevelStem stem, boolean debug, long seed, List<CustomSpawner> customSpawners, boolean tickTime, CallbackInfo ci) {
		this.wireHandler = new WireHandler((ServerLevel)(Object)this, storage);
	}

	@Override
	public WireHandler duty$getWireHandler() {
		return wireHandler;
	}
}
