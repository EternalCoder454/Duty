package net.dutymod.fixerupper.util;

import net.minecraft.server.packs.PackType;

public class PackTypeHelper {
    public static boolean isVanillaPackType(PackType type) {
        return type == PackType.CLIENT_RESOURCES || type == PackType.SERVER_DATA;
    }
}
