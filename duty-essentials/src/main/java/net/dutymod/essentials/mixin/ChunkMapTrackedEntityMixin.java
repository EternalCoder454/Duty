package net.dutymod.essentials.mixin;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    public void updatePlayer(ServerPlayer player, CallbackInfo ci) {
        if (this.entity instanceof DutyServerPlayer serverPlayer && serverPlayer.duty$isVanished()) {
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                ci.cancel();
            }
        }
    }
}