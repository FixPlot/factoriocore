package com.fixplot.factoriocore.content.logistics.splitter;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SplitterBlock extends BeltBlock {
    public static final BooleanProperty PART_RIGHT = BooleanProperty.of("part_right");
    private static final VoxelShape SPLITTER_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 11.0, 16.0);

    public SplitterBlock(Settings settings, int tier) {
        super(settings, tier, false);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART_RIGHT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_RIGHT);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SPLITTER_SHAPE;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing(); if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) { facing = facing.getOpposite(); }
        BlockPos rightPos = ctx.getBlockPos().offset(facing.rotateYClockwise());
        World world = ctx.getWorld();

        if (world.getBlockState(rightPos).canReplace(ctx) && world.getWorldBorder().contains(rightPos)) {
            return this.getDefaultState().with(FACING, facing).with(PART_RIGHT, false);
        }
        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && !state.get(PART_RIGHT)) {
            Direction facing = state.get(FACING);
            BlockPos rightPos = pos.offset(facing.rotateYClockwise());
            world.setBlockState(rightPos, state.with(PART_RIGHT, true), Block.NOTIFY_ALL);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            Direction facing = state.get(FACING);
            boolean isRight = state.get(PART_RIGHT);
            BlockPos otherPos = isRight
                    ? pos.offset(facing.rotateYCounterclockwise())
                    : pos.offset(facing.rotateYClockwise());
            BlockState otherState = world.getBlockState(otherPos);
            if (otherState.isOf(this) && otherState.get(PART_RIGHT) != isRight) {
                world.setBlockState(otherPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.isSneaking() && world.getBlockEntity(pos) instanceof BeltBlockEntity beltEntity) {
            if (!world.isClient) {
                player.openHandledScreen(beltEntity.getMasterSplitter());
            }
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }
}
