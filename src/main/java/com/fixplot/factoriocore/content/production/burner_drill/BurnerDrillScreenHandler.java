package com.fixplot.factoriocore.content.production.burner_drill;

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

public class BurnerDrillScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final BurnerDrillBlockEntity drillEntity;

    public BurnerDrillScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof BurnerDrillBlockEntity drill)
                        ? drill.getMaster() : null,
                new ArrayPropertyDelegate(5));
    }

    public BurnerDrillScreenHandler(int syncId, PlayerInventory playerInventory,
                                    BurnerDrillBlockEntity drillEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.BURNER_DRILL_SCREEN_HANDLER, syncId);
        this.drillEntity = drillEntity;
        this.inventory = drillEntity != null ? drillEntity : new SimpleInventory(2);
        this.propertyDelegate = delegate;

        this.addProperties(delegate);

        // 1. Слот ТОПЛИВА слева внизу (индекс 0, как пункт 1 на скриншоте вики)
        this.addSlot(new Slot(this.inventory, BurnerDrillBlockEntity.FUEL_SLOT, 11, 108) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return AbstractFurnaceBlockEntity.canUseAsFuel(stack);
            }
        });

        // 2. Слот ПРОДУКТА справа от зелёной шкалы прогресса (индекс 1, как пункт 4 на скриншоте вики)
        this.addSlot(new Slot(this.inventory, BurnerDrillBlockEntity.OUTPUT_SLOT, 207, 84) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // 3. Инвентарь игрока
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 138 + row * 18));
            }
        }

        // 4. Хотбар игрока
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

    public int getMiningProgress() {
        return this.propertyDelegate.get(2);
    }

    public int getMaxMiningTime() {
        return Math.max(1, this.propertyDelegate.get(3));
    }

    public boolean isSleeping() {
        return this.propertyDelegate.get(4) != 0;
    }

    public boolean hasFuel() {
        return getBurnTime() > 0 || !this.slots.get(0).getStack().isEmpty();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < 2) {
                if (!this.insertItem(originalStack, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (AbstractFurnaceBlockEntity.canUseAsFuel(originalStack)) {
                    if (!this.insertItem(originalStack, 0, 1, false)) {
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
