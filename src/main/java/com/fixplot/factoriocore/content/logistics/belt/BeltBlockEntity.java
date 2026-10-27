package com.fixplot.factoriocore.content.logistics.belt;

import com.fixplot.factoriocore.content.logistics.fast_belt.FastBeltBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterBlock;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterScreenHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import com.fixplot.factoriocore.content.logistics.underground.UndergroundBeltBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class BeltBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory, GeoBlockEntity {
    public static final BlockEntityType<BeltBlockEntity> TYPE = ModBlocks.BELT_BLOCK_ENTITY;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Оставляем пустым, пока нет файла belt.animation.json
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static final String LEFT_LANE_NBT_KEY = "LeftLane";
    private static final String RIGHT_LANE_NBT_KEY = "RightLane";
    private static final String UNDERGROUND_MODE_KEY = "UndergroundMode";
    private static final String BELT_TIER_KEY = "BeltTier";
    private static final String LEFT_TUNNEL_KEY = "LeftTunnel";
    private static final String RIGHT_TUNNEL_KEY = "RightTunnel";

    public static final int MAX_TUNNEL_SLOTS = 36;

    public static final int ACTION_SPLITTER_TOGGLE_IN_PRIORITY = 3000;
    public static final int ACTION_SPLITTER_TOGGLE_IN_RIGHT = 3001;
    public static final int ACTION_SPLITTER_TOGGLE_OUT_PRIORITY = 3002;
    public static final int ACTION_SPLITTER_TOGGLE_OUT_RIGHT = 3003;

    public enum UndergroundMode {
        NONE,
        INPUT,
        OUTPUT
    }

    private UndergroundMode undergroundMode = UndergroundMode.NONE;
    private int beltTier = 1;

    private boolean inputPriorityEnabled = false;
    private boolean inputPriorityRight = false;
    private boolean outputPriorityEnabled = false;
    private boolean outputPriorityRight = false;
    private ItemStack splitterFilter = ItemStack.EMPTY;

    private boolean nextOutRightForLeftLane = false;
    private boolean nextOutRightForRightLane = false;
    private boolean nextInRightForLeftLane = false;
    private boolean nextInRightForRightLane = false;

    private int renderTicks = 0;
    private long lastSteppedWorldTime = -1L;

    private final BeltLane leftLane = new BeltLane();
    private final BeltLane rightLane = new BeltLane();

    private final ItemStack[] leftTunnel = new ItemStack[MAX_TUNNEL_SLOTS];
    private final ItemStack[] rightTunnel = new ItemStack[MAX_TUNNEL_SLOTS];

    protected final PropertyDelegate splitterPropertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> inputPriorityEnabled ? 1 : 0;
                case 1 -> inputPriorityRight ? 1 : 0;
                case 2 -> outputPriorityEnabled ? 1 : 0;
                case 3 -> outputPriorityRight ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> inputPriorityEnabled = (value != 0);
                case 1 -> inputPriorityRight = (value != 0);
                case 2 -> outputPriorityEnabled = (value != 0);
                case 3 -> outputPriorityRight = (value != 0);
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public BeltBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
        Arrays.fill(this.leftTunnel, ItemStack.EMPTY);
        Arrays.fill(this.rightTunnel, ItemStack.EMPTY);

        if (state.getBlock() instanceof BeltBlock beltBlock) {
            this.beltTier = beltBlock.getTier();
            if (beltBlock.isUndergroundBlock()) {
                boolean isOut = state.contains(UndergroundBeltBlock.OUTPUT) && state.get(UndergroundBeltBlock.OUTPUT);
                this.undergroundMode = isOut ? UndergroundMode.OUTPUT : UndergroundMode.INPUT;
            }
        }
    }

    public int getMoveDelay() {
        return switch (this.beltTier) {
            case 2 -> 2;
            case 3 -> 1;
            default -> 4;
        };
    }

    public boolean isSplitter() {
        return getCachedState().getBlock() instanceof SplitterBlock;
    }

    public boolean isRightSplitterPart() {
        BlockState state = getCachedState();
        return state.contains(SplitterBlock.PART_RIGHT) && state.get(SplitterBlock.PART_RIGHT);
    }

    public BeltBlockEntity getMasterSplitter() {
        if (!isSplitter() || !isRightSplitterPart() || this.world == null) {
            return this;
        }
        Direction facing = getCachedState().get(BeltBlock.FACING);
        BlockPos leftPos = this.pos.offset(facing.rotateYCounterclockwise());
        if (this.world.getBlockEntity(leftPos) instanceof BeltBlockEntity leftPartner && leftPartner.isSplitter()) {
            return leftPartner;
        }
        return this;
    }

    @Nullable
    public BeltBlockEntity getOtherSplitterHalf() {
        if (!isSplitter() || this.world == null) {
            return null;
        }
        Direction facing = getCachedState().get(BeltBlock.FACING);
        BlockPos otherPos = isRightSplitterPart()
                ? this.pos.offset(facing.rotateYCounterclockwise())
                : this.pos.offset(facing.rotateYClockwise());
        if (this.world.getBlockEntity(otherPos) instanceof BeltBlockEntity other && other.isSplitter()) {
            return other;
        }
        return null;
    }

    public void handleSplitterGuiAction(int action) {
        BeltBlockEntity master = getMasterSplitter();
        switch (action) {
            case ACTION_SPLITTER_TOGGLE_IN_PRIORITY -> master.inputPriorityEnabled = !master.inputPriorityEnabled;
            case ACTION_SPLITTER_TOGGLE_IN_RIGHT -> {
                master.inputPriorityRight = !master.inputPriorityRight;
                master.inputPriorityEnabled = true;
            }
            case ACTION_SPLITTER_TOGGLE_OUT_PRIORITY -> master.outputPriorityEnabled = !master.outputPriorityEnabled;
            case ACTION_SPLITTER_TOGGLE_OUT_RIGHT -> {
                master.outputPriorityRight = !master.outputPriorityRight;
                master.outputPriorityEnabled = true;
            }
        }
        master.markDirty();
        if (master.world != null) {
            master.world.updateListeners(master.pos, master.getCachedState(), master.getCachedState(), Block.NOTIFY_ALL);
        }
    }

    public BeltLane getLeftLane() {
        return this.leftLane;
    }

    public BeltLane getRightLane() {
        return this.rightLane;
    }

    public ItemStack[] getLeftTunnel() {
        return this.leftTunnel;
    }

    public ItemStack[] getRightTunnel() {
        return this.rightTunnel;
    }

    public int getRenderTicks() {
        return this.renderTicks;
    }

    public UndergroundMode getUndergroundMode() {
        return this.undergroundMode;
    }

    public void setUndergroundMode(UndergroundMode mode) {
        this.undergroundMode = mode;
    }

    public boolean isUnderground() {
        return this.undergroundMode != UndergroundMode.NONE;
    }

    public int getBeltTier() {
        return this.beltTier;
    }

    public void setBeltTier(int tier) {
        this.beltTier = Math.max(1, Math.min(3, tier));
    }

    public int getMaxUndergroundOffset() {
        return switch (this.beltTier) {
            case 2 -> 7;
            case 3 -> 9;
            default -> 5;
        };
    }

    public void clearMovementFlags() {
        this.leftLane.resetMovementFlags();
        this.rightLane.resetMovementFlags();
    }

    public boolean isSlotCoveredByHood(int slot) {
        if (this.undergroundMode == UndergroundMode.INPUT) {
            return slot >= 2;
        } else if (this.undergroundMode == UndergroundMode.OUTPUT) {
            return slot <= 1;
        }
        return false;
    }

    @Nullable
    public BeltBlockEntity findPairedOutput(World world, BlockPos pos, Direction facing) {
        if (this.undergroundMode != UndergroundMode.INPUT) {
            return null;
        }
        int maxOffset = getMaxUndergroundOffset();
        for (int d = 1; d <= maxOffset; d++) {
            BlockPos checkPos = pos.offset(facing, d);
            if (world.getBlockEntity(checkPos) instanceof BeltBlockEntity other) {
                if (other.isUnderground() && other.beltTier == this.beltTier) {
                    Direction otherFacing = other.getCachedState().get(BeltBlock.FACING);
                    if (otherFacing == facing) {
                        if (other.undergroundMode == UndergroundMode.INPUT) {
                            return null;
                        }
                        if (other.undergroundMode == UndergroundMode.OUTPUT) {
                            return other;
                        }
                    } else if (otherFacing == facing.getOpposite()) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, BeltBlockEntity entity) {
        int delay = entity.getMoveDelay();
        if (entity.renderTicks < delay) {
            entity.renderTicks++;
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, BeltBlockEntity entity) {
        if (world.isClient()) {
            return;
        }

        long worldTime = world.getTime();
        int delay = entity.getMoveDelay();
        if (worldTime % delay != 0) {
            return;
        }

        stepBeltChain(world, pos, state, entity, worldTime, 0);
    }

    private static void stepBeltChain(World world, BlockPos pos, BlockState state,
                                      BeltBlockEntity entity, long worldTime, int depth) {
        if (entity.lastSteppedWorldTime == worldTime || depth > 64) {
            return;
        }

        if (entity.isSplitter()) {
            BeltBlockEntity leftHalf = entity.getMasterSplitter();
            BeltBlockEntity rightHalf = leftHalf.getOtherSplitterHalf();
            if (rightHalf == null) {
                entity.lastSteppedWorldTime = worldTime;
                return;
            }
            if (leftHalf.lastSteppedWorldTime == worldTime) {
                return;
            }
            leftHalf.lastSteppedWorldTime = worldTime;
            rightHalf.lastSteppedWorldTime = worldTime;

            stepSplitterPair(world, leftHalf, rightHalf, worldTime, depth);
            return;
        }

        entity.lastSteppedWorldTime = worldTime;

        Direction facing = state.get(BeltBlock.FACING);
        boolean hadMovingBefore = entity.leftLane.resetMovementFlags() | entity.rightLane.resetMovementFlags();
        boolean thisChanged = false;

        if (entity.undergroundMode == UndergroundMode.INPUT) {
            BeltBlockEntity pairedOutput = entity.findPairedOutput(world, pos, facing);
            if (pairedOutput != null) {
                stepBeltChain(world, pairedOutput.getPos(), pairedOutput.getCachedState(), pairedOutput, worldTime, depth + 1);

                int distanceBlocks = pos.getManhattanDistance(pairedOutput.getPos());
                int tunnelLength = Math.max(4, Math.min(MAX_TUNNEL_SLOTS, distanceBlocks * 4));

                boolean outChanged = false;
                for (int step = 0; step < entity.beltTier; step++) {
                    if (advanceTunnel(entity.leftLane, entity.leftTunnel, tunnelLength, pairedOutput.leftLane)) {
                        thisChanged = true;
                        outChanged = true;
                    }
                    if (advanceTunnel(entity.rightLane, entity.rightTunnel, tunnelLength, pairedOutput.rightLane)) {
                        thisChanged = true;
                        outChanged = true;
                    }
                }

                if (outChanged) {
                    pairedOutput.markDirty();
                    world.updateListeners(pairedOutput.getPos(), pairedOutput.getCachedState(), pairedOutput.getCachedState(), Block.NOTIFY_ALL);
                    InserterBlockEntity.wakeUpAdjacentInserters(world, pairedOutput.getPos());
                }
            }

            if (entity.leftLane.tickRange(0, 1)) {
                thisChanged = true;
            }
            if (entity.rightLane.tickRange(0, 1)) {
                thisChanged = true;
            }

            if (thisChanged) {
                InserterBlockEntity.wakeUpAdjacentInserters(world, pos);
            }
            if (thisChanged || hadMovingBefore) {
                entity.markDirty();
                world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
            }
            return;
        }

        BlockPos nextPos = pos.offset(facing);
        BlockEntity nextBe = world.getBlockEntity(nextPos);

        if (nextBe instanceof BeltBlockEntity nextBelt) {
            stepBeltChain(world, nextPos, nextBelt.getCachedState(), nextBelt, worldTime, depth + 1);
        }

        boolean nextChanged = false;

        if (nextBe instanceof BeltBlockEntity nextBelt) {
            if (transferBeltToBelt(world, entity, nextPos, nextBelt, facing)) {
                thisChanged = true;
                nextChanged = true;
            }
            if (nextChanged) {
                nextBelt.markDirty();
                world.updateListeners(nextPos, nextBelt.getCachedState(), nextBelt.getCachedState(), Block.NOTIFY_ALL);
                InserterBlockEntity.wakeUpAdjacentInserters(world, nextPos);
            }
        }

        int minSlot = (entity.undergroundMode == UndergroundMode.OUTPUT) ? 2 : 0;
        if (entity.leftLane.tickRange(minSlot, 3)) {
            thisChanged = true;
        }
        if (entity.rightLane.tickRange(minSlot, 3)) {
            thisChanged = true;
        }

        if (thisChanged) {
            InserterBlockEntity.wakeUpAdjacentInserters(world, pos);
        }

        if (thisChanged || hadMovingBefore) {
            entity.markDirty();
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    private static void stepSplitterPair(World world, BeltBlockEntity leftHalf, BeltBlockEntity rightHalf,
                                         long worldTime, int depth) {
        Direction facing = leftHalf.getCachedState().get(BeltBlock.FACING);

        BlockPos nextLeftPos = leftHalf.pos.offset(facing);
        BlockPos nextRightPos = rightHalf.pos.offset(facing);

        if (world.getBlockEntity(nextLeftPos) instanceof BeltBlockEntity nextLeftBelt) {
            stepBeltChain(world, nextLeftPos, nextLeftBelt.getCachedState(), nextLeftBelt, worldTime, depth + 1);
        }
        if (world.getBlockEntity(nextRightPos) instanceof BeltBlockEntity nextRightBelt) {
            stepBeltChain(world, nextRightPos, nextRightBelt.getCachedState(), nextRightBelt, worldTime, depth + 1);
        }

        boolean hadMovingLeft = leftHalf.leftLane.resetMovementFlags() | leftHalf.rightLane.resetMovementFlags();
        boolean hadMovingRight = rightHalf.leftLane.resetMovementFlags() | rightHalf.rightLane.resetMovementFlags();
        boolean leftChanged = false;
        boolean rightChanged = false;

        if (world.getBlockEntity(nextLeftPos) instanceof BeltBlockEntity nextLeftBelt) {
            if (transferBeltToBelt(world, leftHalf, nextLeftPos, nextLeftBelt, facing)) {
                leftChanged = true;
                nextLeftBelt.markDirty();
                world.updateListeners(nextLeftPos, nextLeftBelt.getCachedState(), nextLeftBelt.getCachedState(), Block.NOTIFY_ALL);
                InserterBlockEntity.wakeUpAdjacentInserters(world, nextLeftPos);
            }
        }
        if (world.getBlockEntity(nextRightPos) instanceof BeltBlockEntity nextRightBelt) {
            if (transferBeltToBelt(world, rightHalf, nextRightPos, nextRightBelt, facing)) {
                rightChanged = true;
                nextRightBelt.markDirty();
                world.updateListeners(nextRightPos, nextRightBelt.getCachedState(), nextRightBelt.getCachedState(), Block.NOTIFY_ALL);
                InserterBlockEntity.wakeUpAdjacentInserters(world, nextRightPos);
            }
        }

        if (leftHalf.leftLane.tickRange(2, 3)) leftChanged = true;
        if (leftHalf.rightLane.tickRange(2, 3)) leftChanged = true;
        if (rightHalf.leftLane.tickRange(2, 3)) rightChanged = true;
        if (rightHalf.rightLane.tickRange(2, 3)) rightChanged = true;

        int maskLeftLanes = routeSplitterSubLanes(leftHalf, leftHalf.leftLane, rightHalf.leftLane, true);
        if ((maskLeftLanes & 1) != 0) leftChanged = true;
        if ((maskLeftLanes & 2) != 0) rightChanged = true;

        int maskRightLanes = routeSplitterSubLanes(leftHalf, leftHalf.rightLane, rightHalf.rightLane, false);
        if ((maskRightLanes & 1) != 0) leftChanged = true;
        if ((maskRightLanes & 2) != 0) rightChanged = true;

        if (leftHalf.leftLane.tickRange(0, 1)) leftChanged = true;
        if (leftHalf.rightLane.tickRange(0, 1)) leftChanged = true;
        if (rightHalf.leftLane.tickRange(0, 1)) rightChanged = true;
        if (rightHalf.rightLane.tickRange(0, 1)) rightChanged = true;

        if (leftChanged) {
            InserterBlockEntity.wakeUpAdjacentInserters(world, leftHalf.pos);
        }
        if (rightChanged) {
            InserterBlockEntity.wakeUpAdjacentInserters(world, rightHalf.pos);
        }

        if (leftChanged || hadMovingLeft) {
            leftHalf.markDirty();
            world.updateListeners(leftHalf.pos, leftHalf.getCachedState(), leftHalf.getCachedState(), Block.NOTIFY_ALL);
        }
        if (rightChanged || hadMovingRight) {
            rightHalf.markDirty();
            world.updateListeners(rightHalf.pos, rightHalf.getCachedState(), rightHalf.getCachedState(), Block.NOTIFY_ALL);
        }
    }

    private static int routeSplitterSubLanes(BeltBlockEntity master, BeltLane leftBeltLane, BeltLane rightBeltLane, boolean isLeftSubLane) {
        int changedMask = 0;

        boolean checkRightInputFirst;
        if (master.inputPriorityEnabled) {
            checkRightInputFirst = master.inputPriorityRight;
        } else {
            checkRightInputFirst = isLeftSubLane ? master.nextInRightForLeftLane : master.nextInRightForRightLane;
            if (isLeftSubLane) {
                master.nextInRightForLeftLane = !master.nextInRightForLeftLane;
            } else {
                master.nextInRightForRightLane = !master.nextInRightForRightLane;
            }
        }

        for (int pass = 0; pass < 2; pass++) {
            boolean fromRight = (pass == 0) == checkRightInputFirst;
            BeltLane srcLane = fromRight ? rightBeltLane : leftBeltLane;
            int srcBit = fromRight ? 2 : 1;

            if (!srcLane.hasItemAt(1)) {
                continue;
            }

            ItemStack item = srcLane.getStack(1);

            if (master.outputPriorityEnabled && !master.splitterFilter.isEmpty()) {
                boolean matchesFilter = ItemStack.areItemsEqual(item, master.splitterFilter);
                boolean targetRight = matchesFilter ? master.outputPriorityRight : !master.outputPriorityRight;
                BeltLane targetLane = targetRight ? rightBeltLane : leftBeltLane;
                int dstBit = targetRight ? 2 : 1;

                if (targetLane.canInsertAt(2)) {
                    targetLane.insertAtFromBelt(2, srcLane.extractAt(1));
                    changedMask |= (srcBit | dstBit);
                }
                continue;
            }

            boolean preferRightOut;
            if (master.outputPriorityEnabled) {
                preferRightOut = master.outputPriorityRight;
            } else {
                preferRightOut = isLeftSubLane ? master.nextOutRightForLeftLane : master.nextOutRightForRightLane;
            }

            BeltLane firstOut = preferRightOut ? rightBeltLane : leftBeltLane;
            BeltLane secondOut = preferRightOut ? leftBeltLane : rightBeltLane;
            int firstBit = preferRightOut ? 2 : 1;
            int secondBit = preferRightOut ? 1 : 2;

            if (firstOut.canInsertAt(2)) {
                firstOut.insertAtFromBelt(2, srcLane.extractAt(1));
                changedMask |= (srcBit | firstBit);
                if (!master.outputPriorityEnabled) {
                    if (isLeftSubLane) master.nextOutRightForLeftLane = !preferRightOut;
                    else master.nextOutRightForRightLane = !preferRightOut;
                }
            } else if (secondOut.canInsertAt(2)) {
                secondOut.insertAtFromBelt(2, srcLane.extractAt(1));
                changedMask |= (srcBit | secondBit);
                if (!master.outputPriorityEnabled) {
                    if (isLeftSubLane) master.nextOutRightForLeftLane = preferRightOut;
                    else master.nextOutRightForRightLane = preferRightOut;
                }
            }
        }

        return changedMask;
    }

    private static boolean transferBeltToBelt(World world, BeltBlockEntity fromBelt, BlockPos nextPos,
                                              BeltBlockEntity nextBelt, Direction facing) {
        boolean changed = false;
        Direction nextFacing = nextBelt.getCachedState().get(BeltBlock.FACING);

        if (nextFacing == facing) {
            if (nextBelt.undergroundMode != UndergroundMode.OUTPUT) {
                if (fromBelt.leftLane.canExtract() && nextBelt.leftLane.canInsert()) {
                    nextBelt.leftLane.insertFromBelt(fromBelt.leftLane.extract());
                    changed = true;
                }
                if (fromBelt.rightLane.canExtract() && nextBelt.rightLane.canInsert()) {
                    nextBelt.rightLane.insertFromBelt(fromBelt.rightLane.extract());
                    changed = true;
                }
            }
        } else if (isCornerTurn(world, nextPos, nextBelt, nextFacing, facing)) {
            if (fromBelt.leftLane.canExtract() && nextBelt.leftLane.canInsert()) {
                nextBelt.leftLane.insertFromBelt(fromBelt.leftLane.extract());
                changed = true;
            }
            if (fromBelt.rightLane.canExtract() && nextBelt.rightLane.canInsert()) {
                nextBelt.rightLane.insertFromBelt(fromBelt.rightLane.extract());
                changed = true;
            }
        } else if (facing == nextFacing.rotateYClockwise()) {
            if (!nextBelt.isSlotCoveredByHood(2) && trySideInsert(fromBelt.leftLane, nextBelt.leftLane, 2, nextBelt)) {
                changed = true;
            }
            if (!nextBelt.isSlotCoveredByHood(1) && trySideInsert(fromBelt.rightLane, nextBelt.leftLane, 1, nextBelt)) {
                changed = true;
            }
        } else if (facing == nextFacing.rotateYCounterclockwise()) {
            if (!nextBelt.isSlotCoveredByHood(2) && trySideInsert(fromBelt.rightLane, nextBelt.rightLane, 2, nextBelt)) {
                changed = true;
            }
            if (!nextBelt.isSlotCoveredByHood(1) && trySideInsert(fromBelt.leftLane, nextBelt.rightLane, 1, nextBelt)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean advanceTunnel(BeltLane inputLane, ItemStack[] tunnel, int tunnelLength, BeltLane outputLane) {
        boolean moved = false;

        int lastIdx = tunnelLength - 1;
        if (!tunnel[lastIdx].isEmpty() && outputLane.canInsertAt(2)) {
            outputLane.insertAtFromBelt(2, tunnel[lastIdx]);
            tunnel[lastIdx] = ItemStack.EMPTY;
            moved = true;
        }

        for (int i = lastIdx - 1; i >= 0; i--) {
            if (!tunnel[i].isEmpty() && tunnel[i + 1].isEmpty()) {
                tunnel[i + 1] = tunnel[i];
                tunnel[i] = ItemStack.EMPTY;
                moved = true;
            }
        }

        if (inputLane.hasItemAt(1) && tunnel[0].isEmpty()) {
            tunnel[0] = inputLane.extractAt(1);
            moved = true;
        }

        return moved;
    }

    private static boolean isCornerTurn(World world, BlockPos nextPos, BeltBlockEntity nextBelt,
                                        Direction nextFacing, Direction incomingFacing) {
        if (nextBelt.isUnderground() || nextBelt.isSplitter()) {
            return false;
        }
        if (incomingFacing != nextFacing.rotateYClockwise() && incomingFacing != nextFacing.rotateYCounterclockwise()) {
            return false;
        }

        BlockPos backPos = nextPos.offset(nextFacing.getOpposite());
        if (world.getBlockEntity(backPos) instanceof BeltBlockEntity backBelt) {
            if (backBelt.getCachedState().get(BeltBlock.FACING) == nextFacing
                    && backBelt.undergroundMode != UndergroundMode.INPUT) {
                return false;
            }
        }

        BlockPos oppositeSidePos = nextPos.offset(incomingFacing);
        if (world.getBlockEntity(oppositeSidePos) instanceof BeltBlockEntity otherSideBelt) {
            if (otherSideBelt.getCachedState().get(BeltBlock.FACING) == incomingFacing.getOpposite()
                    && otherSideBelt.undergroundMode != UndergroundMode.INPUT) {
                return false;
            }
        }

        return true;
    }

    private static boolean trySideInsert(BeltLane fromLane, BeltLane toLane, int targetSlot, BeltBlockEntity targetBelt) {
        if (!fromLane.canExtract()) {
            return false;
        }
        if (!targetBelt.isSlotCoveredByHood(targetSlot) && toLane.canInsertAt(targetSlot)) {
            toLane.insertAt(targetSlot, fromLane.extract());
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getMasterSplitter().splitterFilter.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot == 0) {
            return getMasterSplitter().splitterFilter;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0) {
            BeltBlockEntity master = getMasterSplitter();
            master.splitterFilter = ItemStack.EMPTY;
            master.markDirty();
            if (master.world != null) {
                master.world.updateListeners(master.pos, master.getCachedState(), master.getCachedState(), Block.NOTIFY_ALL);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return removeStack(slot, 1);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            BeltBlockEntity master = getMasterSplitter();
            master.splitterFilter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            if (!stack.isEmpty()) {
                master.outputPriorityEnabled = true;
            }
            master.markDirty();
            if (master.world != null) {
                master.world.updateListeners(master.pos, master.getCachedState(), master.getCachedState(), Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        getMasterSplitter().splitterFilter = ItemStack.EMPTY;
    }

    @Override
    public Text getDisplayName() {
        return getMasterSplitter().getCachedState().getBlock().getName();
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        BeltBlockEntity master = getMasterSplitter();
        return new SplitterScreenHandler(syncId, playerInventory, master, master.splitterPropertyDelegate);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return getMasterSplitter().getPos();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString(UNDERGROUND_MODE_KEY, this.undergroundMode.name());
        nbt.putInt(BELT_TIER_KEY, this.beltTier);

        nbt.putBoolean("InPriority", this.inputPriorityEnabled);
        nbt.putBoolean("InRight", this.inputPriorityRight);
        nbt.putBoolean("OutPriority", this.outputPriorityEnabled);
        nbt.putBoolean("OutRight", this.outputPriorityRight);
        if (!this.splitterFilter.isEmpty()) {
            nbt.put("SplitterFilter", this.splitterFilter.encode(registries));
        }

        NbtCompound leftTag = new NbtCompound();
        this.leftLane.writeNbt(leftTag, registries);
        nbt.put(LEFT_LANE_NBT_KEY, leftTag);

        NbtCompound rightTag = new NbtCompound();
        this.rightLane.writeNbt(rightTag, registries);
        nbt.put(RIGHT_LANE_NBT_KEY, rightTag);

        writeTunnelNbt(nbt, LEFT_TUNNEL_KEY, this.leftTunnel, registries);
        writeTunnelNbt(nbt, RIGHT_TUNNEL_KEY, this.rightTunnel, registries);
    }

    private void writeTunnelNbt(NbtCompound nbt, String key, ItemStack[] tunnel, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (int i = 0; i < tunnel.length; i++) {
            if (!tunnel[i].isEmpty()) {
                NbtCompound entry = new NbtCompound();
                entry.putByte("Slot", (byte) i);
                entry.put("Stack", tunnel[i].encode(registries));
                list.add(entry);
            }
        }
        if (!list.isEmpty()) {
            nbt.put(key, list);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains(UNDERGROUND_MODE_KEY)) {
            try {
                this.undergroundMode = UndergroundMode.valueOf(nbt.getString(UNDERGROUND_MODE_KEY));
            } catch (IllegalArgumentException e) {
                this.undergroundMode = UndergroundMode.NONE;
            }
        }
        if (nbt.contains(BELT_TIER_KEY)) {
            this.beltTier = Math.max(1, Math.min(3, nbt.getInt(BELT_TIER_KEY)));
        }

        this.inputPriorityEnabled = nbt.getBoolean("InPriority");
        this.inputPriorityRight = nbt.getBoolean("InRight");
        this.outputPriorityEnabled = nbt.getBoolean("OutPriority");
        this.outputPriorityRight = nbt.getBoolean("OutRight");
        if (nbt.contains("SplitterFilter")) {
            this.splitterFilter = ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("SplitterFilter"));
        } else {
            this.splitterFilter = ItemStack.EMPTY;
        }

        if (nbt.contains(LEFT_LANE_NBT_KEY)) {
            this.leftLane.readNbt(nbt.getCompound(LEFT_LANE_NBT_KEY), registries);
        }
        if (nbt.contains(RIGHT_LANE_NBT_KEY)) {
            this.rightLane.readNbt(nbt.getCompound(RIGHT_LANE_NBT_KEY), registries);
        }
        readTunnelNbt(nbt, LEFT_TUNNEL_KEY, this.leftTunnel, registries);
        readTunnelNbt(nbt, RIGHT_TUNNEL_KEY, this.rightTunnel, registries);

        if (this.world != null && this.world.isClient()) {
            this.renderTicks = 0;
        }
    }

    private void readTunnelNbt(NbtCompound nbt, String key, ItemStack[] tunnel, RegistryWrapper.WrapperLookup registries) {
        Arrays.fill(tunnel, ItemStack.EMPTY);
        if (!nbt.contains(key, NbtElement.LIST_TYPE)) return;
        NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < tunnel.length && entry.contains("Stack")) {
                tunnel[slot] = ItemStack.fromNbtOrEmpty(registries, entry.getCompound("Stack"));
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}