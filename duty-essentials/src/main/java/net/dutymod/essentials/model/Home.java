package net.dutymod.essentials.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

public class Home {

    public static final Codec<Home> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(home -> home.name),
            Position.CODEC.fieldOf("position").forGetter(home -> home.position)
    ).apply(instance, Home::new));

    public final String name;
    public final Position position;

    public Home(String name, Position position) {
        this.name = name;
        this.position = position;
    }

    public static Home deserialize(CompoundTag tag) {
        String name = tag.getString("Name").orElse("");
        Position position = tag.getCompound("Position").map(Position::deserialize).orElse(Position.ZERO);
        return new Home(name, position);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.put("Position", position.serialize());
        return tag;
    }
}
