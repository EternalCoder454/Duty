package net.dutymod.fixerupper.registry;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;

public class LifecycleMap<T> extends Reference2ReferenceOpenHashMap<ResourceKey<T>, RegistrationInfo> {
    public LifecycleMap() {
        this.defaultReturnValue(RegistrationInfo.BUILT_IN);
    }

    @Override
    public RegistrationInfo put(ResourceKey<T> t, RegistrationInfo lifecycle) {
        if(lifecycle != defRetValue)
            return super.put(t, lifecycle);
        else {
            // fastutil's get returns defRetValue for a missing key rather than null, which is why
            // upstream guarded with containsKey. getOrDefault expresses the same thing in one
            // lookup instead of hashing the key twice.
            return super.getOrDefault(t, null);
        }
    }
}
