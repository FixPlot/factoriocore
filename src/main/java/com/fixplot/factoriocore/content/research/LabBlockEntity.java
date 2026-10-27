package com.fixplot.factoriocore.content.research;

import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
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

public class LabBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {

    public static final long POWER_CONSUMPTION_WATTS = 60_000L;
    private static final int[] SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6};

    // 7 слотов под научные колбочки (0..3 активные, 4..6 резерв под фиолетовую/жёлтую/белую науку)
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(7, ItemStack.EMPTY);
    private boolean isPowered = false;
    private boolean isWorking = false;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> isWorking ? Math.max(1, Math.round(ResearchManager.getOverallProgressRatio() * 100.0f)) : 0;
                case 1 -> 100;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LAB_BLOCK_ENTITY, pos, state);
    }

    public boolean isPowered() { return isPowered; }
    public boolean isWorking() { return isWorking; }

    public static int getSlotForPack(Item item) {
        if (item == ModItems.AUTOMATION_SCIENCE_PACK) return 0;
        if (item == ModItems.LOGISTIC_SCIENCE_PACK) return 1;
        if (item == ModItems.MILITARY_SCIENCE_PACK) return 2;
        if (item == ModItems.CHEMICAL_SCIENCE_PACK) return 3;
        return -1;
    }

    public static void tick(World world, BlockPos pos, BlockState state, LabBlockEntity be) {
        if (world == null || world.isClient) return;
        if (state.get(LabBlock.PART) != 4 || state.get(LabBlock.UPPER)) return;

        boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
        ResearchManager.TechNode activeTech = ResearchManager.getActiveTech();
        boolean canResearch = hasPower && activeTech != null && be.hasRequiredPacks(activeTech);

        if (be.isPowered != hasPower || be.isWorking != canResearch) {
            be.isPowered = hasPower;
            be.isWorking = canResearch;
            be.sync();
        }

        if (canResearch) {
            boolean unitFinished = ResearchManager.advanceResearchTickFromLab(activeTech);
            if (unitFinished) {
                be.consumePacks(activeTech);
                be.sync();
            }
        }
    }

    public boolean hasRequiredPacks(ResearchManager.TechNode tech) {
        if (tech == null) return false;
        for (ResearchManager.SciencePack sp : tech.packs) {
            int slot = sp.ordinal();
            ItemStack st = inventory.get(slot);
            if (st.isEmpty() || !st.isOf(sp.getItem())) {
                return false;
            }
        }
        return true;
    }

    private void consumePacks(ResearchManager.TechNode tech) {
        for (ResearchManager.SciencePack sp : tech.packs) {
            int slot = sp.ordinal();
            ItemStack st = inventory.get(slot);
            if (!st.isEmpty()) {
                st.decrement(1);
            }
        }
    }

    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return getSlotForPack(stack.getItem()) == slot;
    }

    @Override public int[] getAvailableSlots(Direction side) { return SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return isValid(slot, stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }

    @Override public int size() { return inventory.size(); }
    @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return (slot >= 0 && slot < inventory.size()) ? inventory.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack r = Inventories.splitStack(inventory, slot, amount); if (!r.isEmpty()) sync(); return r; }
    @Override public ItemStack removeStack(int slot) { ItemStack r = Inventories.removeStack(inventory, slot); if (!r.isEmpty()) sync(); return r; }
    @Override public void setStack(int slot, ItemStack stack) { if (slot >= 0 && slot < inventory.size()) { inventory.set(slot, stack); sync(); } }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player, 10.0F); }
    @Override public void clear() { inventory.clear(); sync(); }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.factoriocore.lab");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LabScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putBoolean("Powered", isPowered);
        nbt.putBoolean("Working", isWorking);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        inventory.clear();
        Inventories.readNbt(nbt, inventory, registries);
        isPowered = nbt.getBoolean("Powered");
        isWorking = nbt.getBoolean("Working");
    }

    @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}