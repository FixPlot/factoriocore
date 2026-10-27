package com.fixplot.factoriocore.content.combat;

import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public class LandMineBlockEntity extends BlockEntity {

    // В Factorio мина взводится через ~1.5 секунды (30 тиков) после установки
    public static final int ARM_DELAY_TICKS = 30;
    private int armedTicks = 0;

    public LandMineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LAND_MINE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, LandMineBlockEntity entity) {
        if (world.isClient) return;

        if (entity.armedTicks < ARM_DELAY_TICKS) {
            entity.armedTicks++;
            if (entity.armedTicks == ARM_DELAY_TICKS) {
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.4f, 1.6f);
            }
            return;
        }

        if ((world.getTime() & 1L) != 0L) {
            return;
        }

        // По канону Factorio мины реагируют ТОЛЬКО на врагов (Monster), никогда не взрываясь под игроком!
        Box triggerBox = new Box(pos).expand(0.85, 0.5, 0.85);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, triggerBox,
                living -> living.isAlive() && !living.isSpectator() && living instanceof Monster);

        if (!targets.isEmpty() && world instanceof ServerWorld serverWorld) {
            detonate(serverWorld, pos);
        }
    }

    public static void detonate(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.2;
        double cz = pos.getZ() + 0.5;

        // Эффект взрыва и ударной волны без разрушения заводских блоков и без урона игроку
        world.playSound(null, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.1f, 0.95f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy + 0.3, cz, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.FLAME, cx, cy + 0.2, cz, 28, 0.6, 0.3, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, cx, cy + 0.3, cz, 18, 0.7, 0.4, 0.7, 0.04);

        Box blastBox = new Box(pos).expand(3.5, 2.5, 3.5);
        List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, blastBox,
                living -> living.isAlive() && living instanceof Monster);
        for (LivingEntity victim : victims) {
            double dist = Math.sqrt(victim.squaredDistanceTo(cx, cy, cz));
            if (dist <= 4.0) {
                float factor = (float) Math.max(0.25, 1.0 - (dist / 4.2));
                victim.timeUntilRegen = 0;
                victim.damage(world.getDamageSources().explosion(null, null), 28.0f * factor);
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("ArmedTicks", this.armedTicks);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.armedTicks = nbt.getInt("ArmedTicks");
    }
}