/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
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
package net.dutymod.client.mixin.ifast.map_atlas_generation;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.dutymod.client.ifast.feature.map_atlas_generation.MapAtlasTexture;
import net.dutymod.client.ifast.injection.interfaces.IMapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(MapTextureManager.class)
public abstract class MixinMapTextureManager implements IMapTextureManager {

    @Unique
    private final Int2ObjectMap<MapAtlasTexture> duty$mapAtlasTextures = new Int2ObjectOpenHashMap<>();

    @Unique
    private final Int2IntMap duty$mapIdToAtlasMapping = new Int2IntOpenHashMap();

    @Inject(method = "resetData", at = @At("RETURN"))
    private void clearMapAtlas(final CallbackInfo ci) {
        for (MapAtlasTexture texture : this.duty$mapAtlasTextures.values()) {
            texture.close();
        }

        this.duty$mapAtlasTextures.clear();
        this.duty$mapIdToAtlasMapping.clear();
    }

    @Inject(method = "getOrCreateMapInstance", at = @At("HEAD"))
    private void createMapAtlasTexture(MapId mapId, MapItemSavedData data, CallbackInfoReturnable<MapTextureManager.MapInstance> cir) {
        this.duty$mapIdToAtlasMapping.computeIfAbsent(mapId.id(), k -> {
            for (MapAtlasTexture atlasTexture : this.duty$mapAtlasTextures.values()) {
                final int location = atlasTexture.getNextMapLocation();
                if (location != -1) {
                    return location;
                }
            }

            final MapAtlasTexture atlasTexture = new MapAtlasTexture(this.duty$mapAtlasTextures.size());
            this.duty$mapAtlasTextures.put(atlasTexture.getId(), atlasTexture);
            return atlasTexture.getNextMapLocation();
        });
    }

    @Override
    public MapAtlasTexture duty$getMapAtlasTexture(final int id) {
        return this.duty$mapAtlasTextures.get(id);
    }

    @Override
    public int duty$getAtlasMapping(final int mapId) {
        return this.duty$mapIdToAtlasMapping.getOrDefault(mapId, -1);
    }

    @Override
    public Collection<MapAtlasTexture> duty$getAllMapAtlasTextures() {
        return this.duty$mapAtlasTextures.values();
    }

}
