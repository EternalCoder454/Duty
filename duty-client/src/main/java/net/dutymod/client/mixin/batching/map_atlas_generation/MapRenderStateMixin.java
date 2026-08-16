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
package net.dutymod.client.mixin.batching.map_atlas_generation;

import net.minecraft.client.renderer.state.MapRenderState;
import net.dutymod.client.batching.feature.map_atlas_generation.MapAtlasTexture;
import net.dutymod.client.batching.injection.interfaces.IMapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MapRenderState.class)
public abstract class MapRenderStateMixin implements IMapRenderState {

    @Unique
    private int duty$atlasX;

    @Unique
    private int duty$atlasY;

    @Unique
    private MapAtlasTexture duty$atlasTexture;

    @Override
    public int duty$getAtlasX() {
        return this.duty$atlasX;
    }

    @Override
    public void duty$setAtlasX(final int x) {
        this.duty$atlasX = x;
    }

    @Override
    public int duty$getAtlasY() {
        return this.duty$atlasY;
    }

    @Override
    public void duty$setAtlasY(final int y) {
        this.duty$atlasY = y;
    }

    @Override
    public MapAtlasTexture duty$getAtlasTexture() {
        return this.duty$atlasTexture;
    }

    @Override
    public void duty$setAtlasTexture(final MapAtlasTexture atlasTexture) {
        this.duty$atlasTexture = atlasTexture;
    }

}
