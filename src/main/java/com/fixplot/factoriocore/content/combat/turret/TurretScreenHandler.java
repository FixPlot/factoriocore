package com.fixplot.factoriocore.content.combat.turret;

import com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons;
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

public class TurretScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final TurretBlockEntity turretEntity;

    public TurretScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof TurretBlockEntity t) ? t.getMaster() : null,
                new ArrayPropertyDelegate(7));
    }

    public TurretScreenHandler(int syncId, PlayerInventory playerInventory, TurretBlockEntity turretEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.TURRET_SCREEN_HANDLER, syncId);
        this.turretEntity = turretEntity;
        this.inventory = turretEntity != null ? turretEntity : new SimpleInventory(1);
        this.propertyDelegate = delegate;
        this.addProperties(delegate);

        // Слот магазина патронов (активен для Пулемётной турели)
        this.addSlot(new Slot(this.inventory, 0, 11, 88) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return getTurretKind() == 0 && stack.getItem() instanceof FactorioWeapons.AmmoMagazineItem;
            }

            @Override
            public boolean isEnabled() {
                return getTurretKind() == 0;
            }
        });

        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 138 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 196));
        }
    }

    public int getTurretKind() { return this.propertyDelegate.get(0); }
    public int getCurrentRounds() { return this.propertyDelegate.get(1); }
    public int getMaxRounds() { return Math.max(1, this.propertyDelegate.get(2)); }
    public int getFuelFluidMb() { return this.propertyDelegate.get(3); }
    public boolean isPowered() { return this.propertyDelegate.get(4) != 0; }
    public int getKillsCount() { return this.propertyDelegate.get(5); }
    public int getTotalDamage() { return this.propertyDelegate.get(6); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot == 0) {
                if (!this.insertItem(originalStack, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (getTurretKind() == 0 && originalStack.getItem() instanceof FactorioWeapons.AmmoMagazineItem) {
                if (!this.insertItem(originalStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
            if (originalStack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}