package net.dutymod.server.mixin.jigsaw;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.dutymod.server.jigsaw.utils.PalettedStructureBlockInfoList;

import java.util.List;

@Mixin(value = StructureTemplate.Palette.class)
public class StructureTemplatePaletteMixin {

    @Shadow
    @Final
    @Mutable
    private List<StructureTemplate.StructureBlockInfo> blocks;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void duty$shrinkStructureTemplateBlocksList(CallbackInfo ci) {
        blocks = new PalettedStructureBlockInfoList(blocks);
    }
}
