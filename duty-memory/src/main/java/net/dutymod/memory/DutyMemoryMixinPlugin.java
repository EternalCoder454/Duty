package net.dutymod.memory;

import net.dutymod.core.DutyConfig;
import net.dutymod.core.DutyLog;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gates Duty: Memory's mixins on the config.
 *
 * <p>A disabled option must mean the mixin is never applied, not that it is applied and then
 * checks a flag at runtime. Anything else leaves the patched bytecode in place, so "turn it off to
 * see if Duty is the problem" would not actually rule Duty out -- which defeats the point of having
 * the switch.
 */
public class DutyMemoryMixinPlugin implements IMixinConfigPlugin {

    /**
     * Mixin class name fragment to the config key that controls it.
     *
     * <p>Entries are matched with {@code contains}, so a package fragment gates every mixin in
     * that package as one unit. That is deliberate rather than convenient: a feature's accessors,
     * duck interfaces and injecting mixins have to apply together or not at all. Gating them
     * individually is what produced the {@code ClassCastException} during the EntityCulling port,
     * where the duck interface was skipped while code that cast to it still ran.
     *
     * <p>Order matters -- the first match wins -- so anything needing a narrower rule than its
     * enclosing package must be listed above that package.
     */
    private static final Map<String, String> GATES = new LinkedHashMap<>();

    static {
        GATES.put("mixin.blockstate.StateHolderMixin", MemoryOptions.BLOCK_STATE_DEDUPLICATION);
        GATES.put("mixin.blockstate.StateDefinitionMixin", MemoryOptions.BLOCK_STATE_DEDUPLICATION);
        GATES.put("mixin.blockstate.StateHolderAccessor", MemoryOptions.BLOCK_STATE_DEDUPLICATION);
        GATES.put("mixin.blockstatecache.", MemoryOptions.BLOCK_STATE_CACHE_DEDUPLICATION);
        GATES.put("mixin.datacomponents.", MemoryOptions.DATA_COMPONENT_DEDUPLICATION);
        GATES.put("mixin.holderset.", MemoryOptions.SMALL_HOLDER_SET_SCAN);
        GATES.put("mixin.tags.TagKeyMixin", MemoryOptions.TAG_KEY_INTERNING);
        GATES.put("mixin.tags.ResourceKeyMixin", MemoryOptions.TAG_KEY_INTERNING);
    }

    @Override
    public void onLoad(String mixinPackage) {
        MemoryOptions.init();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> gate : GATES.entrySet()) {
            if (mixinClassName.contains(gate.getKey())) {
                boolean enabled = DutyConfig.get(gate.getValue());
                if (!enabled) {
                    DutyLog.debug("Skipping " + mixinClassName + ": " + gate.getValue() + " is disabled.");
                }
                return enabled;
            }
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}
}
