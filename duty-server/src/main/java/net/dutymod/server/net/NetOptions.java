package net.dutymod.server.net;

import net.dutymod.framework.DutyConfig;

/**
 * Every toggle Duty's network pipeline owns.
 *
 * <p>Upstream (KryptonReno) backed these with Sewlia-config and a generated LibSL options screen,
 * which made the jar depend on two libraries at runtime purely to store a handful of numbers.
 * Duty already has a config file, so both dependencies are dropped and the values are read from
 * that instead -- the same substitution made for Quiet's YACL config.
 *
 * <p>Defaults are upstream's, deliberately: compression level 4, oversized packets rejected, and
 * wide VarInts rejected. The two "permit" options exist for talking to servers that violate the
 * protocol, and turning either on weakens a bounds check, so neither is on by default.
 */
public final class NetOptions {
    // -- Compression ---------------------------------------------------------------------------

    /** Master switch for native (libdeflate) compression. */
    public static final String NATIVE_COMPRESSION = "server.native_compression";

    /** zlib compression level, 1 (fastest) to 9 (smallest). */
    public static final String COMPRESSION_LEVEL = "server.compression_level";

    /** Accept compressed packets that inflate to more than the protocol's size limit. */
    public static final String PERMIT_OVERSIZED_PACKETS = "server.permit_oversized_packets";

    // -- Encryption ----------------------------------------------------------------------------

    /** Master switch for native (OpenSSL) AES on the connection cipher. */
    public static final String NATIVE_ENCRYPTION = "server.native_encryption";

    /** Apply the native cipher to the client's own outbound connection as well. */
    public static final String CLIENT_ENCRYPTION = "server.client_encryption";

    // -- Codec ---------------------------------------------------------------------------------

    /** Accept VarInts encoded in more bytes than they need. */
    public static final String ALLOW_WIDE_VARINT = "server.allow_wide_varint";

    /** Use the branch-free VarLong writer. */
    public static final String FAST_VARLONG = "server.fast_varlong";

    /** Halve the concurrent-queue operations when draining queued packets on the main thread. */
    public static final String PACKET_PROCESSOR_OPT = "server.packet_processor_opt";

    static {
        DutyConfig.register(NATIVE_COMPRESSION, true,
                "Compress packets with libdeflate instead of java.util.zip.Deflater. Vanilla\n"
                        + "deflates through a heap byte[] and copies every packet in and out of it;\n"
                        + "the native path works on the direct buffer Netty already holds. Falls\n"
                        + "back to a Java implementation automatically if the native cannot load,\n"
                        + "so turning this off is only useful for isolating a suspected fault.");
        DutyConfig.register(COMPRESSION_LEVEL, 4,
                "zlib compression level, 1 (fastest) to 9 (smallest). 4 is upstream's default and\n"
                        + "is a better trade for a local server than vanilla's 6: the packets are\n"
                        + "slightly larger but never leave the machine.");
        DutyConfig.register(PERMIT_OVERSIZED_PACKETS, false,
                "Accept compressed packets that inflate past the protocol's size limit. This\n"
                        + "relaxes a bounds check that exists to stop a malicious peer from making\n"
                        + "the client allocate unbounded memory. Only enable it for a specific\n"
                        + "server that needs it.");

        DutyConfig.register(NATIVE_ENCRYPTION, true,
                "Encipher packets with OpenSSL AES instead of javax.crypto.Cipher. Vanilla copies\n"
                        + "each packet through two heap byte[] buffers; the native path enciphers\n"
                        + "the direct buffer in place. Only does anything on an encrypted (online\n"
                        + "multiplayer) connection -- singleplayer traffic is never enciphered.");
        DutyConfig.register(CLIENT_ENCRYPTION, true,
                "Also use the native cipher for this client's own outbound connection, not just\n"
                        + "for connections a hosted server accepts.");

        DutyConfig.register(ALLOW_WIDE_VARINT, false,
                "Accept VarInts padded out to more bytes than the value needs. Vanilla rejects\n"
                        + "these and so does Duty; a few proxies emit them. Enabling this weakens a\n"
                        + "malformed-packet check, so leave it off unless a server needs it.");
        DutyConfig.register(FAST_VARLONG, true,
                "Use the branch-free VarLong writer. Same output as vanilla, fewer branches.");
        DutyConfig.register(PACKET_PROCESSOR_OPT, true,
                "Halve the concurrent-queue operations performed when draining queued packets on\n"
                        + "the main thread.");
    }

    private NetOptions() {}

    /** Forces the static initializer above to run. */
    public static void init() {}

    // -- Typed accessors -----------------------------------------------------------------------
    //
    // These are read from Netty pipeline callbacks, which run per packet. DutyConfig.get is
    // synchronized, so the hot paths (compression level, oversized packets, wide VarInt) read
    // through the cached fields below rather than hitting the lock on every packet.

    // The latch is volatile because these are read from several Netty threads and written by
    // whichever one arrives first. Without it a thread can see loaded == true while the three
    // fields below still hold their defaults, and a compressionLevel read as zero is no
    // compression at all -- silently, and only when the race happens to land. Writing the latch
    // last is not enough on its own; the volatile write is what publishes the three before it.
    private static volatile boolean loaded;
    private static int compressionLevel;
    private static boolean permitOversizedPackets;
    private static boolean allowWideVarInt;

    private static void load() {
        if (loaded) {
            return;
        }
        init();
        compressionLevel = DutyConfig.getInt(COMPRESSION_LEVEL, 1, 9);
        permitOversizedPackets = DutyConfig.get(PERMIT_OVERSIZED_PACKETS);
        allowWideVarInt = DutyConfig.get(ALLOW_WIDE_VARINT);
        loaded = true;
    }

    public static int compressionLevel() {
        load();
        return compressionLevel;
    }

    public static boolean permitOversizedPackets() {
        load();
        return permitOversizedPackets;
    }

    public static boolean allowWideVarInt() {
        load();
        return allowWideVarInt;
    }
}
