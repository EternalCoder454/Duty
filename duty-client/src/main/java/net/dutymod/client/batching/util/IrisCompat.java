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
package net.dutymod.client.batching.util;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.lenni0451.reflect.accessor.FieldAccessor;
import net.lenni0451.reflect.stream.RStream;
import net.dutymod.client.batching.Batching;

import java.util.function.BooleanSupplier;

public class IrisCompat {

    public static boolean IRIS_LOADED = false;

    public static BooleanSupplier isRenderingLevel;
    public static BooleanConsumer renderWithExtendedVertexFormat;
    public static ThreadLocal<Boolean> skipExtension;

    /**
     * Wires up the three Iris fields the batching code needs.
     *
     * <p>Upstream called {@code System.exit(-1)} when this failed, which kills the game process
     * outright -- no crash report, no error screen, the window simply gone -- because an optional
     * mod moved a field. Duty's first rule is that it works alongside other mods, so a failure
     * here disables the Iris path and leaves everything else running.
     *
     * <p>The flag is set last for the same reason. Every consumer reads these three fields only
     * behind {@code IRIS_LOADED}, so setting it first meant a failure left the flag true and the
     * fields null, and the exit was the only thing standing between that and a
     * {@code NullPointerException} on the render path.
     */
    public static void init() {
        try {
            final Class<?> immediateStateClass = Class.forName("net.irisshaders.iris.vertices.ImmediateState");

            isRenderingLevel = FieldAccessor.makeGetter(BooleanSupplier.class, null, immediateStateClass.getDeclaredField("isRenderingLevel"));
            renderWithExtendedVertexFormat = FieldAccessor.makeSetter(BooleanConsumer.class, null, immediateStateClass.getDeclaredField("renderWithExtendedVertexFormat"));
            skipExtension = RStream.of(immediateStateClass).fields().by("skipExtension").get();
            IRIS_LOADED = true;
        } catch (Throwable t) {
            IRIS_LOADED = false;
            isRenderingLevel = null;
            renderWithExtendedVertexFormat = null;
            skipExtension = null;
            Batching.LOGGER.error("Could not wire up Iris compatibility, so batching will run without it."
                    + " Update Iris if this shows up as wrong geometry under shaders.", t);
        }
    }

}
