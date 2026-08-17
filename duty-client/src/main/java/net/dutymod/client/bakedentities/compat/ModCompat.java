package net.dutymod.client.bakedentities.compat;

import java.util.Arrays;
import java.util.List;

import net.dutymod.client.bakedentities.compat.emf.EMFCompat;
import net.dutymod.client.bakedentities.compat.iris.IrisCompat;
import net.dutymod.client.bakedentities.compat.lootr.LootrCompat;
import net.dutymod.client.bakedentities.config.SettingsManager;
import net.dutymod.framework.platform.Platform;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ModCompat {
    private final static boolean isIrisLoaded = Platform.get().isModLoadedAtStartup("iris") || Platform.get().isModLoadedAtStartup("oculus");
    private final static boolean isSodiumLoaded = Platform.get().isModLoadedAtStartup("sodium") || Platform.get().isModLoadedAtStartup("embeddium");
    private final static boolean isEMFLoaded = Platform.get().isModLoadedAtStartup("entity_model_features");
    private final static boolean isPunchyLoaded = Platform.get().isModLoadedAtStartup("punchy");

    private static final List<String> incompatibleMods = Arrays.asList("vulkanmod","optifine","embeddium","optifabric");

    public static void init(){
        if(Platform.get().isModLoadedAtStartup("lootr")) LootrCompat.init();
    }

    public static boolean isIrisLoaded(){
        return isIrisLoaded;
    }

    public static boolean isSodiumLoaded(){
        return isSodiumLoaded;
    }

    public static boolean isEMFLoaded(){
        return isEMFLoaded;
    }

    public static boolean isPunchyLoaded(){
        return isPunchyLoaded;
    }

    public static boolean isShadowPass(){
        if(isIrisLoaded()) return IrisCompat.isShadowPass();
        else return false;
    }

    public static ModelPart applyEMFRestPose(ModelPart root, BlockState state){
        if(isEMFLoaded() && SettingsManager.EMF_COMPAT.getValue()) return EMFCompat.applyRestPose(root, state);
        else return root;
    }

    public static boolean shouldRenderEntity(BlockEntity be){
        if(isPunchyLoaded()) return be.getBlockPos() == BlockPos.ZERO;
        return false;
    }

    public static boolean isIncompatibilityDetected(){
        for(String mod : incompatibleMods){
            if(Platform.get().isModLoadedAtStartup(mod)) return true;
        }
        return false;
    }

    public static String getIncompatibleMod(){
        for(String mod : incompatibleMods){
            if(Platform.get().isModLoadedAtStartup(mod)) return Platform.get().modName(mod);
        }
        return null;
    }
}
