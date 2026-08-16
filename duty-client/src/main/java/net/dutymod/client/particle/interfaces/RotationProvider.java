package net.dutymod.client.particle.interfaces;

import net.minecraft.client.Camera;
import org.joml.Vector3f;

public interface RotationProvider {

    Vector3f duty$getDefaultBillboardVectors(float x, float y);

    void duty$setupDefaultBillboardVectors(Camera camera);

}