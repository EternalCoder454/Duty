package net.dutymod.client.obe.api.blockentity;

import net.dutymod.client.obe.renderer.blockentity.ext.BlockEntityExt;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityAPI {

    /*
     * Add a special renderer to a block entity
     * @param be The block entity to which a special renderer must be added
     * @since 1.1.21
     */
    public void addSpecialRenderer(BlockEntity be){
        if(be instanceof BlockEntityExt ext) ext.hasSpecialRenderer(true);
    }
}
