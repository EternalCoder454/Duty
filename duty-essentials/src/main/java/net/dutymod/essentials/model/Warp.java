package net.dutymod.essentials.model;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;

public class Warp {

    public String name;
    public Position position;

    public Warp(String name, Position position) {
        this.name = name;
        this.position = position;
    }

    public static Warp deserialize(Dynamic<?> dynamic) {
        String name = dynamic.get("Name").asString("");
        Position position = Position.deserialize(dynamic.get("Position").orElseEmptyMap());
        return new Warp(name, position);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.put("Position", position.serialize());
        return tag;
    }
}
