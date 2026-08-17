package net.dutymod.essentials.command.player;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;

/**
 * {@code /flyspeed <1-20> [targets]} -- multiplies flying speed. 1 is vanilla.
 *
 * <h2>Why the ceiling is 20, and what happens near it</h2>
 *
 * <p>The server checks how far a player moved between move packets and teleports them back if it
 * looks impossible: {@code shouldCheckPlayerMovement} allows a squared distance of 100 per tick,
 * so about 10 blocks a tick. Creative flight is roughly 0.55 blocks a tick, which puts 20x at
 * around 11 -- just over the line.
 *
 * <p>That matters less than it sounds, because the same method returns early for
 * {@code isSingleplayerOwner()}: the host of a single-player or LAN world is exempt from the check
 * entirely, and can use the whole range. A player <em>connected to</em> a dedicated server is not,
 * and may see rubber-banding in the upper part of it. Elytra flight is allowed 300 instead of 100,
 * so it has far more headroom.
 *
 * <p>The cap is 20 rather than something larger because past this the check stops being the
 * limiting factor and chunk loading starts to be.
 */
public class FlySpeedCommand extends SpeedCommand {
    /** Vanilla's {@code Abilities} constructor value. */
    private static final float VANILLA_FLYING_SPEED = 0.05F;

    @Override
    protected String kind() {
        return "fly";
    }

    @Override
    protected int maxMultiplier() {
        return 20;
    }

    @Override
    protected void apply(ServerPlayer player, int multiplier) {
        player.getAbilities().setFlyingSpeed(VANILLA_FLYING_SPEED * multiplier);
        // Sends ClientboundPlayerAbilitiesPacket. Without this the server would be the only side
        // that knew, and the client -- which is what actually moves the player -- would not.
        player.onUpdateAbilities();
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "flyspeed", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.flyspeed"))
                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, maxMultiplier()))
                        .executes(context -> setSpeed(
                                context.getSource(),
                                Collections.singleton(context.getSource().getPlayerOrException()),
                                IntegerArgumentType.getInteger(context, "multiplier")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> setSpeed(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        IntegerArgumentType.getInteger(context, "multiplier"))))));
    }
}
