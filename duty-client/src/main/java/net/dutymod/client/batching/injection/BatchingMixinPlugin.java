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
package net.dutymod.client.batching.injection;

import net.dutymod.client.batching.Batching;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class BatchingMixinPlugin implements IMixinConfigPlugin {

    private String mixinPackage;

    @Override
    public void onLoad(final String mixinPackage) {
        this.mixinPackage = mixinPackage + ".";

        Batching.earlyInit();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!mixinClassName.startsWith(this.mixinPackage)) {
            return false;
        }

        final String mixinName = mixinClassName.substring(this.mixinPackage.length());
        final String packageName = mixinName.substring(0, mixinName.lastIndexOf('.'));

        if (!Batching.config.enhanced_batching && packageName.startsWith("enhanced_batching")) {
            return false;
        }
        if (!Batching.config.font_atlas_resizing && packageName.startsWith("font_atlas_resizing")) {
            return false;
        }
        if (!Batching.config.map_atlas_generation && packageName.startsWith("map_atlas_generation")) {
            return false;
        }
        if (!Batching.config.skip_text_translucency_sorting && packageName.startsWith("skip_text_translucency_sorting")) {
            return false;
        }
        if (!Batching.config.fast_text_lookup && packageName.startsWith("fast_text_lookup")) {
            return false;
        }
        if (!Batching.config.avoid_redundant_framebuffer_switching && packageName.startsWith("avoid_redundant_framebuffer_switching")) {
            return false;
        }
        if (!Batching.config.fix_slow_buffer_upload_on_apple_gpu && packageName.startsWith("fix_slow_buffer_upload_on_apple_gpu")) {
            return false;
        }
        if (Batching.config.experimental_disable_resource_pack_conflict_handling && packageName.startsWith("resource_pack_conflict_handling")) {
            return false;
        }
        if (!Batching.config.experimental_sign_text_buffering && packageName.startsWith("sign_text_buffering")) {
            return false;
        }
        if (!Batching.config.debug_only_print_additional_error_information && packageName.startsWith("print_additional_error_information")) {
            return false;
        }

        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

}
