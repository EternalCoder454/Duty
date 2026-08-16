package net.dutymod.server.net;

/**
 * The points at which Duty rebuilds part of a connection's Netty pipeline.
 *
 * <p>Compression and encryption are both installed after the connection is already live -- the
 * server tells the client to start compressing, and enciphering begins partway through login --
 * so the handlers have to be swapped in on a running pipeline. Naming the transitions keeps the
 * two mixins that perform them readable, and makes a mis-ordered swap obvious in a stack trace.
 */
public enum PipelineEvent {
    COMPRESSION_ENABLED,
    COMPRESSION_THRESHOLD_UPDATED,
    COMPRESSION_DISABLED,
    ENCRYPTION_ENABLED,
}
