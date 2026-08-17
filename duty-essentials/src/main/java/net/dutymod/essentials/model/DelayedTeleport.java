package net.dutymod.essentials.model;

import java.util.function.Consumer;

import net.dutymod.essentials.level.DutyServerPlayer;

public record DelayedTeleport(Position target, Position startPos, long executeAt, String cooldownType, int cooldownSeconds, Consumer<DutyServerPlayer> onComplete) {
}
