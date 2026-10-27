package com.fixplot.factoriocore.content.research;

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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class LabBlock extends BlockWithEntity {
    public static final MapCodec<LabBlock> CODEC = createCodec(LabBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 8);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");
    private static final VoxelShape UPPER_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public static Consumer<BlockPos> clientTechTreeOpener = null;

    public LabBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, 4).with(UPPER, false));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, UPPER);
    }

    public static BlockPos getPartPos(BlockPos centerPos, Direction facing, int part, boolean upper) {
        int row = part / 3 - 1;
        int col = part % 3 - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos p = centerPos.offset(facing, row).offset(right, col);
        return upper ? p.up() : p;
    }

    public static BlockPos getMasterPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof LabBlock)) return pos;
        int part = state.get(PART);
        Direction facing = state.get(FACING);
        boolean upper = state.get(UPPER);
        int row = part / 3 - 1;
        int col = part % 3 - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = upper ? pos.down() : pos;
        return base.offset(facing, -row).offset(right, -col);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos center = ctx.getBlockPos();
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        for (int p = 0; p < 9; p++) {
            for (int u = 0; u <= 1; u++) {
                BlockPos check = getPartPos(center, facing, p, u == 1);
                if (!world.getWorldBorder().contains(check) || !world.getBlockState(check).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        Direction facing = state.get(FACING);
        for (int p = 0; p < 9; p++) {
            for (int u = 0; u <= 1; u++) {
                if (p == 4 && u == 0) continue;
                BlockPos partPos = getPartPos(pos, facing, p, u == 1);
                world.setBlockState(partPos, getDefaultState().with(FACING, facing).with(PART, p).with(UPPER, u == 1), Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos master = getMasterPos(pos, state);
        BlockState masterState = world.getBlockState(master);
        if (masterState.getBlock() instanceof LabBlock) {
            Direction facing = masterState.get(FACING);
            if (!world.isClient && world.getBlockEntity(master) instanceof LabBlockEntity labBe) {
                ItemScatterer.spawn(world, master, labBe);
            }
            for (int p = 0; p < 9; p++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos tp = getPartPos(master, facing, p, u == 1);
                    if (!tp.equals(pos) && world.getBlockState(tp).getBlock() instanceof LabBlock) {
                        world.setBlockState(tp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    }
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos master = getMasterPos(pos, state);
        if (world.getBlockEntity(master) instanceof LabBlockEntity lab) {
            int packSlot = LabBlockEntity.getSlotForPack(stack.getItem());
            if (packSlot >= 0) {
                if (!world.isClient) {
                    ItemStack cur = lab.getStack(packSlot);
                    if (cur.isEmpty()) {
                        lab.setStack(packSlot, stack.copy());
                        if (!player.isCreative()) stack.setCount(0);
                    } else if (cur.getCount() < cur.getMaxCount()) {
                        int add = Math.min(stack.getCount(), cur.getMaxCount() - cur.getCount());
                        cur.increment(add);
                        if (!player.isCreative()) stack.decrement(add);
                        lab.sync();
                    }
                    world.playSound(null, master, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 1.2f);
                    player.sendMessage(Text.literal("§aКолбочки загружены в лабораторию! [§e"
                            + lab.getStack(0).getCount() + " кр, "
                            + lab.getStack(1).getCount() + " зел, "
                            + lab.getStack(2).getCount() + " воен, "
                            + lab.getStack(3).getCount() + " син§a]"), true);
                }
                return ItemActionResult.SUCCESS;
            }
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockPos master = getMasterPos(pos, state);
        if (player.isSneaking()) {
            if (world.isClient && clientTechTreeOpener != null) {
                clientTechTreeOpener.accept(master);
            }
            return ActionResult.SUCCESS;
        }
        if (!world.isClient && world.getBlockEntity(master) instanceof LabBlockEntity labBe) {
            player.openHandledScreen(labBe);
        }
        return ActionResult.SUCCESS;
    }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? UPPER_SHAPE : VoxelShapes.fullCube();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? UPPER_SHAPE : VoxelShapes.fullCube();
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return (state.get(PART) == 4 && !state.get(UPPER)) ? new LabBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || state.get(PART) != 4 || state.get(UPPER)) return null;
        return validateTicker(type, ModBlocks.LAB_BLOCK_ENTITY, LabBlockEntity::tick);
    }
}