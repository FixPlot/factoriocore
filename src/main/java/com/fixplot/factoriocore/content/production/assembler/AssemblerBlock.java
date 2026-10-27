package com.fixplot.factoriocore.content.production.assembler;

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
import net.minecraft.screen.NamedScreenHandlerFactory;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AssemblerBlock extends BlockWithEntity {
    public static final MapCodec<AssemblerBlock> CODEC = createCodec(AssemblerBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 8);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");
    private static final net.minecraft.util.shape.VoxelShape UPPER_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public AssemblerBlock(Settings settings) {
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

    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
        int localX = (part % 3) - 1;
        int localZ = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos p = masterPos.offset(right, localX).offset(facing, localZ);
        return upper ? p.up() : p;
    }

    public static BlockPos getMasterPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AssemblerBlock)) return pos;
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        Direction facing = state.get(FACING);
        int localX = (part % 3) - 1;
        int localZ = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = upper ? pos.down() : pos;
        return base.offset(right, -localX).offset(facing, -localZ);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos masterPos = ctx.getBlockPos();
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }

        for (int part = 0; part < 9; part++) {
            for (int u = 0; u <= 1; u++) {
                BlockPos p = getPartPos(masterPos, facing, part, u == 1);
                if (!world.getWorldBorder().contains(p) || !world.getBlockState(p).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return this.getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        Direction facing = state.get(FACING);
        for (int part = 0; part < 9; part++) {
            for (int u = 0; u <= 1; u++) {
                if (part == 4 && u == 0) continue;
                BlockPos p = getPartPos(pos, facing, part, u == 1);
                world.setBlockState(p, this.getDefaultState()
                        .with(FACING, facing)
                        .with(PART, part)
                        .with(UPPER, u == 1), Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);
            if (world.getBlockEntity(masterPos) instanceof AssemblerBlockEntity asm) {
                ItemScatterer.spawn(world, masterPos, asm);
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

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            BlockEntity be = world.getBlockEntity(masterPos);
            if (be instanceof NamedScreenHandlerFactory factory) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? UPPER_SHAPE : net.minecraft.util.shape.VoxelShapes.fullCube();
    }

    @Override
    protected net.minecraft.util.shape.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? UPPER_SHAPE : net.minecraft.util.shape.VoxelShapes.fullCube();
    }

    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (state.get(PART) == 4 && !state.get(UPPER)) {
            return new AssemblerBlockEntity(pos, state);
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || state.get(PART) != 4 || state.get(UPPER)) {
            return null;
        }
        return validateTicker(type, ModBlocks.ASSEMBLER_BLOCK_ENTITY, AssemblerBlockEntity::tick);
    }
}