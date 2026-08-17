package net.dutymod.essentials.command.player;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collections;

/**
 * {@code /walkspeed <1-10> [targets]} -- multiplies walking speed. 1 is vanilla.
 *
 * <p>Goes through the movement-speed attribute rather than {@code Abilities.walkingSpeed}; see
 * {@link SpeedCommand} for why that field would only have changed the player's field of view.
 */
public class WalkSpeedCommand extends SpeedCommand {
    /**
     * The modifier Duty owns on the movement-speed attribute.
     *
     * <p>A stable id is what makes this repeatable: {@code addOrReplacePermanentModifier} replaces
     * any modifier already registered under it, so running the command ten times leaves one
     * modifier rather than ten stacked ones.
     */
    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DutyEssentials.MOD_ID, "walk_speed");

    @Override
    protected String kind() {
        return "walk";
    }

    @Override
    protected void apply(ServerPlayer player, int multiplier) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        // Always clear first, so dropping back to 1 leaves the attribute exactly as vanilla had it
        // rather than carrying a modifier that happens to add nothing.
        attribute.removeModifier(MODIFIER_ID);
        if (multiplier <= 1) {
            return;
        }
        // ADD_MULTIPLIED_BASE takes a fraction of the base value, so a multiplier of 3 is +2.
        // Permanent rather than transient: permanent modifiers are written to the player's saved
        // data, which is what carries the setting across a relog without Duty storing anything.
        attribute.addOrReplacePermanentModifier(new AttributeModifier(
                MODIFIER_ID, multiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "walkspeed", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.walkspeed"))
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
