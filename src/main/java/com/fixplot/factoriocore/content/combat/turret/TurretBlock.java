package com.fixplot.factoriocore.content.combat.turret;

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

public class TurretBlock extends BlockWithEntity {
    public static final MapCodec<TurretBlock> CODEC = createCodec(s -> new TurretBlock(s, 0));
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 3);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

    // 0 = Пулемётная турель (Gun Turret), 1 = Лазерная турель (Laser Turret), 2 = Огнемётная турель (Flamethrower Turret)
    private final int turretKind;

    public TurretBlock(Settings settings, int turretKind) {
        super(settings);
        this.turretKind = turretKind;
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 0)
                .with(UPPER, false));
    }

    public int getTurretKind() {
        return this.turretKind;
    }

    public int getAttackRangeBlocks() {
        return switch (this.turretKind) {
            case 1 -> 24; // Лазерная турель
            case 2 -> 26; // Огнемётная турель
            default -> 18; // Пулемётная турель
        };
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, UPPER);
    }

    public static BlockPos getPartPos(BlockPos masterPos, int part, boolean upper) {
        int dx = part % 2;
        int dz = part / 2;
        return masterPos.add(dx, upper ? 1 : 0, dz);
    }

    public static BlockPos getMasterPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof TurretBlock)) return pos;
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        return pos.add(-(part % 2), upper ? -1 : 0, -(part / 2));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        World world = ctx.getWorld();
        BlockPos masterPos = ctx.getBlockPos();

        for (int part = 0; part < 4; part++) {
            for (int u = 0; u <= 1; u++) {
                BlockPos check = getPartPos(masterPos, part, u == 1);
                if (!world.getWorldBorder().contains(check) || !world.getBlockState(check).canReplace(ctx)) {
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
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    if (part == 0 && u == 0) continue;
                    BlockPos p = getPartPos(pos, part, u == 1);
                    world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (world.getBlockEntity(masterPos) instanceof TurretBlockEntity turretBe) {
                ItemScatterer.spawn(world, masterPos, turretBe);
            }
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos p = getPartPos(masterPos, part, u == 1);
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
            if (world.getBlockEntity(masterPos) instanceof TurretBlockEntity turretBe) {
                player.openHandledScreen(turretBe);
            }
        }
        return ActionResult.SUCCESS;
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
        return 0.12f;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return (state.get(PART) == 0 && !state.get(UPPER)) ? new TurretBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 0 || state.get(UPPER)) return null;
        return validateTicker(type, ModBlocks.TURRET_BLOCK_ENTITY,
                world.isClient ? TurretBlockEntity::clientTick : TurretBlockEntity::tick);
    }
}