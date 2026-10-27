package com.fixplot.factoriocore.content.logistics.chest;

import com.fixplot.factoriocore.content.crafting.InventoryAutoSorter;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactorioChestBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    public static final int MAX_POSSIBLE_SLOTS = 48;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(MAX_POSSIBLE_SLOTS, ItemStack.EMPTY);
    private int totalSlots = 16;
    private int unlockedSlots = 16;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> unlockedSlots;
                case 1 -> totalSlots;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) unlockedSlots = value;
            if (index == 1) totalSlots = value;
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public FactorioChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FACTORIO_CHEST_BLOCK_ENTITY, pos, state);
        if (state.getBlock() instanceof FactorioChestBlock chestBlock) {
            this.totalSlots = chestBlock.getSlotCount();
            this.unlockedSlots = this.totalSlots;
        }
    }

    public int getTotalSlots() {
        if (getCachedState().getBlock() instanceof FactorioChestBlock chestBlock) {
            this.totalSlots = chestBlock.getSlotCount();
        }
        return this.totalSlots;
    }

    public int getUnlockedSlots() {
        return Math.min(this.unlockedSlots, getTotalSlots());
    }

    public void setUnlockedSlots(int count) {
        this.unlockedSlots = Math.max(0, Math.min(getTotalSlots(), count));
        markDirty();
        if (this.world != null) {
            InserterBlockEntity.wakeUpAdjacentInserters(this.world, this.pos);
        }
    }

    /**
     * Переопределяем markDirty(), чтобы при работе манипуляторов, буров или игрока
     * содержимое сундука МГНОВЕННО синхронизировалось с клиентом (без необходимости открывать сундук)!
     */
    @Override
    public void markDirty() {
        super.markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    /**
     * Собирает сводку содержимого сундука (объединяя стаки одного предмета)
     * и сортирует их строго по порядку меню крафта Factorio!
     */
    public List<ItemStack> getSortedContentSummary(int maxTypes) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < getTotalSlots(); i++) {
            ItemStack s = this.items.get(i);
            if (!s.isEmpty()) {
                counts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> aggregated = new ArrayList<>(counts.size());
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            aggregated.add(new ItemStack(e.getKey(), e.getValue()));
        }

        aggregated.sort(Comparator
                .comparingInt(InventoryAutoSorter::getCategoryOrder)
                .thenComparingInt((ItemStack s) -> InventoryAutoSorter.getFactorioRecipeOrder(s.getItem()))
                .thenComparing(s -> Registries.ITEM.getId(s.getItem()).toString())
                .thenComparingInt((ItemStack s) -> -s.getCount()));

        if (aggregated.size() > maxTypes) {
            return aggregated.subList(0, maxTypes);
        }
        return aggregated;
    }

    @Override
    public int size() {
        return getTotalSlots();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getUnlockedSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getTotalSlots(); i++) {
            if (!this.items.get(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= 0 && slot < getTotalSlots()) {
            return this.items.get(slot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(this.items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(this.items, slot);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAX_POSSIBLE_SLOTS) {
            this.items.set(slot, stack);
            if (stack.getCount() > this.getMaxCountPerStack()) {
                stack.setCount(this.getMaxCountPerStack());
            }
            markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        this.items.clear();
        markDirty();
    }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new FactorioChestScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("TotalSlots", getTotalSlots());
        nbt.putInt("UnlockedSlots", getUnlockedSlots());
        Inventories.writeNbt(nbt, this.items, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.items.clear();
        Inventories.readNbt(nbt, this.items, registries);
        if (nbt.contains("TotalSlots")) {
            this.totalSlots = nbt.getInt("TotalSlots");
        }
        if (nbt.contains("UnlockedSlots")) {
            this.unlockedSlots = Math.max(0, Math.min(getTotalSlots(), nbt.getInt("UnlockedSlots")));
        } else {
            this.unlockedSlots = getTotalSlots();
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