package com.fixplot.factoriocore.content.logistics.burner_inserter;

import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.registry.ModScreenHandlers;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
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

public class BurnerInserterScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final InserterBlockEntity blockEntity;

    public BurnerInserterScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof InserterBlockEntity ins) ? ins : null,
                new ArrayPropertyDelegate(3));
    }

    public BurnerInserterScreenHandler(int syncId, PlayerInventory playerInventory, InserterBlockEntity blockEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.BURNER_INSERTER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.inventory = blockEntity != null ? blockEntity : new SimpleInventory(7);
        this.propertyDelegate = delegate;

        this.addProperties(delegate);

        // 1. Слот содержимого лапки (слот 0 в контейнере -> индекс 5 в блоке)
        this.addSlot(new Slot(this.inventory, InserterBlockEntity.HAND_SLOT_INDEX, 13, 75) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // 2. Слот ТОПЛИВА (слот 1 в контейнере -> индекс 6 в блоке)
        this.addSlot(new Slot(this.inventory, InserterBlockEntity.FUEL_SLOT_INDEX, 69, 103) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return AbstractFurnaceBlockEntity.canUseAsFuel(stack);
            }
        });

        // 3. Инвентарь игрока (3 ряда по 9 слотов)
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 132 + row * 18));
            }
        }

        // 4. Хотбар игрока (9 слотов)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 190));
        }
    }

    public boolean isSleeping() {
        return this.propertyDelegate.get(0) != 0;
    }

    public int getBurnTime() {
        return this.propertyDelegate.get(1);
    }

    public int getMaxBurnTime() {
        return this.propertyDelegate.get(2);
    }

    public boolean hasFuelEnergy() {
        return getBurnTime() > 0 || !this.slots.get(1).getStack().isEmpty();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            // Слоты 0 (Лапка) и 1 (Топливо) -> в инвентарь игрока
            if (invSlot < 2) {
                if (!this.insertItem(originalStack, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря игрока по Shift-клику переносим топливо в слот топлива (индекс 1)
                if (AbstractFurnaceBlockEntity.canUseAsFuel(originalStack)) {
                    if (!this.insertItem(originalStack, 1, 2, false)) {
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
