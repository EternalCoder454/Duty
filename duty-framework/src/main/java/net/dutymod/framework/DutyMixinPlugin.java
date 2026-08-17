package net.dutymod.framework;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The parts of {@link IMixinConfigPlugin} nobody wants to write.
 *
 * <p>The interface has seven methods. Duty's eight config plugins between them use three: deciding
 * whether a mixin applies, doing some setup on load, and -- in exactly one case -- running an ASM
 * pass afterwards. The other four were implemented empty eight times over, which is around forty
 * methods of nothing.
 *
 * <p>Extending this leaves each plugin containing only its actual decision. That is worth more than
 * the lines saved: a plugin that is four lines long is one you can check at a glance, and the empty
 * overrides were the kind of noise a real change hides in.
 *
 * <p>Defaults are vanilla-mixin behaviour: apply everything, contribute no extra mixins, transform
 * nothing.
 */
public abstract class DutyMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        // The earliest point every Duty module reliably reaches, and GC monitoring wants to be
        // running before the first world load rather than from whenever a report is asked for.
        // install() is idempotent, so every module calling it costs one compareAndSet.
        DutyGc.install();
        DutyReport.install();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /** Applies every mixin in the config. Override to gate on a mod, an option or a version. */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    /** Contributes no mixins beyond those the config lists. */
    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
