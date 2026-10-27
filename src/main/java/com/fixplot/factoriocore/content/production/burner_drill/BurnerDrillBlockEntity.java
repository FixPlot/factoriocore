package com.fixplot.factoriocore.content.production.burner_drill;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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

import java.util.ArrayList;
import java.util.List;

public class BurnerDrillBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    public static final int MINING_TIME_TICKS = 80;
    public static final int FUEL_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private ItemStack fuelStack = ItemStack.EMPTY;
    private ItemStack outputStack = ItemStack.EMPTY;

    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int miningProgress = 0;
    private int resourceCycleIndex = 0;
    private boolean sleeping = false;

    private float clientDrillRotation = 0.0f;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> miningProgress;
                case 3 -> MINING_TIME_TICKS;
                case 4 -> sleeping ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> maxBurnTime = value;
                case 2 -> miningProgress = value;
                case 4 -> sleeping = (value != 0);
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };

    public BurnerDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BURNER_DRILL_BLOCK_ENTITY, pos, state);
    }

    public BurnerDrillBlockEntity getMaster() {
        if (this.world == null) return this;
        BlockState state = getCachedState();
        if (state.getBlock() instanceof BurnerDrillBlock) {
            if (state.get(BurnerDrillBlock.PART) == 0 && !state.get(BurnerDrillBlock.UPPER)) {
                return this;
            }
            BlockPos masterPos = BurnerDrillBlock.getMasterPos(this.pos, state);
            if (this.world.getBlockEntity(masterPos) instanceof BurnerDrillBlockEntity master) {
                return master;
            }
        }
        return this;
    }

    public void wakeUp() {
        BurnerDrillBlockEntity master = getMaster();
        if (master.sleeping) {
            master.sleeping = false;
        }
    }

    public boolean isWorking() {
        return !this.sleeping && this.burnTime > 0;
    }

    public float getClientDrillRotation(float tickDelta) {
        if (!this.sleeping && this.burnTime > 0) {
            return this.clientDrillRotation + tickDelta * 12.0f;
        }
        return this.clientDrillRotation;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, BurnerDrillBlockEntity entity) {
        if (!entity.sleeping && entity.burnTime > 0) {
            entity.clientDrillRotation = (entity.clientDrillRotation + 12.0f) % 360.0f;
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, BurnerDrillBlockEntity entity) {
        if (world == null || world.isClient()) return;

        boolean wasSleeping = entity.sleeping;
        if (entity.sleeping) {
            if (((world.getTime() + pos.asLong()) & 15L) != 0L) {
                return;
            }
            entity.sleeping = false;
        }

        Direction facing = state.get(BurnerDrillBlock.FACING);

        if (!entity.outputStack.isEmpty()) {
            if (entity.tryExportOutput(world, pos, facing)) {
                entity.syncState();
            }
            if (entity.outputStack.getCount() >= entity.outputStack.getMaxCount()) {
                entity.sleeping = true;
                if (!wasSleeping) entity.syncState();
                return;
            }
        }

        BlockPos targetOrePos = entity.findActiveOrePosUnderneath(world, pos, facing);
        if (targetOrePos == null) {
            boolean hadProgress = entity.miningProgress > 0;
            entity.miningProgress = 0;
            entity.sleeping = true;
            if (!wasSleeping || hadProgress) entity.syncState();
            return;
        }

        ItemStack minedResource = mapOreBlockToProduct(world.getBlockState(targetOrePos).getBlock());
        if (minedResource.isEmpty()) {
            entity.sleeping = true;
            if (!wasSleeping) entity.syncState();
            return;
        }

        if (!entity.outputStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(entity.outputStack, minedResource)) {
            entity.sleeping = true;
            if (!wasSleeping) entity.syncState();
            return;
        }

        if (!entity.ensureFuel()) {
            entity.sleeping = true;
            if (!wasSleeping) entity.syncState();
            return;
        }

        if (wasSleeping) {
            entity.syncState();
        }

        entity.burnTime--;
        entity.miningProgress++;

        if (entity.miningProgress >= MINING_TIME_TICKS) {
            entity.miningProgress = 0;
            entity.resourceCycleIndex++;

            ResourceOreBlock.consumeOreAt(world, targetOrePos, 1);

            if (entity.outputStack.isEmpty()) {
                entity.outputStack = minedResource.copyWithCount(1);
            } else {
                entity.outputStack.increment(1);
            }

            entity.tryExportOutput(world, pos, facing);
            entity.syncState();
        } else if (entity.burnTime == 0) {
            entity.syncState();
        } else {
            entity.markDirty();
        }
    }

    @Nullable
    private BlockPos findActiveOrePosUnderneath(World world, BlockPos masterPos, Direction facing) {
        List<BlockPos> orePositions = new ArrayList<>(4);
        for (int part = 0; part < 4; part++) {
            BlockPos groundPos = BurnerDrillBlock.getPartPos(masterPos, facing, part, false).down();
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            if (!mapOreBlockToProduct(groundBlock).isEmpty()) {
                orePositions.add(groundPos);
            }
        }
        if (orePositions.isEmpty()) {
            return null;
        }
        return orePositions.get(Math.floorMod(this.resourceCycleIndex, orePositions.size()));
    }

    private boolean ensureFuel() {
        if (this.burnTime > 0) {
            return true;
        }
        if (!this.fuelStack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(this.fuelStack)) {
            Integer fuelTicks = FuelRegistry.INSTANCE.get(this.fuelStack.getItem());
            int ticks = (fuelTicks != null && fuelTicks > 0) ? (int) (fuelTicks * 0.35f) : 200;
            this.burnTime = Math.max(80, ticks);
            this.maxBurnTime = this.burnTime;

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

    public ItemStack findResourceUnderneath(World world, BlockPos masterPos, Direction facing) {
        BlockPos targetPos = findActiveOrePosUnderneath(world, masterPos, facing);
        if (targetPos == null) {
            return ItemStack.EMPTY;
        }
        return mapOreBlockToProduct(world.getBlockState(targetPos).getBlock());
    }

    private static ItemStack mapOreBlockToProduct(Block block) {
        if (block instanceof ResourceOreBlock customOre) {
            return customOre.createProductStack(1);
        }
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE || block == Blocks.COAL_BLOCK) {
            return new ItemStack(Items.COAL);
        }
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.RAW_IRON_BLOCK) {
            return new ItemStack(Items.RAW_IRON);
        }
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE || block == Blocks.RAW_COPPER_BLOCK) {
            return new ItemStack(Items.RAW_COPPER);
        }
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE || block == Blocks.RAW_GOLD_BLOCK) {
            return new ItemStack(Items.RAW_GOLD);
        }
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack(Items.REDSTONE);
        }
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return new ItemStack(Items.LAPIS_LAZULI);
        }
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return new ItemStack(Items.DIAMOND);
        }
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
            return new ItemStack(Items.EMERALD);
        }
        return ItemStack.EMPTY;
    }

    private boolean tryExportOutput(World world, BlockPos masterPos, Direction facing) {
        if (this.outputStack.isEmpty()) return false;

        BlockPos primaryTarget = BurnerDrillBlock.getPartPos(masterPos, facing, 3, false).offset(facing);
        if (tryExportToPos(world, primaryTarget, facing)) {
            return true;
        }

        BlockPos secondaryTarget = BurnerDrillBlock.getPartPos(masterPos, facing, 2, false).offset(facing);
        return tryExportToPos(world, secondaryTarget, facing);
    }

    private boolean tryExportToPos(World world, BlockPos targetPos, Direction drillFacing) {
        BlockEntity directBe = world.getBlockEntity(targetPos);

        if (directBe instanceof BeltBlockEntity belt) {
            Direction beltFacing = belt.getCachedState().get(BeltBlock.FACING);
            boolean inserted = false;

            if (beltFacing == drillFacing) {
                BeltLane rightLane = belt.getRightLane();
                if (!belt.isSlotCoveredByHood(0) && rightLane.insertAt(0, this.outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(1) && rightLane.insertAt(1, this.outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.getOpposite()) {
                BeltLane lane = belt.getLeftLane();
                if (!belt.isSlotCoveredByHood(3) && lane.insertAt(3, this.outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.rotateYClockwise()) {
                BeltLane nearLane = belt.getRightLane();
                if (!belt.isSlotCoveredByHood(1) && nearLane.insertAt(1, this.outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(2) && nearLane.insertAt(2, this.outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.rotateYCounterclockwise()) {
                BeltLane nearLane = belt.getLeftLane();
                if (!belt.isSlotCoveredByHood(1) && nearLane.insertAt(1, this.outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(2) && nearLane.insertAt(2, this.outputStack)) {
                    inserted = true;
                }
            }

            if (inserted) {
                this.outputStack.decrement(1);
                if (this.outputStack.isEmpty()) this.outputStack = ItemStack.EMPTY;
                BeltBlock.syncManualChange(world, targetPos, belt.getCachedState(), belt);
                return true;
            }
            return false;
        }

        BlockState targetState = world.getBlockState(targetPos);
        BlockPos canonicalPos = BlockHealthManager.getCanonicalPos(targetPos, targetState);
        BlockEntity be = world.getBlockEntity(canonicalPos);

        if (be instanceof BurnerDrillBlockEntity otherDrill) {
            BurnerDrillBlockEntity targetMaster = otherDrill.getMaster();
            if (targetMaster != this && AbstractFurnaceBlockEntity.canUseAsFuel(this.outputStack)) {
                if (insertSingleWithLimit(targetMaster, FUEL_SLOT, this.outputStack, InserterBlockEntity.BURNER_FUEL_LIMIT)) {
                    targetMaster.wakeUp();
                    targetMaster.syncState();
                    return true;
                }
            }
            return false;
        }

        if (be instanceof FactorioFurnaceBlockEntity factorioFurnace) {
            int targetSlot = AbstractFurnaceBlockEntity.canUseAsFuel(this.outputStack) ? 1 : 0;
            int limit = (targetSlot == 1) ? InserterBlockEntity.BURNER_FUEL_LIMIT : InserterBlockEntity.FURNACE_INPUT_LIMIT;
            if (insertSingleWithLimit(factorioFurnace, targetSlot, this.outputStack, limit)) {
                factorioFurnace.wakeUp();
                factorioFurnace.syncState();
                return true;
            }
            return false;
        }

        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            int targetSlot = AbstractFurnaceBlockEntity.canUseAsFuel(this.outputStack) ? 1 : 0;
            int limit = (targetSlot == 1) ? InserterBlockEntity.BURNER_FUEL_LIMIT : InserterBlockEntity.FURNACE_INPUT_LIMIT;
            return insertSingleWithLimit(furnace, targetSlot, this.outputStack, limit);
        }

        if (be instanceof InserterBlockEntity inserter && inserter.isBurner()) {
            if (AbstractFurnaceBlockEntity.canUseAsFuel(this.outputStack)) {
                return insertSingleWithLimit(inserter, InserterBlockEntity.FUEL_SLOT_INDEX, this.outputStack, InserterBlockEntity.BURNER_FUEL_LIMIT);
            }
            return false;
        }

        Inventory inv = null;
        if (targetState.getBlock() instanceof ChestBlock chestBlock && be instanceof ChestBlockEntity) {
            inv = ChestBlock.getInventory(chestBlock, targetState, world, canonicalPos, true);
        } else if (be instanceof Inventory genericInv && !(be instanceof InserterBlockEntity)) {
            inv = genericInv;
        }

        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                if (insertSingleWithLimit(inv, i, this.outputStack, this.outputStack.getMaxCount())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean insertSingleWithLimit(Inventory inv, int slot, ItemStack sourceStack, int limit) {
        if (sourceStack.isEmpty() || !inv.isValid(slot, sourceStack)) {
            return false;
        }
        ItemStack current = inv.getStack(slot);
        int maxAllowed = Math.min(limit, Math.min(inv.getMaxCount(sourceStack), sourceStack.getMaxCount()));

        if (current.isEmpty()) {
            inv.setStack(slot, sourceStack.split(1));
            if (this.outputStack.isEmpty()) this.outputStack = ItemStack.EMPTY;
            inv.markDirty();
            return true;
        } else if (ItemStack.areItemsAndComponentsEqual(current, sourceStack) && current.getCount() < maxAllowed) {
            current.increment(1);
            sourceStack.decrement(1);
            if (this.outputStack.isEmpty()) this.outputStack = ItemStack.EMPTY;
            inv.markDirty();
            return true;
        }
        return false;
    }

    public void syncState() {
        markDirty();
        if (this.world != null) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && AbstractFurnaceBlockEntity.canUseAsFuel(stack);
    }

    @Override
    public boolean isEmpty() {
        BurnerDrillBlockEntity m = getMaster();
        return m.fuelStack.isEmpty() && m.outputStack.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        BurnerDrillBlockEntity m = getMaster();
        return switch (slot) {
            case FUEL_SLOT -> m.fuelStack;
            case OUTPUT_SLOT -> m.outputStack;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        BurnerDrillBlockEntity m = getMaster();
        if (slot == FUEL_SLOT) {
            ItemStack res = m.fuelStack.split(amount);
            if (m.fuelStack.isEmpty()) m.fuelStack = ItemStack.EMPTY;
            m.wakeUp();
            m.syncState();
            return res;
        } else if (slot == OUTPUT_SLOT) {
            ItemStack res = m.outputStack.split(amount);
            if (m.outputStack.isEmpty()) m.outputStack = ItemStack.EMPTY;
            m.wakeUp();
            m.syncState();
            return res;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return removeStack(slot, 64);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        BurnerDrillBlockEntity m = getMaster();
        if (slot == FUEL_SLOT) {
            m.fuelStack = stack;
        } else if (slot == OUTPUT_SLOT) {
            m.outputStack = stack;
        }
        m.wakeUp();
        m.syncState();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(getMaster(), player, 10.0F);
    }

    @Override
    public void clear() {
        BurnerDrillBlockEntity m = getMaster();
        m.fuelStack = ItemStack.EMPTY;
        m.outputStack = ItemStack.EMPTY;
        m.wakeUp();
        m.syncState();
    }

    public ItemStack getFuelStack() {
        return getMaster().fuelStack;
    }

    public ItemStack getOutputStack() {
        return getMaster().outputStack;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.factoriocore.burner_mining_drill");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        BurnerDrillBlockEntity master = getMaster();
        return new BurnerDrillScreenHandler(syncId, playerInventory, master, master.propertyDelegate);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return getMaster().getPos();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!this.fuelStack.isEmpty()) {
            nbt.put("FuelStack", this.fuelStack.encode(registries));
        }
        if (!this.outputStack.isEmpty()) {
            nbt.put("OutputStack", this.outputStack.encode(registries));
        }
        nbt.putInt("BurnTime", this.burnTime);
        nbt.putInt("MaxBurnTime", this.maxBurnTime);
        nbt.putInt("MiningProgress", this.miningProgress);
        nbt.putBoolean("Sleeping", this.sleeping);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.fuelStack = nbt.contains("FuelStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("FuelStack"))
                : ItemStack.EMPTY;
        this.outputStack = nbt.contains("OutputStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("OutputStack"))
                : ItemStack.EMPTY;
        this.burnTime = nbt.getInt("BurnTime");
        this.maxBurnTime = nbt.getInt("MaxBurnTime");
        this.miningProgress = nbt.getInt("MiningProgress");
        this.sleeping = nbt.getBoolean("Sleeping");
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