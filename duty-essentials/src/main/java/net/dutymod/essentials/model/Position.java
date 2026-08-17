package net.dutymod.essentials.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelData;

import java.util.Objects;

public class Position {

    public static final Position ZERO = new Position(0, 0, 0, 0, 0, Identifier.parse("overworld"));
    public static final Codec<Position> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.DOUBLE.fieldOf("X").forGetter(position -> position.x),
                    Codec.DOUBLE.fieldOf("Y").forGetter(position -> position.y),
                    Codec.DOUBLE.fieldOf("Z").forGetter(position -> position.z),
                    Codec.FLOAT.fieldOf("Yaw").forGetter(position -> position.yaw),
                    Codec.FLOAT.fieldOf("Pitch").forGetter(position -> position.pitch),
                    Identifier.CODEC.fieldOf("Dimension").forGetter(position -> position.dimension)
            ).apply(instance, Position::new)
    );

    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public Identifier dimension;

    public Position(double x, double y, double z, float yaw, float pitch, Identifier dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension;
    }

    public static Position deserialize(Dynamic<?> dynamic) {
        double x = dynamic.get("X").asDouble(0);
        double y = dynamic.get("Y").asDouble(0);
        double z = dynamic.get("Z").asDouble(0);
        float yaw = dynamic.get("Yaw").asFloat(0);
        float pitch = dynamic.get("Pitch").asFloat(0);
        Identifier dimension = Identifier.parse(dynamic.get("Dimension").asString("overworld"));
        return new Position(x, y, z, yaw, pitch, dimension);
    }

    public static Position deserialize(CompoundTag tag) {
        double x = tag.getDouble("X").orElse(0D);
        double y = tag.getDouble("Y").orElse(0D);
        double z = tag.getDouble("Z").orElse(0D);
        float yaw = tag.getFloat("Yaw").orElse(0F);
        float pitch = tag.getFloat("Pitch").orElse(0F);
        Identifier dimension = Identifier.parse(tag.getString("Dimension").orElse("overworld"));
        return new Position(x, y, z, yaw, pitch, dimension);
    }

    public static Position fromRespawnData(LevelData.RespawnData respawnData) {
        return new Position(
                respawnData.globalPos().pos().getX(),
                respawnData.globalPos().pos().getY(),
                respawnData.globalPos().pos().getZ(),
                respawnData.yaw(),
                respawnData.pitch(),
                respawnData.globalPos().dimension().identifier()
        );
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putString("Dimension", dimension.toString());
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        boolean xEquals = Double.compare(position.x, x) == 0;
        boolean yEquals = Double.compare(position.y, y) == 0;
        boolean zEquals = Double.compare(position.z, z) == 0;
        boolean yawEquals = Float.compare(position.yaw, yaw) == 0;
        boolean pitchEquals = Float.compare(position.pitch, pitch) == 0;
        boolean dimensionEquals = dimension.equals(position.dimension);
        return xEquals && yEquals && zEquals && yawEquals && pitchEquals && dimensionEquals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, yaw, pitch, dimension);
    }

    public boolean equalsIgnoreAngle(Position position) {
        if (this == position) return true;
        if (position == null) return false;
        boolean xEquals = Double.compare(position.x, x) == 0;
        boolean yEquals = Double.compare(position.y, y) == 0;
        boolean zEquals = Double.compare(position.z, z) == 0;
        boolean dimensionEquals = dimension.equals(position.dimension);
        return xEquals && yEquals && zEquals && dimensionEquals;
    }
}
