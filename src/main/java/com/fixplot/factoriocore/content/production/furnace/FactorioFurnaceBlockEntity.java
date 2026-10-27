package com.fixplot.factoriocore.content.production.furnace;

import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class FactorioFurnaceBlockEntity extends BlockEntity
        implements ExtendedScreenHandlerFactory<BlockPos>, SidedInventory,
        com.fixplot.factoriocore.content.simulation.VirtualSimulatable {

    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    public static final int STONE_SMELT_TICKS = 64;
    public static final int STEEL_SMELT_TICKS = 32;
    public static final int COAL_BURN_TICKS = 889;

    // В Factorio на выплавку 1 Стальной балки требуется 5 Железных плит (слитков)
    public static final int IRON_PER_STEEL_PLATE = 5;

    private static final int[] TOP_SLOTS = new int[]{ INPUT_SLOT };
    private static final int[] SIDE_SLOTS = new int[]{ FUEL_SLOT, INPUT_SLOT };
    private static final int[] BOTTOM_SLOTS = new int[]{ OUTPUT_SLOT, FUEL_SLOT };

    private ItemStack inputStack = ItemStack.EMPTY;
    private ItemStack fuelStack = ItemStack.EMPTY;
    private ItemStack outputStack = ItemStack.EMPTY;

    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int cookTime = 0;
    private boolean sleeping = false;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> cookTime;
                case 3 -> getRequiredCookTime();
                case 4 -> sleeping ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> maxBurnTime = value;
                case 2 -> cookTime = value;
                case 4 -> sleeping = (value != 0);
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };

    public FactorioFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FACTORIO_FURNACE_BLOCK_ENTITY, pos, state);
    }

    public int getTier() {
        BlockState state = getCachedState();
        if (state.getBlock() instanceof FactorioFurnaceBlock furnaceBlock) {
            return furnaceBlock.getTier();
        }
        return 1;
    }

    public int getRequiredCookTime() {
        int base = (getTier() >= 2) ? STEEL_SMELT_TICKS : STONE_SMELT_TICKS;
        if (!this.inputStack.isEmpty() && this.inputStack.isOf(Items.IRON_INGOT)) {
            return base * 2;
        }
        return base;
    }

    public static int getRequiredInputCount(ItemStack input) {
        if (input.isOf(Items.IRON_INGOT)) {
            return IRON_PER_STEEL_PLATE;
        }
        return 1;
    }

    public FactorioFurnaceBlockEntity getMaster() {
        if (this.world == null) return this;
        BlockState state = getCachedState();
        if (state.getBlock() instanceof FactorioFurnaceBlock) {
            if (state.get(FactorioFurnaceBlock.PART) == 0 && !state.get(FactorioFurnaceBlock.UPPER)) {
                return this;
            }
            BlockPos masterPos = FactorioFurnaceBlock.getMasterPos(this.pos, state);
            if (this.world.getBlockEntity(masterPos) instanceof FactorioFurnaceBlockEntity master) {
                return master;
            }
        }
        return this;
    }

    public void wakeUp() {
        FactorioFurnaceBlockEntity master = getMaster();
        if (master.sleeping) {
            master.sleeping = false;
        }
    }

    public boolean isBurning() {
        return getMaster().burnTime > 0 && !getMaster().sleeping;
    }

    public static void tick(World world, BlockPos pos, BlockState state, FactorioFurnaceBlockEntity entity) {
        if (world == null || world.isClient) return;

        boolean wasSleeping = entity.sleeping;
        if (entity.sleeping) {
            if (world.getTime() % 10 != 0) {
                return;
            }
            entity.sleeping = false;
        }

        int requiredInput = getRequiredInputCount(entity.inputStack);
        if (entity.inputStack.isEmpty() || entity.inputStack.getCount() < requiredInput) {
            boolean hadCookTime = entity.cookTime > 0;
            entity.cookTime = 0;
            entity.sleeping = true;
            if (!wasSleeping || hadCookTime) {
                entity.syncState();
            }
            return;
        }

        ItemStack recipeResult = getSmeltingResult(world, entity.inputStack);
        if (recipeResult.isEmpty() || !canAcceptOutput(entity.outputStack, recipeResult)) {
            boolean hadCookTime = entity.cookTime > 0;
            entity.cookTime = 0;
            entity.sleeping = true;
            if (!wasSleeping || hadCookTime) {
                entity.syncState();
            }
            return;
        }

        if (!entity.ensureFuel()) {
            entity.sleeping = true;
            if (!wasSleeping) {
                entity.syncState();
            }
            return;
        }

        if (wasSleeping) {
            entity.syncState();
        }

        entity.burnTime--;
        entity.cookTime++;

        int requiredTicks = entity.getRequiredCookTime();
        if (entity.cookTime >= requiredTicks) {
            entity.cookTime = 0;
            entity.inputStack.decrement(requiredInput);
            if (entity.inputStack.isEmpty()) {
                entity.inputStack = ItemStack.EMPTY;
            }

            if (entity.outputStack.isEmpty()) {
                entity.outputStack = recipeResult.copy();
            } else {
                entity.outputStack.increment(recipeResult.getCount());
            }

            wakeUpSurroundingInserters(world, pos, state);
            entity.syncState();
        } else if (entity.burnTime == 0) {
            entity.syncState();
        } else {
            entity.markDirty();
        }
    }

    public static void wakeUpSurroundingInserters(World world, BlockPos masterPos, BlockState state) {
        if (world == null || world.isClient) return;
        Direction facing = state.contains(FactorioFurnaceBlock.FACING) ? state.get(FactorioFurnaceBlock.FACING) : Direction.NORTH;
        for (int part = 0; part < 4; part++) {
            BlockPos partPos = FactorioFurnaceBlock.getPartPos(masterPos, facing, part, false);
            InserterBlockEntity.wakeUpAdjacentInserters(world, partPos);
        }
    }

    private boolean ensureFuel() {
        if (this.burnTime > 0) {
            return true;
        }
        if (!this.fuelStack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(this.fuelStack)) {
            Integer vanillaTicks = FuelRegistry.INSTANCE.get(this.fuelStack.getItem());
            int base = (vanillaTicks != null && vanillaTicks > 0) ? vanillaTicks : 1600;
            int factorioTicks = Math.max(40, Math.round(base * (COAL_BURN_TICKS / 1600.0f)));
            this.burnTime = factorioTicks;
            this.maxBurnTime = factorioTicks;

            Item remainder = this.fuelStack.getItem().getRecipeRemainder();
            this.fuelStack.decrement(1);
            if (this.fuelStack.isEmpty()) {
                this.fuelStack = (remainder != null) ? new ItemStack(remainder) : ItemStack.EMPTY;
            }
            syncState();
            return true;
        }
        return false;
    }

    public static ItemStack getSmeltingResult(World world, ItemStack input) {
        if (input.isOf(Items.IRON_INGOT)) {
            if (!com.fixplot.factoriocore.content.research.ResearchManager.isTechResearched("steel_processing")) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(ModItems.STEEL_PLATE, 1);
        }
        if (world == null || input.isEmpty()) return ItemStack.EMPTY;
        SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);
        Optional<RecipeEntry<SmeltingRecipe>> match = world.getRecipeManager()
                .getFirstMatch(RecipeType.SMELTING, recipeInput, world);
        return match.map(entry -> entry.value().getResult(world.getRegistryManager()).copy())
                .orElse(ItemStack.EMPTY);
    }

    private static boolean canAcceptOutput(ItemStack currentOutput, ItemStack result) {
        if (currentOutput.isEmpty()) return true;
        if (!ItemStack.areItemsAndComponentsEqual(currentOutput, result)) return false;
        return currentOutput.getCount() + result.getCount() <= currentOutput.getMaxCount();
    }

    public void syncState() {
        markDirty();
        if (this.world != null) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == OUTPUT_SLOT) {
            return false;
        }
        if (slot == FUEL_SLOT) {
            return AbstractFurnaceBlockEntity.canUseAsFuel(stack);
        }
        return !AbstractFurnaceBlockEntity.canUseAsFuel(stack);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) return BOTTOM_SLOTS;
        return side == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (slot == FUEL_SLOT) {
            return stack.isOf(Items.BUCKET);
        }
        return slot == OUTPUT_SLOT;
    }

    @Override
    public boolean isEmpty() {
        FactorioFurnaceBlockEntity m = getMaster();
        return m.inputStack.isEmpty() && m.fuelStack.isEmpty() && m.outputStack.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        FactorioFurnaceBlockEntity m = getMaster();
        return switch (slot) {
            case INPUT_SLOT -> m.inputStack;
            case FUEL_SLOT -> m.fuelStack;
            case OUTPUT_SLOT -> m.outputStack;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        FactorioFurnaceBlockEntity m = getMaster();
        ItemStack res = ItemStack.EMPTY;
        if (slot == INPUT_SLOT) {
            res = m.inputStack.split(amount);
            if (m.inputStack.isEmpty()) m.inputStack = ItemStack.EMPTY;
        } else if (slot == FUEL_SLOT) {
            res = m.fuelStack.split(amount);
            if (m.fuelStack.isEmpty()) m.fuelStack = ItemStack.EMPTY;
        } else if (slot == OUTPUT_SLOT) {
            res = m.outputStack.split(amount);
            if (m.outputStack.isEmpty()) m.outputStack = ItemStack.EMPTY;
        }
        if (!res.isEmpty()) {
            m.wakeUp();
            m.syncState();
            if (m.world != null) {
                wakeUpSurroundingInserters(m.world, m.pos, m.getCachedState());
            }
        }
        return res;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return removeStack(slot, 64);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        FactorioFurnaceBlockEntity m = getMaster();
        switch (slot) {
            case INPUT_SLOT -> m.inputStack = stack;
            case FUEL_SLOT -> m.fuelStack = stack;
            case OUTPUT_SLOT -> m.outputStack = stack;
        }
        m.wakeUp();
        m.syncState();
        if (m.world != null) {
            wakeUpSurroundingInserters(m.world, m.pos, m.getCachedState());
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(getMaster(), player, 10.0F);
    }

    @Override
    public void clear() {
        FactorioFurnaceBlockEntity m = getMaster();
        m.inputStack = ItemStack.EMPTY;
        m.fuelStack = ItemStack.EMPTY;
        m.outputStack = ItemStack.EMPTY;
        m.wakeUp();
        m.syncState();
    }

    @Override
    public Text getDisplayName() {
        return getMaster().getCachedState().getBlock().getName();
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        FactorioFurnaceBlockEntity master = getMaster();
        return new FactorioFurnaceScreenHandler(syncId, playerInventory, master, master.propertyDelegate);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return getMaster().getPos();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!this.inputStack.isEmpty()) nbt.put("InputStack", this.inputStack.encode(registries));
        if (!this.fuelStack.isEmpty()) nbt.put("FuelStack", this.fuelStack.encode(registries));
        if (!this.outputStack.isEmpty()) nbt.put("OutputStack", this.outputStack.encode(registries));
        nbt.putInt("BurnTime", this.burnTime);
        nbt.putInt("MaxBurnTime", this.maxBurnTime);
        nbt.putInt("CookTime", this.cookTime);
        nbt.putBoolean("Sleeping", this.sleeping);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.inputStack = nbt.contains("InputStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("InputStack")) : ItemStack.EMPTY;
        this.fuelStack = nbt.contains("FuelStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("FuelStack")) : ItemStack.EMPTY;
        this.outputStack = nbt.contains("OutputStack")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("OutputStack")) : ItemStack.EMPTY;
        this.burnTime = nbt.getInt("BurnTime");
        this.maxBurnTime = nbt.getInt("MaxBurnTime");
        this.cookTime = nbt.getInt("CookTime");
        this.sleeping = nbt.getBoolean("Sleeping");
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

    // =====================================================================
    // VirtualSimulatable: симуляция печи вне тикающего чанка (catch-up)
    // =====================================================================

    private long virtualTickedGameTime = 0L;

    @Override
    public NbtCompound captureVirtualState() {
        // Нода нужна всегда: печь может проснуться при catch-up (закончилось топливо,
        // появился вход). Снапшот не хранится отдельно — NBT BE уже в чанке.
        return new NbtCompound();
    }

    @Override
    public void markVirtualTicked(long gameTime) {
        FactorioFurnaceBlockEntity master = getMaster();
        master.virtualTickedGameTime = gameTime;
    }

    @Override
    public long getLastVirtualTicked() {
        return getMaster().virtualTickedGameTime;
    }

    /**
     * Доматывание виртуально прожитых тиков: точная арифметика циклов плавки
     * без обращения к World (рецепт уже вычислен, стаковые операции клампятся).
     * Оценка сверху: N полных циклов + остаток cookTime.
     */
    @Override
    public void catchUp(long elapsedTicks) {
        FactorioFurnaceBlockEntity master = getMaster();
        if (this.world == null || this.world.isClient || elapsedTicks <= 0) return;

        long effective = Math.min(elapsedTicks, 4 * 60 * 20); // кламп на 4 игровые минуты
        int requiredTicks = master.getRequiredCookTime();
        int requiredInput = getRequiredInputCount(master.inputStack);

        if (master.inputStack.isEmpty() || master.inputStack.getCount() < requiredInput) {
            master.cookTime = 0;
            master.sleeping = true;
            return; // нечего плавить — спим, как живой тикер
        }

        ItemStack recipeResult = getSmeltingResult(this.world, master.inputStack);
        if (recipeResult.isEmpty()) {
            master.cookTime = 0;
            master.sleeping = true;
            return;
        }

        // Расход топлива пропорционален времени: тик горения за 60 мБ пара не важен
        // для печи — сжигаем топливо напрямую из стака.
        long totalCookTime = master.cookTime + effective;
        int cyclesCompleted = (int) Math.min(
                totalCookTime / requiredTicks,
                master.inputStack.getCount() / requiredInput);

        // Ограничение выходом: не дюпаем сверх вместимости слота.
        int maxCyclesByOutput = Integer.MAX_VALUE;
        if (!master.outputStack.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(master.outputStack, recipeResult)) {
                master.cookTime = 0;
                master.sleeping = true;
                return; // выход занят чужим предметом
            }
            int space = master.outputStack.getMaxCount() - master.outputStack.getCount();
            maxCyclesByOutput = space / recipeResult.getCount();
        }
        cyclesCompleted = Math.min(cyclesCompleted, maxCyclesByOutput);
        if (cyclesCompleted <= 0) {
            // Чистое время без завершения цикла: прокручиваем только cookTime.
            master.cookTime = (int) Math.min(totalCookTime, requiredTicks - 1);
            master.markDirty();
            return;
        }

        // Топливо: каждый цикл стоит requiredTicks тиков горения.
        long burnCost = (long) cyclesCompleted * requiredTicks;
        // burnTime уже учтён в виртуальном прошедшем — сжигаем из стака при нехватке.
        if (master.burnTime > 0) {
            long used = Math.min(master.burnTime, burnCost);
            master.burnTime -= (int) used;
            burnCost -= used;
        }
        while (burnCost > 0 && !master.fuelStack.isEmpty()
                && AbstractFurnaceBlockEntity.canUseAsFuel(master.fuelStack)) {
            Integer vanillaTicks = FuelRegistry.INSTANCE.get(master.fuelStack.getItem());
            int base = (vanillaTicks != null && vanillaTicks > 0) ? vanillaTicks : 1600;
            int factorioTicks = Math.max(40, Math.round(base * (COAL_BURN_TICKS / 1600.0f)));
            master.fuelStack.decrement(1);
            if (master.fuelStack.isEmpty()) {
                Item remainder = master.fuelStack.getItem().getRecipeRemainder();
                master.fuelStack = (remainder != null) ? new ItemStack(remainder) : ItemStack.EMPTY;
            }
            master.burnTime = factorioTicks;
            master.maxBurnTime = factorioTicks;
            long used = Math.min(master.burnTime, burnCost);
            master.burnTime -= (int) used;
            burnCost -= used;
        }
        if (burnCost > 0) {
            // Топлива не хватило: циклов столько, сколько топлива покрыло.
            cyclesCompleted = Math.max(0, cyclesCompleted - (int) ((burnCost + requiredTicks - 1) / requiredTicks));
        }

        if (cyclesCompleted > 0) {
            master.inputStack.decrement(cyclesCompleted * requiredInput);
            if (master.inputStack.isEmpty()) master.inputStack = ItemStack.EMPTY;
            int produced = cyclesCompleted * recipeResult.getCount();
            if (master.outputStack.isEmpty()) {
                master.outputStack = recipeResult.copy();
                produced -= recipeResult.getCount();
                master.outputStack.setCount(Math.min(master.outputStack.getCount(), master.outputStack.getMaxCount()));
            }
            if (produced > 0) {
                int space = master.outputStack.getMaxCount() - master.outputStack.getCount();
                master.outputStack.increment(Math.min(produced, Math.max(0, space))); // кламп: дюп невозможен
            }
        }

        long leftover = (master.cookTime + effective) % requiredTicks;
        master.cookTime = (int) leftover;
        master.sleeping = master.inputStack.isEmpty() || master.inputStack.getCount() < requiredInput;
        master.markDirty();
    }
}