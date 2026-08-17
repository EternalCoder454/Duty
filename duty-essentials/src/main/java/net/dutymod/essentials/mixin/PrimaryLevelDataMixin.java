package net.dutymod.essentials.mixin;

import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.dutymod.essentials.model.Position;
import net.dutymod.essentials.model.Warp;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.OptionalDynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin implements ServerLevelData, WorldData, EssentialsLevelData {

    @Unique
    private Position duty$spawnPosition = Position.ZERO;

    @Unique
    private Map<String, Warp> duty$Warps = new HashMap<>();

    @Override
    public Position duty$getSpawnPosition() {
        return duty$spawnPosition;
    }

    @Override
    public void duty$setSpawnPosition(Position position) {
        duty$spawnPosition = position;
    }

    @Override
    public List<Warp> duty$getWarps() {
        return new ArrayList<>(duty$Warps.values());
    }

    @Override
    public void duty$setWarps(List<Warp> warps) {
        duty$Warps = warps.stream().collect(Collectors.toMap(warp -> warp.name, warp -> warp));
    }

    @Override
    public void duty$addWarp(Warp warp) {
        duty$Warps.put(warp.name, warp);
    }

    @Override
    public void duty$removeWarp(String name) {
        duty$Warps.remove(name);
    }

    @Override
    public Optional<Warp> duty$getWarp(String name) {
        return duty$Warps.containsKey(name) ? Optional.of(duty$Warps.get(name)) : Optional.empty();
    }

    // Only fired when creating a new world.
    @Inject(method = "<init>(Lnet/minecraft/world/level/LevelSettings;Lnet/minecraft/world/level/storage/PrimaryLevelData$SpecialWorldProperty;Lcom/mojang/serialization/Lifecycle;)V", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        duty$spawnPosition = Position.ZERO;
        duty$Warps = new HashMap<>();
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static <T> void parse(Dynamic<T> input, LevelSettings settings, PrimaryLevelData.SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle, CallbackInfoReturnable<PrimaryLevelData> cir) {
        OptionalDynamic<T> duty_essentials = input.get("DutyEssentials");

        Position duty$spawnPosition = Position.deserialize(duty_essentials.get("Spawn").orElseEmptyMap());

        if (duty$spawnPosition.equals(Position.ZERO)) {
            duty$spawnPosition = Position.fromRespawnData(cir.getReturnValue().getRespawnData());
        }

        List<Warp> duty$Warps = duty_essentials.get("Warps").asStream().map(Warp::deserialize)
                .collect(Collectors.toList());

        if (cir.getReturnValue() instanceof EssentialsLevelData data) {
            data.duty$setSpawnPosition(duty$spawnPosition);
            data.duty$setWarps(duty$Warps);
        }
    }

    @Inject(method = "setTagData", at = @At("HEAD"))
    private void setTagData(CompoundTag tag, UUID singlePlayerUUID, CallbackInfo ci) {
        CompoundTag duty_essentialsTag = new CompoundTag();

        if (this.duty$spawnPosition.equals(Position.ZERO)) {
            this.duty$spawnPosition = Position.fromRespawnData(this.getRespawnData());
        }

        duty_essentialsTag.put("Spawn", duty$spawnPosition.serialize());
        duty_essentialsTag.put("Warps", duty$Warps.values().stream().map(Warp::serialize)
                .collect(Collectors.toCollection(ListTag::new)));

        tag.put("DutyEssentials", duty_essentialsTag);
    }
}
