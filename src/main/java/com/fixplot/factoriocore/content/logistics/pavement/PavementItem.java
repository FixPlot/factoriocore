package com.fixplot.factoriocore.content.logistics.pavement;

import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PavementItem extends BlockItem {

    public PavementItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos clickedPos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();

        // Если кликнули по траве/папоротнику — берём сам блок земли под ними
        if (!isReplaceableGround(world.getBlockState(clickedPos)) && isReplaceableGround(world.getBlockState(clickedPos.down()))) {
            clickedPos = clickedPos.down();
        }

        if (isReplaceableGround(world.getBlockState(clickedPos))) {
            int radius = (player != null && player.isSneaking()) ? 1 : 0; // Shift = кисть 3x3
            int placed = 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (player != null && !player.isCreative() && stack.getCount() <= 0) {
                        break;
                    }
                    BlockPos targetPos = clickedPos.add(dx, 0, dz);
                    BlockState current = world.getBlockState(targetPos);

                    if (isReplaceableGround(current) && !current.isOf(this.getBlock())) {
                        if (!world.isClient) {
                            // Срезаем мелкую траву над блоком при укладке бетона
                            BlockState above = world.getBlockState(targetPos.up());
                            if (above.isOf(Blocks.SHORT_GRASS) || above.isOf(Blocks.FERN) || above.isOf(Blocks.DEAD_BUSH)) {
                                world.setBlockState(targetPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            }
                            world.setBlockState(targetPos, this.getBlock().getDefaultState(), Block.NOTIFY_ALL);
                        }
                        placed++;
                        if (player != null && !player.isCreative()) {
                            stack.decrement(1);
                        }
                    }
                }
            }

            if (placed > 0) {
                world.playSound(null, clickedPos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.9f, 1.15f);
                return ActionResult.success(world.isClient);
            }
        }

        return super.useOnBlock(context);
    }

    public static boolean isReplaceableGround(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.GRASS_BLOCK
                || b == Blocks.DIRT
                || b == Blocks.COARSE_DIRT
                || b == Blocks.ROOTED_DIRT
                || b == Blocks.PODZOL
                || b == Blocks.MOSS_BLOCK
                || b == Blocks.MUD
                || b == Blocks.PACKED_MUD
                || b == Blocks.MUD_BRICKS
                || b == Blocks.DRIPSTONE_BLOCK
                || b == Blocks.SAND
                || b == Blocks.RED_SAND
                || b == Blocks.SANDSTONE
                || b == Blocks.RED_SANDSTONE
                || b == ModBlocks.LANDFILL
                || b instanceof PavementBlock;
    }
}
