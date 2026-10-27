package com.fixplot.factoriocore.content.production.furnace;

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

public class FactorioFurnaceScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final FactorioFurnaceBlockEntity furnaceEntity;

    public FactorioFurnaceScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof FactorioFurnaceBlockEntity f)
                        ? f.getMaster() : null,
                new ArrayPropertyDelegate(5));
    }

    public FactorioFurnaceScreenHandler(int syncId, PlayerInventory playerInventory,
                                        FactorioFurnaceBlockEntity furnaceEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.FACTORIO_FURNACE_SCREEN_HANDLER, syncId);
        this.furnaceEntity = furnaceEntity;
        this.inventory = furnaceEntity != null ? furnaceEntity : new SimpleInventory(3);
        this.propertyDelegate = delegate;

        this.addProperties(delegate);

        // 1. Слот СЫРЬЯ слева от зелёной шкалы плавки (индекс 0)
        this.addSlot(new Slot(this.inventory, FactorioFurnaceBlockEntity.INPUT_SLOT, 11, 84) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return !AbstractFurnaceBlockEntity.canUseAsFuel(stack);
            }
        });

        // 2. Слот ТОПЛИВА слева от красной шкалы топлива (индекс 1)
        this.addSlot(new Slot(this.inventory, FactorioFurnaceBlockEntity.FUEL_SLOT, 11, 108) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return AbstractFurnaceBlockEntity.canUseAsFuel(stack);
            }
        });

        // 3. Слот ГОТОВОГО ПРОДУКТА справа от зелёной шкалы плавки (индекс 2)
        this.addSlot(new Slot(this.inventory, FactorioFurnaceBlockEntity.OUTPUT_SLOT, 207, 84) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // 4. Инвентарь игрока
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 138 + row * 18));
            }
        }

        // 5. Хотбар игрока
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 196));
        }
    }

    public int getBurnTime() {
        return this.propertyDelegate.get(0);
    }

    public int getMaxBurnTime() {
        return Math.max(1, this.propertyDelegate.get(1));
    }

    public int getCookTime() {
        return this.propertyDelegate.get(2);
    }

    public int getRequiredCookTime() {
        return Math.max(1, this.propertyDelegate.get(3));
    }

    public boolean isSleeping() {
        return this.propertyDelegate.get(4) != 0;
    }

    public boolean hasFuel() {
        return getBurnTime() > 0 || !this.slots.get(1).getStack().isEmpty();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < 3) {
                if (!this.insertItem(originalStack, 3, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (AbstractFurnaceBlockEntity.canUseAsFuel(originalStack)) {
                    if (!this.insertItem(originalStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.insertItem(originalStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
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
