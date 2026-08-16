package net.dutymod.server.mixin.net.pipeline;

import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.dutymod.server.net.PipelineEvent;
import net.dutymod.server.net.ClientConnectionEncryptionExtension;
import net.dutymod.server.net.pipeline.MinecraftCipherDecoder;
import net.dutymod.server.net.pipeline.MinecraftCipherEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

@Mixin(Connection.class)
public class EncryptionConnectionMixin implements ClientConnectionEncryptionExtension {
    @Shadow
    private boolean encrypted;
    @Shadow
    private Channel channel;

    @Override
    public void setupEncryption(SecretKey key) throws GeneralSecurityException {
        if (!this.encrypted) {
            VelocityCipher decryption = Natives.cipher.get().forDecryption(key);
            VelocityCipher encryption = Natives.cipher.get().forEncryption(key);

            this.encrypted = true;
            this.channel.pipeline().addBefore("splitter", "decrypt", new MinecraftCipherDecoder(decryption));
            this.channel.pipeline().addBefore("prepender", "encrypt", new MinecraftCipherEncoder(encryption));

            this.channel.pipeline().fireUserEventTriggered(PipelineEvent.ENCRYPTION_ENABLED);
        }
    }
}
