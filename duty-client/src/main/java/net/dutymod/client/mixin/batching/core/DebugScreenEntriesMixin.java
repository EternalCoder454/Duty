/*
 * This file is part of Batching - https://github.com/RaphiMC/Batching
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.dutymod.client.mixin.batching.core;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.ResourceLocation;
import net.dutymod.client.batching.feature.core.BatchingDebugScreenEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {

    @Shadow
    @Final
    @Mutable
    public static Map<DebugScreenProfile, Map<ResourceLocation, DebugScreenEntryStatus>> PROFILES;

    @Shadow
    private static ResourceLocation register(ResourceLocation name, DebugScreenEntry entry) {
        return null;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void addBatchingEntry(CallbackInfo ci) {
        final ResourceLocation entryId = register(BatchingDebugScreenEntry.ENTRY_ID, new BatchingDebugScreenEntry());
        final Map<DebugScreenProfile, Map<ResourceLocation, DebugScreenEntryStatus>> profiles = new HashMap<>();
        for (Map.Entry<DebugScreenProfile, Map<ResourceLocation, DebugScreenEntryStatus>> entry : PROFILES.entrySet()) {
            final Map<ResourceLocation, DebugScreenEntryStatus> entries = new HashMap<>(entry.getValue());
            entries.put(entryId, DebugScreenEntryStatus.IN_OVERLAY);
            profiles.put(entry.getKey(), entries);
        }
        PROFILES = profiles;
    }

}
