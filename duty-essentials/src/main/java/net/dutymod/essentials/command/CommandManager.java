package net.dutymod.essentials.command;

import java.util.function.UnaryOperator;

import net.dutymod.essentials.config.CommandConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class CommandManager {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String commandName, UnaryOperator<LiteralArgumentBuilder<CommandSourceStack>> builderConsumer) {
        CommandConfig.CommandEntry config = CommandConfig.COMMANDS.get(commandName);
        if (config == null || config.enabled().get()) {
            LiteralArgumentBuilder<CommandSourceStack> commandBuilder = builderConsumer.apply(Commands.literal(commandName));
            dispatcher.register(commandBuilder);

            if (config != null) {
                for (String alias : config.aliases().get()) {
                    dispatcher.register(Commands.literal(alias)
                            .redirect(dispatcher.getRoot().getChild(commandName).getRedirect() != null ? dispatcher.getRoot().getChild(commandName).getRedirect() : dispatcher.getRoot().getChild(commandName))
                            .executes(commandBuilder.getCommand())
                            .requires(commandBuilder.getRequirement())
                    );
                }
            }
        }
    }
}
