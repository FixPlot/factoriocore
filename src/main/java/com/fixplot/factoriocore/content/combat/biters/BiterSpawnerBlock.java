package com.fixplot.factoriocore.content.combat.biters;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Блок гнезда кусак (спавнер). В тике (серверном) «пожирает» загрязнение из
 * VirtualPollutionGrid своего чанка и конвертирует его в spawn_charge.
 * Физического спавна мобов НЕ производит: заряд регистрируется как штурмовая
 * единица в пуле ближайшего отряда BiterAttackDirector (макро-ИИ).
 */
public class BiterSpawnerBlock extends BlockWithEntity {

    public static final MapCodec<BiterSpawnerBlock> CODEC = createCodec(BiterSpawnerBlock::new);

    public BiterSpawnerBlock(Settings settings) {
        super(settings.mapColor(MapColor.DARK_RED));
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
        return new BiterSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlocks.BITER_SPAWNER_BLOCK_ENTITY, BiterSpawnerBlockEntity::tick);
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        // Гнездо ломается заметно дольше камня — как факториовский spawner.
        return 0.05f;
    }
}
