package net.dutymod.essentials.mixin;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.exception.HomeLimitReachedException;
import net.dutymod.essentials.level.DutyServerLevel;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.dutymod.essentials.model.*;
import net.dutymod.essentials.utils.ChatFormatter;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements DutyServerPlayer {

    @Unique
    private DelayedTeleport duty$DelayedTeleport = null;

    @Unique
    private Map<String, Long> duty$TeleportCooldowns = new HashMap<>();

    public ServerPlayerMixin(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Shadow
    public abstract void sendSystemMessage(Component arg, boolean bl);

    @Shadow
    private boolean disconnected;

    @Shadow
    protected abstract boolean acceptsChatMessages();

    @Shadow
    public abstract boolean teleportTo(ServerLevel arg, double d, double e, double f, Set<Relative> set, float g, float h, boolean bl);

    @Shadow
    public abstract ServerLevel level();

    @Shadow
    public abstract CommandSourceStack createCommandSourceStack();

    @Unique
    private Map<String, Home> duty$Homes = new HashMap<>();

    @Unique
    private Position duty$LastPosition = Position.ZERO;

    @Unique
    private final List<TPARequest> duty$TPARequests = new ArrayList<>();

    @Unique
    private boolean duty$acceptsTPARequests = true;

    @Unique
    private String duty$Nick = null;

    @Unique
    private boolean duty$isAFK = false;

    @Unique
    private Position duty$AFKPosition = Position.ZERO;

    @Unique
    private @Nullable UUID duty$lastMessageSender = null;

    @Unique
    private boolean duty$hasGodMode = false;

    @Unique
    private boolean duty$vanished = false;

    @Unique
    private long duty$LastRTPTime = 0;


    @Override
    public UUID duty$getUUID() {
        return this.getUUID();
    }

    @Override
    public Component duty$getName() {
        if (this.duty$getNick() != null && !this.duty$getNick().isEmpty()) {
            return ChatFormatter.format(this.duty$getNick());
        }
        return DutyEssentials.coloredLiteral(this.getGameProfile().name());
    }

    @Override
    public boolean duty$isOnline() {
        return !this.disconnected;
    }

    @Override
    public void duty$sendSystemMessage(Component message, boolean actionBar) {
        // Duty: Essentials has no client half, so a connected client never has this module's
        // language file. Resolving the component to a literal here is what makes these messages
        // legible on a vanilla client rather than showing raw translation keys.
        this.sendSystemMessage(ChatFormatter.flattenToLiteral(message), actionBar);
    }

    @Override
    public void duty$broadcastSystemMessage(Component message, boolean actionBar) {
        if (this.level().getServer() instanceof MinecraftServer server) {
            server.sendSystemMessage(message);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player instanceof DutyServerPlayer duty_essentialsServerPlayer) {
                    duty_essentialsServerPlayer.duty$sendSystemMessage(message, actionBar);
                }
            }
        }
    }

    @Override
    public void duty$sendFailedSystemMessage(Component message) {
        this.duty$sendSystemMessage(Component.empty().append(message).withStyle(ChatFormatting.RED), false);
    }

    @Override
    public DutyServerLevel duty$getLevel() {
        return (DutyServerLevel) this.level();
    }

    @Override
    public ServerLevel duty$getLevel(ResourceLocation dimension) {
        return this.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    @Override
    public DutyServerLevel duty$getOverworld() {
        return (DutyServerLevel) this.level().getServer().getLevel(Level.OVERWORLD);
    }

    @Override
    public EssentialsLevelData duty$getLevelData() {
        return duty$getOverworld().duty$getLevelData();
    }

    @Override
    public Position duty$getPosition() {
        Vec3 vec3 = this.position();
        return new Position(vec3.x, vec3.y, vec3.z, this.getYRot(), this.getXRot(), duty$getLevel().duty$getDimension());
    }

    @Override
    public void duty$teleport(Position position) {
        ServerLevel serverLevel = duty$getLevel(position.dimension);
        this.duty$setLastPosition();
        this.teleportTo(serverLevel, position.x, position.y, position.z, Set.of(), position.yaw, position.pitch, true);
    }

    @Override
    public List<Home> duty$getHomes() {
        return new ArrayList<>(duty$Homes.values());
    }

    @Override
    public Optional<Home> duty$getHome(String name) {
        return duty$Homes.containsKey(name) ? Optional.of(duty$Homes.get(name)) : Optional.empty();
    }

    @Override
    public void duty$scheduleTeleport(Position position, int delaySeconds, String cooldownType, int cooldownSeconds, java.util.function.Consumer<DutyServerPlayer> onComplete) {
        if (delaySeconds <= 0) {
            if (cooldownSeconds > 0) {
                this.duty$setTeleportCooldown(cooldownType, cooldownSeconds);
            }
            this.duty$teleport(position);
            if (onComplete != null) {
                onComplete.accept(this);
            }
        } else {
            this.duty$DelayedTeleport = new DelayedTeleport(position, duty$getPosition(), System.currentTimeMillis() + (delaySeconds * 1000L), cooldownType, cooldownSeconds, onComplete);
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("teleport.delayed", delaySeconds), false);
        }
    }

    @Override
    public DelayedTeleport duty$getDelayedTeleport() {
        return duty$DelayedTeleport;
    }

    @Override
    public void duty$cancelDelayedTeleport() {
        if (duty$DelayedTeleport != null) {
            this.duty$DelayedTeleport = null;
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.canceled"), false);
        }
    }

    @Override
    public long duty$getTeleportCooldown(String type) {
        return duty$TeleportCooldowns.getOrDefault(type, 0L);
    }

    @Override
    public void duty$setTeleportCooldown(String type, int cooldownSeconds) {
        if (cooldownSeconds > 0) {
            duty$TeleportCooldowns.put(type, System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    @Override
    public Map<String, Long> duty$getTeleportCooldowns() {
        return duty$TeleportCooldowns;
    }

    @Override
    public void duty$setTeleportCooldowns(Map<String, Long> cooldowns) {
        this.duty$TeleportCooldowns = cooldowns;
    }

    @Override
    public int duty$getHomeLimit() {
        if (DutyEssentials.API.hasPermission(this.createCommandSourceStack(), "home.limit.unlimited")) {
            return -1;
        }
        for (int i = 50; i >= 1; i--) {
            if (DutyEssentials.API.hasPermission(this.createCommandSourceStack(), "home.limit." + i)) {
                return i;
            }
        }
        return EssentialsOptions.homesLimit.get();
    }

    @Override
    public int duty$getMaxNickLength() {
        if (DutyEssentials.API.hasPermission(this.createCommandSourceStack(), "nick.length.unlimited")) {
            return 256;
        }
        for (int i = 32; i >= 1; i--) {
            if (DutyEssentials.API.hasPermission(this.createCommandSourceStack(), "nick.length." + i)) {
                return i;
            }
        }
        return EssentialsOptions.maxNickLength.get();
    }

    @Override
    public void duty$addHome(Home home) throws HomeLimitReachedException {
        int homesLimit = duty$getHomeLimit();
        if (homesLimit >= 0 && duty$Homes.size() >= homesLimit) {
            if (!duty$Homes.containsKey(home.name)) {
                throw new HomeLimitReachedException();
            }
        }
        duty$Homes.put(home.name, home);
    }

    @Override
    public void duty$removeHome(String name) {
        duty$Homes.remove(name);
    }

    @Override
    public void duty$setHomes(List<Home> homes) {
        duty$Homes = homes.stream().collect(Collectors.toMap(home -> home.name, home -> home));
    }

    @Override
    public Position duty$getLastPosition() {
        return duty$LastPosition;
    }

    @Override
    public void duty$setLastPosition(Position position) {
        duty$LastPosition = position;
    }

    @Override
    public void duty$setLastPosition() {
        duty$setLastPosition(duty$getPosition());
    }

    @Override
    public boolean duty$hasLastPosition() {
        return !duty$LastPosition.equals(Position.ZERO);
    }

    @Override
    public List<TPARequest> duty$getTPARequests() {
        duty$TPARequests.removeIf(request -> !request.isPending());
        return duty$TPARequests;
    }

    @Override
    public void duty$addTPARequest(TPARequest request) {
        this.duty$TPARequests.add(request);
    }

    @Override
    public void duty$removeTPARequest(TPARequest request) {
        this.duty$TPARequests.remove(request);
    }

    @Override
    public void duty$sendTPARequest(DutyServerPlayer player, boolean isHere) {
        if (!player.duty$isOnline()) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.tpa.receiver_offline", player.duty$getName()), false);
            return;
        }

        if (!player.duty$acceptsTPARequests()) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.tpa.receiver_requests_disabled", player.duty$getName()), false);
            return;
        }

        TPARequest request = new TPARequest(this, player, System.currentTimeMillis(), isHere);
        player.duty$receiveTPARequest(request);
        if (request.isHere) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.sent.here", player.duty$getName()), false);
        } else {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.sent", player.duty$getName()), false);
        }
    }

    @Override
    public void duty$receiveTPARequest(TPARequest request) {
        this.duty$addTPARequest(request);
        if (request.isHere) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.received.here", request.sender.duty$getName()), false);
        } else {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.received", request.sender.duty$getName()), false);
        }
    }

    @Override
    public void duty$acceptTPARequest(TPARequest request) {
        if (!request.sender.duty$isOnline()) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.sender_offline", request.sender.duty$getName()), false);
        } else {
            Integer delay = EssentialsOptions.tpaTeleportDelay.get();
            if (request.isHere) {
                this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.accepted.here", request.sender.duty$getName()), false);
                request.sender.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.accepted.here.sender", this.duty$getName()), false);
                this.duty$scheduleTeleport(request.sender.duty$getPosition(), delay, null, 0, null);
            } else {
                this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.accepted", request.sender.duty$getName()), false);
                request.sender.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.accepted.sender", this.duty$getName()), false);
                request.sender.duty$scheduleTeleport(this.duty$getPosition(), delay, null, 0, null);
            }
            this.duty$removeTPARequest(request);
        }
    }

    @Override
    public void duty$acceptTPARequest() {
        List<TPARequest> requests = this.duty$getTPARequests();
        if (requests.isEmpty()) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.tpa.no_requests"), false);
        } else {
            TPARequest request = requests.getFirst();
            this.duty$acceptTPARequest(request);
        }
    }

    @Override
    public void duty$denyTPARequest(TPARequest request) {
        this.duty$removeTPARequest(request);
        request.sender.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.denied", this.duty$getName()), false);
        this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.denied.sender", request.sender.duty$getName()), false);
    }

    @Override
    public void duty$denyTPARequest() {
        List<TPARequest> requests = this.duty$getTPARequests();
        if (requests.isEmpty()) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.tpa.no_requests"), false);
        } else {
            TPARequest request = requests.getFirst();
            this.duty$denyTPARequest(request);
        }
    }

    @Override
    public void duty$toggleTPARequests() {
        this.duty$acceptsTPARequests = !this.duty$acceptsTPARequests;
        if (this.duty$acceptsTPARequests) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.requests_enabled"), false);
        } else {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.tpa.requests_disabled"), false);
        }
    }

    @Override
    public boolean duty$acceptsTPARequests() {
        return duty$acceptsTPARequests;
    }

    @Override
    public String duty$getNick() {
        return this.duty$Nick;
    }

    @Override
    public String duty$getNonNullNick() {
        return this.duty$Nick == null ? "" : this.duty$Nick;
    }

    @Override
    public boolean duty$hasNick() {
        return this.duty$Nick != null && !this.duty$Nick.isEmpty();
    }

    @Override
    public void duty$setNick(String nick) {
        this.duty$Nick = nick;
        this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.nick.set", duty$getName()), false);
        this.duty$broadcastNickChange();
    }

    @Override
    public void duty$removeNick() {
        this.duty$Nick = "";
        this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.nick.removed"), false);
        this.duty$broadcastNickChange();
    }

    @Override
    public void duty$broadcastNickChange() {
        if (this.level().getServer() instanceof MinecraftServer server) {
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of((ServerPlayer) (Object) this)));
        }
    }

    @Override
    public boolean duty$isAFK() {
        return duty$isAFK;
    }

    @Override
    public void duty$setAFK(boolean afk) {
        if (afk && duty$isAFK) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.afk.already"), false);
            return;
        }
        duty$isAFK = afk;
        duty$AFKPosition = duty$getPosition();
        if (afk) {
            this.duty$broadcastSystemMessage(DutyEssentials.prefixedTranslatable("commands.afk.set", duty$getName()), false);
        } else {
            this.duty$broadcastSystemMessage(DutyEssentials.prefixedTranslatable("commands.afk.removed", duty$getName()), false);
        }
    }

    @Override
    public Optional<DutyServerPlayer> duty$getLastMessageSender() {
        if (duty$lastMessageSender == null) {
            return Optional.empty();
        }

        if (this.level().getServer() instanceof MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(duty$lastMessageSender);
            if (player instanceof DutyServerPlayer duty_essentialsServerPlayer) {
                return Optional.of(duty_essentialsServerPlayer);
            }
        }
        return Optional.empty();
    }

    @Override
    public void duty$setLastMessageSender(UUID senderUUID) {
        duty$lastMessageSender = senderUUID;
    }

    @Override
    public boolean duty$hasGodMode() {
        return duty$hasGodMode;
    }

    @Override
    public void duty$setGodMode(boolean godMode) {
        duty$hasGodMode = godMode;
        if (godMode) {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.god.toggled.on"), false);
        } else {
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.god.toggled.off"), false);
        }
    }

    @Override
    public void duty$toggleGodMode() {
        duty$setGodMode(!duty$hasGodMode);
    }

    @Override
    public boolean duty$isVanished() {
        return duty$vanished;
    }

    @Override
    public void duty$setVanished(boolean vanished) {
        this.duty$vanished = vanished;
        // Update invisibility metadata first so spawn packets are correct
        this.setInvisible(vanished);

        MinecraftServer server = this.level().getServer();

        if (vanished) {
            ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(List.of(this.getUUID()));
            ClientboundRemoveEntitiesPacket removeEntitiesPacket = new ClientboundRemoveEntitiesPacket(this.getId());
            Component leftMessage = Component.translatable("multiplayer.player.left", this.getDisplayName()).withStyle(ChatFormatting.YELLOW);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player == (Object) this) continue;

                if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    player.connection.send(removePacket);
                    player.connection.send(removeEntitiesPacket);
                    player.sendSystemMessage(leftMessage);
                } else {
                    player.sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.vanish.notify.enabled", this.getDisplayName()).withStyle(ChatFormatting.GRAY));
                }
            }
            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.vanish.enabled"), false);
        } else {
            ClientboundPlayerInfoUpdatePacket addPacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of((ServerPlayer) (Object) this));
            Component joinMessage = Component.translatable("multiplayer.player.joined", this.getDisplayName()).withStyle(ChatFormatting.YELLOW);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player == (Object) this) continue;

                if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    // Send tab list packet BEFORE spawning the entity
                    player.connection.send(addPacket);
                    player.sendSystemMessage(joinMessage);
                } else {
                    player.sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.vanish.notify.disabled", this.getDisplayName()).withStyle(ChatFormatting.GRAY));
                }
            }

            // Refresh entity tracking for everyone after confirming they have the tab info
            this.level().getChunkSource().removeEntity(this);
            this.level().getChunkSource().addEntity(this);

            this.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.vanish.disabled"), false);
        }
    }

    @Override
    public long duty$getLastRTPTime() {
        return duty$LastRTPTime;
    }

    @Override
    public void duty$setLastRTPTime(long time) {
        duty$LastRTPTime = time;
    }

    @Override
    public LevelData.RespawnData duty$getNewRespawnData() {
        return new LevelData.RespawnData(
                new GlobalPos(
                        this.level().dimension(),
                        this.blockPosition()
                ),
                this.getYRot(),
                this.getXRot()
        );
    }

    @Inject(at = @At("TAIL"), method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V")
    public void restoreFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if (oldPlayer instanceof DutyServerPlayer oldDutyServerPlayer) {
            this.duty$Homes = oldDutyServerPlayer.duty$getHomes().stream()
                    .collect(Collectors.toMap(home -> home.name, home -> home));
            this.duty$LastPosition = oldDutyServerPlayer.duty$getLastPosition();
            this.duty$acceptsTPARequests = oldDutyServerPlayer.duty$acceptsTPARequests();
            this.duty$Nick = oldDutyServerPlayer.duty$getNick();
            this.duty$hasGodMode = oldDutyServerPlayer.duty$hasGodMode();
            this.duty$vanished = oldDutyServerPlayer.duty$isVanished();
            this.duty$LastRTPTime = oldDutyServerPlayer.duty$getLastRTPTime();
            this.duty$TeleportCooldowns = new HashMap<>(oldDutyServerPlayer.duty$getTeleportCooldowns());
        }
    }

    @Inject(at = @At("TAIL"), method = "addAdditionalSaveData")
    public void addAdditionalSaveData(ValueOutput valueOutput, CallbackInfo ci) {
        valueOutput.store("DutyEssentials", ServerPlayerData.CODEC, new ServerPlayerData(
                this.duty$getHomes(),
                this.duty$getLastPosition(),
                this.duty$acceptsTPARequests(),
                this.duty$getNonNullNick(),
                this.duty$hasGodMode(),
                this.duty$isVanished(),
                this.duty$getLastRTPTime(),
                this.duty$getTeleportCooldowns()
        ));
    }

    @Inject(at = @At("TAIL"), method = "readAdditionalSaveData")
    public void readAdditionalSaveData(ValueInput valueInput, CallbackInfo ci) {
        valueInput.read("DutyEssentials", ServerPlayerData.CODEC).ifPresent(data -> {
            this.duty$Homes = data.homes().stream()
                    .collect(Collectors.toMap(home -> home.name, home -> home));
            this.duty$LastPosition = data.lastPosition();
            this.duty$acceptsTPARequests = data.acceptsTPARequests();
            if (data.nick() != null && !data.nick().isEmpty()) {
                this.duty$Nick = data.nick();
            }
            this.duty$hasGodMode = data.hasGodMode();
            this.duty$vanished = data.vanished();
            this.duty$LastRTPTime = data.lastRTPTime();
            this.duty$TeleportCooldowns = new HashMap<>(data.teleportCooldowns());
        });
    }

    @Inject(at = @At("TAIL"), method = "getTabListDisplayName()Lnet/minecraft/network/chat/Component;", cancellable = true)
    public void getTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        if (this.duty$hasNick()) {
            cir.setReturnValue(ChatFormatter.format(this.duty$getNick()));
        }
    }

    @Inject(at = @At("HEAD"), method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V")
    public void sendChatMessage(OutgoingChatMessage message, boolean bl, ChatType.Bound bound, CallbackInfo ci) {
        if (duty$isAFK() && duty$sendsMessageThemself(bound.chatType())) {
            duty$setAFK(false);
        }

        if (this.acceptsChatMessages()) {
            if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING) || bound.chatType().is(ChatType.TEAM_MSG_COMMAND_INCOMING)) {
                if (message instanceof OutgoingChatMessage.Player(
                        PlayerChatMessage playerMessage
                )) {
                    duty$setLastMessageSender(playerMessage.link().sender());
                }
            }
        }
    }

    @Unique
    private boolean duty$sendsMessageThemself(Holder<ChatType> type) {
        return type.is(ChatType.CHAT)
                || type.is(ChatType.MSG_COMMAND_OUTGOING)
                || type.is(ChatType.TEAM_MSG_COMMAND_OUTGOING)
                || type.is(ChatType.SAY_COMMAND)
                || type.is(ChatType.EMOTE_COMMAND);
    }

    @Inject(at = @At("HEAD"), method = "isInvulnerableTo", cancellable = true)
    public void isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (duty$hasGodMode()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(at = @At("HEAD"), method = "tick()V")
    public void tick(CallbackInfo ci) {
        if (this.duty$isAFK() && !this.duty$getPosition().equals(this.duty$AFKPosition)) {
            this.duty$setAFK(false);
        }
        if (this.duty$isVanished()) {
            this.setInvisible(true); // Enforce invisibility
        }

        if (this.duty$DelayedTeleport != null) {
            if (!this.duty$getPosition().equalsIgnoreAngle(this.duty$DelayedTeleport.startPos())) {
                this.duty$cancelDelayedTeleport();
            } else if (System.currentTimeMillis() >= this.duty$DelayedTeleport.executeAt()) {
                if (this.duty$DelayedTeleport.cooldownSeconds() > 0) {
                    this.duty$setTeleportCooldown(this.duty$DelayedTeleport.cooldownType(), this.duty$DelayedTeleport.cooldownSeconds());
                }
                this.duty$teleport(this.duty$DelayedTeleport.target());
                if (this.duty$DelayedTeleport.onComplete() != null) {
                    this.duty$DelayedTeleport.onComplete().accept(this);
                }
                this.duty$DelayedTeleport = null;
            }
        }
    }
}