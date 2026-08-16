package net.dutymod.server.mixin.net.pipeline;

import io.netty.channel.ChannelOutboundHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.LocalFrameEncoder;
import net.dutymod.server.net.pipeline.MinecraftVarintPrepender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Connection.class)
public class PrependerConnectionMixin {
    /**
     * @author Andrew Steinborn
     * @reason replace Mojang prepender with a more efficient one
     */
    @Overwrite
    private static ChannelOutboundHandler createFrameEncoder(boolean local) {
        if (local) {
            return new LocalFrameEncoder();
        } else {
            return MinecraftVarintPrepender.INSTANCE;
        }
    }
}
