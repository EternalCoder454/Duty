package net.dutymod.fixerupper.common.mixin.perf.ticking_chunk_alloc;

import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.time.LocalDate;

@Mixin(value = Bat.class, priority = 1200)
public class BatMixin {
    private static long duty$lastQueriedTime = -1L;
    private static LocalDate duty$lastQueriedDate = null;

    /**
     * @author embeddedt
     * @reason avoid excessive allocations from continuously querying the date, only get a new date once every 30 seconds
     */
    @Redirect(method = "isHalloween", at = @At(value = "INVOKE", target = "Ljava/time/LocalDate;now()Ljava/time/LocalDate;"), require = 0)
    private static LocalDate useCachedLocalDate() {
        LocalDate date = duty$lastQueriedDate;
        if(date == null || Math.abs(System.currentTimeMillis() - duty$lastQueriedTime) > 30000) {
            duty$lastQueriedDate = date = LocalDate.now();
            duty$lastQueriedTime = System.currentTimeMillis();
        }
        return date;
    }
}
