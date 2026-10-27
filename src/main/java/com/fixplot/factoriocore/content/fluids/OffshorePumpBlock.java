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
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class OffshorePumpBlock extends BlockWithEntity {
    public static final MapCodec<OffshorePumpBlock> CODEC = createCodec(OffshorePumpBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // part: 0 = Береговая часть (на суше, в уровень с трубами), 1 = Водозаборная часть ("Г" над водой)
    public static final IntProperty PART = IntProperty.of("part", 0, 1);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

    public OffshorePumpBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 0)
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
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
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
        return 0.125f;
    }

    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
        BlockPos base = (part == 1) ? masterPos.offset(facing) : masterPos;
        return upper ? base.up() : base;
    }

    public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
        if (!(state.getBlock() instanceof OffshorePumpBlock)) return currentPos;
        Direction facing = state.get(FACING);
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        BlockPos base = upper ? currentPos.down() : currentPos;
        return (part == 1) ? base.offset(facing.getOpposite()) : base;
    }

    private static boolean isWaterBlock(BlockState st) {
        return st.isOf(Blocks.WATER)
                || st.isOf(Blocks.SEAGRASS)
                || st.isOf(Blocks.TALL_SEAGRASS)
                || st.isOf(Blocks.KELP)
                || st.isOf(Blocks.KELP_PLANT);
    }

    /**
     * Если игрок кликнул в боковую грань берега (над самой водой) — автоматически сдвигаем мастер-точку
     * на 1 блок назад и вверх на кромку берега, чтобы помпа всегда вставала вровень с наземными трубами!
     */
    public static BlockPos resolveShoreMasterPos(World world, BlockPos rawPos, Direction facing) {
        if (isWaterBlock(world.getBlockState(rawPos.down())) && !isWaterBlock(world.getBlockState(rawPos.offset(facing.getOpposite())))) {
            BlockPos candidate = rawPos.offset(facing.getOpposite()).up();
            if (world.getBlockState(candidate).isReplaceable()) {
                return candidate;
            }
        }
        return rawPos;
    }

    /**
     * Проверяет береговую установку:
     * - Задняя часть (masterPos) стоит на твёрдом блоке берега (например, Y=65 поверх травы Y=64).
     * - Передняя часть (waterHeadPos = masterPos.offset(facing)) висит над озером, где вода может быть
     *   как на уровне берега (down(1) = Y=64), так и на 1 блок ниже берега, как в озёрах Наувиса (down(2) = Y=63)!
     */
    public static boolean isValidShorelinePlacement(World world, BlockPos masterPos, Direction facing, ItemPlacementContext ctx) {
        BlockPos waterHeadPos = masterPos.offset(facing);

        for (int part = 0; part <= 1; part++) {
            BlockPos p = getPartPos(masterPos, facing, part, false);
            if (!world.getWorldBorder().contains(p) || !world.getBlockState(p).canReplace(ctx)) {
                return false;
            }
        }

        BlockState shoreGround = world.getBlockState(masterPos.down());
        if (shoreGround.isAir() || isWaterBlock(shoreGround) || !shoreGround.isSolidBlock(world, masterPos.down())) {
            return false;
        }

        // Проверяем воду под передним коленом как на Y-1, так и на Y-2 (стандартный берег Наувиса!)
        BlockState water1 = world.getBlockState(waterHeadPos.down(1));
        BlockState water2 = world.getBlockState(waterHeadPos.down(2));
        return isWaterBlock(water1) || (water1.isAir() && isWaterBlock(water2));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        World world = ctx.getWorld();
        BlockPos masterPos = resolveShoreMasterPos(world, ctx.getBlockPos(), facing);
        if (!isValidShorelinePlacement(world, masterPos, facing, ctx)) {
            return null;
        }
        return this.getDefaultState().with(FACING, facing).with(PART, 0).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && state.get(PART) == 0 && !state.get(UPPER)) {
            Direction facing = state.get(FACING);
            BlockPos frontPos = getPartPos(pos, facing, 1, false);
            world.setBlockState(frontPos, state.with(PART, 1).with(UPPER, false), Block.NOTIFY_ALL);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);
            for (int part = 0; part <= 1; part++) {
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
        if (state.get(PART) != 0 || state.get(UPPER)) return null;
        return new OffshorePumpBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 0 || state.get(UPPER)) return null;
        return world.isClient ? null : validateTicker(type, ModBlocks.OFFSHORE_PUMP_BLOCK_ENTITY, OffshorePumpBlockEntity::tick);
    }
}