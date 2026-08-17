package net.dutymod.essentials.command.inventory;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

public class EnderChestCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "enderchest", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.enderchest"))
                .executes(context -> openEnderChest(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(source -> DutyEssentials.API.hasPermission(source, "command.enderchest.others"))
                        .executes(context -> openEnderChest(context.getSource(), EntityArgument.getPlayer(context, "target")))));
    }

    private int openEnderChest(CommandSourceStack source, ServerPlayer target) {
        if (source.getPlayer() instanceof DutyServerPlayer viewer) {
            Component title = target == viewer ?
                    Component.translatable("container.enderchest") :
                    target.getDisplayName();

            ((ServerPlayer) viewer).openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> ChestMenu.threeRows(id, inventory, target.getEnderChestInventory()),
                    title
            ));
            return 1;
        }
        source.sendFailure(NEEDS_PLAYER_ERROR);
        return 0;
    }
}