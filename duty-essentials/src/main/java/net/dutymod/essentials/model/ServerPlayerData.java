package net.dutymod.essentials.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record ServerPlayerData(
        List<Home> homes,
        Position lastPosition,
        boolean acceptsTPARequests,
        String nick,
        boolean hasGodMode,
        boolean vanished,
        long lastRTPTime,
        Map<String, Long> teleportCooldowns

) {
    public static final Codec<ServerPlayerData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Home.CODEC.listOf().fieldOf("Homes").forGetter(ServerPlayerData::homes),
                    Position.CODEC.fieldOf("LastPosition").forGetter(ServerPlayerData::lastPosition),
                    Codec.BOOL.fieldOf("AcceptsTPARequests").forGetter(ServerPlayerData::acceptsTPARequests),
                    Codec.STRING.fieldOf("Nick").forGetter(ServerPlayerData::nick),
                    Codec.BOOL.fieldOf("GodMode").forGetter(ServerPlayerData::hasGodMode),
                    Codec.BOOL.optionalFieldOf("Vanished", false).forGetter(ServerPlayerData::vanished),
                    Codec.LONG.fieldOf("LastRTPTime").forGetter(ServerPlayerData::lastRTPTime),
                    Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("TeleportCooldowns", new HashMap<>()).forGetter(ServerPlayerData::teleportCooldowns)
            ).apply(instance, ServerPlayerData::new)
    );
}