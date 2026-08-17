package net.dutymod.essentials.command.player;

import com.mojang.brigadier.CommandDispatcher;
import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.level.GameType;

import java.util.Collections;

/**
 * One-word gamemode switches: {@code /gmc}, {@code /gms}, {@code /gma}, {@code /gmsp}.
 *
 * <p>Vanilla makes you write out {@code /gamemode adventure}, and even with tab completion that is
 * a lot of keystrokes for something done constantly while building. These are four literals that
 * skip the argument entirely.
 *
 * <p>They are not aliases of {@code /gamemode}. An alias in {@link CommandManager} redirects to the
 * same node and would still expect the gamemode argument -- {@code /gmc creative} -- which defeats
 * the point. Each of these is its own command with the mode already chosen.
 *
 * <p>The behaviour itself is {@link GamemodeCommand#setMode}, called directly rather than copied,
 * so the messages, the command-feedback game rule and the "other player" wording stay in one place.
 */
public class GameModeShortcutCommand implements Command {
    private final String literal;
    private final GameType gameType;

    public GameModeShortcutCommand(String literal, GameType gameType) {
        this.literal = literal;
        this.gameType = gameType;
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, literal, builder -> builder
                // Same permission node as /gamemode: these are the same act, spelled shorter.
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.gamemode"))
                .executes(context -> GamemodeCommand.setMode(
                        context,
                        Collections.singleton(context.getSource().getPlayerOrException()),
                        gameType))
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(context -> GamemodeCommand.setMode(
                                context,
                                EntityArgument.getPlayers(context, "target"),
                                gameType))));
    }
}
