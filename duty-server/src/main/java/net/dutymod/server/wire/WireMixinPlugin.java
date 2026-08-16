package net.dutymod.server.wire;

import net.dutymod.core.DutyLog;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Alternate Current mixins only when Duty is configured to own redstone dust.
 *
 * <p>Gating here rather than inside the mixins is the whole point. Alternate Current and Lithium
 * both patch {@code RedStoneWireBlock.affectNeighborsAfterRemoval}; a runtime {@code if} would
 * still put Duty's injection on that method and leave the two implementations layered on each
 * other. Refusing to apply the mixin at all leaves the class exactly as Lithium expects it, so the
 * disabled state is genuinely "Duty is not here" rather than "Duty is here but idle".
 *
 * <p>All four mixins are gated together. They are one implementation: {@code ServerLevelMixin}
 * creates the wire handler, {@code MinecraftServerMixin} saves its config,
 * {@code RedStoneWireBlockMixin} routes updates into it and {@code ExperimentalRedstoneUtilsMixin}
 * supplies orientation. Applying any subset would leave the handler half-installed.
 */
public final class WireMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        RedstoneWire.init();
        this.enabled = RedstoneWire.enabled();
        if (this.enabled) {
            DutyLog.info("Alternate Current is enabled; Duty owns redstone dust. Lithium's "
                    + "mixin.block.redstone_wire must be false or the two will fight over "
                    + "RedStoneWireBlock.affectNeighborsAfterRemoval.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
