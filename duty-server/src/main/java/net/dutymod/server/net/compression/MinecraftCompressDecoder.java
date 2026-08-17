package net.dutymod.server.net.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.dutymod.server.net.NetOptions;
import net.dutymod.server.net.util.VarIntUtil;

import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;

/**
 * Decompresses a Minecraft packet.
 */
public class MinecraftCompressDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024;
    private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024;

    private static final int UNCOMPRESSED_CAP =
            NetOptions.permitOversizedPackets()
                    ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

    private final VelocityCompressor compressor;
    private final boolean validate;
    private int threshold;


    public MinecraftCompressDecoder(int threshold, boolean validate, VelocityCompressor compressor) {
        this.threshold = threshold;
        this.compressor = compressor;
        this.validate = validate;
    }

    private static final net.dutymod.framework.DutyMetrics.Timer DECODE =
            net.dutymod.framework.DutyMetrics.timer("server.net.decompress");

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final long duty$started = DECODE.begin();
        try {
            duty$decode(ctx, in, out);
        } finally {
            DECODE.end(duty$started);
        }
    }

    private void duty$decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int claimedUncompressedSize = VarIntUtil.readVarInt(in);

        if (claimedUncompressedSize == 0) {
            int actualUncompressedSize = in.readableBytes();
            checkState(actualUncompressedSize < threshold, "Actual uncompressed size %s is greater than"
                    + " threshold %s", actualUncompressedSize, threshold);
            out.add(in.retain());
            return;
        }

        if (claimedUncompressedSize > HARD_MAXIMUM_UNCOMPRESSED_SIZE) {
            throw new DecoderException("Uncompressed size " + claimedUncompressedSize + " exceeds hard maximum size of " + HARD_MAXIMUM_UNCOMPRESSED_SIZE);
        }

        if (validate) {
            checkState(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
                    + " threshold %s", claimedUncompressedSize, threshold);
            checkState(claimedUncompressedSize <= UNCOMPRESSED_CAP,
                    "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
                    UNCOMPRESSED_CAP);
        }

        decompress(compressor, ctx, in, out, claimedUncompressedSize);
    }

    private void decompress(VelocityCompressor compressor, ChannelHandlerContext ctx, ByteBuf in, List<Object> out,
                            int claimedUncompressedSize) throws Exception {
        ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
        ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
        try {
            compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
            out.add(uncompressed);
        } catch (Exception e) {
            uncompressed.release();
            throw e;
        } finally {
            compatibleIn.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        compressor.close();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
