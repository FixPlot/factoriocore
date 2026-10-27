package com.fixplot.factoriocore.content.combat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class FactorioWallBlock extends Block {
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty WEST = Properties.WEST;

    private static final VoxelShape OUTLINE_SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    // Коллизия высотой 1.5 блока (24 пикселя), чтобы ни игрок, ни кусаки/монстры не могли перепрыгнуть стену
    private static final VoxelShape COLLISION_SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 24.0, 14.0);

    public FactorioWallBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(NORTH, false)
                .with(EAST, false)
                .with(SOUTH, false)
                .with(WEST, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    public static boolean canConnectTo(BlockState neighborState) {
        Block b = neighborState.getBlock();
        return b instanceof FactorioWallBlock || b instanceof FactorioGateBlock;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockView world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        return this.getDefaultState()
                .with(NORTH, canConnectTo(world.getBlockState(pos.north())))
                .with(EAST, canConnectTo(world.getBlockState(pos.east())))
                .with(SOUTH, canConnectTo(world.getBlockState(pos.south())))
                .with(WEST, canConnectTo(world.getBlockState(pos.west())));
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return switch (direction) {
            case NORTH -> state.with(NORTH, canConnectTo(neighborState));
            case EAST -> state.with(EAST, canConnectTo(neighborState));
            case SOUTH -> state.with(SOUTH, canConnectTo(neighborState));
            case WEST -> state.with(WEST, canConnectTo(neighborState));
            default -> state;
        };
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 0.10f;
    }
}