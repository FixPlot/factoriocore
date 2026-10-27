package com.fixplot.factoriocore.content.logistics.inserter;

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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

public class InserterScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final BlockPos targetPos;
    public final InserterBlockEntity blockEntity;

    public InserterScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof InserterBlockEntity ins) ? ins : null,
                new ArrayPropertyDelegate(5));
    }

    public InserterScreenHandler(int syncId, PlayerInventory playerInventory, InserterBlockEntity blockEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.INSERTER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.targetPos = blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
        this.inventory = blockEntity != null ? blockEntity : new SimpleInventory(6);
        this.propertyDelegate = delegate;

        this.addProperties(delegate);

        // 1. Слот содержимого лапки (0)
        this.addSlot(new Slot(this.inventory, InserterBlockEntity.HAND_SLOT_INDEX, 13, 75) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // 2. Пять призрачных слотов фильтра (1..5)
        for (int i = 0; i < InserterBlockEntity.FILTER_SLOTS_COUNT; i++) {
            this.addSlot(new Slot(this.inventory, i, 135 + i * 18, 106) {
                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    return false;
                }
            });
        }

        // 3. Инвентарь игрока
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 158 + row * 18));
            }
        }

        // 4. Хотбар игрока
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 216));
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 1 && slotIndex <= InserterBlockEntity.FILTER_SLOTS_COUNT) {
            Slot filterSlot = this.slots.get(slotIndex);
            ItemStack cursorStack = this.getCursorStack();
            filterSlot.setStack(cursorStack.isEmpty() ? ItemStack.EMPTY : cursorStack.copyWithCount(1));
            filterSlot.markDirty();
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    public int getMaxPickupCount() {
        return this.propertyDelegate.get(0);
    }

    public boolean isSleeping() {
        return this.propertyDelegate.get(1) != 0;
    }

    public boolean isFilterEnabled() {
        return this.propertyDelegate.get(2) != 0;
    }

    public boolean isBlacklistMode() {
        return this.propertyDelegate.get(3) != 0;
    }

    public boolean isOverrideStackSize() {
        return this.propertyDelegate.get(4) != 0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        Slot slot = this.slots.get(invSlot);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        if (invSlot == 0) {
            ItemStack originalStack = slot.getStack();
            ItemStack newStack = originalStack.copy();
            if (!this.insertItem(originalStack, 6, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            return newStack;
        }

        if (invSlot >= 1 && invSlot <= InserterBlockEntity.FILTER_SLOTS_COUNT) {
            slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            return ItemStack.EMPTY;
        }

        ItemStack clickedStack = slot.getStack();
        for (int i = 1; i <= InserterBlockEntity.FILTER_SLOTS_COUNT; i++) {
            Slot filterSlot = this.slots.get(i);
            if (filterSlot.hasStack() && ItemStack.areItemsEqual(filterSlot.getStack(), clickedStack)) {
                return ItemStack.EMPTY;
            }
        }
        for (int i = 1; i <= InserterBlockEntity.FILTER_SLOTS_COUNT; i++) {
            Slot filterSlot = this.slots.get(i);
            if (!filterSlot.hasStack()) {
                filterSlot.setStack(clickedStack.copyWithCount(1));
                filterSlot.markDirty();
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}