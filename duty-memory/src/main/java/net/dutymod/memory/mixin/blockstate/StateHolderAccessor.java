package net.dutymod.memory.mixin.blockstate;

import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the private property list a state was built from, needed to size the shared table. */
@Mixin(StateHolder.class)
public interface StateHolderAccessor {
    @Accessor("propertyKeys")
    Property<?>[] duty$getPropertyKeys();
}
