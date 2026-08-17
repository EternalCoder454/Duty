package net.dutymod.essentials.command.inventory;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

public class InvseeCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "invsee", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.invsee"))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer viewer) {
                                ((ServerPlayer) viewer).openMenu(new SimpleMenuProvider(
                                        (id, inventory, p) -> ChestMenu.sixRows(id, inventory, target.getInventory()),
                                        target.getDisplayName()
                                ));
                                return 1;
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        })));
    }
}