package com.fixplot.factoriocore.content.world.ore;

import com.fixplot.factoriocore.network.SyncOreAmountPayload;
import com.fixplot.factoriocore.worldgen.NauvisNoise;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

public class ResourceOreBlock extends BlockWithEntity {
    public static final MapCodec<ResourceOreBlock> CODEC = createCodec(s -> new ResourceOreBlock(s, 0));

    private final int oreType;

    public ResourceOreBlock(Settings settings, int oreType) {
        super(settings);
        this.oreType = oreType;
    }

    public int getOreType() {
        return this.oreType;
    }

    public Item getProductItem() {
        return switch (this.oreType) {
            case 0 -> Items.RAW_IRON;
            case 1 -> Items.RAW_COPPER;
            case 2 -> Items.COAL;
            default -> Items.COBBLESTONE;
        };
    }

    public ItemStack createProductStack(int count) {
        return new ItemStack(getProductItem(), count);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ResourceOreBlockEntity(pos, state);
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 0.10f;
    }

    public static void broadcastOreAmount(World world, BlockPos pos, int amount) {
        if (world instanceof ServerWorld serverWorld) {
            SyncOreAmountPayload payload = new SyncOreAmountPayload(pos.toImmutable(), amount);
            for (ServerPlayerEntity player : PlayerLookup.world(serverWorld)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static int consumeOreAt(World world, BlockPos pos, int amount) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ResourceOreBlock)) {
            return 0;
        }

        BlockEntity be = world.getBlockEntity(pos);
        ResourceOreBlockEntity oreBe;

        // Проверяем: если be — это нужный нам класс, просто подселяем его в oreBe
        if (be instanceof ResourceOreBlockEntity existing) {
            oreBe = existing;
        } else {
            // Если его нет или он сломан, создаём новый, пишем в ту же переменную и добавляем в мир
            oreBe = new ResourceOreBlockEntity(pos, state);
            world.addBlockEntity(oreBe);
        }

        // Дальше переменная oreBe гарантированно существует и инициализирована!
        int left = oreBe.deplete(amount);
        broadcastOreAmount(world, pos, left);

        if (left <= 0) {
            replaceWithDepletedGround(world, pos, state);
        }
        return left;
    }


    private static void replaceWithDepletedGround(World world, BlockPos pos, BlockState state) {
        boolean rooted = NauvisNoise.rand01(pos.getX(), pos.getZ(), 918273L) < 0.45;
        BlockState depletedGround = rooted ? Blocks.ROOTED_DIRT.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState();
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
        world.setBlockState(pos, depletedGround, Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.8f, 0.85f);
    }
}