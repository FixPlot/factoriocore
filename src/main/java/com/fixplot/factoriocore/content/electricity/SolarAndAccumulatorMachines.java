package com.fixplot.factoriocore.content.electricity;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SolarAndAccumulatorMachines {

    // =================================================================================
    // 1. СОЛНЕЧНАЯ ПАНЕЛЬ (SOLAR PANEL — 3x3x1, 60 кВт днём)
    // =================================================================================
    public static class SolarPanelBlock extends BlockWithEntity {
        public static final MapCodec<SolarPanelBlock> CODEC = createCodec(SolarPanelBlock::new);
        public static final IntProperty PART = IntProperty.of("part", 0, 8);

        private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

        public SolarPanelBlock(Settings settings) {
            super(settings);
            this.setDefaultState(this.stateManager.getDefaultState().with(PART, 4));
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return CODEC;
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(PART);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
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
            return 0.12f;
        }

        public static BlockPos getPartPos(BlockPos masterPos, int part) {
            int dx = (part % 3) - 1;
            int dz = (part / 3) - 1;
            return masterPos.add(dx, 0, dz);
        }

        public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
            if (!(state.getBlock() instanceof SolarPanelBlock)) return currentPos;
            int part = state.get(PART);
            int dx = (part % 3) - 1;
            int dz = (part / 3) - 1;
            return currentPos.add(-dx, 0, -dz);
        }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            BlockPos masterPos = ctx.getBlockPos();
            World world = ctx.getWorld();
            for (int part = 0; part < 9; part++) {
                BlockPos check = getPartPos(masterPos, part);
                if (!world.getWorldBorder().contains(check) || !world.getBlockState(check).canReplace(ctx)) {
                    return null;
                }
            }
            return this.getDefaultState().with(PART, 4);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
            super.onPlaced(world, pos, state, placer, itemStack);
            if (!world.isClient && state.get(PART) == 4) {
                for (int part = 0; part < 9; part++) {
                    if (part == 4) continue;
                    world.setBlockState(getPartPos(pos, part), state.with(PART, part), Block.NOTIFY_ALL);
                }
            }
        }

        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                for (int part = 0; part < 9; part++) {
                    BlockPos p = getPartPos(masterPos, part);
                    if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    }
                }
            }
            return super.onBreak(world, pos, state, player);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient) {
                long out = SolarPanelBlockEntity.getCurrentOutputWatts(world);
                int pct = Math.round((out * 100.0f) / SolarPanelBlockEntity.MAX_OUTPUT_WATTS);
                player.sendMessage(Text.literal(String.format(
                        "§e[Солнечная панель] Выработка: §a%s §7(%d%% от макс. 60.0 кВт)",
                        PowerNetworkManager.formatWatts(out), pct)), true);
            }
            return ActionResult.SUCCESS;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return state.get(PART) == 4 ? new SolarPanelBlockEntity(pos, state) : null;
        }
    }

    public static class SolarPanelBlockEntity extends BlockEntity {
        public static final long MAX_OUTPUT_WATTS = 60_000L; // 60 кВт по канону Factorio

        public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.SOLAR_PANEL_BLOCK_ENTITY, pos, state);
        }

        /**
         * Вычисляет текущую мощность солнечной панели по времени суток:
         * - 0..12000 (день) -> 100% (60 кВт)
         * - 12000..13500 (закат) -> плавный спад 100% -> 0%
         * - 13500..22500 (ночь) -> 0 кВт
         * - 22500..24000 (рассвет) -> плавный рост 0% -> 100%
         */
        public static float getDaylightFactor(World world) {
            if (world == null || !world.getDimension().hasSkyLight()) return 0.0f;
            long tod = world.getTimeOfDay() % 24000L;
            if (tod < 0) tod += 24000L;

            float factor;
            if (tod <= 12000L) {
                factor = 1.0f;
            } else if (tod < 13500L) {
                factor = 1.0f - ((tod - 12000L) / 1500.0f);
            } else if (tod < 22500L) {
                factor = 0.0f;
            } else {
                factor = (tod - 22500L) / 1500.0f;
            }

            if (world.isThundering()) factor *= 0.45f;
            else if (world.isRaining()) factor *= 0.70f;
            return MathHelper.clamp(factor, 0.0f, 1.0f);
        }

        public static long getCurrentOutputWatts(World world) {
            return Math.round(MAX_OUTPUT_WATTS * getDaylightFactor(world));
        }
    }

    // =================================================================================
    // 2. АККУМУЛЯТОР (ACCUMULATOR — 2x2x2, ёмкость 5 МДж, макс. мощность 300 кВт)
    // =================================================================================
    public static class AccumulatorBlock extends BlockWithEntity {
        public static final MapCodec<AccumulatorBlock> CODEC = createCodec(AccumulatorBlock::new);
        public static final IntProperty PART = IntProperty.of("part", 0, 3);
        public static final BooleanProperty UPPER = BooleanProperty.of("upper");

        private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 18.0, 16.0);

        public AccumulatorBlock(Settings settings) {
            super(settings);
            this.setDefaultState(this.stateManager.getDefaultState().with(PART, 0).with(UPPER, false));
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return CODEC;
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(PART, UPPER);
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
            return 0.15f;
        }

        public static BlockPos getPartPos(BlockPos masterPos, int part, boolean upper) {
            int dx = part % 2;
            int dz = part / 2;
            return masterPos.add(dx, upper ? 1 : 0, dz);
        }

        public static BlockPos getMasterPos(BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof AccumulatorBlock)) return pos;
            int part = state.get(PART);
            boolean upper = state.get(UPPER);
            return pos.add(-(part % 2), upper ? -1 : 0, -(part / 2));
        }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            World world = ctx.getWorld();
            BlockPos masterPos = ctx.getBlockPos();
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos check = getPartPos(masterPos, part, u == 1);
                    if (!world.getWorldBorder().contains(check) || !world.getBlockState(check).canReplace(ctx)) {
                        return null;
                    }
                }
            }
            return this.getDefaultState().with(PART, 0).with(UPPER, false);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
            super.onPlaced(world, pos, state, placer, itemStack);
            if (!world.isClient && state.get(PART) == 0 && !state.get(UPPER)) {
                for (int part = 0; part < 4; part++) {
                    for (int u = 0; u <= 1; u++) {
                        if (part == 0 && u == 0) continue;
                        BlockPos p = getPartPos(pos, part, u == 1);
                        world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                    }
                }
            }
        }

        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient) {
                BlockPos masterPos = getMasterPos(pos, state);
                for (int part = 0; part < 4; part++) {
                    for (int u = 0; u <= 1; u++) {
                        BlockPos p = getPartPos(masterPos, part, u == 1);
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
                if (world.getBlockEntity(masterPos) instanceof AccumulatorBlockEntity acc) {
                    player.sendMessage(Text.literal(String.format(
                            "§b[Аккумулятор] Заряд: §e%.2f / 5.00 МДж §7(%.0f%%) §f| Макс. мощность: §a300 кВт",
                            acc.getStoredJoules() / 1_000_000.0, acc.getChargeRatio() * 100.0f)), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return (state.get(PART) == 0 && !state.get(UPPER)) ? new AccumulatorBlockEntity(pos, state) : null;
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            if (state.get(PART) != 0 || state.get(UPPER)) return null;
            return world.isClient ? null : validateTicker(type, ModBlocks.ACCUMULATOR_BLOCK_ENTITY, AccumulatorBlockEntity::tick);
        }
    }

    public static class AccumulatorBlockEntity extends BlockEntity {
        public static final long MAX_CAPACITY_JOULES = 5_000_000L; // 5 МДж
        public static final long MAX_TRANSFER_WATTS = 300_000L;    // 300 кВт

        private long storedJoules = 0L;
        private int chargeState = 0; // 0 = покой, 1 = заряжается, -1 = разряжается в сеть

        public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.ACCUMULATOR_BLOCK_ENTITY, pos, state);
        }

        public long getStoredJoules() { return this.storedJoules; }
        public long getCapacityJoules() { return MAX_CAPACITY_JOULES; }
        public int getChargeState() { return this.chargeState; }

        public float getChargeRatio() {
            return MathHelper.clamp((float) this.storedJoules / (float) MAX_CAPACITY_JOULES, 0.0f, 1.0f);
        }

        public boolean canProvidePower() {
            return this.storedJoules >= 15_000L;
        }

        public static void tick(World world, BlockPos pos, BlockState state, AccumulatorBlockEntity acc) {
            if (world == null || world.isClient) return;
            if ((world.getTime() & 3L) != 0L) return; // Обновляем каждые 4 тика (0.2 сек)

            long rateWatts = PowerNetworkManager.getAccumulatorChargeOrDischargeWatts(world, pos, acc);
            int prevState = acc.chargeState;
            long prevBucket = acc.storedJoules / 250_000L;

            if (rateWatts > 0L && acc.storedJoules < MAX_CAPACITY_JOULES) {
                // Заряжаемся от излишков Солнечных панелей и Паровых двигателей (rateWatts * 0.2 сек = rateWatts / 5 Дж)
                long addJoules = Math.max(1L, rateWatts / 5L);
                acc.storedJoules = Math.min(MAX_CAPACITY_JOULES, acc.storedJoules + addJoules);
                acc.chargeState = 1;
            } else if (rateWatts < 0L && acc.storedJoules > 0L) {
                // Разряжаемся в сеть ровно на величину дефицита мощности
                long drainJoules = Math.max(1L, (-rateWatts) / 5L);
                acc.storedJoules = Math.max(0L, acc.storedJoules - drainJoules);
                acc.chargeState = -1;
            } else {
                acc.chargeState = 0;
            }

            long newBucket = acc.storedJoules / 250_000L;
            if (prevState != acc.chargeState || prevBucket != newBucket) {
                acc.sync();
            } else {
                acc.markDirty();
            }
        }

        public void sync() {
            markDirty();
            if (this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            nbt.putLong("StoredJoules", this.storedJoules);
            nbt.putInt("ChargeState", this.chargeState);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            this.storedJoules = nbt.getLong("StoredJoules");
            this.chargeState = nbt.getInt("ChargeState");
        }

        @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
        @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    }
}