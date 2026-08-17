package net.dutymod.essentials;

import net.dutymod.framework.DutyLog;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.utils.ChatFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;

/**
 * Duty: Essentials' shared surface -- message building and permission checks.
 *
 * <p>Upstream reaches all of this through its author's multiloader library. Duty targets one
 * loader, so the library is not shipped; what the forty command classes actually used from it came
 * to five methods, and they are on {@link Api} below. Keeping the {@code API} field means those
 * classes port across unchanged rather than being edited forty times.
 */
public final class DutyEssentials {
    public static final String MOD_ID = "duty_essentials";

    public static final Api API = new Api();

    /** The five methods the commands needed from upstream's platform layer. */
    public static final class Api {
        private Api() {}

        /** {@code translatable("commands.back")} becomes {@code duty_essentials.commands.back}. */
        public MutableComponent translatable(String key) {
            return Component.translatable(MOD_ID + "." + key);
        }

        public MutableComponent translatable(String key, Object... args) {
            return Component.translatable(MOD_ID + "." + key, args);
        }

        public ResourceLocation getId(String path) {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        }

        public void info(String message) {
            DutyLog.info(message);
        }

        public void error(String message) {
            DutyLog.warn(message);
        }

        /** The failure being reported is usually a chunk load, so the cause is the useful half. */
        public void error(String message, Throwable cause) {
            DutyLog.warn(message + ": " + cause);
        }

        /**
         * Whether {@code source} may run a command.
         *
         * <p>The {@code node} is upstream's permission-node string, kept in the signature because
         * every call site passes one and because a permission manager could route on it later.
         * Nothing here consumes it today: with no permission mod installed there is nothing to ask,
         * so the decision falls to the vanilla level, which is the honest answer rather than a
         * pretend one.
         */
        public boolean hasPermission(CommandSourceStack source, String node, PermissionLevel level) {
            Permission required = switch (level) {
                case ALL -> null;
                case MODERATORS -> Permissions.COMMANDS_MODERATOR;
                case GAMEMASTERS -> Permissions.COMMANDS_GAMEMASTER;
                case ADMINS -> Permissions.COMMANDS_ADMIN;
                case OWNERS -> Permissions.COMMANDS_OWNER;
            };
            return required == null || source.permissions().hasPermission(required);
        }

        /**
         * The operator-only form. Upstream's two-argument overload has no level, and every call
         * site that uses it is an administrative command -- {@code /broadcast}, {@code /delwarp},
         * {@code /nick.others} -- so it maps to the game-master level rather than to "anyone".
         */
        public boolean hasPermission(CommandSourceStack source, String node) {
            return hasPermission(source, node, PermissionLevel.GAMEMASTERS);
        }
    }

    private DutyEssentials() {}

    // -- Message building -----------------------------------------------------------------------

    public static MutableComponent prefixedTranslatable(String key) {
        return getPrefix().append(API.translatable(key));
    }

    public static MutableComponent prefixedTranslatable(String key, Object... args) {
        return getPrefix().append(API.translatable(key, args));
    }

    public static MutableComponent prefixedVanillaTranslatable(String key, Object... args) {
        return getPrefix().append(Component.translatable(key, args));
    }

    public static MutableComponent prefixedFailureTranslatable(String key) {
        return getFailurePrefix().append(API.translatable(key));
    }

    public static MutableComponent prefixedFailureTranslatable(String key, Object... args) {
        return getFailurePrefix().append(colored(API.translatable(key, args), 0xFF5555));
    }

    public static MutableComponent getPrefix(int color) {
        return colored(API.translatable("prefix.left_bracket"), 0xFFFFFF)
                .append(colored(
                        EssentialsOptions.prefix.get().isEmpty()
                                ? API.translatable("prefix.name")
                                : ChatFormatter.format(EssentialsOptions.prefix.get()),
                        color))
                .append(colored(API.translatable("prefix.right_bracket"), 0xFFFFFF))
                .append(API.translatable("prefix.space"));
    }

    public static MutableComponent getPrefix() {
        return getPrefix(EssentialsOptions.primaryColor.get());
    }

    public static MutableComponent getFailurePrefix() {
        return getPrefix(0xFF5555);
    }

    public static MutableComponent colored(String str) {
        return Component.literal(str).withStyle(style -> style.withColor(EssentialsOptions.primaryColor.get()));
    }

    public static MutableComponent colored(MutableComponent component) {
        return component.withStyle(style -> style.withColor(EssentialsOptions.primaryColor.get()));
    }

    public static MutableComponent colored(MutableComponent component, int color) {
        return component.withStyle(style -> style.withColor(color));
    }

    public static MutableComponent coloredFailure(String str) {
        return Component.literal(str).withStyle(style -> style.withColor(0xFFFFFF));
    }

    public static MutableComponent coloredLiteral(String str) {
        return Component.literal(str).withStyle(style -> style.withColor(EssentialsOptions.primaryColor.get()));
    }
}
