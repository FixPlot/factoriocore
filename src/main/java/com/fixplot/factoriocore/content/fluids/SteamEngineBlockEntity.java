package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SteamEngineBlockEntity extends BlockEntity implements FactorioFluidContainer {

    public static final int CAPACITY_MB = 2000;
    public static final int STEAM_CONSUMPTION_MB_PER_TICK = 30;
    public static final long MAX_OUTPUT_WATTS = 900_000L;

    private int steamMb = 0;
    private int currentOutputWatts = 0;
    private float steamRemainder = 0.0f;
    private float cachedLoadRatio = 0.0f;
    private long lastLoadCheckTick = -20L;
    private float clientFlywheelAngle = 0.0f;

    public SteamEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STEAM_ENGINE_BLOCK_ENTITY, pos, state);
    }

    /**
     * Есть ли пар в цилиндре для покрытия нагрузки электросети.
     */
    public boolean hasSteamAvailable() {
        return this.steamMb > 0;
    }

    /**
     * Вырабатывает ли двигатель электроэнергию прямо сейчас (есть и пар, и реальный спрос в сети!).
     */
    public boolean isGeneratingPower() {
        return this.currentOutputWatts > 0 && this.steamMb > 0;
    }

    public long getCurrentOutputWatts() {
        return this.steamMb > 0 ? this.currentOutputWatts : 0L;
    }

    public float getLoadRatio() {
        if (this.steamMb <= 0 || this.currentOutputWatts <= 0) return 0.0f;
        return MathHelper.clamp(this.currentOutputWatts / (float) MAX_OUTPUT_WATTS, 0.0f, 1.0f);
    }

    public float getClientFlywheelAngle(float tickDelta) {
        float ratio = getLoadRatio();
        if (ratio > 0.001f) {
            float speed = Math.max(1.5f, 18.0f * ratio);
            return (this.clientFlywheelAngle + tickDelta * speed) % 360.0f;
        }
        return this.clientFlywheelAngle;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, SteamEngineBlockEntity engine) {
        float ratio = engine.getLoadRatio();
        if (ratio > 0.001f) {
            float speed = Math.max(1.5f, 18.0f * ratio);
            engine.clientFlywheelAngle = (engine.clientFlywheelAngle + speed) % 360.0f;
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, SteamEngineBlockEntity engine) {
        if (world == null || world.isClient) return;

        int prevWatts = engine.currentOutputWatts;
        boolean dirty = false;

        // 1. Сквозная передача пара через оба торца двигателя (part 1 и part 13) для каскадов Бойлер -> Двигатель 1 -> Двигатель 2
        if (engine.steamMb > 20) {
            Direction facing = state.get(SteamEngineBlock.FACING);
            BlockPos backNeighbor = SteamEngineBlock.getPartPos(pos, facing, 13, 0).offset(facing);
            BlockPos frontNeighbor = pos.offset(facing.getOpposite());

            dirty |= equalizeSteamWithNeighbor(world, engine, backNeighbor, facing.getOpposite());
            dirty |= equalizeSteamWithNeighbor(world, engine, frontNeighbor, facing);
        }

        // 2. Определяем реальную нагрузку электросети (раз в 4 тика)
        long now = world.getTime();
        if (now - engine.lastLoadCheckTick >= 4L) {
            engine.lastLoadCheckTick = now + (pos.asLong() & 3L);
            engine.cachedLoadRatio = (engine.steamMb > 0)
                    ? PowerNetworkManager.getSteamEngineLoadRatio(world, pos)
                    : 0.0f;
        }

        // 3. Потребляем пар СТРОГО пропорционально нагрузке (при 0% нагрузки пар НЕ тратится вообще!)
        if (engine.steamMb > 0 && engine.cachedLoadRatio > 0.0005f) {
            float exactSteamNeeded = (STEAM_CONSUMPTION_MB_PER_TICK * engine.cachedLoadRatio) + engine.steamRemainder;
            int consumeMb = Math.min(engine.steamMb, (int) exactSteamNeeded);
            engine.steamRemainder = exactSteamNeeded - consumeMb;

            if (consumeMb > 0) {
                engine.steamMb -= consumeMb;
                dirty = true;
            }

            engine.currentOutputWatts = Math.round(MAX_OUTPUT_WATTS * engine.cachedLoadRatio);

            // Пар из выхлопной трубы идёт тем чаще, чем выше нагрузка двигателя
            int particleInterval = engine.cachedLoadRatio > 0.5f ? 3 : 7;
            if ((now & particleInterval) == 0L && world instanceof ServerWorld sw) {
                Direction facing = state.get(SteamEngineBlock.FACING);
                BlockPos midPos = pos.offset(facing, 2);
                sw.spawnParticles(ParticleTypes.CLOUD,
                        midPos.getX() + 0.5, midPos.getY() + 3.55, midPos.getZ() + 0.5,
                        engine.cachedLoadRatio > 0.5f ? 2 : 1, 0.22, 0.08, 0.22, 0.015);
            }
        } else {
            engine.currentOutputWatts = 0;
            engine.steamRemainder = 0.0f;
        }

        if (Math.abs(prevWatts - engine.currentOutputWatts) > 2000
                || (prevWatts == 0) != (engine.currentOutputWatts == 0)
                || (dirty && (now % 10) == 0)) {
            engine.sync();
        } else if (dirty) {
            engine.markDirty();
        }
    }

    private static boolean equalizeSteamWithNeighbor(World world, SteamEngineBlockEntity self, BlockPos neighborPos, Direction fromSide) {
        BlockState neighborState = world.getBlockState(neighborPos);
        BlockPos canonical = BlockHealthManager.getCanonicalPos(neighborPos, neighborState);
        BlockEntity be = world.getBlockEntity(canonical);

        if (be instanceof SteamEngineBlockEntity otherEngine && otherEngine != self) {
            int diff = self.steamMb - otherEngine.steamMb;
            if (diff > 10) {
                int move = Math.min(diff / 2, 120);
                otherEngine.steamMb += move;
                self.steamMb -= move;
                otherEngine.markDirty();
                return true;
            }
        } else if (be instanceof PipeBlockEntity pipe) {
            int diff = self.steamMb - pipe.getFluidAmount();
            if (diff > 40 && pipe.canAcceptFluid(FluidKind.STEAM, fromSide)) {
                int offer = Math.min(90, diff / 2);
                int accepted = pipe.insertFluid(FluidKind.STEAM, offer, 165, fromSide);
                if (accepted > 0) {
                    self.steamMb -= accepted;
                    return true;
                }
            }
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
        return this.steamMb > 0 ? FluidKind.STEAM : FluidKind.NONE;
    }

    @Override
    public int getFluidAmount() {
        return this.steamMb;
    }

    @Override
    public int getFluidCapacity() {
        return CAPACITY_MB;
    }

    @Override
    public int getTemperatureC() {
        return 165;
    }

    @Override
    public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
        return kind == FluidKind.STEAM && this.steamMb < CAPACITY_MB;
    }

    @Override
    public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
        if (kind != FluidKind.STEAM || maxAmount <= 0) return 0;
        int space = CAPACITY_MB - this.steamMb;
        int add = Math.min(space, maxAmount);
        if (add > 0) {
            boolean wasEmpty = (this.steamMb == 0);
            this.steamMb += add;
            if (wasEmpty) {
                this.lastLoadCheckTick = -20L;
                sync();
            } else {
                markDirty();
            }
        }
        return add;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("SteamMb", this.steamMb);
        nbt.putInt("CurrentOutputWatts", this.currentOutputWatts);
        nbt.putFloat("SteamRemainder", this.steamRemainder);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.steamMb = nbt.getInt("SteamMb");
        this.currentOutputWatts = nbt.getInt("CurrentOutputWatts");
        this.steamRemainder = nbt.getFloat("SteamRemainder");
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