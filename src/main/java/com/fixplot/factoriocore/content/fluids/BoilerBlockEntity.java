package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BoilerBlockEntity extends BlockEntity implements FactorioFluidContainer, Inventory, NamedScreenHandlerFactory {

    public static final int CAPACITY_MB = 2000;
    public static final int STEAM_PER_TICK_MB = 60;

    private ItemStack fuelStack = ItemStack.EMPTY;
    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int steamBoiledSubTick = 0; // Накапливает мБ выработанного пара (60 мБ = 1 тик топлива)
    private int currentSteamRateMb = 0; // Фактическая выработка пара в текущем тике (0..60 мБ/тик)
    private int waterMb = 0;
    private int steamMb = 0;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> Math.max(1, maxBurnTime);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) burnTime = value;
            else if (index == 1) maxBurnTime = value;
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BOILER_BLOCK_ENTITY, pos, state);
    }

    /**
     * Бойлер активно горит ТОЛЬКО тогда, когда он реально вырабатывает пар (буфер пара не полон до отказа).
     */
    public boolean isBurning() {
        return this.burnTime > 0 && this.currentSteamRateMb > 0;
    }

    public float getLoadRatio() {
        return Math.min(1.0f, this.currentSteamRateMb / (float) STEAM_PER_TICK_MB);
    }

    public int getWaterMb() {
        return this.waterMb;
    }

    public int getSteamMb() {
        return this.steamMb;
    }

    public static void tick(World world, BlockPos pos, BlockState state, BoilerBlockEntity boiler) {
        if (world == null || world.isClient) return;

        boolean wasBurning = boiler.isBurning();
        boolean dirty = false;
        Direction facing = state.get(BoilerBlock.FACING);

        // 1. Сначала выталкиваем готовый пар в паровой двигатель или трубу спереди/сзади
        if (boiler.steamMb > 0) {
            BlockPos frontSteamOut = BoilerBlock.getPartPos(pos, facing, 4, false).offset(facing);
            int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                    world, frontSteamOut, facing.getOpposite(), FluidKind.STEAM, Math.min(boiler.steamMb, 120), 165);
            if (pushed > 0) {
                boiler.steamMb -= pushed;
                dirty = true;
            }
            if (boiler.steamMb > 0) {
                BlockPos backSteamOut = pos.offset(facing.getOpposite());
                int pushedBack = FactorioFluidContainer.pushFluidToNeighbor(
                        world, backSteamOut, facing, FluidKind.STEAM, Math.min(boiler.steamMb, 120), 165);
                if (pushedBack > 0) {
                    boiler.steamMb -= pushedBack;
                    dirty = true;
                }
            }
        }

        // 2. Кипятим ровно столько воды, сколько реально нужно для заполнения парового буфера (0..60 мБ/тик)
        int spaceForSteam = CAPACITY_MB - boiler.steamMb;
        int toBoilMb = Math.min(STEAM_PER_TICK_MB, Math.min(boiler.waterMb, spaceForSteam));

        if (toBoilMb > 0 && boiler.ensureFuel()) {
            boiler.waterMb -= toBoilMb;
            boiler.steamMb += toBoilMb;
            boiler.currentSteamRateMb = toBoilMb;
            dirty = true;

            // Расходуем топливо строго пропорционально объёму выработанного пара (60 мБ = 1 тик горения)
            boiler.steamBoiledSubTick += toBoilMb;
            while (boiler.steamBoiledSubTick >= STEAM_PER_TICK_MB && boiler.burnTime > 0) {
                boiler.steamBoiledSubTick -= STEAM_PER_TICK_MB;
                boiler.burnTime--;
            }

            if ((world.getTime() & 7L) == 0L && world instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.getX() + 0.5, pos.getY() + 2.55, pos.getZ() + 0.5,
                        1, 0.1, 0.05, 0.1, 0.015);
            }
        } else {
            boiler.currentSteamRateMb = 0;
        }

        // 3. Сквозная передача воды по бокам в соседние бойлеры и трубы
        if (boiler.waterMb > 0) {
            Direction left = facing.rotateYCounterclockwise();
            Direction right = facing.rotateYClockwise();

            BlockPos[] sideChecks = new BlockPos[]{
                    BoilerBlock.getPartPos(pos, facing, 0, false).offset(left),
                    BoilerBlock.getPartPos(pos, facing, 3, false).offset(left),
                    BoilerBlock.getPartPos(pos, facing, 2, false).offset(right),
                    BoilerBlock.getPartPos(pos, facing, 5, false).offset(right)
            };
            Direction[] fromDirs = new Direction[]{ right, right, left, left };

            for (int i = 0; i < sideChecks.length; i++) {
                if (boiler.waterMb <= 0) break;
                BlockPos targetPos = sideChecks[i];
                BlockState targetState = world.getBlockState(targetPos);
                BlockPos canonical = BlockHealthManager.getCanonicalPos(targetPos, targetState);
                BlockEntity be = world.getBlockEntity(canonical);

                if (be instanceof BoilerBlockEntity otherBoiler && otherBoiler != boiler) {
                    int diff = boiler.waterMb - otherBoiler.waterMb;
                    if (diff > 4) {
                        int transfer = diff / 2;
                        otherBoiler.waterMb += transfer;
                        boiler.waterMb -= transfer;
                        otherBoiler.markDirty();
                        dirty = true;
                    }
                } else if (be instanceof PipeBlockEntity pipe) {
                    int diff = boiler.waterMb - pipe.getFluidAmount();
                    if (diff > 20 && pipe.canAcceptFluid(FluidKind.WATER, fromDirs[i])) {
                        int offer = Math.min(80, diff / 2);
                        int accepted = pipe.insertFluid(FluidKind.WATER, offer, 15, fromDirs[i]);
                        if (accepted > 0) {
                            boiler.waterMb -= accepted;
                            dirty = true;
                        }
                    }
                }
            }
        }

        boolean nowBurning = boiler.isBurning();
        if (wasBurning != nowBurning) {
            boiler.sync();
        } else if (dirty) {
            boiler.markDirty();
            if ((world.getTime() % 10) == 0) {
                world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
            }
        }
    }

    private boolean ensureFuel() {
        if (this.burnTime > 0) return true;
        if (!this.fuelStack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(this.fuelStack)) {
            Integer vanilla = FuelRegistry.INSTANCE.get(this.fuelStack.getItem());
            int ticks = (vanilla != null && vanilla > 0) ? Math.max(40, vanilla / 3) : 400;
            this.burnTime = ticks;
            this.maxBurnTime = ticks;

            Item rem = this.fuelStack.getItem().getRecipeRemainder();
            this.fuelStack.decrement(1);
            if (this.fuelStack.isEmpty()) {
                this.fuelStack = (rem != null) ? new ItemStack(rem) : ItemStack.EMPTY;
            }
            sync();
            return true;
        }
        return false;
    }

    public void sync() {
        markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public FluidKind getFluidKind() {
        return this.steamMb > 0 ? FluidKind.STEAM : (this.waterMb > 0 ? FluidKind.WATER : FluidKind.NONE);
    }

    @Override
    public int getFluidAmount() {
        return this.steamMb > 0 ? this.steamMb : this.waterMb;
    }

    @Override
    public int getFluidCapacity() {
        return CAPACITY_MB;
    }

    @Override
    public int getTemperatureC() {
        return this.steamMb > 0 ? 165 : 15;
    }

    @Override
    public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
        return kind == FluidKind.WATER && this.waterMb < CAPACITY_MB;
    }

    @Override
    public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
        if (kind != FluidKind.WATER || maxAmount <= 0) return 0;
        int space = CAPACITY_MB - this.waterMb;
        int add = Math.min(space, maxAmount);
        if (add > 0) {
            boolean wasEmpty = (this.waterMb == 0);
            this.waterMb += add;
            markDirty();
            if (wasEmpty && this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }
        return add;
    }

    @Override public int size() { return 1; }
    @Override public boolean isValid(int slot, ItemStack stack) { return slot == 0 && AbstractFurnaceBlockEntity.canUseAsFuel(stack); }
    @Override public boolean isEmpty() { return this.fuelStack.isEmpty(); }
    @Override public ItemStack getStack(int slot) { return slot == 0 ? this.fuelStack : ItemStack.EMPTY; }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0) {
            ItemStack res = this.fuelStack.split(amount);
            if (this.fuelStack.isEmpty()) this.fuelStack = ItemStack.EMPTY;
            sync();
            return res;
        }
        return ItemStack.EMPTY;
    }

    @Override public ItemStack removeStack(int slot) { return removeStack(slot, 64); }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            this.fuelStack = stack;
            sync();
        }
    }

    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override public void clear() { this.fuelStack = ItemStack.EMPTY; sync(); }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.factoriocore.boiler");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BoilerScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!this.fuelStack.isEmpty()) nbt.put("FuelStack", this.fuelStack.encode(registries));
        nbt.putInt("BurnTime", this.burnTime);
        nbt.putInt("MaxBurnTime", this.maxBurnTime);
        nbt.putInt("SteamBoiledSubTick", this.steamBoiledSubTick);
        nbt.putInt("CurrentSteamRateMb", this.currentSteamRateMb);
        nbt.putInt("WaterMb", this.waterMb);
        nbt.putInt("SteamMb", this.steamMb);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.fuelStack = nbt.contains("FuelStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("FuelStack")) : ItemStack.EMPTY;
        this.burnTime = nbt.getInt("BurnTime");
        this.maxBurnTime = nbt.getInt("MaxBurnTime");
        this.steamBoiledSubTick = nbt.getInt("SteamBoiledSubTick");
        this.currentSteamRateMb = nbt.getInt("CurrentSteamRateMb");
        this.waterMb = nbt.getInt("WaterMb");
        this.steamMb = nbt.getInt("SteamMb");
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}