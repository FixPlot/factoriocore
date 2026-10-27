package com.fixplot.factoriocore.content.production.electric_drill;

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
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ElectricDrillBlock extends BlockWithEntity {
    public static final MapCodec<ElectricDrillBlock> CODEC = createCodec(ElectricDrillBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 8);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 24.0, 16.0);

    public ElectricDrillBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 4)
                .with(UPPER, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, UPPER);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
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

    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
        int wOff = (part % 3) - 1;
        int dOff = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = masterPos.offset(right, wOff).offset(facing, dOff);
        return upper ? base.up() : base;
    }

    public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricDrillBlock)) return currentPos;
        Direction facing = state.get(FACING);
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        int wOff = (part % 3) - 1;
        int dOff = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = upper ? currentPos.down() : currentPos;
        return base.offset(right, -wOff).offset(facing, -dOff);
    }

    public static boolean hasOreInFootprint(World world, BlockPos originPos, Direction facing) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos checkPos = originPos.add(dx, dy, dz);
                    if (world.getBlockState(checkPos).getBlock() instanceof com.fixplot.factoriocore.content.world.ore.ResourceOreBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        BlockPos masterPos = ctx.getBlockPos();
        World world = ctx.getWorld();

        for (int part = 0; part < 9; part++) {
            for (int yOff = 0; yOff <= 2; yOff++) {
                BlockPos checkPos = getPartPos(masterPos, facing, part, false).up(yOff);
                if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                    return null;
                }
            }
        }
        if (!hasOreInFootprint(world, masterPos, facing)) return null;
        return this.getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && state.get(PART) == 4 && !state.get(UPPER)) {
            Direction facing = state.get(FACING);
            for (int part = 0; part < 9; part++) {
                for (int u = 0; u <= 1; u++) {
                    if (part == 4 && u == 0) continue;
                    BlockPos p = getPartPos(pos, facing, part, u == 1);
                    world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (world.getBlockEntity(masterPos) instanceof ElectricDrillBlockEntity drillBe) {
                player.openHandledScreen(drillBe);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);

            if (world.getBlockEntity(masterPos) instanceof ElectricDrillBlockEntity drillBe) {
                ItemScatterer.spawn(world, masterPos, drillBe);
            }

            for (int part = 0; part < 9; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos p = getPartPos(masterPos, facing, part, u == 1);
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
        if (state.get(PART) != 4 || state.get(UPPER)) return null;
        return new ElectricDrillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 4 || state.get(UPPER)) return null;
        return validateTicker(type, ModBlocks.ELECTRIC_DRILL_BLOCK_ENTITY,
                world.isClient ? ElectricDrillBlockEntity::clientTick : ElectricDrillBlockEntity::tick);
    }
}