package com.fixplot.factoriocore.content.logistics.belt;

import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlock;
import com.fixplot.factoriocore.content.logistics.express_belt.ExpressBeltBlock;
import com.fixplot.factoriocore.content.logistics.fast_belt.FastBeltBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterBlock;
import com.fixplot.factoriocore.content.logistics.underground.UndergroundBeltBlock;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BeltBlock extends BlockWithEntity {
    public static final MapCodec<BeltBlock> CODEC = createCodec(BeltBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    private final int tier;
    private final boolean undergroundBlock;

    public BeltBlock(Settings settings) {
        this(settings, 1, false);
    }

    public BeltBlock(Settings settings, int tier, boolean undergroundBlock) {
        super(settings);
        this.tier = tier;
        this.undergroundBlock = undergroundBlock;
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    public int getTier() {
        return this.tier;
    }

    public boolean isUndergroundBlock() {
        return this.undergroundBlock;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction dir = ctx.getHorizontalPlayerFacing();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            dir = dir.getOpposite();
        }
        return this.getDefaultState().with(FACING, dir);
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
        if (this instanceof SplitterBlock || this instanceof UndergroundBeltBlock) {
            return BlockRenderType.MODEL;
        }
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, BeltBlockEntity.TYPE,
                world.isClient ? BeltBlockEntity::clientTick : BeltBlockEntity::tick);
    }

    public static boolean isLeftSideOfBelt(Direction facing, double relX, double relZ) {
        return switch (facing) {
            case NORTH -> relX < 0;
            case SOUTH -> relX > 0;
            case WEST  -> relZ > 0;
            case EAST  -> relZ < 0;
            default    -> false;
        };
    }

    public static double getProgressAlongBelt(Direction facing, double relX, double relZ) {
        return switch (facing) {
            case NORTH -> 0.5 - relZ;
            case SOUTH -> 0.5 + relZ;
            case WEST  -> 0.5 - relX;
            case EAST  -> 0.5 + relX;
            default    -> 0.5;
        };
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        Direction facing = state.get(FACING);

        if (entity instanceof ItemEntity itemEntity && !world.isClient && entity.isAlive()) {
            if (world.getBlockEntity(pos) instanceof BeltBlockEntity beltEntity) {
                ItemStack dropped = itemEntity.getStack();
                if (!dropped.isEmpty()) {
                    double relX = entity.getX() - (pos.getX() + 0.5);
                    double relZ = entity.getZ() - (pos.getZ() + 0.5);

                    boolean isLeft = isLeftSideOfBelt(facing, relX, relZ);
                    double along = getProgressAlongBelt(facing, relX, relZ);
                    int slot = MathHelper.clamp((int) (along * 4.0), 0, 3);

                    if (beltEntity.isSlotCoveredByHood(slot)) {
                        return;
                    }

                    BeltLane lane = isLeft ? beltEntity.getLeftLane() : beltEntity.getRightLane();
                    if (lane.insertAt(slot, dropped)) {
                        dropped.decrement(1);
                        if (dropped.isEmpty()) {
                            itemEntity.discard();
                        } else {
                            itemEntity.setStack(dropped);
                        }
                        syncManualChange(world, pos, state, beltEntity);
                    }
                }
            }
            return;
        }

        if (state.getBlock() instanceof SplitterBlock) {
            return;
        }

        if (!entity.isSneaking() && entity.getY() >= pos.getY() + 0.3) {
            double basePush = 0.045;
            if (state.getBlock() instanceof FastBeltBlock || (state.getBlock() instanceof UndergroundBeltBlock ub && ub.getTier() == 2)) {
                basePush = 0.09;
            } else if (state.getBlock() instanceof ExpressBeltBlock || (state.getBlock() instanceof UndergroundBeltBlock ub && ub.getTier() == 3)) {
                basePush = 0.135;
            }

            Vec3d vel = entity.getVelocity();
            double pushX = facing.getOffsetX() * basePush;
            double pushZ = facing.getOffsetZ() * basePush;
            entity.setVelocity(
                    MathHelper.clamp(vel.x + pushX, -0.4, 0.4),
                    vel.y,
                    MathHelper.clamp(vel.z + pushZ, -0.4, 0.4)
            );
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.getBlockEntity(pos) instanceof BeltBlockEntity beltEntity) {
            ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);

            if (player.isSneaking() && (!stack.isEmpty() || !beltEntity.isUnderground())) {
                return ActionResult.PASS;
            }
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                    && (bi.getBlock() instanceof BeltBlock
                    || bi.getBlock() instanceof InserterBlock
                    || bi.getBlock() instanceof BurnerDrillBlock
                    || bi.getBlock() instanceof FactorioFurnaceBlock
                    || bi.getBlock() instanceof FactorioChestBlock)) {
                return ActionResult.PASS;
            }

            if (world.isClient) return ActionResult.SUCCESS;

            if (beltEntity.isSplitter() && !player.isSneaking()) {
                player.openHandledScreen(beltEntity.getMasterSplitter());
                return ActionResult.SUCCESS;
            }

            if (player.isSneaking() && beltEntity.isUnderground()) {
                boolean nextIsOutput = beltEntity.getUndergroundMode() == BeltBlockEntity.UndergroundMode.INPUT;
                BeltBlockEntity.UndergroundMode nextMode = nextIsOutput
                        ? BeltBlockEntity.UndergroundMode.OUTPUT
                        : BeltBlockEntity.UndergroundMode.INPUT;
                beltEntity.setUndergroundMode(nextMode);

                BlockState updatedState = state.contains(UndergroundBeltBlock.OUTPUT)
                        ? state.with(UndergroundBeltBlock.OUTPUT, nextIsOutput)
                        : state;
                world.setBlockState(pos, updatedState, Block.NOTIFY_ALL);
                syncManualChange(world, pos, updatedState, beltEntity);
                return ActionResult.SUCCESS;
            }

            Direction facing = state.get(FACING);
            double relX = hit.getPos().x - (pos.getX() + 0.5);
            double relZ = hit.getPos().z - (pos.getZ() + 0.5);

            boolean isLeftClicked = isLeftSideOfBelt(facing, relX, relZ);
            double along = getProgressAlongBelt(facing, relX, relZ);
            int clickedSlot = MathHelper.clamp((int) (along * 4.0), 0, 3);

            BeltLane targetLane = isLeftClicked ? beltEntity.getLeftLane() : beltEntity.getRightLane();
            BeltLane secondaryLane = isLeftClicked ? beltEntity.getRightLane() : beltEntity.getLeftLane();

            if (stack.isEmpty()) {
                if (targetLane.hasItemAt(clickedSlot)) {
                    player.giveItemStack(targetLane.extractAt(clickedSlot));
                    syncManualChange(world, pos, state, beltEntity);
                    return ActionResult.SUCCESS;
                } else if (secondaryLane.hasItemAt(clickedSlot)) {
                    player.giveItemStack(secondaryLane.extractAt(clickedSlot));
                    syncManualChange(world, pos, state, beltEntity);
                    return ActionResult.SUCCESS;
                } else if (targetLane.canExtract()) {
                    player.giveItemStack(targetLane.extract());
                    syncManualChange(world, pos, state, beltEntity);
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }

            if (!beltEntity.isSlotCoveredByHood(clickedSlot) && targetLane.canInsertAt(clickedSlot)) {
                ItemStack single = stack.split(1);
                targetLane.insertAt(clickedSlot, single);
                syncManualChange(world, pos, state, beltEntity);
                return ActionResult.SUCCESS;
            } else if (!beltEntity.isSlotCoveredByHood(0) && targetLane.canInsert()) {
                ItemStack single = stack.split(1);
                targetLane.insert(single);
                syncManualChange(world, pos, state, beltEntity);
                return ActionResult.SUCCESS;
            } else if (!beltEntity.isSlotCoveredByHood(0) && secondaryLane.canInsert()) {
                ItemStack single = stack.split(1);
                secondaryLane.insert(single);
                syncManualChange(world, pos, state, beltEntity);
                return ActionResult.SUCCESS;
            }
        }
        return super.onUse(state, world, pos, player, hit);
    }

    public static void syncManualChange(World world, BlockPos pos, BlockState state, BeltBlockEntity beltEntity) {
        beltEntity.clearMovementFlags();
        beltEntity.markDirty();
        world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BeltBlockEntity beltEntity) {
                dropLaneItems(world, pos, beltEntity.getLeftLane());
                dropLaneItems(world, pos, beltEntity.getRightLane());
                dropTunnelItems(world, pos, beltEntity.getLeftTunnel());
                dropTunnelItems(world, pos, beltEntity.getRightTunnel());
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    private void dropLaneItems(World world, BlockPos pos, BeltLane lane) {
        for (int i = 0; i < BeltLane.CAPACITY; i++) {
            ItemStack stack = lane.getStack(i);
            if (!stack.isEmpty()) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        lane.clear();
    }

    private void dropTunnelItems(World world, BlockPos pos, ItemStack[] tunnel) {
        for (int i = 0; i < tunnel.length; i++) {
            if (!tunnel[i].isEmpty()) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tunnel[i]);
                tunnel[i] = ItemStack.EMPTY;
            }
        }
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 0.125f;
    }
}