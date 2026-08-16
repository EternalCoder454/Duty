package net.dutymod.server.mixin.structure;

import com.mojang.datafixers.util.Pair;
import net.dutymod.server.structure.StructureSearchBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Arms {@link StructureSearchBudget} around a structure search, and enforces it on the stronghold
 * path.
 *
 * <p>Vanilla splits the search in two. Ordinary structures are placed on a scattered grid and go
 * through the {@code RandomSpreadStructurePlacement} overload; strongholds sit on concentric rings
 * and go through the {@code ConcentricRingsStructurePlacement} one. Both are reached from
 * {@code findNearestMapStructure}, which is why the budget is armed there rather than in each.
 *
 * <p>Only the ring path is enforced <em>here</em>. Duty's own biome-envelope search already
 * replaces the scattered-grid path wholesale -- it cancels vanilla's version at HEAD and runs its
 * own loop -- so an injection into vanilla's loop would compile, apply, and then never execute a
 * single time. That path checks the budget directly inside the loop that actually runs.
 */
@Mixin(ChunkGenerator.class)
public abstract class StructureSearchBudgetMixin {
    @Inject(
            method = "findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;",
            at = @At("HEAD")
    )
    private void duty$armSearchBudget(
            ServerLevel level,
            HolderSet<Structure> targets,
            BlockPos origin,
            int radius,
            boolean skipKnownStructures,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        StructureSearchBudget.arm(targets);
    }

    /**
     * Disarms on the way out.
     *
     * <p>Not strictly required -- {@link StructureSearchBudget#arm} replaces the deadline on the
     * next search, and nothing reads it outside one -- but leaving a thread permanently armed is
     * the kind of state that only stays harmless until someone adds a third call site.
     */
    @Inject(
            method = "findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;",
            at = @At("RETURN")
    )
    private void duty$disarmSearchBudget(
            ServerLevel level,
            HolderSet<Structure> targets,
            BlockPos origin,
            int radius,
            boolean skipKnownStructures,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        StructureSearchBudget.disarm();
    }

    /**
     * Checked once per ring position, immediately before the chunk load that makes a ring position
     * expensive. Returning null here is what an exhausted search returns anyway.
     */
    @Inject(
            method = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement;)Lcom/mojang/datafixers/util/Pair;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getStructureGeneratingAt(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/StructureManager;ZLnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement;Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Pair;"
            ),
            cancellable = true
    )
    private void duty$checkRingSearchBudget(
            Set<Holder<Structure>> targets,
            ServerLevel level,
            StructureManager structureManager,
            BlockPos origin,
            boolean skipKnownStructures,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (StructureSearchBudget.expired()) {
            cir.setReturnValue(null);
        }
    }
}
