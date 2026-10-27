package com.fixplot.factoriocore.content.production.electric_drill;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ElectricDrillBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    public static final int MINING_TIME_TICKS = 40;
    public static final long POWER_CONSUMPTION_WATTS = 90_000L;
    public static final int OUTPUT_SLOT = 4;

    // Слоты 0..3 под модули, слот 4 — выход добытой руды
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(5, ItemStack.EMPTY);
    private int miningProgress = 0;
    private int resourceCycleIndex = 0;
    private boolean powered = false;
    private boolean activelyMining = false;
    private float clientDrillRotation = 0.0f;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> miningProgress;
                case 1 -> MINING_TIME_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) miningProgress = value;
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public ElectricDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ELECTRIC_DRILL_BLOCK_ENTITY, pos, state);
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isActivelyMining() {
        return this.powered && this.activelyMining;
    }

    public ItemStack getOutputStack() {
        return this.items.get(OUTPUT_SLOT);
    }

    public void clearOutput() {
        this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
    }

    public float getClientDrillRotation(float tickDelta) {
        if (this.powered) {
            return (this.clientDrillRotation + tickDelta * 16.0f) % 360.0f;
        }
        return this.clientDrillRotation;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, ElectricDrillBlockEntity drill) {
        if (drill.powered) {
            drill.clientDrillRotation = (drill.clientDrillRotation + 16.0f) % 360.0f;
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, ElectricDrillBlockEntity drill) {
        if (world == null || world.isClient) return;

        Direction facing = state.get(ElectricDrillBlock.FACING);
        ItemStack outStack = drill.items.get(OUTPUT_SLOT);

        if (!outStack.isEmpty()) {
            BlockPos frontOutPos = pos.offset(facing, 2);
            if (drill.tryExportToPos(world, frontOutPos, facing)) {
                drill.sync();
            }
            outStack = drill.items.get(OUTPUT_SLOT);
            if (outStack.getCount() >= outStack.getMaxCount()) {
                return;
            }
        }

        boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
        if (drill.powered != hasPower) {
            drill.powered = hasPower;
            drill.sync();
        }
        if (!hasPower) {
            return;
        }

        BlockPos orePos = drill.findOreIn5x5Area(world, pos);
        if (orePos == null) {
            drill.miningProgress = 0;
            if (drill.activelyMining) {
                drill.activelyMining = false;
                drill.sync();
            }
            return;
        }
        if (!drill.activelyMining) {
            drill.activelyMining = true;
            drill.sync();
        }

        Block oreBlock = world.getBlockState(orePos).getBlock();
        if (!(oreBlock instanceof ResourceOreBlock customOre)) {
            return;
        }
        ItemStack product = customOre.createProductStack(1);

        drill.miningProgress++;
        if (drill.miningProgress >= MINING_TIME_TICKS) {
            drill.miningProgress = 0;
            drill.resourceCycleIndex++;

            ResourceOreBlock.consumeOreAt(world, orePos, 1);

            outStack = drill.items.get(OUTPUT_SLOT);
            if (outStack.isEmpty()) {
                drill.items.set(OUTPUT_SLOT, product.copy());
            } else if (ItemStack.areItemsAndComponentsEqual(outStack, product)) {
                outStack.increment(1);
            }

            drill.tryExportToPos(world, pos.offset(facing, 2), facing);
            drill.sync();
        } else {
            drill.markDirty();
        }
    }

    @Nullable
    public BlockPos findOreIn5x5Area(World world, BlockPos masterPos) {
        List<BlockPos> found = new ArrayList<>(25);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos groundPos = masterPos.add(dx, -1, dz);
                if (world.getBlockState(groundPos).getBlock() instanceof ResourceOreBlock) {
                    found.add(groundPos);
                }
            }
        }
        if (found.isEmpty()) return null;
        return found.get(Math.floorMod(this.resourceCycleIndex, found.size()));
    }

    private boolean tryExportToPos(World world, BlockPos targetPos, Direction drillFacing) {
        ItemStack outputStack = this.items.get(OUTPUT_SLOT);
        if (outputStack.isEmpty()) return false;
        BlockEntity directBe = world.getBlockEntity(targetPos);

        if (directBe instanceof BeltBlockEntity belt) {
            Direction beltFacing = belt.getCachedState().get(BeltBlock.FACING);
            boolean inserted = false;

            if (beltFacing == drillFacing) {
                BeltLane rightLane = belt.getRightLane();
                if (!belt.isSlotCoveredByHood(0) && rightLane.insertAt(0, outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(1) && rightLane.insertAt(1, outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.getOpposite()) {
                BeltLane lane = belt.getLeftLane();
                if (!belt.isSlotCoveredByHood(3) && lane.insertAt(3, outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.rotateYClockwise()) {
                BeltLane nearLane = belt.getRightLane();
                if (!belt.isSlotCoveredByHood(1) && nearLane.insertAt(1, outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(2) && nearLane.insertAt(2, outputStack)) {
                    inserted = true;
                }
            } else if (beltFacing == drillFacing.rotateYCounterclockwise()) {
                BeltLane nearLane = belt.getLeftLane();
                if (!belt.isSlotCoveredByHood(1) && nearLane.insertAt(1, outputStack)) {
                    inserted = true;
                } else if (!belt.isSlotCoveredByHood(2) && nearLane.insertAt(2, outputStack)) {
                    inserted = true;
                }
            }

            if (inserted) {
                outputStack.decrement(1);
                if (outputStack.isEmpty()) this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
                BeltBlock.syncManualChange(world, targetPos, belt.getCachedState(), belt);
                return true;
            }
            return false;
        }

        BlockState targetState = world.getBlockState(targetPos);
        BlockPos canonicalPos = BlockHealthManager.getCanonicalPos(targetPos, targetState);
        BlockEntity be = world.getBlockEntity(canonicalPos);

        if (be instanceof FactorioFurnaceBlockEntity furnace) {
            int slot = AbstractFurnaceBlockEntity.canUseAsFuel(outputStack) ? 1 : 0;
            ItemStack cur = furnace.getStack(slot);
            if (cur.isEmpty()) {
                furnace.setStack(slot, outputStack.split(1));
                if (outputStack.isEmpty()) this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
                return true;
            } else if (ItemStack.areItemsAndComponentsEqual(cur, outputStack) && cur.getCount() < InserterBlockEntity.FURNACE_INPUT_LIMIT) {
                cur.increment(1);
                outputStack.decrement(1);
                if (outputStack.isEmpty()) this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
                furnace.syncState();
                return true;
            }
            return false;
        }

        Inventory inv = null;
        if (targetState.getBlock() instanceof ChestBlock chestBlock && be instanceof ChestBlockEntity) {
            inv = ChestBlock.getInventory(chestBlock, targetState, world, canonicalPos, true);
        } else if (be instanceof Inventory genericInv) {
            inv = genericInv;
        }
        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                if (!inv.isValid(i, outputStack)) continue;
                ItemStack cur = inv.getStack(i);
                if (cur.isEmpty()) {
                    inv.setStack(i, outputStack.split(1));
                    if (outputStack.isEmpty()) this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
                    inv.markDirty();
                    return true;
                } else if (ItemStack.areItemsAndComponentsEqual(cur, outputStack) && cur.getCount() < cur.getMaxCount()) {
                    cur.increment(1);
                    outputStack.decrement(1);
                    if (outputStack.isEmpty()) this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
                    inv.markDirty();
                    return true;
                }
            }
        }
        return false;
    }

    public void sync() {
        markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override public int size() { return this.items.size(); }
    @Override public boolean isValid(int slot, ItemStack stack) { return false; }
    @Override public boolean isEmpty() { return this.items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return (slot >= 0 && slot < this.items.size()) ? this.items.get(slot) : ItemStack.EMPTY; }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack res = Inventories.splitStack(this.items, slot, amount);
        if (!res.isEmpty()) sync();
        return res;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack res = Inventories.removeStack(this.items, slot);
        if (!res.isEmpty()) sync();
        return res;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.items.size()) {
            this.items.set(slot, stack);
            sync();
        }
    }

    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player, 10.0F); }
    @Override public void clear() { this.items.clear(); sync(); }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.factoriocore.electric_mining_drill");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ElectricDrillScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, this.items, registries);
        nbt.putInt("MiningProgress", this.miningProgress);
        nbt.putBoolean("Powered", this.powered);
        nbt.putBoolean("ActivelyMining", this.activelyMining);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.items.clear();
        Inventories.readNbt(nbt, this.items, registries);
        if (nbt.contains("OutputStack") && this.items.get(OUTPUT_SLOT).isEmpty()) {
            this.items.set(OUTPUT_SLOT, ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("OutputStack")));
        }
        this.miningProgress = nbt.getInt("MiningProgress");
        this.powered = nbt.getBoolean("Powered");
        this.activelyMining = nbt.getBoolean("ActivelyMining");
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