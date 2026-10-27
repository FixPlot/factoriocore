package com.fixplot.factoriocore.content.logistics.belt;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.Arrays;
import java.util.Objects;

public final class BeltLane {

    public static final int CAPACITY = 4;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = CAPACITY - 1;

    private static final String ITEMS_NBT_KEY = "Items";
    private static final String SLOT_NBT_KEY = "Slot";
    private static final String STACK_NBT_KEY = "Stack";
    private static final String MOVING_NBT_KEY = "Moving";

    private final ItemStack[] slots = new ItemStack[CAPACITY];
    private final boolean[] moving = new boolean[CAPACITY];

    public BeltLane() {
        Arrays.fill(this.slots, ItemStack.EMPTY);
        Arrays.fill(this.moving, false);
    }

    public boolean resetMovementFlags() {
        boolean hadMoving = false;
        for (int i = 0; i < CAPACITY; i++) {
            if (this.moving[i]) {
                this.moving[i] = false;
                hadMoving = true;
            }
        }
        return hadMoving;
    }

    public boolean isMoving(int slot) {
        Objects.checkIndex(slot, CAPACITY);
        return this.moving[slot];
    }

    public boolean tick() {
        return tickRange(INPUT_SLOT, OUTPUT_SLOT);
    }

    public boolean tickRange(int minSlot, int maxSlot) {
        boolean moved = false;
        for (int i = maxSlot - 1; i >= minSlot; i--) {
            if (!this.slots[i].isEmpty() && this.slots[i + 1].isEmpty()) {
                this.slots[i + 1] = this.slots[i];
                this.slots[i] = ItemStack.EMPTY;
                this.moving[i + 1] = true;
                this.moving[i] = false;
                moved = true;
            }
        }
        return moved;
    }

    public boolean canInsert() {
        return this.slots[INPUT_SLOT].isEmpty();
    }

    public boolean insert(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !this.canInsert()) {
            return false;
        }
        this.slots[INPUT_SLOT] = stack.copyWithCount(1);
        this.moving[INPUT_SLOT] = false;
        return true;
    }

    public boolean insertFromBelt(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !this.canInsert()) {
            return false;
        }
        this.slots[INPUT_SLOT] = stack.copyWithCount(1);
        this.moving[INPUT_SLOT] = true;
        return true;
    }

    public boolean insertAtFromBelt(int slot, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && canInsertAt(slot)) {
            this.slots[slot] = stack.copyWithCount(1);
            this.moving[slot] = true;
            return true;
        }
        return false;
    }

    public boolean canExtract() {
        return !this.slots[OUTPUT_SLOT].isEmpty();
    }

    public ItemStack extract() {
        if (!this.canExtract()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = this.slots[OUTPUT_SLOT];
        this.slots[OUTPUT_SLOT] = ItemStack.EMPTY;
        this.moving[OUTPUT_SLOT] = false;
        return extracted;
    }

    public ItemStack peekOutput() {
        return this.slots[OUTPUT_SLOT];
    }

    public ItemStack getStack(int slot) {
        Objects.checkIndex(slot, CAPACITY);
        return this.slots[slot];
    }

    public boolean isEmpty() {
        for (ItemStack stack : this.slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        Arrays.fill(this.slots, ItemStack.EMPTY);
        Arrays.fill(this.moving, false);
    }

    public boolean canInsertAt(int slot) {
        return slot >= 0 && slot < CAPACITY && this.slots[slot].isEmpty();
    }

    public boolean insertAt(int slot, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && canInsertAt(slot)) {
            this.slots[slot] = stack.copyWithCount(1);
            this.moving[slot] = false;
            return true;
        }
        return false;
    }

    public boolean hasItemAt(int slot) {
        return slot >= 0 && slot < CAPACITY && !this.slots[slot].isEmpty();
    }

    public ItemStack extractAt(int slot) {
        if (hasItemAt(slot)) {
            ItemStack stack = this.slots[slot];
            this.slots[slot] = ItemStack.EMPTY;
            this.moving[slot] = false;
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack stack = this.slots[i];
            if (!stack.isEmpty()) {
                NbtCompound entry = new NbtCompound();
                entry.putByte(SLOT_NBT_KEY, (byte) i);
                entry.put(STACK_NBT_KEY, stack.encode(registries));
                entry.putBoolean(MOVING_NBT_KEY, this.moving[i]);
                list.add(entry);
            }
        }
        nbt.put(ITEMS_NBT_KEY, list);
    }

    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        this.clear();
        if (!nbt.contains(ITEMS_NBT_KEY, NbtElement.LIST_TYPE)) {
            return;
        }

        NbtList list = nbt.getList(ITEMS_NBT_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (entry.contains(SLOT_NBT_KEY) && entry.contains(STACK_NBT_KEY)) {
                int slot = entry.getByte(SLOT_NBT_KEY);
                if (slot >= 0 && slot < CAPACITY) {
                    this.slots[slot] = ItemStack.fromNbt(registries, entry.getCompound(STACK_NBT_KEY))
                            .orElse(ItemStack.EMPTY);
                    this.moving[slot] = entry.getBoolean(MOVING_NBT_KEY);
                }
            }
        }
    }
}