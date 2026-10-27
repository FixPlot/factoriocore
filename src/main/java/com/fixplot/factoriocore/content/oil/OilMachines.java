package com.fixplot.factoriocore.content.oil;

import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.fluids.FactorioFluidContainer;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import com.fixplot.factoriocore.registry.ModScreenHandlers;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class OilMachines {

    // =================================================================================
    // 1. МЕСТОРОЖДЕНИЕ СЫРОЙ НЕФТИ (CRUDE OIL DEPOSIT)
    // =================================================================================
    public static class CrudeOilDepositBlock extends BlockWithEntity {
        public static final MapCodec<CrudeOilDepositBlock> CODEC = createCodec(CrudeOilDepositBlock::new);

        public CrudeOilDepositBlock(Settings settings) {
            super(settings);
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return CODEC;
        }

        @Override
        protected BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.MODEL;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (random.nextFloat() < 0.45f) {
                double px = pos.getX() + 0.2 + random.nextDouble() * 0.6;
                double pz = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
                world.addParticle(ParticleTypes.LARGE_SMOKE, px, pos.getY() + 1.02, pz, 0.0, 0.02, 0.0);
            }
        }

        @Override
        protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
            return player.isCreative() ? 0.2f : 0.0f;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new CrudeOilDepositBlockEntity(pos, state);
        }
    }

    public static class CrudeOilDepositBlockEntity extends BlockEntity {
        public CrudeOilDepositBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CRUDE_OIL_DEPOSIT_BLOCK_ENTITY, pos, state);
        }
    }

    // =================================================================================
    // БАЗОВЫЙ КАРКАС 3x3 (ДЛЯ НЕФТЯНОЙ ВЫШКИ И ХИМИЧЕСКОГО ЗАВОДА)
    // =================================================================================
    public abstract static class Base3x3MachineBlock extends BlockWithEntity {
        public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
        public static final IntProperty PART = IntProperty.of("part", 0, 8);
        public static final BooleanProperty UPPER = BooleanProperty.of("upper");

        private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 20.0, 16.0);

        protected Base3x3MachineBlock(Settings settings) {
            super(settings);
            this.setDefaultState(this.stateManager.getDefaultState()
                    .with(FACING, Direction.NORTH)
                    .with(PART, 4)
                    .with(UPPER, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART, UPPER);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
        }

        @Override
        protected BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.INVISIBLE;
        }

        @Override
        protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
            return 1.0f;
        }

        @Override
        protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
            return true;
        }

        @Override
        protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
            return 0.10f;
        }

        public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
            int wOff = (part % 3) - 1;
            int dOff = (part / 3) - 1;
            Direction right = facing.rotateYClockwise();
            BlockPos base = masterPos.offset(right, wOff).offset(facing, dOff);
            return upper ? base.up() : base;
        }

        public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
            if (!(state.getBlock() instanceof Base3x3MachineBlock)) return currentPos;
            Direction facing = state.get(FACING);
            int part = state.get(PART);
            boolean upper = state.get(UPPER);
            int wOff = (part % 3) - 1;
            int dOff = (part / 3) - 1;
            Direction right = facing.rotateYClockwise();
            BlockPos base = upper ? currentPos.down() : currentPos;
            return base.offset(right, -wOff).offset(facing, -dOff);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
            super.onPlaced(world, pos, state, placer, itemStack);
            if (!world.isClient && state.get(PART) == 4 && !state.get(UPPER)) {
                Direction facing = state.get(FACING);
                for (int part = 0; part < 9; part++) {
                    for (int u = 0; u <= 1; u++) {
                        if (part == 4 && u == 0) continue;
                        BlockPos p = getPartPos(pos, facing, part, u == 1);
                        world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                    }
                }
            }
        }

        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                Direction facing = state.get(FACING);
                if (world.getBlockEntity(masterPos) instanceof Inventory invBe) {
                    ItemScatterer.spawn(world, masterPos, invBe);
                }
                for (int part = 0; part < 9; part++) {
                    for (int u = 0; u <= 1; u++) {
                        BlockPos p = getPartPos(masterPos, facing, part, u == 1);
                        if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                        }
                    }
                }
            }
            return super.onBreak(world, pos, state, player);
        }
    }

    // =================================================================================
    // 2. НЕФТЯНАЯ ВЫШКА (PUMPJACK — 3x3x2.5)
    // =================================================================================
    public static class PumpjackBlock extends Base3x3MachineBlock {
        public static final MapCodec<PumpjackBlock> CODEC = createCodec(PumpjackBlock::new);

        public PumpjackBlock(Settings settings) {
            super(settings);
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return CODEC;
        }

        public static boolean hasOilInFootprint(World world, BlockPos originPos) {
            return world.getBlockState(originPos.down()).getBlock() instanceof CrudeOilDepositBlock
                    || world.getBlockState(originPos).getBlock() instanceof CrudeOilDepositBlock;
        }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
            if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
                facing = facing.getOpposite();
            }
            BlockPos masterPos = ctx.getBlockPos();
            World world = ctx.getWorld();

            for (int part = 0; part < 9; part++) {
                for (int yOff = 0; yOff <= 1; yOff++) {
                    BlockPos checkPos = getPartPos(masterPos, facing, part, yOff == 1);
                    if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                        return null;
                    }
                }
            }
            if (!hasOilInFootprint(world, masterPos)) return null;
            return this.getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                if (world.getBlockEntity(masterPos) instanceof PumpjackBlockEntity pj) {
                    player.openHandledScreen(pj);
                }
            }
            return ActionResult.SUCCESS;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return (state.get(PART) == 4 && !state.get(UPPER)) ? new PumpjackBlockEntity(pos, state) : null;
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            if (state.get(PART) != 4 || state.get(UPPER)) return null;
            return validateTicker(type, ModBlocks.PUMPJACK_BLOCK_ENTITY,
                    world.isClient ? PumpjackBlockEntity::clientTick : PumpjackBlockEntity::tick);
        }
    }

    public static class PumpjackBlockEntity extends BlockEntity implements FactorioFluidContainer, NamedScreenHandlerFactory {
        public static final long POWER_CONSUMPTION_WATTS = 90_000L;
        public static final int CAPACITY_MB = 2000;
        public static final int OIL_PER_CYCLE_MB = 100;

        private int crudeOilMb = 0;
        private int progress = 0;
        private boolean powered = false;
        private boolean working = false;
        private float clientPhase = 0.0f;

        protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> OilMachineScreenHandler.TYPE_PUMPJACK;
                    case 1 -> 0;
                    case 2 -> progress;
                    case 3 -> 20;
                    case 4 -> powered ? 1 : 0;
                    case 5 -> working ? 1 : 0;
                    case 6 -> crudeOilMb;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int size() { return 11; }
        };

        public PumpjackBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.PUMPJACK_BLOCK_ENTITY, pos, state);
        }

        public boolean isPowered() { return this.powered; }
        public boolean isWorking() { return this.working; }

        public float getClientBeamAngle(float tickDelta) {
            float p = this.working ? (this.clientPhase + tickDelta * 0.15f) : this.clientPhase;
            return (float) Math.sin(p) * 22.0f;
        }

        public static void clientTick(World world, BlockPos pos, BlockState state, PumpjackBlockEntity pj) {
            if (pj.working) {
                pj.clientPhase = (pj.clientPhase + 0.15f) % ((float) Math.PI * 2.0f);
            }
        }

        public static void tick(World world, BlockPos pos, BlockState state, PumpjackBlockEntity pj) {
            if (world == null || world.isClient) return;

            boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
            boolean canPump = hasPower && pj.crudeOilMb + OIL_PER_CYCLE_MB <= CAPACITY_MB
                    && PumpjackBlock.hasOilInFootprint(world, pos);
            boolean dirty = false;

            if (canPump) {
                pj.progress++;
                if (pj.progress >= 20) {
                    pj.progress = 0;
                    pj.crudeOilMb = Math.min(CAPACITY_MB, pj.crudeOilMb + OIL_PER_CYCLE_MB);
                    dirty = true;
                }
            } else {
                pj.progress = 0;
            }

            if (pj.crudeOilMb > 0) {
                Direction facing = state.get(PumpjackBlock.FACING);
                Direction right = facing.rotateYClockwise();
                BlockPos portOutPos = pos.offset(right, 1).offset(facing, 2);
                int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                        world, portOutPos, facing.getOpposite(), FluidKind.CRUDE_OIL, Math.min(pj.crudeOilMb, 120), 25);
                if (pushed > 0) {
                    pj.crudeOilMb -= pushed;
                    dirty = true;
                }
            }

            if (pj.powered != hasPower || pj.working != canPump) {
                pj.powered = hasPower;
                pj.working = canPump;
                pj.sync();
            } else if (dirty && (world.getTime() & 3L) == 0L) {
                pj.sync();
            } else if (dirty) {
                pj.markDirty();
            }
        }

        public void sync() {
            markDirty();
            if (this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }

        @Override public FluidKind getFluidKind() { return FluidKind.CRUDE_OIL; }
        @Override public int getFluidAmount() { return this.crudeOilMb; }
        @Override public int getFluidCapacity() { return CAPACITY_MB; }
        @Override public int getTemperatureC() { return 25; }
        @Override public boolean canAcceptFluid(FluidKind kind, Direction fromSide) { return false; }
        @Override public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) { return 0; }

        @Override
        public Text getDisplayName() {
            return Text.translatable("block.factoriocore.pumpjack");
        }

        @Nullable
        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
            return new OilMachineScreenHandler(syncId, playerInventory, new SimpleInventory(3), this.propertyDelegate);
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            nbt.putInt("CrudeOilMb", this.crudeOilMb);
            nbt.putInt("Progress", this.progress);
            nbt.putBoolean("Powered", this.powered);
            nbt.putBoolean("Working", this.working);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            this.crudeOilMb = nbt.getInt("CrudeOilMb");
            this.progress = nbt.getInt("Progress");
            this.powered = nbt.getBoolean("Powered");
            this.working = nbt.getBoolean("Working");
        }

        @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
        @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    }

    // =================================================================================
    // 3. НЕФТЕПЕРЕРАБАТЫВАЮЩИЙ ЗАВОД (OIL REFINERY — 5x5x3.4, Полная переработка нефти!)
    // =================================================================================
    public static class OilRefineryBlock extends BlockWithEntity {
        public static final MapCodec<OilRefineryBlock> CODEC = createCodec(OilRefineryBlock::new);
        public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
        public static final IntProperty PART = IntProperty.of("part", 0, 24);
        public static final BooleanProperty UPPER = BooleanProperty.of("upper");

        private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 24.0, 16.0);

        public OilRefineryBlock(Settings settings) {
            super(settings);
            this.setDefaultState(this.stateManager.getDefaultState()
                    .with(FACING, Direction.NORTH)
                    .with(PART, 12)
                    .with(UPPER, false));
        }

        @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART, UPPER);
        }

        @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
        }

        @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
        }

        @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
        @Override protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) { return 1.0f; }
        @Override protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return true; }
        @Override protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) { return 0.10f; }

        public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
            int wOff = (part % 5) - 2;
            int dOff = (part / 5) - 2;
            Direction right = facing.rotateYClockwise();
            BlockPos base = masterPos.offset(right, wOff).offset(facing, dOff);
            return upper ? base.up() : base;
        }

        public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
            if (!(state.getBlock() instanceof OilRefineryBlock)) return currentPos;
            Direction facing = state.get(FACING);
            int part = state.get(PART);
            boolean upper = state.get(UPPER);
            int wOff = (part % 5) - 2;
            int dOff = (part / 5) - 2;
            Direction right = facing.rotateYClockwise();
            BlockPos base = upper ? currentPos.down() : currentPos;
            return base.offset(right, -wOff).offset(facing, -dOff);
        }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
            if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
                facing = facing.getOpposite();
            }
            BlockPos masterPos = ctx.getBlockPos();
            World world = ctx.getWorld();

            for (int part = 0; part < 25; part++) {
                for (int yOff = 0; yOff <= 2; yOff++) {
                    BlockPos checkPos = getPartPos(masterPos, facing, part, false).up(yOff);
                    if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                        return null;
                    }
                }
            }
            return this.getDefaultState().with(FACING, facing).with(PART, 12).with(UPPER, false);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
            super.onPlaced(world, pos, state, placer, itemStack);
            if (!world.isClient && state.get(PART) == 12 && !state.get(UPPER)) {
                Direction facing = state.get(FACING);
                for (int part = 0; part < 25; part++) {
                    for (int u = 0; u <= 1; u++) {
                        if (part == 12 && u == 0) continue;
                        BlockPos p = getPartPos(pos, facing, part, u == 1);
                        world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                    }
                }
            }
        }

        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                Direction facing = state.get(FACING);
                if (world.getBlockEntity(masterPos) instanceof OilRefineryBlockEntity refBe) {
                    ItemScatterer.spawn(world, masterPos, refBe);
                }
                for (int part = 0; part < 25; part++) {
                    for (int u = 0; u <= 1; u++) {
                        BlockPos p = getPartPos(masterPos, facing, part, u == 1);
                        if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                        }
                    }
                }
            }
            return super.onBreak(world, pos, state, player);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                if (world.getBlockEntity(masterPos) instanceof OilRefineryBlockEntity ref) {
                    player.openHandledScreen(ref);
                }
            }
            return ActionResult.SUCCESS;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return (state.get(PART) == 12 && !state.get(UPPER)) ? new OilRefineryBlockEntity(pos, state) : null;
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            if (state.get(PART) != 12 || state.get(UPPER)) return null;
            return world.isClient ? null : validateTicker(type, ModBlocks.OIL_REFINERY_BLOCK_ENTITY, OilRefineryBlockEntity::tick);
        }
    }

    public static class OilRefineryBlockEntity extends BlockEntity implements FactorioFluidContainer, SidedInventory, NamedScreenHandlerFactory {
        public static final long POWER_CONSUMPTION_WATTS = 420_000L;
        public static final int CAPACITY_MB = 4000;
        private static final int[] SLOTS = new int[]{0, 1, 2};

        public java.util.List<FactorioFluidContainer.HudFluidLine> getHudFluidLines() {
            java.util.List<FactorioFluidContainer.HudFluidLine> lines = new java.util.ArrayList<>(5);

            if (this.recipeMode == 2) {
                lines.add(new FactorioFluidContainer.HudFluidLine("Мазут (Вход)", this.crudeOilMb, CAPACITY_MB, FluidKind.HEAVY_OIL.color));
                lines.add(new FactorioFluidContainer.HudFluidLine("Пар / Вода (Вход)", this.waterMb, CAPACITY_MB, FluidKind.STEAM.color));
            } else {
                lines.add(new FactorioFluidContainer.HudFluidLine("Сырая нефть (Вход)", this.crudeOilMb, CAPACITY_MB, FluidKind.CRUDE_OIL.color));
                if (this.recipeMode == 1) {
                    lines.add(new FactorioFluidContainer.HudFluidLine("Вода (Вход)", this.waterMb, CAPACITY_MB, FluidKind.WATER.color));
                }
            }

            if (this.recipeMode == 0) {
                lines.add(new FactorioFluidContainer.HudFluidLine("Попутный газ (Выход)", this.petroleumGasMb, CAPACITY_MB, FluidKind.PETROLEUM_GAS.color));
            } else {
                lines.add(new FactorioFluidContainer.HudFluidLine("Мазут (Выход)", this.heavyOilMb, CAPACITY_MB, FluidKind.HEAVY_OIL.color));
                lines.add(new FactorioFluidContainer.HudFluidLine("Дизель (Выход)", this.lightOilMb, CAPACITY_MB, FluidKind.LIGHT_OIL.color));
                lines.add(new FactorioFluidContainer.HudFluidLine("Попутный газ (Выход)", this.petroleumGasMb, CAPACITY_MB, FluidKind.PETROLEUM_GAS.color));
            }

            return lines;
        }

        private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);
        // 0 = Базовая переработка нефти, 1 = Продвинутая переработка нефти, 2 = Сжижение угля
        private int recipeMode = 0;
        private int progress = 0;

        // Входные буферы (Сырая нефть / Мазут для сжижения + Вода/Пар)
        private int crudeOilMb = 0;
        private int waterMb = 0;

        // 3 выходных буфера (Мазут -> левый порт part 20, Дизель -> центральный порт part 22, Попутный газ -> правый порт part 24)
        private int heavyOilMb = 0;
        private int lightOilMb = 0;
        private int petroleumGasMb = 0;

        private boolean powered = false;
        private boolean working = false;

        protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> OilMachineScreenHandler.TYPE_REFINERY;
                    case 1 -> recipeMode;
                    case 2 -> progress;
                    case 3 -> 100;
                    case 4 -> powered ? 1 : 0;
                    case 5 -> working ? 1 : 0;
                    case 6 -> crudeOilMb;
                    case 7 -> waterMb;
                    case 8 -> heavyOilMb;
                    case 9 -> lightOilMb;
                    case 10 -> petroleumGasMb;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    recipeMode = Math.max(0, Math.min(2, value));
                    progress = 0;
                    sync();
                }
            }

            @Override
            public int size() {
                return 11;
            }
        };

        public OilRefineryBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.OIL_REFINERY_BLOCK_ENTITY, pos, state);
        }

        public int getRecipeMode() { return this.recipeMode; }
        public boolean isPowered() { return this.powered; }
        public boolean isWorking() { return this.working; }
        public int getCrudeOilMb() { return this.crudeOilMb; }
        public int getWaterMb() { return this.waterMb; }
        public int getHeavyOilMb() { return this.heavyOilMb; }
        public int getLightOilMb() { return this.lightOilMb; }
        public int getPetroleumGasMb() { return this.petroleumGasMb; }

        public static void tick(World world, BlockPos pos, BlockState state, OilRefineryBlockEntity ref) {
            if (world == null || world.isClient) return;

            boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
            boolean canRefine = hasPower && ref.canRunRefineryCycle();
            boolean dirty = false;

            if (canRefine) {
                ref.progress += 2;
                if (ref.progress >= 100) {
                    ref.progress = 0;
                    ref.completeRefineryCycle();
                    dirty = true;
                }
                if ((world.getTime() & 7L) == 0L && world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.FLAME,
                            pos.getX() - 1.2, pos.getY() + 3.45, pos.getZ() - 1.2,
                            3, 0.12, 0.10, 0.12, 0.01);
                }
            } else {
                ref.progress = 0;
            }

            Direction facing = state.get(OilRefineryBlock.FACING);
            Direction right = facing.rotateYClockwise();

            if (ref.recipeMode == 0) {
                // В базовой переработке попутный газ выходит из правого порта (+2), а также центрального и левого для удобства
                int[] outOffsets = { 2, 0, -2 };
                for (int wOff : outOffsets) {
                    if (ref.petroleumGasMb <= 0) break;
                    BlockPos outPos = pos.offset(right, wOff).offset(facing, 3);
                    int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                            world, outPos, facing.getOpposite(), FluidKind.PETROLEUM_GAS, Math.min(ref.petroleumGasMb, 120), 25);
                    if (pushed > 0) {
                        ref.petroleumGasMb -= pushed;
                        dirty = true;
                    }
                }
            } else {
                // В продвинутой переработке и сжижении угля:
                // Левый передний порт (wOff = -2, part = 20) -> МАЗУТ (HEAVY_OIL)
                if (ref.heavyOilMb > 0) {
                    BlockPos leftPort = pos.offset(right, -2).offset(facing, 3);
                    int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                            world, leftPort, facing.getOpposite(), FluidKind.HEAVY_OIL, Math.min(ref.heavyOilMb, 120), 25);
                    if (pushed > 0) { ref.heavyOilMb -= pushed; dirty = true; }
                }
                // Центральный передний порт (wOff = 0, part = 22) -> ДИЗЕЛЬНОЕ ТОПЛИВО (LIGHT_OIL)
                if (ref.lightOilMb > 0) {
                    BlockPos midPort = pos.offset(facing, 3);
                    int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                            world, midPort, facing.getOpposite(), FluidKind.LIGHT_OIL, Math.min(ref.lightOilMb, 120), 25);
                    if (pushed > 0) { ref.lightOilMb -= pushed; dirty = true; }
                }
                // Правый передний порт (wOff = +2, part = 24) -> ПОПУТНЫЙ НЕФТЯНОЙ ГАЗ (PETROLEUM_GAS)
                if (ref.petroleumGasMb > 0) {
                    BlockPos rightPort = pos.offset(right, 2).offset(facing, 3);
                    int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                            world, rightPort, facing.getOpposite(), FluidKind.PETROLEUM_GAS, Math.min(ref.petroleumGasMb, 120), 25);
                    if (pushed > 0) { ref.petroleumGasMb -= pushed; dirty = true; }
                }
            }

            if (ref.powered != hasPower || ref.working != canRefine) {
                ref.powered = hasPower;
                ref.working = canRefine;
                ref.sync();
            } else if (dirty && (world.getTime() & 3L) == 0L) {
                ref.sync();
            } else if (dirty) {
                ref.markDirty();
            }
        }

        private boolean canRunRefineryCycle() {
            return switch (this.recipeMode) {
                case 0 -> this.crudeOilMb >= 200 && this.petroleumGasMb + 250 <= CAPACITY_MB;
                case 1 -> this.crudeOilMb >= 200 && this.waterMb >= 100
                        && this.heavyOilMb + 100 <= CAPACITY_MB
                        && this.lightOilMb + 180 <= CAPACITY_MB
                        && this.petroleumGasMb + 220 <= CAPACITY_MB;
                default -> this.inventory.get(0).isOf(Items.COAL) && this.inventory.get(0).getCount() >= 2
                        && this.waterMb >= 100
                        && this.heavyOilMb + 260 <= CAPACITY_MB
                        && this.lightOilMb + 80 <= CAPACITY_MB
                        && this.petroleumGasMb + 40 <= CAPACITY_MB;
            };
        }

        private void completeRefineryCycle() {
            switch (this.recipeMode) {
                case 0 -> {
                    this.crudeOilMb -= 200;
                    this.petroleumGasMb = Math.min(CAPACITY_MB, this.petroleumGasMb + 250);
                }
                case 1 -> {
                    this.crudeOilMb -= 200;
                    this.waterMb -= 100;
                    this.heavyOilMb = Math.min(CAPACITY_MB, this.heavyOilMb + 100);
                    this.lightOilMb = Math.min(CAPACITY_MB, this.lightOilMb + 180);
                    this.petroleumGasMb = Math.min(CAPACITY_MB, this.petroleumGasMb + 220);
                }
                default -> {
                    this.inventory.get(0).decrement(2);
                    this.waterMb -= 100;
                    this.heavyOilMb = Math.min(CAPACITY_MB, this.heavyOilMb + 260);
                    this.lightOilMb = Math.min(CAPACITY_MB, this.lightOilMb + 80);
                    this.petroleumGasMb = Math.min(CAPACITY_MB, this.petroleumGasMb + 40);
                }
            }
            sync();
        }

        public void sync() {
            markDirty();
            if (this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }

        @Override
        public FluidKind getFluidKind() {
            if (this.petroleumGasMb > 0) return FluidKind.PETROLEUM_GAS;
            if (this.lightOilMb > 0) return FluidKind.LIGHT_OIL;
            if (this.heavyOilMb > 0) return FluidKind.HEAVY_OIL;
            return this.crudeOilMb > 0 ? FluidKind.CRUDE_OIL : FluidKind.NONE;
        }

        @Override public int getFluidAmount() { return Math.max(this.petroleumGasMb, Math.max(this.lightOilMb, Math.max(this.heavyOilMb, this.crudeOilMb))); }
        @Override public int getFluidCapacity() { return CAPACITY_MB; }
        @Override public int getTemperatureC() { return 25; }

        @Override
        public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
            if (kind == FluidKind.CRUDE_OIL) return this.crudeOilMb < CAPACITY_MB;
            if (kind == FluidKind.WATER || kind == FluidKind.STEAM) return this.waterMb < CAPACITY_MB;
            if (kind == FluidKind.HEAVY_OIL && this.recipeMode == 2) return this.crudeOilMb < CAPACITY_MB;
            return false;
        }

        @Override
        public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
            if (maxAmount <= 0) return 0;
            if (kind == FluidKind.CRUDE_OIL || (kind == FluidKind.HEAVY_OIL && this.recipeMode == 2)) {
                int add = Math.min(CAPACITY_MB - this.crudeOilMb, maxAmount);
                if (add > 0) {
                    boolean wasEmpty = (this.crudeOilMb == 0);
                    this.crudeOilMb += add;
                    if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) sync();
                    else markDirty();
                }
                return add;
            }
            if (kind == FluidKind.WATER || kind == FluidKind.STEAM) {
                int add = Math.min(CAPACITY_MB - this.waterMb, maxAmount);
                if (add > 0) {
                    boolean wasEmpty = (this.waterMb == 0);
                    this.waterMb += add;
                    if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) sync();
                    else markDirty();
                }
                return add;
            }
            return 0;
        }

        @Override public int[] getAvailableSlots(Direction side) { return SLOTS; }
        @Override public boolean isValid(int slot, ItemStack stack) { return slot == 0 && stack.isOf(Items.COAL); }
        @Override public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return isValid(slot, stack); }
        @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }
        @Override public int size() { return 3; }
        @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getStack(int slot) { return (slot >= 0 && slot < 3) ? inventory.get(slot) : ItemStack.EMPTY; }
        @Override public ItemStack removeStack(int slot, int amount) { ItemStack r = Inventories.splitStack(inventory, slot, amount); if (!r.isEmpty()) sync(); return r; }
        @Override public ItemStack removeStack(int slot) { ItemStack r = Inventories.removeStack(inventory, slot); if (!r.isEmpty()) sync(); return r; }
        @Override public void setStack(int slot, ItemStack stack) { if (slot >= 0 && slot < 3) { inventory.set(slot, stack); sync(); } }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear() { inventory.clear(); sync(); }

        @Override
        public Text getDisplayName() {
            return Text.translatable("block.factoriocore.oil_refinery");
        }

        @Nullable
        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
            return new OilMachineScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            Inventories.writeNbt(nbt, this.inventory, registries);
            nbt.putInt("RecipeMode", this.recipeMode);
            nbt.putInt("Progress", this.progress);
            nbt.putInt("CrudeOilMb", this.crudeOilMb);
            nbt.putInt("WaterMb", this.waterMb);
            nbt.putInt("HeavyOilMb", this.heavyOilMb);
            nbt.putInt("LightOilMb", this.lightOilMb);
            nbt.putInt("PetroleumGasMb", this.petroleumGasMb);
            nbt.putBoolean("Powered", this.powered);
            nbt.putBoolean("Working", this.working);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            this.inventory.clear();
            Inventories.readNbt(nbt, this.inventory, registries);
            this.recipeMode = nbt.getInt("RecipeMode");
            this.progress = nbt.getInt("Progress");
            this.crudeOilMb = nbt.getInt("CrudeOilMb");
            this.waterMb = nbt.getInt("WaterMb");
            this.heavyOilMb = nbt.getInt("HeavyOilMb");
            this.lightOilMb = nbt.getInt("LightOilMb");
            this.petroleumGasMb = nbt.getInt("PetroleumGasMb");
            this.powered = nbt.getBoolean("Powered");
            this.working = nbt.getBoolean("Working");
        }

        @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
        @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    }

    // =================================================================================
    // 4. ХИМИЧЕСКИЙ ЗАВОД (CHEMICAL PLANT — 3x3x2.0: все 11 рецептов со скрина 183934.png!)
    // =================================================================================
    public static class ChemicalPlantBlock extends Base3x3MachineBlock {
        public static final MapCodec<ChemicalPlantBlock> CODEC = createCodec(ChemicalPlantBlock::new);

        public ChemicalPlantBlock(Settings settings) {
            super(settings);
        }

        @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
            if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
                facing = facing.getOpposite();
            }
            BlockPos masterPos = ctx.getBlockPos();
            World world = ctx.getWorld();

            for (int part = 0; part < 9; part++) {
                for (int yOff = 0; yOff <= 1; yOff++) {
                    BlockPos checkPos = getPartPos(masterPos, facing, part, yOff == 1);
                    if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                        return null;
                    }
                }
            }
            return this.getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                if (world.getBlockEntity(masterPos) instanceof ChemicalPlantBlockEntity chem) {
                    player.openHandledScreen(chem);
                }
            }
            return ActionResult.SUCCESS;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return (state.get(PART) == 4 && !state.get(UPPER)) ? new ChemicalPlantBlockEntity(pos, state) : null;
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            if (state.get(PART) != 4 || state.get(UPPER)) return null;
            return world.isClient ? null : validateTicker(type, ModBlocks.CHEMICAL_PLANT_BLOCK_ENTITY, ChemicalPlantBlockEntity::tick);
        }
    }

    public static class ChemicalPlantBlockEntity extends BlockEntity implements FactorioFluidContainer, SidedInventory, NamedScreenHandlerFactory {
        public static final long POWER_CONSUMPTION_WATTS = 210_000L;
        public static final int CAPACITY_MB = 4000;
        private static final int[] SLOTS = new int[]{0, 1, 2};

        public java.util.List<FactorioFluidContainer.HudFluidLine> getHudFluidLines() {
            java.util.List<FactorioFluidContainer.HudFluidLine> lines = new java.util.ArrayList<>(3);

            FluidKind reqMain = getRequiredMainFluid();
            if (reqMain != FluidKind.NONE) {
                lines.add(new FactorioFluidContainer.HudFluidLine(
                        reqMain.getLocalizedName() + " (Вход)", this.mainFluidMb, CAPACITY_MB, reqMain.color));
            }

            if (requiresWater()) {
                lines.add(new FactorioFluidContainer.HudFluidLine(
                        FluidKind.WATER.getLocalizedName() + " (Вход)", this.waterMb, CAPACITY_MB, FluidKind.WATER.color));
            }

            FluidKind prodFluid = getProducedFluidKind();
            if (prodFluid != FluidKind.NONE) {
                lines.add(new FactorioFluidContainer.HudFluidLine(
                        prodFluid.getLocalizedName() + " (Выход)", this.outFluidMb, CAPACITY_MB, prodFluid.color));
            }

            return lines;
        }

        public int getPetroleumGasMb() {
            return this.mainFluidMb;
        }


        // Слот 0 = Первое твёрдое сырьё, Слот 1 = Второе твёрдое сырьё, Слот 2 = Готовая продукция
        private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

        /*
         * Все 11 рецептов Химического завода (по скриншоту 183934.png):
         * 0: Крекинг мазута в дизельное топливо (Heavy Oil + Water -> Light Oil)
         * 1: Крекинг дизельного топлива в попутный газ (Light Oil + Water -> Petroleum Gas)
         * 2: Твёрдое топливо из попутного газа (Petroleum Gas -> 1x Solid Fuel)
         * 3: Твёрдое топливо из дизельного топлива (Light Oil -> 1x Solid Fuel)
         * 4: Твёрдое топливо из мазута (Heavy Oil -> 1x Solid Fuel)
         * 5: Смазочная жидкость (Heavy Oil -> Lubricant)
         * 6: Серная кислота (5x Sulfur + 1x Iron Ingot + Water -> Sulfuric Acid)
         * 7: Пластиковый брусок (Petroleum Gas + 1x Coal -> 2x Plastic Bar)
         * 8: Сера (Petroleum Gas + Water -> 2x Sulfur)
         * 9: Аккумулятор (Sulfuric Acid + 1x Iron Ingot + 1x Copper Ingot -> 1x Battery)
         * 10: Взрывчатка (1x Sulfur + 1x Coal + Water -> 2x Explosives)
         */
        private int recipeMode = 7; // По умолчанию Пластиковый брусок
        private int progress = 0;

        private int mainFluidMb = 0; // Попутный газ / Мазут / Дизель / Серная кислота (по рецепту)
        private int waterMb = 0;     // Вода (для крекинга, серы, кислоты и взрывчатки)
        private int outFluidMb = 0;  // Выходная жидкость (Дизель, Газ, Смазка, Серная кислота)

        private boolean powered = false;
        private boolean working = false;

        protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> OilMachineScreenHandler.TYPE_CHEM_PLANT;
                    case 1 -> recipeMode;
                    case 2 -> progress;
                    case 3 -> 40;
                    case 4 -> powered ? 1 : 0;
                    case 5 -> working ? 1 : 0;
                    case 6 -> mainFluidMb;
                    case 7 -> waterMb;
                    case 8 -> outFluidMb;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    int clamped = Math.max(0, Math.min(10, value));
                    if (recipeMode != clamped) {
                        recipeMode = clamped;
                        progress = 0;
                        mainFluidMb = 0;
                        outFluidMb = 0;
                        sync();
                    }
                }
            }

            @Override
            public int size() {
                return 11;
            }
        };

        public ChemicalPlantBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CHEMICAL_PLANT_BLOCK_ENTITY, pos, state);
        }

        public int getRecipeMode() { return this.recipeMode; }
        public boolean isPowered() { return this.powered; }
        public boolean isWorking() { return this.working; }
        public int getMainFluidMb() { return this.mainFluidMb; }
        public int getWaterMb() { return this.waterMb; }
        public int getOutFluidMb() { return this.outFluidMb; }

        public FluidKind getRequiredMainFluid() {
            return switch (this.recipeMode) {
                case 0, 4, 5 -> FluidKind.HEAVY_OIL;
                case 1, 3 -> FluidKind.LIGHT_OIL;
                case 2, 7, 8 -> FluidKind.PETROLEUM_GAS;
                case 9 -> FluidKind.SULFURIC_ACID;
                default -> FluidKind.NONE;
            };
        }

        public boolean requiresWater() {
            return this.recipeMode == 0 || this.recipeMode == 1 || this.recipeMode == 6 || this.recipeMode == 8 || this.recipeMode == 10;
        }

        public FluidKind getProducedFluidKind() {
            return switch (this.recipeMode) {
                case 0 -> FluidKind.LIGHT_OIL;
                case 1 -> FluidKind.PETROLEUM_GAS;
                case 5 -> FluidKind.LUBRICANT;
                case 6 -> FluidKind.SULFURIC_ACID;
                default -> FluidKind.NONE;
            };
        }

        public ItemStack getProducedItemStack() {
            return switch (this.recipeMode) {
                case 2, 3, 4 -> new ItemStack(ModItems.SOLID_FUEL, 1);
                case 7 -> new ItemStack(ModItems.PLASTIC_BAR, 2);
                case 8 -> new ItemStack(ModItems.SULFUR, 2);
                case 9 -> new ItemStack(ModItems.BATTERY, 1);
                case 10 -> new ItemStack(ModItems.EXPLOSIVES, 2);
                default -> ItemStack.EMPTY;
            };
        }

        public ItemStack getRecipeIcon() {
            ItemStack itemOut = getProducedItemStack();
            if (!itemOut.isEmpty()) return itemOut;
            return new ItemStack(ModBlocks.PIPE);
        }

        public static boolean isSlotValidForMode(int mode, int slot, ItemStack stack) {
            if (slot == 0) {
                return switch (mode) {
                    case 6, 10 -> stack.isOf(ModItems.SULFUR);
                    case 7 -> stack.isOf(Items.COAL);
                    case 9 -> stack.isOf(Items.IRON_INGOT);
                    default -> false;
                };
            }
            if (slot == 1) {
                return switch (mode) {
                    case 6 -> stack.isOf(Items.IRON_INGOT);
                    case 9 -> stack.isOf(Items.COPPER_INGOT);
                    case 10 -> stack.isOf(Items.COAL);
                    default -> false;
                };
            }
            return false;
        }

        public static void tick(World world, BlockPos pos, BlockState state, ChemicalPlantBlockEntity chem) {
            if (world == null || world.isClient) return;



            boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
            boolean canCraft = hasPower && chem.canRunCycle();
            boolean dirty = false;

            if (canCraft) {
                chem.progress++;
                if (chem.progress >= 40) {
                    chem.progress = 0;
                    chem.completeCycle(world, pos, state);
                    dirty = true;
                }
            } else {
                chem.progress = 0;
            }

            // Выгружаем произведённую жидкость (Дизель / Газ / Смазка / Серная кислота) через 2 передних порта (part 6 и 8)
            FluidKind outKind = chem.getProducedFluidKind();
            if (outKind != FluidKind.NONE && chem.outFluidMb > 0) {
                Direction facing = state.get(Base3x3MachineBlock.FACING);
                Direction right = facing.rotateYClockwise();
                int[] portOffsets = { -1, 1 };
                for (int wOff : portOffsets) {
                    if (chem.outFluidMb <= 0) break;
                    BlockPos outPos = pos.offset(right, wOff).offset(facing, 2);
                    int pushed = FactorioFluidContainer.pushFluidToNeighbor(
                            world, outPos, facing.getOpposite(), outKind, Math.min(chem.outFluidMb, 120), 25);
                    if (pushed > 0) {
                        chem.outFluidMb -= pushed;
                        dirty = true;
                    }
                }
            }

            if (chem.powered != hasPower || chem.working != canCraft) {
                chem.powered = hasPower;
                chem.working = canCraft;
                chem.sync();
            } else if (dirty && (world.getTime() & 3L) == 0L) {
                chem.sync();
            } else if (dirty) {
                chem.markDirty();
            }
        }

        private boolean canRunCycle() {
            ItemStack prodItem = getProducedItemStack();
            if (!prodItem.isEmpty()) {
                ItemStack curOut = this.inventory.get(2);
                if (!curOut.isEmpty() && (!ItemStack.areItemsAndComponentsEqual(curOut, prodItem)
                        || curOut.getCount() + prodItem.getCount() > curOut.getMaxCount())) {
                    return false;
                }
            } else if (this.outFluidMb + 250 > CAPACITY_MB) {
                return false;
            }

            ItemStack s0 = this.inventory.get(0);
            ItemStack s1 = this.inventory.get(1);

            return switch (this.recipeMode) {
                case 0 -> this.mainFluidMb >= 200 && this.waterMb >= 150;
                case 1 -> this.mainFluidMb >= 150 && this.waterMb >= 150;
                case 2, 4 -> this.mainFluidMb >= 200;
                case 3, 5 -> this.mainFluidMb >= 100;
                case 6 -> this.waterMb >= 200 && s0.isOf(ModItems.SULFUR) && s0.getCount() >= 5 && s1.isOf(Items.IRON_INGOT) && s1.getCount() >= 1;
                case 7 -> this.mainFluidMb >= 200 && s0.isOf(Items.COAL) && s0.getCount() >= 1;
                case 8 -> this.mainFluidMb >= 150 && this.waterMb >= 150;
                case 9 -> this.mainFluidMb >= 100 && s0.isOf(Items.IRON_INGOT) && s0.getCount() >= 1 && s1.isOf(Items.COPPER_INGOT) && s1.getCount() >= 1;
                case 10 -> this.waterMb >= 100 && s0.isOf(ModItems.SULFUR) && s0.getCount() >= 1 && s1.isOf(Items.COAL) && s1.getCount() >= 1;
                default -> false;
            };
        }

        private void completeCycle(World world, BlockPos masterPos, BlockState state) {
            switch (this.recipeMode) {
                case 0 -> { this.mainFluidMb -= 200; this.waterMb -= 150; this.outFluidMb = Math.min(CAPACITY_MB, this.outFluidMb + 150); }
                case 1 -> { this.mainFluidMb -= 150; this.waterMb -= 150; this.outFluidMb = Math.min(CAPACITY_MB, this.outFluidMb + 100); }
                case 2, 4 -> this.mainFluidMb -= 200;
                case 3 -> this.mainFluidMb -= 100;
                case 5 -> { this.mainFluidMb -= 100; this.outFluidMb = Math.min(CAPACITY_MB, this.outFluidMb + 100); }
                case 6 -> { this.waterMb -= 200; this.inventory.get(0).decrement(5); this.inventory.get(1).decrement(1); this.outFluidMb = Math.min(CAPACITY_MB, this.outFluidMb + 250); }
                case 7 -> { this.mainFluidMb -= 200; this.inventory.get(0).decrement(1); }
                case 8 -> { this.mainFluidMb -= 150; this.waterMb -= 150; }
                case 9 -> { this.mainFluidMb -= 100; this.inventory.get(0).decrement(1); this.inventory.get(1).decrement(1); }
                case 10 -> { this.waterMb -= 100; this.inventory.get(0).decrement(1); this.inventory.get(1).decrement(1); }
            }

            ItemStack prodItem = getProducedItemStack();
            if (!prodItem.isEmpty()) {
                ItemStack out = this.inventory.get(2);
                if (out.isEmpty()) {
                    this.inventory.set(2, prodItem.copy());
                } else {
                    out.increment(prodItem.getCount());
                }
                wakeSurroundingInserters(world, masterPos, state);
            }
            sync();
        }

        private static void wakeSurroundingInserters(World world, BlockPos masterPos, BlockState state) {
            if (world == null || world.isClient) return;
            Direction facing = state.contains(Base3x3MachineBlock.FACING) ? state.get(Base3x3MachineBlock.FACING) : Direction.NORTH;
            for (int part = 0; part < 9; part++) {
                InserterBlockEntity.wakeUpAdjacentInserters(world, Base3x3MachineBlock.getPartPos(masterPos, facing, part, false));
            }
        }

        public void sync() {
            markDirty();
            if (this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }

        @Override
        public FluidKind getFluidKind() {
            if (this.outFluidMb > 0) return getProducedFluidKind();
            if (this.mainFluidMb > 0) return getRequiredMainFluid();
            return this.waterMb > 0 ? FluidKind.WATER : FluidKind.NONE;
        }

        @Override public int getFluidAmount() { return Math.max(this.outFluidMb, Math.max(this.mainFluidMb, this.waterMb)); }
        @Override public int getFluidCapacity() { return CAPACITY_MB; }
        @Override public int getTemperatureC() { return 25; }

        @Override
        public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
            FluidKind req = getRequiredMainFluid();
            if (req != FluidKind.NONE && kind == req) return this.mainFluidMb < CAPACITY_MB;
            if (requiresWater() && kind == FluidKind.WATER) return this.waterMb < CAPACITY_MB;
            return false;
        }

        @Override
        public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
            if (maxAmount <= 0) return 0;
            FluidKind req = getRequiredMainFluid();
            if (req != FluidKind.NONE && kind == req) {
                int add = Math.min(CAPACITY_MB - this.mainFluidMb, maxAmount);
                if (add > 0) {
                    boolean wasEmpty = (this.mainFluidMb == 0);
                    this.mainFluidMb += add;
                    if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) sync();
                    else markDirty();
                }
                return add;
            }
            if (requiresWater() && kind == FluidKind.WATER) {
                int add = Math.min(CAPACITY_MB - this.waterMb, maxAmount);
                if (add > 0) {
                    boolean wasEmpty = (this.waterMb == 0);
                    this.waterMb += add;
                    if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) sync();
                    else markDirty();
                }
                return add;
            }
            return 0;
        }

        @Override public int[] getAvailableSlots(Direction side) { return SLOTS; }
        @Override public boolean isValid(int slot, ItemStack stack) { return isSlotValidForMode(this.recipeMode, slot, stack); }
        @Override public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return isValid(slot, stack); }
        @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return slot == 2; }
        @Override public int size() { return 3; }
        @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getStack(int slot) { return (slot >= 0 && slot < 3) ? inventory.get(slot) : ItemStack.EMPTY; }
        @Override public ItemStack removeStack(int slot, int amount) { ItemStack r = Inventories.splitStack(inventory, slot, amount); if (!r.isEmpty()) sync(); return r; }
        @Override public ItemStack removeStack(int slot) { ItemStack r = Inventories.removeStack(inventory, slot); if (!r.isEmpty()) sync(); return r; }
        @Override public void setStack(int slot, ItemStack stack) { if (slot >= 0 && slot < 3) { inventory.set(slot, stack); sync(); } }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear() { inventory.clear(); sync(); }

        @Override
        public Text getDisplayName() {
            return Text.translatable("block.factoriocore.chemical_plant");
        }

        @Nullable
        @Override
        public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
            return new OilMachineScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            Inventories.writeNbt(nbt, this.inventory, registries);
            nbt.putInt("RecipeMode", this.recipeMode);
            nbt.putInt("Progress", this.progress);
            nbt.putInt("MainFluidMb", this.mainFluidMb);
            nbt.putInt("WaterMb", this.waterMb);
            nbt.putInt("OutFluidMb", this.outFluidMb);
            nbt.putBoolean("Powered", this.powered);
            nbt.putBoolean("Working", this.working);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            this.inventory.clear();
            Inventories.readNbt(nbt, this.inventory, registries);
            this.recipeMode = nbt.getInt("RecipeMode");
            this.progress = nbt.getInt("Progress");
            this.mainFluidMb = nbt.contains("MainFluidMb") ? nbt.getInt("MainFluidMb") : nbt.getInt("PetroleumGasMb");
            this.waterMb = nbt.getInt("WaterMb");
            this.outFluidMb = nbt.getInt("OutFluidMb");
            this.powered = nbt.getBoolean("Powered");
            this.working = nbt.getBoolean("Working");
        }

        @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
        @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    }

    // =================================================================================
    // 5. УНИВЕРСАЛЬНЫЙ SCREEN HANDLER ДЛЯ НПЗ (5x5), ХИМЗАВОДА (3x3) И ВЫШКИ (3x3)
    // =================================================================================
    public static class OilMachineScreenHandler extends ScreenHandler {
        public static final int TYPE_PUMPJACK = 0;
        public static final int TYPE_REFINERY = 1;
        public static final int TYPE_CHEM_PLANT = 2;

        private final Inventory inventory;
        private final PropertyDelegate propertyDelegate;
        private boolean slotsVisible = true;

        public OilMachineScreenHandler(int syncId, PlayerInventory playerInventory) {
            this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(11));
        }

        public OilMachineScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate delegate) {
            super(ModScreenHandlers.OIL_MACHINE_SCREEN_HANDLER, syncId);
            this.inventory = inventory;
            this.propertyDelegate = delegate;
            this.addProperties(delegate);

            // Слот 0: Первое твёрдое сырьё
            this.addSlot(new Slot(inventory, 0, 217, 144) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    int type = getMachineType();
                    int mode = getRecipeMode();
                    if (type == TYPE_REFINERY && mode == 2) return stack.isOf(Items.COAL);
                    if (type == TYPE_CHEM_PLANT) return ChemicalPlantBlockEntity.isSlotValidForMode(mode, 0, stack);
                    return false;
                }

                @Override
                public boolean isEnabled() {
                    if (!slotsVisible) return false;
                    int type = getMachineType();
                    int mode = getRecipeMode();
                    if (type == TYPE_REFINERY) return mode == 2;
                    if (type == TYPE_CHEM_PLANT) return mode == 6 || mode == 7 || mode == 9 || mode == 10;
                    return false;
                }
            });

            // Слот 1: Второе твёрдое сырьё (для Серной кислоты, Аккумулятора, Взрывчатки)
            this.addSlot(new Slot(inventory, 1, 236, 144) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    if (getMachineType() == TYPE_CHEM_PLANT) {
                        return ChemicalPlantBlockEntity.isSlotValidForMode(getRecipeMode(), 1, stack);
                    }
                    return false;
                }

                @Override
                public boolean isEnabled() {
                    if (!slotsVisible || getMachineType() != TYPE_CHEM_PLANT) return false;
                    int mode = getRecipeMode();
                    return mode == 6 || mode == 9 || mode == 10;
                }
            });

            // Слот 2: Выход готового предмета
            this.addSlot(new Slot(inventory, 2, 398, 144) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isEnabled() {
                    if (!slotsVisible || getMachineType() != TYPE_CHEM_PLANT) return false;
                    int mode = getRecipeMode();
                    return mode == 2 || mode == 3 || mode == 4 || mode == 7 || mode == 8 || mode == 9 || mode == 10;
                }
            });

            // Левая панель "Персонаж": Основной инвентарь (3 ряда по 9)
            for (int r = 0; r < 3; ++r) {
                for (int c = 0; c < 9; ++c) {
                    this.addSlot(new Slot(playerInventory, c + r * 9 + 9, 12 + c * 18, 36 + r * 18) {
                        @Override public boolean isEnabled() { return slotsVisible; }
                    });
                }
            }
            // Хотбар игрока (1 ряд по 9)
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(playerInventory, c, 12 + c * 18, 96) {
                    @Override public boolean isEnabled() { return slotsVisible; }
                });
            }
        }

        public void setSlotsVisible(boolean visible) {
            this.slotsVisible = visible;
        }

        public int getMachineType() { return this.propertyDelegate.get(0); }
        public int getRecipeMode() { return this.propertyDelegate.get(1); }
        public int getProgress() { return this.propertyDelegate.get(2); }
        public int getMaxProgress() { return Math.max(1, this.propertyDelegate.get(3)); }
        public boolean isPowered() { return this.propertyDelegate.get(4) != 0; }
        public boolean isWorking() { return this.propertyDelegate.get(5) != 0; }
        public int getFluid1Mb() { return this.propertyDelegate.get(6); }
        public int getFluid2Mb() { return this.propertyDelegate.get(7); }
        public int getFluid3Mb() { return this.propertyDelegate.get(8); }
        public int getFluid4Mb() { return this.propertyDelegate.get(9); }
        public int getFluid5Mb() { return this.propertyDelegate.get(10); }

        @Override
        public boolean onButtonClick(PlayerEntity player, int id) {
            if (id >= 0 && id <= 10) {
                if (!player.isCreative()) {
                    String reqTech = getRequiredTechForOilRecipe(getMachineType(), id);
                    if (reqTech != null && !com.fixplot.factoriocore.content.research.ResearchManager.isTechResearched(reqTech)) {
                        var node = com.fixplot.factoriocore.content.research.ResearchManager.getById(reqTech);
                        String techName = (node != null) ? node.name : reqTech;
                        player.sendMessage(Text.literal("§cДля этого рецепта необходимо изучить технологию: §e" + techName), true);
                        return false;
                    }
                }
                this.propertyDelegate.set(1, id);
                return true;
            }
            return false;
        }

        public static String getRequiredTechForOilRecipe(int machineType, int mode) {
            if (machineType == TYPE_REFINERY) {
                return (mode == 1 || mode == 2) ? "advanced_oil_processing" : "oil_processing";
            }
            if (machineType == TYPE_CHEM_PLANT) {
                return switch (mode) {
                    case 0, 1 -> "advanced_oil_processing";
                    case 5 -> "lubricant";
                    case 6, 8 -> "sulfur_processing";
                    case 7 -> "plastics";
                    case 9 -> "battery";
                    case 10 -> "explosives";
                    default -> "oil_processing";
                };
            }
            return null;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            ItemStack newStack = ItemStack.EMPTY;
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasStack()) {
                ItemStack originalStack = slot.getStack();
                newStack = originalStack.copy();
                if (index < 3) {
                    if (!this.insertItem(originalStack, 3, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.insertItem(originalStack, 0, 2, false)) {
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
}