package com.fixplot.factoriocore.content.research;

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

public class LabScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;

    public LabScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(7), new ArrayPropertyDelegate(2));
    }

    public LabScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate delegate) {
        super(ModScreenHandlers.LAB_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.propertyDelegate = delegate;
        this.addProperties(delegate);

        int slotsStartX = (236 - (7 * 19)) / 2 + 1;
        for (int i = 0; i < 7; i++) {
            final int slotIndex = i;
            this.addSlot(new Slot(inventory, slotIndex, slotsStartX + i * 19, 103) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return LabBlockEntity.getSlotForPack(stack.getItem()) == slotIndex;
                }
            });
        }

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 37 + j * 18, 137 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 37 + i * 18, 195));
        }
    }

    public int getResearchProgress() { return this.propertyDelegate.get(0); }
    public int getMaxResearchTime() { return Math.max(1, this.propertyDelegate.get(1)); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < 7) {
                if (!this.insertItem(originalStack, 7, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int targetPackSlot = LabBlockEntity.getSlotForPack(originalStack.getItem());
                if (targetPackSlot >= 0) {
                    if (!this.insertItem(originalStack, targetPackSlot, targetPackSlot + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}