package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SteamEngineBlock extends BlockWithEntity {
    public static final MapCodec<SteamEngineBlock> CODEC = createCodec(SteamEngineBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // 3 блока в ширину (X: -1..1) и 5 блоков в длину (Z: 0..4) -> 15 частей основания (0..14)
    public static final IntProperty PART = IntProperty.of("part", 0, 14);
    // layer: 0 = нижний ярус (Y+0), 1 = средний ярус (Y+1), 2 = верхний ярус (Y+2, высота 1.5 -> итого 3.5 блока!)
    public static final IntProperty LAYER = IntProperty.of("layer", 0, 2);

    private static final VoxelShape FULL_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape TOP_HALF_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 24.0, 16.0);

    public SteamEngineBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 0)
                .with(LAYER, 0));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LAYER);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(LAYER) == 2 ? TOP_HALF_SHAPE : FULL_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(LAYER) == 2 ? TOP_HALF_SHAPE : FULL_SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 0.10f;
    }

    /**
     * Сетка 3x5 Парового двигателя относительно центрального переднего блока (part = 1):
     * wOff = (part % 3) - 1  (-1 = левый, 0 = центр, +1 = правый)
     * dOff = part / 3        (0..4 вдоль направления facing)
     */
    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, int layer) {
        int wOff = (part % 3) - 1;
        int dOff = part / 3;
        Direction right = facing.rotateYClockwise();
        return masterPos.offset(right, wOff).offset(facing, dOff).up(layer);
    }

    public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
        if (!(state.getBlock() instanceof SteamEngineBlock)) return currentPos;
        Direction facing = state.get(FACING);
        int part = state.get(PART);
        int layer = state.get(LAYER);
        int wOff = (part % 3) - 1;
        int dOff = part / 3;
        Direction right = facing.rotateYClockwise();
        return currentPos.down(layer).offset(right, -wOff).offset(facing, -dOff);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        BlockPos masterPos = ctx.getBlockPos();
        World world = ctx.getWorld();

        for (int part = 0; part < 15; part++) {
            for (int yOff = 0; yOff <= 3; yOff++) {
                BlockPos checkPos = getPartPos(masterPos, facing, part, yOff);
                if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return this.getDefaultState().with(FACING, facing).with(PART, 1).with(LAYER, 0);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && state.get(PART) == 1 && state.get(LAYER) == 0) {
            Direction facing = state.get(FACING);
            for (int part = 0; part < 15; part++) {
                for (int layer = 0; layer <= 2; layer++) {
                    if (part == 1 && layer == 0) continue;
                    BlockPos p = getPartPos(pos, facing, part, layer);
                    world.setBlockState(p, state.with(PART, part).with(LAYER, layer), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);
            for (int part = 0; part < 15; part++) {
                for (int layer = 0; layer <= 2; layer++) {
                    BlockPos p = getPartPos(masterPos, facing, part, layer);
                    if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    }
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (state.get(PART) != 1 || state.get(LAYER) != 0) return null;
        return new SteamEngineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 1 || state.get(LAYER) != 0) return null;
        return validateTicker(type, ModBlocks.STEAM_ENGINE_BLOCK_ENTITY,
                world.isClient ? SteamEngineBlockEntity::clientTick : SteamEngineBlockEntity::tick);
    }
}