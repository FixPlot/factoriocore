package com.fixplot.factoriocore.content.logistics.pavement;

import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

public class LandfillItem extends BlockItem {

    public LandfillItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            TypedActionResult<ItemStack> res = use(context.getWorld(), player, context.getHand());
            if (res.getResult().isAccepted()) {
                return res.getResult();
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }

        BlockPos hitPos = hit.getBlockPos();
        if (!isWaterOrAquaticPlant(world.getBlockState(hitPos)) && !isWaterOrAquaticPlant(world.getBlockState(hitPos.down()))) {
            BlockPos sidePos = hitPos.offset(hit.getSide());
            if (isWaterOrAquaticPlant(world.getBlockState(sidePos)) || isWaterOrAquaticPlant(world.getBlockState(sidePos.down()))) {
                hitPos = sidePos;
            }
        }

        int radius = user.isSneaking() ? 1 : 0;
        int placedCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!user.isCreative() && stack.getCount() <= 0) {
                    break;
                }
                if (tryFillWaterAtXZ(world, hitPos.getX() + dx, hitPos.getZ() + dz, hitPos.getY())) {
                    placedCount++;
                    if (!user.isCreative()) {
                        stack.decrement(1);
                    }
                }
            }
        }

        if (placedCount > 0) {
            world.playSound(null, hitPos, SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.BLOCKS, 0.9f, 0.95f);
            return TypedActionResult.success(stack, world.isClient());
        }
        return TypedActionResult.pass(stack);
    }

    private static boolean isWaterOrAquaticPlant(BlockState state) {
        return state.isOf(Blocks.WATER)
                || state.isOf(Blocks.SEAGRASS)
                || state.isOf(Blocks.TALL_SEAGRASS)
                || state.isOf(Blocks.KELP)
                || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.LILY_PAD);
    }

    private boolean tryFillWaterAtXZ(World world, int x, int z, int hintY) {
        int waterTopY = -999;
        int startSearchY = Math.max(hintY + 2, NauvisChunkGenerator.MAIN_PLAIN_Y);
        int endSearchY = Math.min(hintY - 6, NauvisChunkGenerator.SEA_LEVEL - 6);

        for (int y = startSearchY; y >= endSearchY; y--) {
            BlockState st = world.getBlockState(new BlockPos(x, y, z));
            if (st.isOf(Blocks.WATER) || st.isOf(Blocks.SEAGRASS) || st.isOf(Blocks.TALL_SEAGRASS) || st.isOf(Blocks.KELP)) {
                waterTopY = y;
                break;
            }
        }

        if (waterTopY == -999) {
            return false;
        }

        int targetSurfaceY = (waterTopY == NauvisChunkGenerator.SEA_LEVEL)
                ? NauvisChunkGenerator.MAIN_PLAIN_Y
                : waterTopY;

        BlockPos surfacePos = new BlockPos(x, targetSurfaceY, z);
        BlockState currentSurface = world.getBlockState(surfacePos);
        if (currentSurface.isOf(this.getBlock())) {
            return false;
        }

        if (!world.isClient) {
            BlockPos abovePos = surfacePos.up();
            if (world.getBlockState(abovePos).isOf(Blocks.LILY_PAD)) {
                world.setBlockState(abovePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            if (currentSurface.isOf(Blocks.LILY_PAD)) {
                world.setBlockState(surfacePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }

            for (int y = targetSurfaceY - 1; y >= targetSurfaceY - 8; y--) {
                BlockPos belowPos = new BlockPos(x, y, z);
                if (isWaterOrAquaticPlant(world.getBlockState(belowPos))) {
                    world.setBlockState(belowPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    break;
                }
            }

            world.setBlockState(surfacePos, this.getBlock().getDefaultState(), Block.NOTIFY_ALL);
        }
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("block.factoriocore.landfill.desc"));
    }
}