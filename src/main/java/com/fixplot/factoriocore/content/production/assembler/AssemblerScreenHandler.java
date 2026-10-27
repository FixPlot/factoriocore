package com.fixplot.factoriocore.content.production.assembler;

import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class AssemblerScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final AssemblerBlockEntity blockEntity;
    private String clientRecipeId = null;
    private boolean slotsVisible = true;

    public AssemblerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(5), new ArrayPropertyDelegate(8));
    }

    public AssemblerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate delegate) {
        super(ModScreenHandlers.ASSEMBLER_SCREEN_HANDLER, syncId);
        checkSize(inventory, 5);
        this.inventory = inventory;
        this.propertyDelegate = delegate;
        this.blockEntity = (inventory instanceof AssemblerBlockEntity be) ? be : null;
        inventory.onOpen(playerInventory.player);

        // 4 слота сырья (каждый привязан строго к своему ингредиенту 0..3)
        this.addSlot(new FactorioInputSlot(inventory, 0, 198, 144));
        this.addSlot(new FactorioInputSlot(inventory, 1, 217, 144));
        this.addSlot(new FactorioInputSlot(inventory, 2, 236, 144));
        this.addSlot(new FactorioInputSlot(inventory, 3, 255, 144));

        // 1 слот готовой продукции
        this.addSlot(new Slot(inventory, 4, 398, 144) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
            @Override public boolean isEnabled() { return AssemblerScreenHandler.this.slotsVisible; }
        });

        // Левая панель "Персонаж": Основной инвентарь (3 ряда по 9)
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(playerInventory, c + r * 9 + 9, 12 + c * 18, 36 + r * 18) {
                    @Override public boolean isEnabled() { return AssemblerScreenHandler.this.slotsVisible; }
                });
            }
        }
        // Хотбар (1 ряд по 9)
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(playerInventory, c, 12 + c * 18, 96) {
                @Override public boolean isEnabled() { return AssemblerScreenHandler.this.slotsVisible; }
            });
        }

        this.addProperties(delegate);
    }

    public void setSlotsVisible(boolean visible) {
        this.slotsVisible = visible;
    }

    public boolean isSynced() {
        return blockEntity != null || propertyDelegate.get(1) > 0;
    }

    public ItemStack getStack(int slot) {
        if (slot >= 0 && slot < this.slots.size()) {
            return this.getSlot(slot).getStack();
        }
        return ItemStack.EMPTY;
    }

    public int getProgress() { return propertyDelegate.get(0); }
    public int getMaxProgress() { return Math.max(1, propertyDelegate.get(1)); }
    public boolean isWorking() { return propertyDelegate.get(2) == 1; }
    public boolean isPowered() { return propertyDelegate.get(3) == 1; }

    public void setClientRecipeId(String rid) {
        this.clientRecipeId = rid;
    }

    public String getRecipeId() {
        if (clientRecipeId != null) {
            return clientRecipeId;
        }
        if (blockEntity != null) {
            return blockEntity.getRecipeId();
        }
        int encoded = propertyDelegate.get(7);
        if (encoded <= 0) return "";
        return FactorioRecipes.getRecipeIdByIndex(encoded - 1);
    }

    public BlockPos getTargetPos() {
        if (blockEntity != null) return blockEntity.getPos();
        return new BlockPos(propertyDelegate.get(4), propertyDelegate.get(5), propertyDelegate.get(6));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (index < 5) {
                if (!this.insertItem(originalStack, 5, 41, true)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(originalStack, 0, 4, false)) return ItemStack.EMPTY;
            }
            if (originalStack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }

    @Override public boolean canUse(PlayerEntity player) { return this.inventory.canPlayerUse(player); }

    private class FactorioInputSlot extends Slot {
        private final int ingIndex;

        public FactorioInputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
            this.ingIndex = index;
        }

        @Override
        public boolean isEnabled() {
            if (!AssemblerScreenHandler.this.slotsVisible) return false;
            FactorioRecipes.Recipe r = FactorioRecipes.getById(getRecipeId());
            return r != null && this.ingIndex < r.ingredients().size();
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            FactorioRecipes.Recipe r = FactorioRecipes.getById(getRecipeId());
            if (r == null || this.ingIndex >= r.ingredients().size()) return false;
            return stack.isOf(r.ingredients().get(this.ingIndex).item());
        }
    }
}