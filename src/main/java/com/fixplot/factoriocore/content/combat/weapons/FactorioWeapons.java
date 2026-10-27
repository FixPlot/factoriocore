package com.fixplot.factoriocore.content.combat.weapons;

import com.fixplot.factoriocore.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FactorioWeapons {

    // =========================================================================
    // 1. МАГАЗИНЫ И ПАТРОНЫ
    // =========================================================================
    public static class AmmoMagazineItem extends Item {
        private final float damagePerShot;
        private final int roundsPerMagazine;
        private final boolean armorPiercing;

        public AmmoMagazineItem(Settings settings, float damagePerShot, int roundsPerMagazine, boolean armorPiercing) {
            super(settings);
            this.damagePerShot = damagePerShot;
            this.roundsPerMagazine = roundsPerMagazine;
            this.armorPiercing = armorPiercing;
        }

        public float getDamagePerShot() { return this.damagePerShot; }
        public int getRoundsPerMagazine() { return this.roundsPerMagazine; }
        public boolean isArmorPiercing() { return this.armorPiercing; }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("Боезапас магазина: ").formatted(Formatting.GRAY)
                    .append(Text.literal(roundsPerMagazine + " выстр.").formatted(Formatting.YELLOW)));
            tooltip.add(Text.literal("Физический урон: ").formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(damagePerShot)).formatted(armorPiercing ? Formatting.GOLD : Formatting.WHITE)));
        }
    }

    // =========================================================================
    // 2. СТРЕЛКОВОЕ ОРУЖИЕ (ПИСТОЛЕТ, АВТОМАТ, ДРОБОВИК)
    // =========================================================================
    public static class FactorioGunItem extends Item {
        public enum GunKind {
            PISTOL(18.0, 8, 1, 1.0f),
            SUBMACHINE_GUN(20.0, 3, 1, 2.2f),
            SHOTGUN(15.0, 14, 12, 7.5f);

            public final double range;
            public final int fireIntervalTicks;
            public final int pellets;
            public final float spreadDegrees;

            GunKind(double range, int fireIntervalTicks, int pellets, float spreadDegrees) {
                this.range = range;
                this.fireIntervalTicks = fireIntervalTicks;
                this.pellets = pellets;
                this.spreadDegrees = spreadDegrees;
            }
        }

        private final GunKind gunKind;

        public FactorioGunItem(Settings settings, GunKind gunKind) {
            super(settings);
            this.gunKind = gunKind;
        }

        public GunKind getGunKind() {
            return this.gunKind;
        }

        @Override
        public int getMaxUseTime(ItemStack stack, LivingEntity user) {
            return 72000;
        }

        @Override
        public UseAction getUseAction(ItemStack stack) {
            return UseAction.BOW;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack gunStack = user.getStackInHand(hand);
            if (this.gunKind == GunKind.SUBMACHINE_GUN) {
                user.setCurrentHand(hand);
                tryFireShot(world, user, gunStack);
                return TypedActionResult.consume(gunStack);
            } else {
                if (tryFireShot(world, user, gunStack)) {
                    user.getItemCooldownManager().set(this, this.gunKind.fireIntervalTicks);
                    return TypedActionResult.success(gunStack, world.isClient());
                }
                return TypedActionResult.fail(gunStack);
            }
        }

        @Override
        public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
            if (user instanceof PlayerEntity player && this.gunKind == GunKind.SUBMACHINE_GUN) {
                int elapsed = getMaxUseTime(stack, user) - remainingUseTicks;
                if (elapsed > 0 && elapsed % this.gunKind.fireIntervalTicks == 0) {
                    if (!tryFireShot(world, player, stack)) {
                        player.stopUsingItem();
                    }
                }
            }
        }

        private boolean tryFireShot(World world, PlayerEntity player, ItemStack gunStack) {
            AmmoMagazineItem ammoType = findAndConsumePlayerAmmo(player, this.gunKind == GunKind.SHOTGUN, !world.isClient);
            if (ammoType == null) {
                if (!world.isClient) {
                    world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.5f);
                    player.sendMessage(Text.literal("§cНет патронов в инвентаре!"), true);
                }
                return false;
            }

            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                float baseDamage = ammoType.getDamagePerShot();
                Vec3d eyePos = player.getEyePos();
                Vec3d lookVec = player.getRotationVec(1.0f);

                for (int p = 0; p < this.gunKind.pellets; p++) {
                    Vec3d dir = applySpread(lookVec, world, this.gunKind.spreadDegrees);
                    fireHitscanRay(serverWorld, player, eyePos, dir, this.gunKind.range,
                            this.gunKind == GunKind.SHOTGUN ? (baseDamage * 0.45f) : baseDamage,
                            ammoType.isArmorPiercing());
                }

                if (this.gunKind == GunKind.SHOTGUN) {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7f, 1.65f);
                } else {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.65f,
                            this.gunKind == GunKind.SUBMACHINE_GUN ? 1.55f : 1.35f);
                }
            }
            return true;
        }

        private static AmmoMagazineItem findAndConsumePlayerAmmo(PlayerEntity player, boolean isShotgun, boolean consumeServer) {
            if (player.isCreative()) {
                return isShotgun
                        ? (AmmoMagazineItem) ModItems.SHOTGUN_SHELL
                        : (AmmoMagazineItem) ModItems.PIERCING_ROUNDS_MAGAZINE;
            }

            var inv = player.getInventory();
            Item[] priority = isShotgun
                    ? new Item[]{ ModItems.SHOTGUN_SHELL }
                    : new Item[]{ ModItems.PIERCING_ROUNDS_MAGAZINE, ModItems.FIREARM_MAGAZINE };

            for (Item targetItem : priority) {
                for (int i = 0; i < inv.main.size(); i++) {
                    ItemStack st = inv.main.get(i);
                    if (!st.isEmpty() && st.isOf(targetItem) && st.getItem() instanceof AmmoMagazineItem mag) {
                        if (consumeServer) {
                            // 1 магазин = 10 выстрелов (списываем магазин с вероятностью 1/roundsPerMagazine на выстрел)
                            if (player.getRandom().nextInt(mag.getRoundsPerMagazine()) == 0) {
                                st.decrement(1);
                                if (st.isEmpty()) inv.main.set(i, ItemStack.EMPTY);
                                inv.markDirty();
                            }
                        }
                        return mag;
                    }
                }
            }
            return null;
        }

        private static Vec3d applySpread(Vec3d dir, World world, float spreadDeg) {
            if (spreadDeg <= 0.01f) return dir;
            double rad = Math.toRadians(spreadDeg);
            double rx = (world.random.nextDouble() - 0.5) * 2.0 * rad;
            double ry = (world.random.nextDouble() - 0.5) * 2.0 * rad;
            double rz = (world.random.nextDouble() - 0.5) * 2.0 * rad;
            return dir.add(rx, ry, rz).normalize();
        }

        public static void fireHitscanRay(ServerWorld world, LivingEntity shooter, Vec3d start, Vec3d dir,
                                          double maxRange, float damage, boolean armorPiercing) {
            Vec3d end = start.add(dir.multiply(maxRange));
            BlockHitResult blockHit = world.raycast(new RaycastContext(
                    start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));

            double hitDistSq = maxRange * maxRange;
            Vec3d finalEnd = end;
            if (blockHit.getType() != HitResult.Type.MISS) {
                finalEnd = blockHit.getPos();
                hitDistSq = start.squaredDistanceTo(finalEnd);
            }

            Box searchBox = new Box(start, finalEnd).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(
                    shooter, start, finalEnd, searchBox,
                    e -> !e.isSpectator() && e.isAlive() && e.canHit() && e != shooter,
                    hitDistSq
            );

            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
                finalEnd = entityHit.getPos();
                target.timeUntilRegen = 0; // Убираем ванильный иммунитет к очередям!
                target.damage(
                        armorPiercing ? world.getDamageSources().indirectMagic(shooter, shooter)
                                : world.getDamageSources().mobAttack(shooter),
                        damage
                );
                world.spawnParticles(ParticleTypes.CRIT, finalEnd.x, finalEnd.y, finalEnd.z, 4, 0.1, 0.1, 0.1, 0.08);
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                world.spawnParticles(ParticleTypes.SMOKE, finalEnd.x, finalEnd.y, finalEnd.z, 2, 0.04, 0.04, 0.04, 0.01);
            }

            // Трассер пули
            double totalDist = start.distanceTo(finalEnd);
            for (double d = 1.2; d < totalDist; d += 2.2) {
                Vec3d p = start.add(dir.multiply(d));
                world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("Дальность стрельбы: ").formatted(Formatting.GRAY)
                    .append(Text.literal((int) gunKind.range + " бл.").formatted(Formatting.AQUA)));
            String rate = String.format("%.1f выстр./сек", 20.0f / gunKind.fireIntervalTicks);
            tooltip.add(Text.literal("Скорострельность: ").formatted(Formatting.GRAY)
                    .append(Text.literal(rate).formatted(Formatting.YELLOW)));
        }
    }

    // =========================================================================
    // 3. ОСКОЛОЧНАЯ ГРАНАТА (УРОН ПО ПЛОЩАДИ + ВАЛКА ЛЕСА БЕЗ ПОЛОМКИ ЗАВОДА!)
    // =========================================================================
    public static class GrenadeItem extends Item {
        public GrenadeItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            user.getItemCooldownManager().set(this, 15);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 0.75f);

            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                Vec3d pos = user.getEyePos().add(user.getRotationVec(1.0f).multiply(0.5));
                Vec3d vel = user.getRotationVec(1.0f).multiply(0.95).add(0.0, 0.12, 0.0);
                ACTIVE_GRENADES.add(new FlyingGrenade(serverWorld, user, pos, vel, 45));
                if (!user.isCreative()) {
                    stack.decrement(1);
                }
            }
            return TypedActionResult.success(stack, world.isClient());
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("Взрывчатка по площади (радиус 4.5 блока).").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Расчищает деревья и уничтожает кусак, не ломая завод!").formatted(Formatting.GREEN));
        }
    }

    private static class FlyingGrenade {
        final ServerWorld world;
        final PlayerEntity thrower;
        Vec3d pos;
        Vec3d vel;
        int fuseTicks;

        FlyingGrenade(ServerWorld world, PlayerEntity thrower, Vec3d pos, Vec3d vel, int fuseTicks) {
            this.world = world;
            this.thrower = thrower;
            this.pos = pos;
            this.vel = vel;
            this.fuseTicks = fuseTicks;
        }
    }

    private static final List<FlyingGrenade> ACTIVE_GRENADES = new ArrayList<>();

    public static void registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE_GRENADES.isEmpty()) return;
            Iterator<FlyingGrenade> it = ACTIVE_GRENADES.iterator();
            while (it.hasNext()) {
                FlyingGrenade g = it.next();
                g.fuseTicks--;
                Vec3d nextPos = g.pos.add(g.vel);

                g.world.spawnParticles(ParticleTypes.SMOKE, g.pos.x, g.pos.y, g.pos.z, 2, 0.04, 0.04, 0.04, 0.0);

                BlockHitResult hit = g.world.raycast(new RaycastContext(
                        g.pos, nextPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, g.thrower));

                if (hit.getType() != HitResult.Type.MISS || g.fuseTicks <= 0) {
                    Vec3d boomPos = (hit.getType() != HitResult.Type.MISS) ? hit.getPos() : nextPos;
                    detonateGrenade(g.world, g.thrower, boomPos);
                    it.remove();
                } else {
                    g.pos = nextPos;
                    g.vel = g.vel.multiply(0.96).subtract(0.0, 0.055, 0.0);
                }
            }
        });
    }

    private static void detonateGrenade(ServerWorld world, PlayerEntity thrower, Vec3d center) {
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.05f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.2, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.FLAME, center.x, center.y + 0.2, center.z, 24, 1.2, 0.4, 1.2, 0.06);

        // 1. Наносим урон врагам в радиусе 4.5 блоков
        Box blastBox = Box.of(center, 9.0, 6.0, 9.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, blastBox, Entity::isAlive)) {
            if (target == thrower) continue;
            double dist = Math.sqrt(target.squaredDistanceTo(center));
            if (dist <= 4.6) {
                float factor = (float) Math.max(0.3, 1.0 - (dist / 4.8));
                target.timeUntilRegen = 0;
                target.damage(world.getDamageSources().explosion(thrower, thrower), 35.0f * factor);
            }
        }

        // 2. Расчищаем деревья и листву в радиусе 4 блоков (как гранаты в Factorio!)
        BlockPos origin = BlockPos.ofFloored(center);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 18) continue;
                for (int dy = -2; dy <= 7; dy++) {
                    BlockPos p = origin.add(dx, dy, dz);
                    BlockState st = world.getBlockState(p);
                    if (st.isIn(BlockTags.LOGS) || st.isIn(BlockTags.LEAVES)) {
                        world.breakBlock(p, false);
                    }
                }
            }
        }
    }
}