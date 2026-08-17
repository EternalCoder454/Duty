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

/** {@code /flyspeed <1-10> [targets]} -- multiplies flying speed. 1 is vanilla. */
public class FlySpeedCommand extends SpeedCommand {
    /** Vanilla's {@code Abilities} constructor value. */
    private static final float VANILLA_FLYING_SPEED = 0.05F;

    @Override
    protected String kind() {
        return "fly";
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
                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, MAX_MULTIPLIER))
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
