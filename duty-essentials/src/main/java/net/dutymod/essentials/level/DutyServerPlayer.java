package net.dutymod.essentials.level;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.dutymod.essentials.exception.HomeLimitReachedException;
import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.dutymod.essentials.model.DelayedTeleport;
import net.dutymod.essentials.model.Home;
import net.dutymod.essentials.model.Position;
import net.dutymod.essentials.model.TPARequest;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;

public interface DutyServerPlayer {

    UUID duty$getUUID();
    Component duty$getName();
    boolean duty$isOnline();
    void duty$sendSystemMessage(Component message, boolean actionBar);
    void duty$broadcastSystemMessage(Component message, boolean actionBar);
    void duty$sendFailedSystemMessage(Component message);


    DutyServerLevel duty$getLevel();
    ServerLevel duty$getLevel(Identifier dimension);
    DutyServerLevel duty$getOverworld();

    EssentialsLevelData duty$getLevelData();
    Position duty$getPosition();
    void duty$teleport(Position position);
    void duty$scheduleTeleport(Position position, int delaySeconds, String cooldownType, int cooldownSeconds, java.util.function.Consumer<DutyServerPlayer> onComplete);
    DelayedTeleport duty$getDelayedTeleport();
    void duty$cancelDelayedTeleport();

    long duty$getTeleportCooldown(String type);
    void duty$setTeleportCooldown(String type, int cooldownSeconds);
    Map<String, Long> duty$getTeleportCooldowns();
    void duty$setTeleportCooldowns(Map<String, Long> cooldowns);

    List<Home> duty$getHomes();
    void duty$setHomes(List<Home> homes);
    Optional<Home> duty$getHome(String name);
    void duty$addHome(Home home) throws HomeLimitReachedException;
    void duty$removeHome(String name);

    Position duty$getLastPosition();
    void duty$setLastPosition(Position position);
    void duty$setLastPosition();
    boolean duty$hasLastPosition();

    List<TPARequest> duty$getTPARequests();
    void duty$addTPARequest(TPARequest request);
    void duty$removeTPARequest(TPARequest request);
    void duty$sendTPARequest(DutyServerPlayer player, boolean isHere);
    void duty$receiveTPARequest(TPARequest request);
    void duty$acceptTPARequest(TPARequest request);
    void duty$acceptTPARequest();
    void duty$denyTPARequest(TPARequest request);
    void duty$denyTPARequest();
    void duty$toggleTPARequests();
    boolean duty$acceptsTPARequests();

    String duty$getNick();
    String duty$getNonNullNick();
    boolean duty$hasNick();
    void duty$setNick(String nick);
    void duty$removeNick();
    void duty$broadcastNickChange();

    long duty$getLastRTPTime();
    void duty$setLastRTPTime(long time);

    boolean duty$isAFK();
    void duty$setAFK(boolean afk);

    Optional<DutyServerPlayer> duty$getLastMessageSender();
    void duty$setLastMessageSender(UUID senderUUID);

    boolean duty$hasGodMode();
    void duty$setGodMode(boolean godMode);
    void duty$toggleGodMode();

    boolean duty$isVanished();
    void duty$setVanished(boolean vanished);

    LevelData.RespawnData duty$getNewRespawnData();


    int duty$getHomeLimit();
    int duty$getMaxNickLength();
}