package com.fixplot.factoriocore.content.combat;

import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FactorioGateBlockEntity extends BlockEntity {

    private static final double TRIGGER_RADIUS_SQ = 3.6 * 3.6;

    public FactorioGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FACTORIO_GATE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, FactorioGateBlockEntity entity) {
        if (world.isClient || (world.getTime() & 1L) != 0L) {
            return;
        }

        boolean shouldOpen = isPlayerNearGateLine(world, pos, state);
        boolean currentlyOpen = state.get(FactorioGateBlock.OPEN);

        if (shouldOpen != currentlyOpen) {
            world.setBlockState(pos, state.with(FactorioGateBlock.OPEN, shouldOpen), Block.NOTIFY_ALL);
            world.playSound(
                    null,
                    pos,
                    shouldOpen ? SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN : SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                    SoundCategory.BLOCKS,
                    0.5f,
                    shouldOpen ? 0.9f : 0.75f
            );
        }
    }

    /**
     * Проверяет, есть ли игрок рядом с этим блоком ворот ИЛИ рядом с соседними воротами в том же ряду
     * (чтобы широкие ворота из 2–4 блоков открывались и закрывались синхронно!).
     */
    private static boolean isPlayerNearGateLine(World world, BlockPos origin, BlockState state) {
        if (isPlayerNearPos(world, origin)) {
            return true;
        }
        Direction facing = state.get(FactorioGateBlock.FACING);
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        for (int step = 1; step <= 4; step++) {
            BlockPos leftPos = origin.offset(left, step);
            if (world.getBlockState(leftPos).getBlock() instanceof FactorioGateBlock) {
                if (isPlayerNearPos(world, leftPos)) return true;
            } else {
                break;
            }
        }
        for (int step = 1; step <= 4; step++) {
            BlockPos rightPos = origin.offset(right, step);
            if (world.getBlockState(rightPos).getBlock() instanceof FactorioGateBlock) {
                if (isPlayerNearPos(world, rightPos)) return true;
            } else {
                break;
            }
        }
        return false;
    }

    private static boolean isPlayerNearPos(World world, BlockPos gatePos) {
        double gx = gatePos.getX() + 0.5;
        double gy = gatePos.getY() + 0.5;
        double gz = gatePos.getZ() + 0.5;

        for (PlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.isAlive()) {
                if (player.squaredDistanceTo(gx, gy, gz) <= TRIGGER_RADIUS_SQ) {
                    return true;
                }
            }
        }
        return false;
    }
}