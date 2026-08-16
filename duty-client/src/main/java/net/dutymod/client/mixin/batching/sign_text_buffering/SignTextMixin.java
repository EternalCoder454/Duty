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
package net.dutymod.client.mixin.batching.sign_text_buffering;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;
import net.dutymod.client.batching.injection.interfaces.ISignText;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Objects;

@Mixin(SignText.class)
public abstract class SignTextMixin implements ISignText {

    @Shadow
    @Final
    private Component[] messages;

    @Shadow
    @Final
    private Component[] filteredMessages;

    @Shadow
    @Final
    private DyeColor color;

    @Shadow
    @Final
    private boolean hasGlowingText;

    @Shadow
    @Nullable
    private FormattedCharSequence[] renderMessages;

    @Unique
    private boolean duty$shouldCache;

    @Unique
    private boolean duty$checkedShouldCache;

    @Unique
    private int duty$cachedHashCode;

    @Unique
    private boolean duty$calculatedHashCode;

    @Inject(method = "getRenderMessages", at = @At("RETURN"))
    private void checkShouldCache(CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        if (!this.duty$checkedShouldCache) {
            this.duty$checkedShouldCache = true;
            this.duty$shouldCache = true;
            for (FormattedCharSequence line : this.renderMessages) {
                if (!this.duty$shouldCache) {
                    break;
                }

                line.accept((index, style, codePoint) -> {
                    if (style.isObfuscated()) {
                        this.duty$shouldCache = false;
                        return false;
                    }

                    return true;
                });
            }
        }
    }

    @Inject(method = "getRenderMessages", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/entity/SignText;renderMessages:[Lnet/minecraft/util/FormattedCharSequence;", opcode = Opcodes.PUTFIELD))
    private void invalidateCache(CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        this.duty$shouldCache = false;
        this.duty$checkedShouldCache = false;
        this.duty$cachedHashCode = 0;
        this.duty$calculatedHashCode = false;
    }

    @Override
    public boolean duty$shouldCache() {
        return this.duty$shouldCache;
    }

    @Override
    public void duty$setShouldCache(final boolean shouldCache) {
        this.duty$shouldCache = shouldCache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SignTextMixin that = (SignTextMixin) o;
        return hasGlowingText == that.hasGlowingText && color == that.color && Arrays.equals(messages, that.messages) && Arrays.equals(filteredMessages, that.filteredMessages);
    }

    @Override
    public int hashCode() {
        if (!this.duty$calculatedHashCode) {
            this.duty$calculatedHashCode = true;
            int result = Objects.hash(color, hasGlowingText);
            result = 31 * result + Arrays.hashCode(messages);
            result = 31 * result + Arrays.hashCode(filteredMessages);
            this.duty$cachedHashCode = result;
        }

        return this.duty$cachedHashCode;
    }

}
