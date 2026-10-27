package com.fixplot.factoriocore.content.logistics.inserter;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterBlock;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterScreenHandler;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class InserterBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    // Каноничные тайминги полуоборота (180°) из Factorio в тиках Minecraft (20 TPS):
    public static final int BURNER_SWING_TICKS = 15; // Полный цикл 30т (1.50 с -> 0.66 предм/с)
    public static final int BASIC_SWING_TICKS = 12;  // Полный цикл 24т (1.20 с -> 0.83 предм/с)
    public static final int LONG_SWING_TICKS = 8;    // Полный цикл 16т (0.80 с -> 1.25 предм/с)
    public static final int FAST_SWING_TICKS = 4;    // Полный цикл 8т  (0.40 с -> 2.50 предм/с)

    public static final int MAX_STACK_CAPACITY = 4;
    public static final int FILTER_SLOTS_COUNT = 5;
    public static final int HAND_SLOT_INDEX = 5;
    public static final int FUEL_SLOT_INDEX = 6;

    public static final int FURNACE_INPUT_LIMIT = 5;
    public static final int BURNER_FUEL_LIMIT = 5;

    public static final int ACTION_TOGGLE_FILTER = 1000;
    public static final int ACTION_TOGGLE_BLACKLIST = 1001;
    public static final int ACTION_TOGGLE_OVERRIDE_STACK = 1002;
    public static final int ACTION_SET_STACK_BASE = 2000;

    public static final long ELECTRIC_ACTIVE_WATTS = 13_000L;
    public static final long LONG_ACTIVE_WATTS = 18_000L;
    public static final long FAST_ACTIVE_WATTS = 46_000L;
    public static final long ELECTRIC_IDLE_WATTS = 400L;

    private ItemStack heldStack = ItemStack.EMPTY;
    private ItemStack fuelStack = ItemStack.EMPTY;
    private int burnTime = 0;
    private int maxBurnTime = 0;

    private int maxPickupCount = 1;
    private boolean filterEnabled = false;
    private boolean blacklistMode = false;
    private boolean overrideStackSize = false;

    private int ticksUntilDelivery = 0;
    private int returnTicks = 0;
    private boolean sleeping = false;

    private int clientSwingTicks = 0;
    private int clientReturnTicks = 0;
    private boolean clientOccluded = false;
    private long lastOcclusionCheckTime = -100L;

    private boolean cachedPowered = false;
    private long lastPowerCheckTick = -100L;

    private final DefaultedList<ItemStack> filterSlots = DefaultedList.ofSize(FILTER_SLOTS_COUNT, ItemStack.EMPTY);

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> maxPickupCount;
                case 1 -> sleeping ? 1 : 0;
                case 2 -> filterEnabled ? 1 : 0;
                case 3 -> blacklistMode ? 1 : 0;
                case 4 -> overrideStackSize ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> maxPickupCount = value;
                case 1 -> sleeping = (value != 0);
                case 2 -> filterEnabled = (value != 0);
                case 3 -> blacklistMode = (value != 0);
                case 4 -> overrideStackSize = (value != 0);
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };

    protected final PropertyDelegate burnerPropertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> sleeping ? 1 : 0;
                case 1 -> burnTime;
                case 2 -> maxBurnTime;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> sleeping = (value != 0);
                case 1 -> burnTime = value;
                case 2 -> maxBurnTime = value;
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };

    public InserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.INSERTER_BLOCK_ENTITY, pos, state);
    }

    public boolean isLongHanded() {
        return getCachedState().isOf(ModBlocks.LONG_HANDED_INSERTER);
    }

    public boolean isFastInserter() {
        return getCachedState().isOf(ModBlocks.FAST_INSERTER);
    }

    public boolean isBurner() {
        return getCachedState().getBlock() instanceof BurnerInserterBlock;
    }

    public int getReachDistance() {
        return isLongHanded() ? 2 : 1;
    }

    public int getSwingDuration() {
        if (isBurner()) return BURNER_SWING_TICKS;
        if (isFastInserter()) return FAST_SWING_TICKS;
        if (isLongHanded()) return LONG_SWING_TICKS;
        return BASIC_SWING_TICKS;
    }

    public long getActiveConsumptionWatts() {
        if (isBurner()) return 0L;
        if (isFastInserter()) return FAST_ACTIVE_WATTS;
        if (isLongHanded()) return LONG_ACTIVE_WATTS;
        return ELECTRIC_ACTIVE_WATTS;
    }

    public void wakeUp() {
        if (this.sleeping) {
            this.sleeping = false;
        }
    }

    public static void wakeUpAdjacentInserters(World world, BlockPos sourcePos) {
        if (world == null || world.isClient()) return;

        wakeAroundPos(world, sourcePos);

        BlockState state = world.getBlockState(sourcePos);
        if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)) {
            switch (state.get(ChestBlock.CHEST_TYPE)) {
                case LEFT, RIGHT -> {
                    Direction chestConnectedDir = ChestBlock.getFacing(state);
                    wakeAroundPos(world, sourcePos.offset(chestConnectedDir));
                }
                default -> {}
            }
        }
    }

    private static void wakeAroundPos(World world, BlockPos center) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            // Проверяем дистанцию 1 (обычные/быстрые/угольные) и дистанцию 2 (длинные красные манипуляторы)
            for (int dist = 1; dist <= 2; dist++) {
                BlockPos neighborPos = center.offset(dir, dist);
                if (world.getBlockState(neighborPos).getBlock() instanceof InserterBlock) {
                    if (world.getBlockEntity(neighborPos) instanceof InserterBlockEntity inserter) {
                        if (inserter.getReachDistance() == dist) {
                            inserter.wakeUp();
                        }
                    }
                }
            }
        }
    }

    public void changeMaxPickupCount(int delta) {
        if (isBurner()) return;

        if (delta == ACTION_TOGGLE_FILTER) {
            this.filterEnabled = !this.filterEnabled;
        } else if (delta == ACTION_TOGGLE_BLACKLIST) {
            this.blacklistMode = !this.blacklistMode;
        } else if (delta == ACTION_TOGGLE_OVERRIDE_STACK) {
            this.overrideStackSize = !this.overrideStackSize;
        } else if (delta > ACTION_SET_STACK_BASE && delta <= ACTION_SET_STACK_BASE + MAX_STACK_CAPACITY) {
            this.maxPickupCount = Math.max(1, Math.min(MAX_STACK_CAPACITY, delta - ACTION_SET_STACK_BASE));
            this.overrideStackSize = true;
        } else {
            this.maxPickupCount = Math.max(1, Math.min(MAX_STACK_CAPACITY, this.maxPickupCount + delta));
            this.overrideStackSize = true;
        }
        this.wakeUp();
        syncState();
    }

    public boolean isClientOccluded(World world) {
        if (world == null) return false;
        long now = world.getTime();
        if (now - this.lastOcclusionCheckTime >= 40L) {
            this.lastOcclusionCheckTime = now + (this.pos.asLong() & 15L);
            this.clientOccluded = !world.getBlockState(this.pos.up()).isAir()
                    && !world.getBlockState(this.pos.north()).isAir()
                    && !world.getBlockState(this.pos.south()).isAir()
                    && !world.getBlockState(this.pos.west()).isAir()
                    && !world.getBlockState(this.pos.east()).isAir();
        }
        return this.clientOccluded;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, InserterBlockEntity entity) {
        int duration = entity.getSwingDuration();
        if (!entity.heldStack.isEmpty()) {
            if (entity.clientSwingTicks < duration) {
                entity.clientSwingTicks++;
            }
        } else if (entity.clientReturnTicks > 0) {
            entity.clientReturnTicks--;
        }
    }

    public float getArmProgress(float tickDelta) {
        int duration = Math.max(1, getSwingDuration());
        if (!this.heldStack.isEmpty()) {
            if (this.clientSwingTicks >= duration) {
                return 1.0f;
            }
            return Math.min(1.0f, (this.clientSwingTicks + tickDelta) / (float) duration);
        } else {
            if (this.clientReturnTicks <= 0) {
                return 0.0f;
            }
            return Math.max(0.0f, (this.clientReturnTicks - tickDelta) / (float) duration);
        }
    }

    public boolean hasElectricity() {
        if (isBurner()) return true;
        if (this.world == null) return false;
        long now = this.world.getTime();
        if (now - this.lastPowerCheckTick >= 10L) {
            this.lastPowerCheckTick = now + (this.pos.asLong() & 7L);
            this.cachedPowered = PowerNetworkManager.isMachinePowered(this.world, this.pos);
        }
        return this.cachedPowered;
    }

    private static boolean hasItemOnGroundAt(World world, BlockPos targetPos) {
        Box checkBox = new Box(targetPos).expand(0.25, 0.4, 0.25);
        return !world.getEntitiesByClass(
                ItemEntity.class,
                checkBox,
                item -> item.isAlive() && !item.getStack().isEmpty()
        ).isEmpty();
    }

    private int getEffectivePickupCount() {
        if (isBurner()) {
            return 1;
        }
        return this.overrideStackSize ? Math.min(MAX_STACK_CAPACITY, this.maxPickupCount) : 1;
    }

    private boolean ensureBurnerFuel() {
        if (!isBurner()) {
            return true;
        }
        if (this.burnTime > 0) {
            return true;
        }
        if (!this.fuelStack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(this.fuelStack)) {
            Integer fuelTicks = FuelRegistry.INSTANCE.get(this.fuelStack.getItem());
            int ticks = (fuelTicks != null && fuelTicks > 0) ? fuelTicks : 200;
            this.burnTime = ticks;
            this.maxBurnTime = ticks;

            Item remainder = this.fuelStack.getItem().getRecipeRemainder();
            this.fuelStack.decrement(1);
            if (this.fuelStack.isEmpty() && remainder != null) {
                this.fuelStack = new ItemStack(remainder);
            }
            syncState();
            return true;
        }
        return false;
    }

    private boolean trySelfRefuelFromSource(World world, BlockPos backPos) {
        if (!isBurner() || !this.fuelStack.isEmpty()) {
            return false;
        }
        if (world.getBlockState(backPos).isAir() && ((world.getTime() + this.pos.asLong()) & 7L) == 0L) {
            Box groundBox = new Box(backPos).expand(0.35, 0.5, 0.35);
            var groundItems = world.getEntitiesByClass(
                    ItemEntity.class, groundBox,
                    item -> item.isAlive() && !item.getStack().isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(item.getStack()));

            for (ItemEntity itemEntity : groundItems) {
                ItemStack groundStack = itemEntity.getStack();
                this.fuelStack = groundStack.split(1);
                if (groundStack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setStack(groundStack);
                }
                ensureBurnerFuel();
                return true;
            }
            return false;
        }
        BlockEntity backBe = world.getBlockEntity(backPos);
        if (backBe instanceof BeltBlockEntity backBelt) {
            BeltLane[] lanes = { backBelt.getLeftLane(), backBelt.getRightLane() };
            for (BeltLane lane : lanes) {
                for (int slot = 3; slot >= 0; slot--) {
                    if (lane.hasItemAt(slot) && AbstractFurnaceBlockEntity.canUseAsFuel(lane.getStack(slot))) {
                        this.fuelStack = lane.extractAt(slot);
                        BeltBlock.syncManualChange(world, backPos, backBelt.getCachedState(), backBelt);
                        ensureBurnerFuel();
                        return true;
                    }
                }
            }
        } else {
            Inventory backInv = getInventoryAt(world, backPos, false);
            if (backInv != null) {
                for (int i = 0; i < backInv.size(); i++) {
                    ItemStack stack = backInv.getStack(i);
                    if (!stack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(stack)) {
                        if (backInv instanceof SidedInventory sided && !sided.canExtract(i, stack, Direction.DOWN)) {
                            continue;
                        }
                        this.fuelStack = backInv.removeStack(i, 1);
                        backInv.markDirty();
                        ensureBurnerFuel();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) return;

        if (this.sleeping) {
            // Быстрый опрос раз в 8 тиков (0.4 сек) на случай появления предметов на земле
            if (((world.getTime() + pos.asLong()) & 7L) != 0L) {
                return;
            }
            this.sleeping = false;
        }

        Direction facing = state.get(InserterBlock.FACING);
        int reach = getReachDistance();
        BlockPos backPos = pos.offset(facing.getOpposite(), reach);
        BlockPos frontPos = pos.offset(facing, reach);

        if (isBurner() && this.fuelStack.isEmpty() && this.heldStack.isEmpty()) {
            trySelfRefuelFromSource(world, backPos);
        }

        if (isBurner() && !ensureBurnerFuel()) {
            this.sleeping = true;
            return;
        }
        if (!isBurner() && !hasElectricity()) {
            this.sleeping = true;
            return;
        }

        if (this.ticksUntilDelivery > 0) {
            if (isBurner() && this.burnTime > 0) this.burnTime--;
            this.ticksUntilDelivery--;
            return;
        }

        if (this.returnTicks > 0) {
            if (isBurner() && this.burnTime > 0) this.burnTime--;
            this.returnTicks--;
            return;
        }

        if (this.heldStack.isEmpty() && world.getBlockState(backPos).isAir() && ((world.getTime() + pos.asLong()) & 7L) == 0L) {
            Box groundBox = new Box(backPos).expand(0.35, 0.5, 0.35);
            var groundItems = world.getEntitiesByClass(
                    ItemEntity.class, groundBox, item -> item.isAlive() && !item.getStack().isEmpty());

            for (ItemEntity itemEntity : groundItems) {
                ItemStack groundStack = itemEntity.getStack();
                int allowed = getAllowedCountForTarget(world, frontPos, groundStack, getEffectivePickupCount());
                if (matchesFilter(groundStack) && allowed > 0) {
                    int takeCount = Math.min(allowed, groundStack.getCount());
                    this.heldStack = groundStack.split(takeCount);
                    if (groundStack.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setStack(groundStack);
                    }
                    if (isBurner() && this.burnTime > 0) this.burnTime--;
                    this.ticksUntilDelivery = getSwingDuration();
                    syncState();
                    return;
                }
            }
        }

        if (this.heldStack.isEmpty()) {
            BlockEntity backBe = world.getBlockEntity(backPos);

            if (backBe instanceof BeltBlockEntity backBelt) {
                ItemStack extracted = extractFromBelt(world, backPos, backBelt, frontPos, facing);
                if (!extracted.isEmpty()) {
                    if (isBurner() && this.burnTime > 0) this.burnTime--;
                    this.heldStack = extracted;
                    this.ticksUntilDelivery = getSwingDuration();
                    syncState();
                    return;
                }
            } else {
                Inventory backInv = getInventoryAt(world, backPos, false);
                if (backInv != null) {
                    ItemStack extracted = extractFromInventory(world, backInv, frontPos, getEffectivePickupCount());
                    if (!extracted.isEmpty()) {
                        if (isBurner() && this.burnTime > 0) this.burnTime--;
                        this.heldStack = extracted;
                        this.ticksUntilDelivery = getSwingDuration();
                        syncState();
                        return;
                    }
                }
            }

            this.sleeping = true;
        } else {
            BlockEntity frontBe = world.getBlockEntity(frontPos);

            if (frontBe instanceof BeltBlockEntity frontBelt) {
                if (insertOntoBelt(world, frontPos, frontBelt, facing, this.heldStack)) {
                    this.heldStack.decrement(1);
                    if (this.heldStack.isEmpty()) {
                        this.heldStack = ItemStack.EMPTY;
                        this.returnTicks = getSwingDuration();
                    } else {
                        this.ticksUntilDelivery = isFastInserter() ? 1 : 2;
                    }
                    syncState();
                    return;
                }
            } else {
                Inventory frontInv = getInventoryAt(world, frontPos, true);
                if (frontInv != null) {
                    int before = this.heldStack.getCount();
                    this.heldStack = insertIntoInventory(frontInv, this.heldStack);
                    if (this.heldStack.isEmpty()) {
                        this.returnTicks = getSwingDuration();
                        syncState();
                        return;
                    } else if (this.heldStack.getCount() != before) {
                        syncState();
                    }
                } else if (world.getBlockState(frontPos).getCollisionShape(world, frontPos).isEmpty() && !hasItemOnGroundAt(world, frontPos)) {
                    ItemStack toDrop = this.heldStack.split(1);
                    if (this.heldStack.isEmpty()) {
                        this.heldStack = ItemStack.EMPTY;
                    }

                    ItemEntity dropped = new ItemEntity(
                            world,
                            frontPos.getX() + 0.5,
                            frontPos.getY() + 0.15,
                            frontPos.getZ() + 0.5,
                            toDrop
                    );
                    dropped.setVelocity(0.0, 0.0, 0.0);
                    dropped.setToDefaultPickupDelay();
                    world.spawnEntity(dropped);

                    if (this.heldStack.isEmpty()) {
                        this.returnTicks = getSwingDuration();
                    } else {
                        this.ticksUntilDelivery = isFastInserter() ? 2 : 4;
                    }
                    wakeUpAdjacentInserters(world, frontPos);
                    syncState();
                    return;
                }
            }

            this.sleeping = true;
        }
    }

    private int getAllowedCountForTarget(World world, BlockPos frontPos, ItemStack candidate, int maxRequested) {
        if (candidate.isEmpty()) return 0;

        BlockEntity frontBe = world.getBlockEntity(frontPos);
        if (frontBe instanceof BeltBlockEntity) {
            return 1;
        }

        Inventory frontInv = getInventoryAt(world, frontPos, true);
        if (frontInv == null) {
            if (world.getBlockState(frontPos).getCollisionShape(world, frontPos).isEmpty()) {
                return hasItemOnGroundAt(world, frontPos) ? 0 : 1;
            }
            return 0;
        }

        if (frontInv instanceof FactorioFurnaceBlockEntity factorioFurnace) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(candidate)) {
                return getRemainingSpaceInSlot(factorioFurnace, 1, candidate, BURNER_FUEL_LIMIT, maxRequested);
            } else {
                return getRemainingSpaceInSlot(factorioFurnace, 0, candidate, FURNACE_INPUT_LIMIT, maxRequested);
            }
        }

        if (frontInv instanceof InserterBlockEntity burnerTarget && burnerTarget.isBurner()) {
            if (!AbstractFurnaceBlockEntity.canUseAsFuel(candidate)) {
                return 0;
            }
            return getRemainingSpaceInSlot(burnerTarget, FUEL_SLOT_INDEX, candidate, BURNER_FUEL_LIMIT, maxRequested);
        }

        if (frontInv instanceof BurnerDrillBlockEntity drillTarget) {
            if (!AbstractFurnaceBlockEntity.canUseAsFuel(candidate)) {
                return 0;
            }
            return getRemainingSpaceInSlot(drillTarget.getMaster(), 0, candidate, BURNER_FUEL_LIMIT, maxRequested);
        }

        if (frontInv instanceof AbstractFurnaceBlockEntity furnace) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(candidate)) {
                return getRemainingSpaceInSlot(furnace, 1, candidate, BURNER_FUEL_LIMIT, maxRequested);
            } else {
                return getRemainingSpaceInSlot(furnace, 0, candidate, FURNACE_INPUT_LIMIT, maxRequested);
            }
        }

        if (frontInv instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity turretTarget) {
            return getRemainingSpaceInSlot(turretTarget.getMaster(), 0, candidate,
                    com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity.INSERTER_AMMO_LIMIT, maxRequested);
        }

        int totalSpace = 0;
        for (int i = 0; i < frontInv.size(); i++) {
            if (!frontInv.isValid(i, candidate)) continue;
            ItemStack current = frontInv.getStack(i);
            if (current.isEmpty()) {
                return maxRequested;
            } else if (ItemStack.areItemsAndComponentsEqual(current, candidate)) {
                int maxSlot = Math.min(frontInv.getMaxCount(candidate), current.getMaxCount());
                totalSpace += Math.max(0, maxSlot - current.getCount());
                if (totalSpace >= maxRequested) {
                    return maxRequested;
                }
            }
        }
        return Math.min(totalSpace, maxRequested);
    }

    private int getRemainingSpaceInSlot(Inventory inv, int slot, ItemStack candidate, int factorioLimit, int maxRequested) {
        if (!inv.isValid(slot, candidate)) {
            return 0;
        }
        ItemStack current = inv.getStack(slot);
        int cap = Math.min(factorioLimit, Math.min(inv.getMaxCount(candidate), candidate.getMaxCount()));
        if (current.isEmpty()) {
            return Math.min(cap, maxRequested);
        }
        if (ItemStack.areItemsAndComponentsEqual(current, candidate)) {
            int space = cap - current.getCount();
            return space > 0 ? Math.min(space, maxRequested) : 0;
        }
        return 0;
    }

    private boolean matchesFilter(ItemStack candidate) {
        if (isBurner() || !this.filterEnabled) {
            return true;
        }
        boolean anyFilterConfigured = false;
        boolean matched = false;

        for (int i = 0; i < FILTER_SLOTS_COUNT; i++) {
            ItemStack filter = this.filterSlots.get(i);
            if (!filter.isEmpty()) {
                anyFilterConfigured = true;
                if (ItemStack.areItemsEqual(candidate, filter)) {
                    matched = true;
                    break;
                }
            }
        }

        if (!anyFilterConfigured) {
            return true;
        }
        return this.blacklistMode ? !matched : matched;
    }

    private ItemStack extractFromBelt(World world, BlockPos beltPos, BeltBlockEntity belt,
                                      BlockPos frontPos, Direction inserterFacing) {
        Direction beltFacing = belt.getCachedState().get(BeltBlock.FACING);

        BeltLane firstLane;
        BeltLane secondLane;

        if (beltFacing == inserterFacing.rotateYClockwise()) {
            firstLane = belt.getRightLane();
            secondLane = belt.getLeftLane();
        } else if (beltFacing == inserterFacing.rotateYCounterclockwise()) {
            firstLane = belt.getLeftLane();
            secondLane = belt.getRightLane();
        } else {
            firstLane = belt.getLeftLane();
            secondLane = belt.getRightLane();
        }

        int[] slotOrder = (beltFacing == inserterFacing)
                ? new int[]{ 3, 2, 1, 0 }
                : new int[]{ 2, 1, 3, 0 };

        ItemStack extracted = tryExtractFromLane(world, firstLane, slotOrder, frontPos);
        if (extracted.isEmpty()) {
            extracted = tryExtractFromLane(world, secondLane, slotOrder, frontPos);
        }

        if (!extracted.isEmpty()) {
            BeltBlock.syncManualChange(world, beltPos, belt.getCachedState(), belt);
        }
        return extracted;
    }

    private ItemStack tryExtractFromLane(World world, BeltLane lane, int[] slotOrder, BlockPos frontPos) {
        for (int slot : slotOrder) {
            if (lane.hasItemAt(slot)) {
                ItemStack candidate = lane.getStack(slot);
                if (matchesFilter(candidate) && getAllowedCountForTarget(world, frontPos, candidate, 1) > 0) {
                    return lane.extractAt(slot);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean insertOntoBelt(World world, BlockPos beltPos, BeltBlockEntity belt,
                                   Direction inserterFacing, ItemStack stackToInsert) {
        Direction beltFacing = belt.getCachedState().get(BeltBlock.FACING);
        boolean inserted = false;

        if (beltFacing == inserterFacing.rotateYClockwise()) {
            BeltLane farLane = belt.getLeftLane();
            inserted = farLane.insertAt(2, stackToInsert) || farLane.insertAt(1, stackToInsert);
        } else if (beltFacing == inserterFacing.rotateYCounterclockwise()) {
            BeltLane farLane = belt.getRightLane();
            inserted = farLane.insertAt(2, stackToInsert) || farLane.insertAt(1, stackToInsert);
        } else if (beltFacing == inserterFacing) {
            BeltLane right = belt.getRightLane();
            inserted = right.insertAt(0, stackToInsert) || right.insertAt(1, stackToInsert);
        } else if (beltFacing == inserterFacing.getOpposite()) {
            BeltLane left = belt.getLeftLane();
            inserted = left.insertAt(3, stackToInsert) || left.insertAt(2, stackToInsert);
        }

        if (inserted) {
            BeltBlock.syncManualChange(world, beltPos, belt.getCachedState(), belt);
        }
        return inserted;
    }

    @Nullable
    private Inventory getInventoryAt(World world, BlockPos targetPos, boolean forInsertion) {
        BlockState state = world.getBlockState(targetPos);
        Block block = state.getBlock();

        BlockPos canonicalPos = BlockHealthManager.getCanonicalPos(targetPos, state);
        if (block instanceof ChestBlock chestBlock && world.getBlockEntity(canonicalPos) instanceof ChestBlockEntity) {
            Inventory chestInv = ChestBlock.getInventory(chestBlock, state, world, targetPos, true);
            if (chestInv != null) {
                return chestInv;
            }
        }

        BlockEntity be = world.getBlockEntity(canonicalPos);
        if (be instanceof BeltBlockEntity) {
            return null;
        }
        if (be instanceof InserterBlockEntity targetInserter) {
            return (forInsertion && targetInserter.isBurner()) ? targetInserter : null;
        }
        return (be instanceof Inventory inv) ? inv : null;
    }

    private ItemStack extractFromInventory(World world, Inventory inv, BlockPos frontPos, int amount) {
        if (inv instanceof InserterBlockEntity) {
            return ItemStack.EMPTY;
        }
        if (inv instanceof AbstractFurnaceBlockEntity furnace) {
            ItemStack output = furnace.getStack(2);
            if (!output.isEmpty() && matchesFilter(output)) {
                int allowed = getAllowedCountForTarget(world, frontPos, output, amount);
                if (allowed > 0) {
                    int toTake = Math.min(allowed, output.getMaxCount());
                    ItemStack extracted = furnace.removeStack(2, toTake);
                    furnace.markDirty();
                    return extracted;
                }
            }
            ItemStack fuel = furnace.getStack(1);
            if (fuel.isOf(Items.BUCKET) && matchesFilter(fuel)) {
                if (getAllowedCountForTarget(world, frontPos, fuel, 1) > 0) {
                    ItemStack extracted = furnace.removeStack(1, 1);
                    furnace.markDirty();
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && matchesFilter(stack)) {
                if (inv instanceof SidedInventory sidedInv && !sidedInv.canExtract(i, stack, Direction.DOWN)) {
                    continue;
                }
                int allowed = getAllowedCountForTarget(world, frontPos, stack, amount);
                if (allowed <= 0) {
                    continue;
                }
                int toTake = Math.min(allowed, stack.getMaxCount());
                ItemStack extracted = inv.removeStack(i, toTake);
                inv.markDirty();
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack insertIntoInventory(Inventory inv, ItemStack stack) {
        ItemStack remaining = stack.copy();

        if (inv instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity turretTarget) {
            return tryInsertIntoSlotWithLimit(turretTarget.getMaster(), 0, remaining,
                    com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity.INSERTER_AMMO_LIMIT);
        }

        if (inv instanceof BurnerDrillBlockEntity drillTarget) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(remaining)) {
                return tryInsertIntoSlotWithLimit(drillTarget.getMaster(), 0, remaining, BURNER_FUEL_LIMIT);
            }
            return remaining;
        }

        if (inv instanceof FactorioFurnaceBlockEntity factorioFurnace) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(remaining)) {
                return tryInsertIntoSlotWithLimit(factorioFurnace, 1, remaining, BURNER_FUEL_LIMIT);
            } else {
                return tryInsertIntoSlotWithLimit(factorioFurnace, 0, remaining, FURNACE_INPUT_LIMIT);
            }
        }

        if (inv instanceof InserterBlockEntity burnerTarget && burnerTarget.isBurner()) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(remaining)) {
                return tryInsertIntoSlotWithLimit(burnerTarget, FUEL_SLOT_INDEX, remaining, BURNER_FUEL_LIMIT);
            }
            return remaining;
        }

        if (inv instanceof AbstractFurnaceBlockEntity furnace) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(remaining)) {
                remaining = tryInsertIntoSlotWithLimit(furnace, 1, remaining, BURNER_FUEL_LIMIT);
            } else {
                remaining = tryInsertIntoSlotWithLimit(furnace, 0, remaining, FURNACE_INPUT_LIMIT);
            }
            return remaining;
        }

        for (int i = 0; i < inv.size(); i++) {
            if (!inv.isValid(i, remaining)) {
                continue;
            }
            remaining = tryInsertIntoSlotWithLimit(inv, i, remaining, remaining.getMaxCount());
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private ItemStack tryInsertIntoSlotWithLimit(Inventory inv, int slot, ItemStack remaining, int customLimit) {
        if (remaining.isEmpty() || !inv.isValid(slot, remaining)) {
            return remaining;
        }
        ItemStack current = inv.getStack(slot);
        int maxAllowed = Math.min(customLimit, Math.min(inv.getMaxCount(remaining), remaining.getMaxCount()));

        if (current.isEmpty()) {
            int toMove = Math.min(remaining.getCount(), maxAllowed);
            if (toMove > 0) {
                inv.setStack(slot, remaining.split(toMove));
                inv.markDirty();
            }
        } else if (ItemStack.areItemsAndComponentsEqual(current, remaining)) {
            int space = maxAllowed - current.getCount();
            int toMove = Math.min(space, remaining.getCount());
            if (toMove > 0) {
                current.increment(toMove);
                remaining.decrement(toMove);
                inv.markDirty();
            }
        }
        return remaining;
    }

    private void syncState() {
        markDirty();
        if (this.world != null) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    public int size() {
        return FILTER_SLOTS_COUNT + 2;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == FUEL_SLOT_INDEX) {
            return isBurner() && AbstractFurnaceBlockEntity.canUseAsFuel(stack);
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : this.filterSlots) {
            if (!s.isEmpty()) return false;
        }
        return this.heldStack.isEmpty() && this.fuelStack.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= 0 && slot < FILTER_SLOTS_COUNT) {
            return this.filterSlots.get(slot);
        } else if (slot == HAND_SLOT_INDEX) {
            return this.heldStack;
        } else if (slot == FUEL_SLOT_INDEX) {
            return this.fuelStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot >= 0 && slot < FILTER_SLOTS_COUNT) {
            ItemStack result = this.filterSlots.get(slot).split(amount);
            this.wakeUp();
            syncState();
            return result;
        } else if (slot == HAND_SLOT_INDEX) {
            ItemStack result = this.heldStack.split(amount);
            if (this.heldStack.isEmpty()) {
                this.heldStack = ItemStack.EMPTY;
                this.ticksUntilDelivery = 0;
            }
            this.wakeUp();
            syncState();
            return result;
        } else if (slot == FUEL_SLOT_INDEX) {
            ItemStack result = this.fuelStack.split(amount);
            if (this.fuelStack.isEmpty()) {
                this.fuelStack = ItemStack.EMPTY;
            }
            this.wakeUp();
            syncState();
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot >= 0 && slot < FILTER_SLOTS_COUNT) {
            ItemStack result = this.filterSlots.set(slot, ItemStack.EMPTY);
            this.wakeUp();
            syncState();
            return result;
        } else if (slot == HAND_SLOT_INDEX) {
            ItemStack result = this.heldStack;
            this.heldStack = ItemStack.EMPTY;
            this.ticksUntilDelivery = 0;
            this.wakeUp();
            syncState();
            return result;
        } else if (slot == FUEL_SLOT_INDEX) {
            ItemStack result = this.fuelStack;
            this.fuelStack = ItemStack.EMPTY;
            this.wakeUp();
            syncState();
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS_COUNT) {
            this.filterSlots.set(slot, stack);
            if (!stack.isEmpty()) {
                this.filterEnabled = true;
            }
        } else if (slot == HAND_SLOT_INDEX) {
            this.heldStack = stack;
            if (stack.isEmpty()) {
                this.ticksUntilDelivery = 0;
            }
        } else if (slot == FUEL_SLOT_INDEX) {
            this.fuelStack = stack;
        }
        this.wakeUp();
        syncState();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        this.filterSlots.clear();
        this.heldStack = ItemStack.EMPTY;
        this.fuelStack = ItemStack.EMPTY;
        this.wakeUp();
        syncState();
    }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        if (isBurner()) {
            return new BurnerInserterScreenHandler(syncId, playerInventory, this, this.burnerPropertyDelegate);
        }
        return new InserterScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        if (!heldStack.isEmpty()) {
            nbt.put("HeldStack", heldStack.encode(registryLookup));
        }
        if (!fuelStack.isEmpty()) {
            nbt.put("FuelStack", fuelStack.encode(registryLookup));
        }
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("MaxBurnTime", maxBurnTime);

        NbtList filterList = new NbtList();
        for (int i = 0; i < FILTER_SLOTS_COUNT; i++) {
            ItemStack s = this.filterSlots.get(i);
            if (!s.isEmpty()) {
                NbtCompound entry = new NbtCompound();
                entry.putByte("Slot", (byte) i);
                entry.put("Stack", s.encode(registryLookup));
                filterList.add(entry);
            }
        }
        nbt.put("Filters", filterList);

        nbt.putInt("MaxPickupCount", maxPickupCount);
        nbt.putBoolean("FilterEnabled", filterEnabled);
        nbt.putBoolean("BlacklistMode", blacklistMode);
        nbt.putBoolean("OverrideStackSize", overrideStackSize);
        nbt.putInt("TicksUntilDelivery", ticksUntilDelivery);
        nbt.putInt("ReturnTicks", returnTicks);
        nbt.putBoolean("Sleeping", sleeping);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        boolean wasEmpty = this.heldStack.isEmpty();

        if (nbt.contains("HeldStack")) {
            this.heldStack = ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("HeldStack"));
        } else {
            this.heldStack = ItemStack.EMPTY;
        }
        if (nbt.contains("FuelStack")) {
            this.fuelStack = ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("FuelStack"));
        } else {
            this.fuelStack = ItemStack.EMPTY;
        }
        this.burnTime = nbt.getInt("BurnTime");
        this.maxBurnTime = nbt.getInt("MaxBurnTime");

        this.filterSlots.clear();
        if (nbt.contains("Filters", NbtElement.LIST_TYPE)) {
            NbtList filterList = nbt.getList("Filters", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < filterList.size(); i++) {
                NbtCompound entry = filterList.getCompound(i);
                int slot = entry.getByte("Slot");
                if (slot >= 0 && slot < FILTER_SLOTS_COUNT && entry.contains("Stack")) {
                    this.filterSlots.set(slot, ItemStack.fromNbtOrEmpty(registryLookup, entry.getCompound("Stack")));
                }
            }
        }

        this.maxPickupCount = Math.max(1, Math.min(MAX_STACK_CAPACITY, nbt.getInt("MaxPickupCount")));
        this.filterEnabled = nbt.getBoolean("FilterEnabled");
        this.blacklistMode = nbt.getBoolean("BlacklistMode");
        this.overrideStackSize = nbt.getBoolean("OverrideStackSize");
        this.ticksUntilDelivery = nbt.getInt("TicksUntilDelivery");
        this.returnTicks = nbt.getInt("ReturnTicks");
        this.sleeping = nbt.getBoolean("Sleeping");

        if (this.world != null && this.world.isClient()) {
            int duration = getSwingDuration();
            if (!this.heldStack.isEmpty() && wasEmpty) {
                this.clientSwingTicks = Math.max(0, duration - this.ticksUntilDelivery);
                this.clientReturnTicks = 0;
            } else if (this.heldStack.isEmpty() && !wasEmpty) {
                this.clientSwingTicks = 0;
                this.clientReturnTicks = this.returnTicks > 0 ? this.returnTicks : duration;
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

    public ItemStack getHeldStack() {
        return heldStack;
    }

    public ItemStack getFuelStack() {
        return fuelStack;
    }

    public boolean isSleeping() {
        return sleeping;
    }
}