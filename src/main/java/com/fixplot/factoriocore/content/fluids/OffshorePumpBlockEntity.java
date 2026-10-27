package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class OffshorePumpBlockEntity extends BlockEntity implements FactorioFluidContainer {

    // В Factorio помпа качает 1200 ед./сек = 60 mB/тик (не требует электричества!)
    public static final int PUMP_RATE_MB_PER_TICK = 60;

    public OffshorePumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.OFFSHORE_PUMP_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, OffshorePumpBlockEntity pump) {
        if (world == null || world.isClient) return;
        Direction facing = state.get(OffshorePumpBlock.FACING);
        // Выходной патрубок помпы находится позади береговой стойки (в сторону суши) и по бокам
        BlockPos backOutput = pos.offset(facing.getOpposite());
        int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                world, backOutput, facing, FluidKind.WATER, PUMP_RATE_MB_PER_TICK, 15);

        if (pushed < PUMP_RATE_MB_PER_TICK) {
            for (Direction side : new Direction[]{ facing.rotateYClockwise(), facing.rotateYCounterclockwise() }) {
                pushed += FactorioFluidContainer.pushFluidToNeighbor(
                        world, pos.offset(side), side.getOpposite(), FluidKind.WATER, PUMP_RATE_MB_PER_TICK - pushed, 15);
                if (pushed >= PUMP_RATE_MB_PER_TICK) break;
            }
        }
    }

    @Override
    public FluidKind getFluidKind() {
        return FluidKind.WATER;
    }

    @Override
    public int getFluidAmount() {
        return 1000;
    }

    @Override
    public int getFluidCapacity() {
        return 1000;
    }

    @Override
    public int getTemperatureC() {
        return 15;
    }

    @Override
    public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
        return false;
    }

    @Override
    public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
        return 0;
    }
}