package com.fixplot.factoriocore.mixin;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class ExampleMixin {
@Inject(at = @At("TAIL"), method = "markDirty()V")
private void onInventoryChanged(CallbackInfo info) {
BlockEntity self = (BlockEntity) (Object) this;
if (self instanceof BeltBlockEntity || self instanceof ResourceOreBlockEntity) {
return; // Конвейеры сами будят соседние манипуляторы только при реальном движении предметов
}
World world = self.getWorld();
if (world != null && !world.isClient()) {
if (self instanceof InserterBlockEntity inserter) {
inserter.wakeUp();
} else {
InserterBlockEntity.wakeUpAdjacentInserters(world, self.getPos());
}
}
}

@Inject(at = @At("TAIL"), method = "markDirty(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V")
private static void onStaticInventoryChanged(World world, BlockPos pos, BlockState state, CallbackInfo info) {
if (world != null && !world.isClient()) {
InserterBlockEntity.wakeUpAdjacentInserters(world, pos);
}
}
}