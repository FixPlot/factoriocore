package com.fixplot.factoriocore.content.production.assembler;

import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AssemblerBlockEntity extends BlockEntity
        implements com.fixplot.factoriocore.content.simulation.VirtualSimulatable, SidedInventory, NamedScreenHandlerFactory {

    public static final long POWER_CONSUMPTION_WATTS = 75_000L;
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(5, ItemStack.EMPTY);

    private String recipeId = "";
    private int progress = 0;
    private int maxProgress = 100;
    private boolean isWorking = false;
    private boolean isPowered = false;

    private static final int[] ALL_SLOTS = new int[]{0, 1, 2, 3, 4};

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> Math.max(1, maxProgress);
                case 2 -> isWorking ? 1 : 0;
                case 3 -> isPowered ? 1 : 0;
                case 4 -> pos.getX();
                case 5 -> pos.getY();
                case 6 -> pos.getZ();
                case 7 -> FactorioRecipes.getRecipeIndex(recipeId) + 1;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                case 2 -> isWorking = value == 1;
                case 3 -> isPowered = value == 1;
            }
        }
        @Override public int size() { return 8; }
    };

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ASSEMBLER_BLOCK_ENTITY, pos, state);
    }

    public String getRecipeId() { return this.recipeId; }

    public void setRecipeId(String newRecipeId) {
        setRecipeId(newRecipeId, null);
    }

    public void setRecipeId(String newRecipeId, @Nullable ServerPlayerEntity player) {
        String normalized = newRecipeId != null ? newRecipeId : "";
        if (!this.recipeId.equals(normalized)) {
            if (world != null && !world.isClient) {
                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack st = inventory.get(i);
                    if (!st.isEmpty()) {
                        if (player != null) {
                            player.getInventory().offerOrDrop(st.copy());
                        } else {
                            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, st.copy());
                        }
                        inventory.set(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        this.recipeId = normalized;
        this.progress = 0;
        this.isWorking = false;
        FactorioRecipes.Recipe r = FactorioRecipes.getById(this.recipeId);
        this.maxProgress = r != null ? Math.max(1, r.timeTicks()) : 100;
        sync();
    }

    public boolean isPowered() { return isPowered; }
    public boolean isWorking() { return isWorking; }

    public static void tick(World world, BlockPos pos, BlockState state, AssemblerBlockEntity be) {
        if (world == null || world.isClient) return;

        boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
        if (be.isPowered != hasPower) {
            be.isPowered = hasPower;
            be.sync();
        }

        if (!hasPower || be.recipeId.isEmpty()) {
            if (be.isWorking) {
                be.isWorking = false;
                be.sync();
            }
            return;
        }

        FactorioRecipes.Recipe r = FactorioRecipes.getById(be.recipeId);
        if (r == null) {
            if (be.isWorking) {
                be.isWorking = false;
                be.sync();
            }
            return;
        }

        if (be.canCraft(r)) {
            if (!be.isWorking) {
                be.isWorking = true;
                be.sync();
            }
            be.progress++;
            if (be.progress >= be.maxProgress) {
                be.consumeIngredients(r);
                be.produceResult(r);
                be.progress = 0;
                be.sync();
            }
        } else {
            if (be.isWorking) {
                be.isWorking = false;
                be.sync();
            }
        }
    }

    private boolean canCraft(FactorioRecipes.Recipe recipe) {
        if (recipe == null) return false;
        for (int i = 0; i < recipe.ingredients().size() && i < 4; i++) {
            FactorioRecipes.Ingredient ing = recipe.ingredients().get(i);
            ItemStack slot = inventory.get(i);
            if (slot.isEmpty() || !slot.isOf(ing.item()) || slot.getCount() < ing.count()) {
                return false;
            }
        }
        ItemStack outSlot = inventory.get(4);
        if (outSlot.isEmpty()) return true;
        if (!ItemStack.areItemsAndComponentsEqual(outSlot, recipe.output())) return false;
        return outSlot.getCount() + recipe.output().getCount() <= outSlot.getMaxCount();
    }

    private void consumeIngredients(FactorioRecipes.Recipe recipe) {
        for (int i = 0; i < recipe.ingredients().size() && i < 4; i++) {
            FactorioRecipes.Ingredient ing = recipe.ingredients().get(i);
            ItemStack slot = inventory.get(i);
            if (!slot.isEmpty() && slot.isOf(ing.item())) {
                slot.decrement(ing.count());
            }
        }
    }

    private void produceResult(FactorioRecipes.Recipe recipe) {
        ItemStack outSlot = inventory.get(4);
        if (outSlot.isEmpty()) {
            inventory.set(4, recipe.output().copy());
        } else {
            outSlot.increment(recipe.output().getCount());
        }
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 4) return false;
        if (this.recipeId.isEmpty()) return false;
        FactorioRecipes.Recipe r = FactorioRecipes.getById(this.recipeId);
        if (r == null || slot >= r.ingredients().size()) return false;
        return stack.isOf(r.ingredients().get(slot).item());
    }

    @Override public int[] getAvailableSlots(Direction side) { return ALL_SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return isValid(slot, stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return slot == 4; }

    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
        }
    }

    @Override public int size() { return inventory.size(); }
    @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return inventory.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack res = Inventories.splitStack(inventory, slot, amount); if (!res.isEmpty()) sync(); return res; }
    @Override public ItemStack removeStack(int slot) { ItemStack res = Inventories.removeStack(inventory, slot); if (!res.isEmpty()) sync(); return res; }
    @Override public void setStack(int slot, ItemStack stack) { inventory.set(slot, stack); sync(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override public void clear() { inventory.clear(); sync(); }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putString("RecipeId", recipeId);
        nbt.putInt("Progress", progress);
        nbt.putInt("MaxProgress", maxProgress);
        nbt.putBoolean("Powered", isPowered);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, inventory, registries);
        recipeId = nbt.getString("RecipeId");
        progress = nbt.getInt("Progress");
        maxProgress = nbt.getInt("MaxProgress");
        isPowered = nbt.getBoolean("Powered");
    }

    @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override public Text getDisplayName() { return Text.translatable("block.factoriocore.assembler"); }
    @Nullable @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new AssemblerScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    // =====================================================================
    // VirtualSimulatable: catch-up крафта вне тикающего чанка
    // =====================================================================

    private long virtualTickedGameTime = 0L;

    @Override
    public NbtCompound captureVirtualState() {
        return new NbtCompound(); // снапшот в NBT чанка, отдельный не нужен
    }

    @Override
    public void markVirtualTicked(long gameTime) {
        this.virtualTickedGameTime = gameTime;
    }

    @Override
    public long getLastVirtualTicked() {
        return this.virtualTickedGameTime;
    }

    /**
     * Доматывание крафта арифметически. Силовая логика не гоняется BFS'ом
     * (это был бы тяжёлый вызов на каждую машину): считаем, что питание
     * сохранило состояние на момент выгрузки.
     */
    @Override
    public void catchUp(long elapsedTicks) {
        if (this.world == null || this.world.isClient || elapsedTicks <= 0) return;
        if (recipeId.isEmpty() || !isPowered) return;

        FactorioRecipes.Recipe r = FactorioRecipes.getById(recipeId);
        if (r == null) return;

        long effective = Math.min(elapsedTicks, 4 * 60 * 20); // кламп 4 мин
        long totalProgress = progress + effective;
        long cyclesCompleted = totalProgress / Math.max(1, maxProgress);

        // Ограничение ингредиентами и выходом — с клампом (дюп невозможен).
        for (int i = 0; i < r.ingredients().size() && i < 4; i++) {
            FactorioRecipes.Ingredient ing = r.ingredients().get(i);
            ItemStack slot = inventory.get(i);
            int affordable = slot.isEmpty() || !slot.isOf(ing.item()) ? 0 : slot.getCount() / ing.count();
            cyclesCompleted = Math.min(cyclesCompleted, affordable);
        }
        ItemStack outSlot = inventory.get(4);
        int outSpace = Integer.MAX_VALUE;
        if (!outSlot.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(outSlot, r.output())) {
                this.progress = 0;
                this.isWorking = false;
                return;
            }
            outSpace = (outSlot.getMaxCount() - outSlot.getCount()) / Math.max(1, r.output().getCount());
        }
        cyclesCompleted = Math.min(cyclesCompleted, outSpace);

        if (cyclesCompleted > 0) {
            for (int i = 0; i < r.ingredients().size() && i < 4; i++) {
                FactorioRecipes.Ingredient ing = r.ingredients().get(i);
                ItemStack slot = inventory.get(i);
                if (!slot.isEmpty() && slot.isOf(ing.item())) {
                    slot.decrement((int) Math.min(cyclesCompleted * ing.count(), slot.getCount()));
                }
            }
            int produced = (int) Math.min(cyclesCompleted * r.output().getCount(),
                    outSlot.isEmpty() ? r.output().getMaxCount()
                            : (outSlot.getMaxCount() - outSlot.getCount()));
            if (outSlot.isEmpty()) {
                ItemStack copy = r.output().copy();
                copy.setCount(Math.min(produced, copy.getMaxCount()));
                inventory.set(4, copy);
            } else {
                outSlot.increment(produced);
            }
            this.markDirty();
        }

        this.progress = (int) (totalProgress % Math.max(1, maxProgress));
        this.isWorking = canCraft(r);
    }
}