package com.fixplot.factoriocore.content.logistics.splitter;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
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

public class SplitterScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final BlockPos targetPos;
    public final BeltBlockEntity splitterEntity;

    public SplitterScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof BeltBlockEntity belt)
                        ? belt.getMasterSplitter() : null,
                new ArrayPropertyDelegate(4));
    }

    public SplitterScreenHandler(int syncId, PlayerInventory playerInventory, BeltBlockEntity splitterEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.SPLITTER_SCREEN_HANDLER, syncId);
        this.splitterEntity = splitterEntity;
        this.targetPos = splitterEntity != null ? splitterEntity.getPos() : BlockPos.ORIGIN;
        this.inventory = splitterEntity != null ? splitterEntity : new SimpleInventory(1);
        this.propertyDelegate = delegate;

        this.addProperties(delegate);

        // Призрачный слот фильтра справа (X: 207, Y: 118)
        this.addSlot(new Slot(this.inventory, 0, 207, 118) {
            @Override
            public boolean canTakeItems(PlayerEntity playerEntity) {
                return false;
            }
        });

        // Инвентарь игрока (отцентрирован под ширину 236)
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 158 + row * 18));
            }
        }

        // Хотбар игрока
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 216));
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == 0) {
            Slot filterSlot = this.slots.get(0);
            ItemStack cursorStack = this.getCursorStack();
            filterSlot.setStack(cursorStack.isEmpty() ? ItemStack.EMPTY : cursorStack.copyWithCount(1));
            filterSlot.markDirty();
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    public boolean isInputPriority() {
        return this.propertyDelegate.get(0) != 0;
    }

    public boolean isInputRight() {
        return this.propertyDelegate.get(1) != 0;
    }

    public boolean isOutputPriority() {
        return this.propertyDelegate.get(2) != 0;
    }

    public boolean isOutputRight() {
        return this.propertyDelegate.get(3) != 0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        Slot slot = this.slots.get(invSlot);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        if (invSlot == 0) {
            slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            return ItemStack.EMPTY;
        }
        Slot filterSlot = this.slots.get(0);
        filterSlot.setStack(slot.getStack().copyWithCount(1));
        filterSlot.markDirty();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}
