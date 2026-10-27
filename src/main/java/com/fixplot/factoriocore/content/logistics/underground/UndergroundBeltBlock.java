package com.fixplot.factoriocore.content.logistics.underground;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class UndergroundBeltBlock extends BeltBlock {
    public static final BooleanProperty OUTPUT = BooleanProperty.of("output");
    private static final VoxelShape UNDERGROUND_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

    public UndergroundBeltBlock(Settings settings, int tier) {
        super(settings, tier, true);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(OUTPUT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUTPUT);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return UNDERGROUND_SHAPE;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof BeltBlockEntity beltEntity) {
            beltEntity.setBeltTier(this.getTier());
            Direction facing = state.get(FACING);
            boolean shouldBeOutput = hasUnpairedInputBehind(world, pos, facing, beltEntity.getMaxUndergroundOffset());
            beltEntity.setUndergroundMode(shouldBeOutput
                    ? BeltBlockEntity.UndergroundMode.OUTPUT
                    : BeltBlockEntity.UndergroundMode.INPUT);

            BlockState newState = state.with(OUTPUT, shouldBeOutput);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            syncManualChange(world, pos, newState, beltEntity);
        }
    }

    private boolean hasUnpairedInputBehind(World world, BlockPos pos, Direction facing, int maxOffset) {
        for (int d = 1; d <= maxOffset; d++) {
            BlockPos backPos = pos.offset(facing.getOpposite(), d);
            if (world.getBlockEntity(backPos) instanceof BeltBlockEntity other) {
                if (other.isUnderground() && other.getBeltTier() == this.getTier()) {
                    Direction otherFacing = other.getCachedState().get(FACING);
                    if (otherFacing == facing) {
                        return other.getUndergroundMode() == BeltBlockEntity.UndergroundMode.INPUT;
                    } else if (otherFacing == facing.getOpposite()) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking() && world.getBlockEntity(pos) instanceof BeltBlockEntity beltEntity) {
            if (world.isClient) return ActionResult.SUCCESS;

            boolean nextIsOutput = beltEntity.getUndergroundMode() == BeltBlockEntity.UndergroundMode.INPUT;
            beltEntity.setUndergroundMode(nextIsOutput
                    ? BeltBlockEntity.UndergroundMode.OUTPUT
                    : BeltBlockEntity.UndergroundMode.INPUT);

            BlockState updatedState = state.with(OUTPUT, nextIsOutput);
            world.setBlockState(pos, updatedState, Block.NOTIFY_ALL);
            syncManualChange(world, pos, updatedState, beltEntity);

            
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }
}
