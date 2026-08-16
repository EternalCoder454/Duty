package net.dutymod.fixerupper.common.mixin.bugfix.recipe_book_type_desync;

import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mixin(RecipeBookSettings.class)
@ClientOnlyMixin
public class RecipeBookSettingsMixin {
    private static int duty$maxVanillaOrdinal;

    static {
        int ord = 0;
        for(Field f : RecipeBookType.class.getDeclaredFields()) {
            if(RecipeBookType.class.isAssignableFrom(f.getType()) && Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    RecipeBookType type = (RecipeBookType)f.get(null);
                    ord = Math.max(type.ordinal(), ord);
                } catch(Exception e) {
                    e.printStackTrace();
                    ord = Integer.MAX_VALUE - 1;
                    break;
                }
            }
        }
        duty$maxVanillaOrdinal = ord;
    }
    /*
    // require = 0: RecipeBookSettings.read is gone in 26.1.2, replaced by STREAM_CODEC /
    // MAP_CODEC. Without this the missing target is a hard crash at mixin apply. Kept rather
    // than deleted so the fix returns if the method ever comes back.
    @Redirect(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readBoolean()Z"), require = 0)
    private static boolean useDefaultBooleanIfVanilla(FriendlyByteBuf buf, @Local(ordinal = 0) RecipeBookType type) {
        if(type.ordinal() >= (duty$maxVanillaOrdinal + 1)) {
            FixerUpper.LOGGER.warn("Not reading recipe book data for type '{}' as we are using vanilla connection", type.name());
            return false; // skip actually reading buffer
        }
        return buf.readBoolean();
    }
    */
}
