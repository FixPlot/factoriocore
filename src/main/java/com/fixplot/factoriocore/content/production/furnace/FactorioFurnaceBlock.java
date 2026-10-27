package com.fixplot.factoriocore.content.production.furnace;

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

public class FactorioFurnaceBlock extends BlockWithEntity {
    public static final MapCodec<FactorioFurnaceBlock> CODEC = createCodec(s -> new FactorioFurnaceBlock(s, 1));
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 3);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 24.0, 16.0);

    // 1 = Каменная печь (0.3125/сек -> 64 тика), 2 = Стальная печь (0.625/сек -> 32 тика)
    private final int tier;

    public FactorioFurnaceBlock(Settings settings, int tier) {
        super(settings);
        this.tier = tier;
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 0)
                .with(UPPER, false));
    }

    public int getTier() {
        return this.tier;
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

    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
        Direction right = facing.rotateYClockwise();
        BlockPos base = switch (part) {
            case 1 -> masterPos.offset(right);
            case 2 -> masterPos.offset(facing);
            case 3 -> masterPos.offset(facing).offset(right);
            default -> masterPos;
        };
        return upper ? base.up() : base;
    }

    public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
        if (!(state.getBlock() instanceof FactorioFurnaceBlock)) {
            return currentPos;
        }
        Direction facing = state.get(FACING);
        Direction left = facing.rotateYCounterclockwise();
        Direction back = facing.getOpposite();
        int part = state.get(PART);
        boolean upper = state.get(UPPER);

        BlockPos base = upper ? currentPos.down() : currentPos;
        return switch (part) {
            case 1 -> base.offset(left);
            case 2 -> base.offset(back);
            case 3 -> base.offset(back).offset(left);
            default -> base;
        };
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

        for (int part = 0; part < 4; part++) {
            for (int yOff = 0; yOff <= 2; yOff++) {
                BlockPos checkPos = getPartPos(masterPos, facing, part, false).up(yOff);
                if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return this.getDefaultState().with(FACING, facing).with(PART, 0).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && state.get(PART) == 0 && !state.get(UPPER)) {
            Direction facing = state.get(FACING);
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    if (part == 0 && u == 0) continue;
                    boolean isUpper = (u == 1);
                    BlockPos partPos = getPartPos(pos, facing, part, isUpper);
                    world.setBlockState(partPos, state.with(PART, part).with(UPPER, isUpper), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);

            if (world.getBlockEntity(masterPos) instanceof FactorioFurnaceBlockEntity masterBe) {
                for (int i = 0; i < masterBe.size(); i++) {
                    ItemStack stack = masterBe.getStack(i);
                    if (!stack.isEmpty()) {
                        ItemScatterer.spawn(world, masterPos.getX() + 1.0, masterPos.getY() + 0.5, masterPos.getZ() + 1.0, stack.copy());
                        masterBe.setStack(i, ItemStack.EMPTY);
                    }
                }
            }

            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos partPos = getPartPos(masterPos, facing, part, u == 1);
                    if (!partPos.equals(pos) && world.getBlockState(partPos).isOf(this)) {
                        world.setBlockState(partPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    }
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (world.getBlockEntity(masterPos) instanceof FactorioFurnaceBlockEntity masterBe) {
                player.openHandledScreen(masterBe);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (world.getBlockEntity(masterPos) instanceof FactorioFurnaceBlockEntity masterBe) {
                masterBe.wakeUp();
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (state.get(PART) != 0 || state.get(UPPER)) { return null; } return new FactorioFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 0 || state.get(UPPER)) {
            return null;
        }
        return validateTicker(type, ModBlocks.FACTORIO_FURNACE_BLOCK_ENTITY,
                world.isClient ? null : FactorioFurnaceBlockEntity::tick);
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 0.10f;
    }
}