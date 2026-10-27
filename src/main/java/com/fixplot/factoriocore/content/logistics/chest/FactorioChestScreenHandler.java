package com.fixplot.factoriocore.content.logistics.chest;

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

public class FactorioChestScreenHandler extends ScreenHandler {

    public static final int COLUMNS = 8;
    public static final int GRID_START_X = 83;

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    public final BlockPos targetPos;
    public final FactorioChestBlockEntity chestEntity;
    public final int totalSlots;
    public final int rows;
    public final int gridStartY;

    public FactorioChestScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory,
                (playerInventory.player.getWorld().getBlockEntity(pos) instanceof FactorioChestBlockEntity chest) ? chest : null,
                new ArrayPropertyDelegate(2));
    }

    public FactorioChestScreenHandler(int syncId, PlayerInventory playerInventory,
                                      FactorioChestBlockEntity chestEntity, PropertyDelegate delegate) {
        super(ModScreenHandlers.FACTORIO_CHEST_SCREEN_HANDLER, syncId);
        this.chestEntity = chestEntity;
        this.targetPos = chestEntity != null ? chestEntity.getPos() : BlockPos.ORIGIN;
        this.totalSlots = chestEntity != null ? chestEntity.getTotalSlots() : 16;
        this.rows = this.totalSlots / COLUMNS;
        // Центрируем сетку по вертикали для деревянного (2 ряда) и железного (4 ряда) сундуков
        this.gridStartY = 21 + ((6 - this.rows) * 9);

        this.inventory = chestEntity != null ? chestEntity : new SimpleInventory(this.totalSlots);
        this.propertyDelegate = delegate;
        this.addProperties(delegate);

        // 1. Слоты сундука (16, 32 или 48 слотов в 8 колонок)
        for (int r = 0; r < this.rows; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int slotIdx = c + r * COLUMNS;
                this.addSlot(new Slot(this.inventory, slotIdx, GRID_START_X + c * 18, this.gridStartY + r * 18));
            }
        }

        // 2. Инвентарь игрока (3 ряда по 9 слотов)
        int invStartX = 38;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invStartX + col * 18, 138 + row * 18));
            }
        }

        // 3. Хотбар игрока
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, invStartX + col * 18, 196));
        }
    }

    public int getUnlockedSlots() {
        return this.propertyDelegate.get(0);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < this.totalSlots) {
                // Из сундука в инвентарь игрока
                if (!this.insertItem(originalStack, this.totalSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря игрока в сундук — ТОЛЬКО в незаблокированные слоты (0 .. unlockedSlots)!
                int unlocked = getUnlockedSlots();
                if (unlocked <= 0 || !this.insertItem(originalStack, 0, unlocked, false)) {
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
