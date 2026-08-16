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
package net.dutymod.client.batching.feature.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.packs.metadata.MetadataSectionType;

import java.util.Collections;
import java.util.List;

public record BatchingResourcePackMetadata(List<String> compatibleFeatures) {

    public static final BatchingResourcePackMetadata DEFAULT = new BatchingResourcePackMetadata(Collections.emptyList());
    public static final Codec<BatchingResourcePackMetadata> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.listOf().fieldOf("compatible_features").forGetter(BatchingResourcePackMetadata::compatibleFeatures)
        ).apply(instance, BatchingResourcePackMetadata::new)
    );
    public static final MetadataSectionType<BatchingResourcePackMetadata> SERIALIZER = new MetadataSectionType<>("immediatelyfast", CODEC);

}
