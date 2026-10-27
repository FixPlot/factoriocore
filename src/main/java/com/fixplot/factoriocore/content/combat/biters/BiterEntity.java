package com.fixplot.factoriocore.content.combat.biters;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Кусака (Biter) — враждебная сущность, целенаправленно ломающая фабрику.
 *
 * Кастомный ИИ (в дополнение к ванильному):
 *  - {@link AttackMachinesGoal}: если рядом (в радиусе поиска) есть блоки фабрики
 *    FactorioCore — идёт к ближайшему и грызёт BlockHealthManager-структуры.
 *    Это «ломать машины» из ТЗ: кусаки бегут к печам/бойлерам/инсертерам.
 *  - Ванильные цели (атака игрока, месть, плавание) сохранены.
 *
 * Тиры отличаются параметрами (HP/урон/скорость), задаваемыми фабрикой
 * EntityType при создании (BiterEntityTypes).
 */
public class BiterEntity extends HostileEntity {

    /** Тег командных штурмов (группа материализации BiterAttackDirector). */
    public static final String RAID_GROUP_TAG_PREFIX = "factoriocore:raid_";

    /** Радиус поиска машин фабрики для ИИ (блоки). */
    private static final int MACHINE_SEARCH_RADIUS = 16;
    /** Урон по машине за укус (через BlockHealthManager). */
    private static final float MACHINE_DAMAGE = 6.0f;
    /** Тиков между укусами машины. */
    private static final int MACHINE_ATTACK_COOLDOWN = 10;

    /** Тир кусаки: 0=small, 1=medium, 2=big, 3=behemoth (масштабирует поведение). */
    private int tier = 0;
    /** Укусить в этом тике? Кулдаун ИИ грызли. */
    private int machineAttackCooldown = 0;

    public BiterEntity(EntityType<? extends BiterEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 5;
    }

    // =====================================================================
    // Атрибуты и ИИ
    // =====================================================================

    public static DefaultAttributeContainer.Builder createBiterAttributes(
            double maxHealth, double attackDamage, double movementSpeed, double followRange) {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, maxHealth)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, attackDamage)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, movementSpeed)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, followRange)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void initGoals() {
        // Порядок приоритетов: машины фабрики — цель №1 (визитка мода: кусаки
        // ломают ЗАВОД, а не только игрока), затем ванильное поведение.
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new AttackMachinesGoal(this));
        this.goalSelector.add(3, new net.minecraft.entity.ai.goal.MeleeAttackGoal(this, 1.1, false));

        this.targetSelector.add(1, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, PlayerEntity.class, true));
        this.targetSelector.add(2, new RevengeGoal(this));
    }

    @Override
    protected @Nullable net.minecraft.sound.SoundEvent getAmbientSound() {
        return net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_AMBIENT;
    }

    @Override
    protected net.minecraft.sound.SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected net.minecraft.sound.SoundEvent getDeathSound() {
        return net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_DEATH;
    }

    // =====================================================================
    // Тир
    // =====================================================================

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = MathHelper.clamp(tier, 0, 3);
    }

    public BiterEvolutionManager.BiterTier getBiterTier() {
        return switch (tier) {
            case 3 -> BiterEvolutionManager.BiterTier.BEHEMOTH;
            case 2 -> BiterEvolutionManager.BiterTier.BIG;
            case 1 -> BiterEvolutionManager.BiterTier.MEDIUM;
            default -> BiterEvolutionManager.BiterTier.SMALL;
        };
    }

    // =====================================================================
    // NBT
    // =====================================================================

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound result = super.writeNbt(nbt);
        result.putInt("Tier", tier);
        return result;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.tier = MathHelper.clamp(nbt.getInt("Tier"), 0, 3);
    }

    // =====================================================================
    // ИИ: грызля машин фабрики
    // =====================================================================

    /**
     * Цель: атаковать машины фабрики FactorioCore. Ищем ближайший блок
     * с BlockHealthManager.getMaxHealth > 0 в радиусе, идём и кусаем
     * через damageBlock. Работает на сервере; у клиента ИИ не тикает.
     */
    static class AttackMachinesGoal extends net.minecraft.entity.ai.goal.Goal {

        private final BiterEntity biter;
        private BlockPos targetMachine = null;
        private int repathCooldown = 0;
        private int searchCooldown = 0;

        AttackMachinesGoal(BiterEntity biter) {
            this.biter = biter;
            this.setControls(java.util.EnumSet.of(
                    net.minecraft.entity.ai.goal.Goal.Control.MOVE,
                    net.minecraft.entity.ai.goal.Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (biter.getWorld().isClient) return false;
            if (searchCooldown > 0) {
                searchCooldown--;
                return false;
            }
            searchCooldown = 20; // поиск цели раз в секунду
            targetMachine = findNearestFactoryMachine();
            return targetMachine != null;
        }

        @Override
        public boolean shouldContinue() {
            if (biter.getWorld().isClient) return false;
            if (targetMachine == null) return false;
            // Цель исчезла (снесли/сломали) — прекращаем.
            return BlockHealthManager.getMaxHealth(
                    biter.getWorld().getBlockState(targetMachine).getBlock()) > 0;
        }

        @Override
        public void start() {
            repathCooldown = 0;
        }

        @Override
        public void stop() {
            targetMachine = null;
            biter.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetMachine == null) return;
            World world = biter.getWorld();

            // Смотрим на машину.
            double mx = targetMachine.getX() + 0.5 - biter.getX();
            double my = targetMachine.getY() + 0.5 - biter.getEyeY();
            double mz = targetMachine.getZ() + 0.5 - biter.getZ();
            biter.getLookControl().lookAt(mx, my, mz, 30f, 30f);

            double distSq = biter.squaredDistanceTo(
                    targetMachine.getX() + 0.5, targetMachine.getY() + 0.5, targetMachine.getZ() + 0.5);
            double reachSq = (1.6 + biter.getWidth()) * (1.6 + biter.getWidth());

            if (distSq <= reachSq) {
                // В зоне укуса: грызём через BlockHealthManager.
                biter.getNavigation().stop();
                if (biter.machineAttackCooldown <= 0 && world instanceof ServerWorld serverWorld) {
                    biter.machineAttackCooldown = MACHINE_ATTACK_COOLDOWN;
                    var state = world.getBlockState(targetMachine);
                    if (!state.isAir()) {
                        // Урон по структуре: масштабируется тиром кусаки.
                        int bite = Math.round(MACHINE_DAMAGE * (1.0f + 0.5f * biter.tier));
                        BlockHealthManager.damageBlock(serverWorld, targetMachine, state, bite);
                        biter.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    }
                }
            } else if (--repathCooldown <= 0) {
                // Идём к машине (перепрокладка раз в полсекунды).
                repathCooldown = 10;
                biter.getNavigation().startMovingTo(
                        targetMachine.getX() + 0.5, targetMachine.getY(), targetMachine.getZ() + 0.5, 1.15);
            }
        }

        @Nullable
        private BlockPos findNearestFactoryMachine() {
            if (!(biter.getWorld() instanceof ServerWorld world)) return null;
            BlockPos center = biter.getBlockPos();

            // Скан по BE чанков в радиусе (не воксели и не глобальный стрим):
            // наши машины всегда BlockEntity, обход 3x3 чанков — дёшево.
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            int cx0 = center.getX() >> 4, cz0 = center.getZ() >> 4;
            int radiusChunks = (MACHINE_SEARCH_RADIUS >> 4) + 1;
            for (int dcx = -radiusChunks; dcx <= radiusChunks; dcx++) {
                for (int dcz = -radiusChunks; dcz <= radiusChunks; dcz++) {
                    if (!world.isChunkLoaded(cx0 + dcx, cz0 + dcz)) continue;
                    var chunk = world.getChunk(cx0 + dcx, cz0 + dcz);
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        BlockPos p = entry.getKey();
                        double d = p.getSquaredDistance(center);
                        if (d > (double) (MACHINE_SEARCH_RADIUS * MACHINE_SEARCH_RADIUS)) continue;
                        if (BlockHealthManager.getMaxHealth(
                                world.getBlockState(p).getBlock()) <= 0) continue;
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                }
            }
            return best;
        }
    }
}
