package com.fixplot.factoriocore.content.combat;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class FactorioGateBlock extends BlockWithEntity {
    public static final MapCodec<FactorioGateBlock> CODEC = createCodec(FactorioGateBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = Properties.OPEN;

    private static final VoxelShape CLOSED_OUTLINE = Block.createCuboidShape(0.0, 0.0, 2.0, 16.0, 16.0, 14.0);
    private static final VoxelShape CLOSED_OUTLINE_X = Block.createCuboidShape(2.0, 0.0, 0.0, 14.0, 16.0, 16.0);
    private static final VoxelShape CLOSED_COLLISION = Block.createCuboidShape(0.0, 0.0, 2.0, 16.0, 24.0, 14.0);
    private static final VoxelShape CLOSED_COLLISION_X = Block.createCuboidShape(2.0, 0.0, 0.0, 14.0, 24.0, 16.0);
    private static final VoxelShape OPEN_OUTLINE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.5, 16.0);

    public FactorioGateBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction dir = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            dir = dir.getOpposite();
        }
        return this.getDefaultState().with(FACING, dir).with(OPEN, false);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(OPEN)) {
            return OPEN_OUTLINE;
        }
        Direction facing = state.get(FACING);
        return (facing.getAxis() == Direction.Axis.X) ? CLOSED_OUTLINE_X : CLOSED_OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(OPEN)) {
            return VoxelShapes.empty(); // В открытом состоянии игрок свободно пробегает сквозь ворота
        }
        Direction facing = state.get(FACING);
        return (facing.getAxis() == Direction.Axis.X) ? CLOSED_COLLISION_X : CLOSED_COLLISION;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FactorioGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlocks.FACTORIO_GATE_BLOCK_ENTITY, FactorioGateBlockEntity::tick);
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 0.10f;
    }
}