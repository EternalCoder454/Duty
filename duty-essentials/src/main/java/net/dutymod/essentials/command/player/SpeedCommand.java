package net.dutymod.essentials.command.player;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Shared half of {@code /flyspeed} and {@code /walkspeed}.
 *
 * <p>Both take a multiplier of vanilla rather than a raw speed. Vanilla's numbers are 0.05 for
 * flying and 0.1 for walking, which are not values anyone wants to type or reason about; "3" is.
 * A multiplier of 1 is exactly vanilla, and is how you undo the command.
 *
 * <h2>The two speeds work through different mechanisms, and that is not arbitrary</h2>
 *
 * <p>Flying speed is a real field: {@code Abilities.flyingSpeed} feeds
 * {@code LivingEntity.getFrictionInfluencedSpeed}, which is the movement calculation itself.
 * Setting it works.
 *
 * <p>Walking speed is not. {@code Abilities.walkingSpeed} exists, and setting it looks like it
 * ought to work, but in 26.1.2 the only things that read it are
 * {@code AbstractClientPlayer.getFieldOfViewModifier} and the save/load path -- so setting it
 * changes the player's field of view and nothing else. (It moves the player in Bukkit-derived
 * servers, which is where the idea that it works comes from.) Walking therefore goes through the
 * {@code minecraft:movement_speed} attribute, which is what actually governs how fast a player
 * moves.
 *
 * <p>Neither needs Duty to persist anything. {@code Abilities} is serialised by vanilla through
 * {@code Abilities.Packed}, and a <em>permanent</em> attribute modifier is written into the
 * player's saved data. Both survive a relog on their own.
 */
public abstract class SpeedCommand implements Command {
    /** The highest multiplier accepted. Ten times vanilla is already very fast. */
    public static final int MAX_MULTIPLIER = 10;

    /** Applies the multiplier to one player. */
    protected abstract void apply(ServerPlayer player, int multiplier);

    /** {@code "fly"} or {@code "walk"}, used to build the message keys. */
    protected abstract String kind();

    protected int setSpeed(CommandSourceStack source, Collection<ServerPlayer> targets, int multiplier) {
        for (ServerPlayer player : targets) {
            apply(player, multiplier);

            if (source.getEntity() == player) {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(
                            DutyEssentials.prefixedTranslatable("commands." + kind() + "speed.set", multiplier), false);
                } else {
                    source.sendSuccess(() -> DutyEssentials.prefixedTranslatable(
                            "commands." + kind() + "speed.set", multiplier), true);
                }
            } else {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(
                            DutyEssentials.prefixedTranslatable("commands." + kind() + "speed.set", multiplier), false);
                }
                source.sendSuccess(() -> DutyEssentials.prefixedTranslatable(
                        "commands." + kind() + "speed.other", player.getDisplayName(), multiplier), true);
            }
        }
        return targets.size();
    }
}
