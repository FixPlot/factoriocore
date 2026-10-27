package com.fixplot.factoriocore.content.logistics.burner_inserter;

import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BurnerInserterBlock extends InserterBlock {

    public BurnerInserterBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof InserterBlockEntity inserter) {
                // При разрушении дропаем и предмет из лапки, и оставшееся топливо
                ItemStack fuel = inserter.getFuelStack();
                if (!fuel.isEmpty()) {
                    ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, fuel);
                    inserter.setStack(InserterBlockEntity.FUEL_SLOT_INDEX, ItemStack.EMPTY);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
