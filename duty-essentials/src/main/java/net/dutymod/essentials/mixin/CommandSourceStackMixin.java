package net.dutymod.essentials.mixin;

import net.dutymod.essentials.command.DutyCommandSourceStack;
import net.dutymod.essentials.level.DutyServerLevel;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.dutymod.essentials.model.Position;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin implements SharedSuggestionProvider, DutyCommandSourceStack {

    @Shadow @Nullable
    public abstract ServerPlayer getPlayer();

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract ServerLevel getLevel();

    @Override
    public DutyServerLevel duty$getLevel() {
        return (DutyServerLevel) getLevel();
    }

    @Override
    public Position duty$getPosition() {
        if (getPlayer() instanceof DutyServerPlayer serverPlayer) {
            return serverPlayer.duty$getPosition();
        }
        Vec3 vec3 = getPosition();
        return new Position(vec3.x, vec3.y, vec3.z, 0, 0, duty$getLevel().duty$getDimension());
    }

    @Override
    public EssentialsLevelData duty$getLevelData() {
        return duty$getOverworld().duty$getLevelData();
    }

    @Override
    public DutyServerLevel duty$getOverworld() {
        return (DutyServerLevel) getLevel().getServer().getLevel(ServerLevel.OVERWORLD);
    }
}
