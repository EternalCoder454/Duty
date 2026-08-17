package net.dutymod.essentials.command.teleportation.level;

import java.util.concurrent.ThreadLocalRandom;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.model.Position;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class RTPCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "rtp", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.rtp", PermissionLevel.ALL))
                .executes(context -> rtp(context.getSource())));
    }

    private int rtp(CommandSourceStack source) {
        if (!(source.getPlayer() instanceof DutyServerPlayer serverPlayer)) {
            source.sendFailure(NEEDS_PLAYER_ERROR);
            return 0;
        }

        if (!DutyEssentials.API.hasPermission(source, "command.rtp.bypass_cooldown")) {
            long lastRTP = serverPlayer.duty$getLastRTPTime();
            long currentTime = System.currentTimeMillis();
            long cooldown = EssentialsOptions.rtpCooldown.get() * 1000L;

            if (currentTime - lastRTP < cooldown) {
                long remaining = (cooldown - (currentTime - lastRTP)) / 1000;
                serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.rtp.cooldown", DutyEssentials.coloredFailure(String.valueOf(remaining))));
                return 0;
            }
        }

        ServerLevel level = (ServerLevel) serverPlayer.duty$getLevel();
        int minRadius = EssentialsOptions.rtpMinRadius.get();
        int maxRadius = EssentialsOptions.rtpMaxRadius.get();

        serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.rtp.searching"), false);

        attemptRtp(serverPlayer, level, minRadius, maxRadius, 15);

        return 1;
    }

    private void attemptRtp(DutyServerPlayer serverPlayer, ServerLevel level, int minRadius, int maxRadius, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.rtp.failed"));
            return;
        }

        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = ThreadLocalRandom.current().nextDouble(minRadius, maxRadius);
        int x = (int) (Math.cos(angle) * distance);
        int z = (int) (Math.sin(angle) * distance);

        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        ServerChunkCache chunkSource = level.getChunkSource();

        chunkSource.addTicketAndLoadWithRadius(TicketType.PORTAL, chunkPos, 2).thenAccept(result -> {
            level.getServer().execute(() -> {
                // If player disconnected while waiting, stop.
                if (((ServerPlayer) serverPlayer).isRemoved()) return;

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos targetPos = new BlockPos(x, y, z);
                BlockState blockState = level.getBlockState(targetPos.below());

                if (isSafe(blockState, level, targetPos)) {
                    int delay = EssentialsOptions.rtpDelay.get();
                    serverPlayer.duty$scheduleTeleport(new Position(
                            x + 0.5,
                            y + 0.5,
                            z + 0.5,
                            serverPlayer.duty$getPosition().yaw,
                            serverPlayer.duty$getPosition().pitch,
                            level.dimension().identifier()
                    ), delay, null, 0, (player) -> {
                        player.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.rtp.success", x, y, z), false);
                        player.duty$setLastRTPTime(System.currentTimeMillis());
                    });
                } else {
                    attemptRtp(serverPlayer, level, minRadius, maxRadius, attemptsLeft - 1);
                }
            });
        }).exceptionally(e -> {
            DutyEssentials.API.error("Error loading chunk for RTP command", e);
            level.getServer().execute(() ->
                    attemptRtp(serverPlayer, level, minRadius, maxRadius, attemptsLeft - 1)
            );
            return null;
        });
    }

    private boolean isSafe(BlockState ground, ServerLevel level, BlockPos pos) {
        if (ground.isAir() || ground.is(BlockTags.FIRE) || ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)) {
            return false;
        }
        if (!ground.getFluidState().isEmpty()) {
            return false;
        }
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }
}