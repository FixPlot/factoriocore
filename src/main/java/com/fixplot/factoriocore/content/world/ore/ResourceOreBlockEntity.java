package com.fixplot.factoriocore.content.world.ore;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceOreBlockEntity extends BlockEntity {

    private static final Map<BlockPos, Integer> CLIENT_ORE_CACHE = new ConcurrentHashMap<>();

    private int remainingAmount = -1;

    public ResourceOreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RESOURCE_ORE_BLOCK_ENTITY, pos, state);
    }

    public static void setClientCachedAmount(BlockPos pos, int amount) {
        if (amount <= 0) {
            CLIENT_ORE_CACHE.remove(pos);
        } else {
            CLIENT_ORE_CACHE.put(pos.toImmutable(), amount);
        }
    }

    /** Клиентская сторона: очистка при дисконнекте/смене мира. */
    public static void clearClientCache() {
        CLIENT_ORE_CACHE.clear();
    }

    public static int getClientDisplayAmount(World world, BlockPos pos, int oreType) {
        Integer cached = CLIENT_ORE_CACHE.get(pos);
        if (cached != null) {
            return cached;
        }
        if (world != null && world.getBlockEntity(pos) instanceof ResourceOreBlockEntity oreBe && oreBe.remainingAmount > 0) {
            return oreBe.remainingAmount;
        }
        return NauvisChunkGenerator.evaluateOreAmountAt(pos.getX(), pos.getZ(), oreType);
    }

    public int getOrInitAmount() {
        if (this.world != null && this.world.isClient) {
            Integer cached = CLIENT_ORE_CACHE.get(this.pos);
            if (cached != null) {
                return cached;
            }
        }
        if (this.remainingAmount < 0) {
            this.remainingAmount = evaluateDefaultAmount();
            markDirty();
        }
        return this.remainingAmount;
    }

    private int evaluateDefaultAmount() {
        int oreType = 0;
        if (getCachedState().getBlock() instanceof ResourceOreBlock oreBlock) {
            oreType = oreBlock.getOreType();
        }
        return NauvisChunkGenerator.evaluateOreAmountAt(this.pos.getX(), this.pos.getZ(), oreType);
    }

    public void setRemainingAmount(int amount) {
        this.remainingAmount = Math.max(0, amount);
        sync();
    }

    public int deplete(int count) {
        int current = getOrInitAmount();
        this.remainingAmount = Math.max(0, current - count);
        sync();
        return this.remainingAmount;
    }

    public void sync() {
        markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        // Инициализируем лениво ДО сериализации, но НЕ делаем markDirty() внутри writeNbt:
        // пометка сохраняемого чанка «грязным» во время его же сохранения — вечный re-save.
        if (this.remainingAmount < 0) {
            this.remainingAmount = evaluateDefaultAmount();
        }
        nbt.putInt("RemainingAmount", Math.max(0, this.remainingAmount));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("RemainingAmount")) {
            this.remainingAmount = nbt.getInt("RemainingAmount");
            // Кэшируем безусловно на клиенте: readNbt вызывается и до привязки world
            // (initial chunk data), и после — при обоих путях данные нужны HUD.
            if (this.world == null || this.world.isClient) {
                CLIENT_ORE_CACHE.put(this.pos.toImmutable(), Math.max(0, this.remainingAmount));
            }
        }
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