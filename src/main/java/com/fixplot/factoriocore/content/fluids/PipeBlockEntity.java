package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PipeBlockEntity extends BlockEntity implements FactorioFluidContainer {

    public static final int CAPACITY_MB = 1000;
    public static final int MAX_UNDERGROUND_DIST = 10;

    private FluidKind fluidKind = FluidKind.NONE;
    private int amountMb = 0;
    private int temperatureC = 15;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PIPE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, PipeBlockEntity pipe) {
        if (world == null || world.isClient || pipe.amountMb <= 0 || pipe.fluidKind == FluidKind.NONE) {
            return;
        }

        boolean changed = false;

        // 1. Подземная труба перекачивает жидкость в парную подземную трубу до 10 блоков
        if (state.getBlock() instanceof PipeBlock pb && pb.isUndergroundPipe()) {
            Direction facing = state.get(PipeBlock.FACING);
            for (int d = 2; d <= MAX_UNDERGROUND_DIST; d++) {
                BlockPos checkPos = pos.offset(facing, d);
                BlockState checkState = world.getBlockState(checkPos);
                if (checkState.getBlock() instanceof PipeBlock otherPb && otherPb.isUndergroundPipe()) {
                    if (checkState.get(PipeBlock.FACING) == facing.getOpposite()) {
                        if (world.getBlockEntity(checkPos) instanceof PipeBlockEntity targetPipe) {
                            changed |= equalizeWithPipe(pipe, targetPipe);
                        }
                        break;
                    }
                }
            }
        }

        // 2. Выравниваем уровень с соседними трубами и подаём жидкость ТОЛЬКО в порт машины
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (pipe.amountMb <= 0) break;
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);

            if (!PipeBlock.canConnectFluidTo(neighborState, dir)) {
                continue;
            }

            BlockPos canonical = BlockHealthManager.getCanonicalPos(neighborPos, neighborState);
            BlockEntity be = world.getBlockEntity(canonical);

            if (be instanceof PipeBlockEntity otherPipe) {
                changed |= equalizeWithPipe(pipe, otherPipe);
            } else if (be instanceof StorageTankBlock.StorageTankBlockEntity tank) {
                if (pipe.amountMb > 320 && tank.canAcceptFluid(pipe.fluidKind, dir.getOpposite())) {
                    int offer = Math.min(pipe.amountMb - 320, 150);
                    int accepted = tank.insertFluid(pipe.fluidKind, offer, pipe.temperatureC, dir.getOpposite());
                    if (accepted > 0) {
                        pipe.amountMb -= accepted;
                        changed = true;
                    }
                }
            } else if (be instanceof FactorioFluidContainer machine && !(be instanceof OffshorePumpBlockEntity)) {
                if (machine.canAcceptFluid(pipe.fluidKind, dir.getOpposite())) {
                    int offer = Math.min(pipe.amountMb, 120);
                    int accepted = machine.insertFluid(pipe.fluidKind, offer, pipe.temperatureC, dir.getOpposite());
                    if (accepted > 0) {
                        pipe.amountMb -= accepted;
                        if (pipe.amountMb <= 0) {
                            pipe.amountMb = 0;
                            pipe.fluidKind = FluidKind.NONE;
                        }
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            pipe.markDirty();
            if ((world.getTime() & 3L) == 0L || pipe.amountMb == 0) {
                pipe.sync();
            }
        }
    }

    private static boolean equalizeWithPipe(PipeBlockEntity a, PipeBlockEntity b) {
        if (b.fluidKind != FluidKind.NONE && b.fluidKind != a.fluidKind) {
            return false;
        }
        int diff = a.amountMb - b.amountMb;
        if (diff <= 2) return false;

        int transfer = diff / 2;
        b.fluidKind = a.fluidKind;
        b.temperatureC = a.temperatureC;
        b.amountMb += transfer;
        a.amountMb -= transfer;
        if (a.amountMb <= 0) {
            a.amountMb = 0;
            a.fluidKind = FluidKind.NONE;
        }
        b.markDirty();
        if (b.world != null && (b.world.getTime() & 3L) == 0L) {
            b.sync();
        }
        return true;
    }

    public void sync() {
        markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public FluidKind getFluidKind() {
        return this.amountMb > 0 ? this.fluidKind : FluidKind.NONE;
    }

    @Override
    public int getFluidAmount() {
        return this.amountMb;
    }

    @Override
    public int getFluidCapacity() {
        return CAPACITY_MB;
    }

    @Override
    public int getTemperatureC() {
        return this.temperatureC;
    }

    @Override
    public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
        return this.amountMb == 0 || (this.fluidKind == kind && this.amountMb < CAPACITY_MB);
    }

    @Override
    public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
        if (!canAcceptFluid(kind, fromSide) || maxAmount <= 0) return 0;
        int space = CAPACITY_MB - this.amountMb;
        int added = Math.min(space, maxAmount);
        if (added > 0) {
            boolean wasEmpty = (this.amountMb == 0);
            this.fluidKind = kind;
            this.temperatureC = tempC;
            this.amountMb += added;
            if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) {
                sync();
            } else {
                markDirty();
            }
        }
        return added;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("FluidKind", this.fluidKind.name());
        nbt.putInt("AmountMb", this.amountMb);
        nbt.putInt("TempC", this.temperatureC);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        try {
            this.fluidKind = FluidKind.valueOf(nbt.getString("FluidKind"));
        } catch (Exception e) {
            this.fluidKind = FluidKind.NONE;
        }
        this.amountMb = nbt.getInt("AmountMb");
        this.temperatureC = nbt.getInt("TempC");
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