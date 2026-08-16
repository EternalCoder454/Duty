package net.dutymod.client.bakedentities.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.dutymod.client.bakedentities.platform.PlatformHelper;

import java.util.List;
import java.util.Set;

public class ModCompatPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.sodium.")) {
            return PlatformHelper.isModLoaded("sodium") || PlatformHelper.isModLoaded("embeddium");
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode targetClass, String mixin, IMixinInfo mixinInfo) {}
    @Override public void postApply(String target, ClassNode targetClass, String mixin, IMixinInfo mixinInfo) {}
}