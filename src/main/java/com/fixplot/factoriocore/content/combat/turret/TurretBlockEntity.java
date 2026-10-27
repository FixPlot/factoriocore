package com.fixplot.factoriocore.content.combat.turret;

import com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.fluids.FactorioFluidContainer;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class TurretBlockEntity extends BlockEntity implements SidedInventory, FactorioFluidContainer, ExtendedScreenHandlerFactory<BlockPos> {

    public static final long LASER_IDLE_WATTS = 24_000L;     // 24 кВт в режиме ожидания
    public static final long LASER_ACTIVE_WATTS = 800_000L;  // 800 кВт при стрельбе лазером
    public static final int FLAME_CAPACITY_MB = 2000;
    public static final int INSERTER_AMMO_LIMIT = 10;        // Лимит магазинов от манипулятора

    private static final int[] SLOTS = new int[]{0};

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private int currentMagazineRounds = 0;
    private int maxMagazineRounds = 10;

    private FluidKind fuelFluidKind = FluidKind.NONE;
    private int fuelFluidMb = 0;

    private float turretYaw = 0.0f;
    private float turretPitch = 0.0f;
    private int fireCooldown = 0;
    private int activeFiringTicks = 0;
    private boolean powered = false;
    private int killsCount = 0;
    private float totalDamageDealt = 0.0f;

    // Координаты текущей цели для отрисовки лазерного луча и пламени на клиенте
    private float targetX = 0.0f;
    private float targetY = 0.0f;
    private float targetZ = 0.0f;

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getTurretKind();
                case 1 -> currentMagazineRounds;
                case 2 -> Math.max(1, maxMagazineRounds);
                case 3 -> fuelFluidMb;
                case 4 -> powered ? 1 : 0;
                case 5 -> killsCount;
                case 6 -> Math.round(totalDamageDealt);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int size() {
            return 7;
        }
    };

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TURRET_BLOCK_ENTITY, pos, state);
        if (state.contains(TurretBlock.FACING)) {
            this.turretYaw = state.get(TurretBlock.FACING).asRotation();
        }
    }

    public int getTurretKind() {
        if (getCachedState().getBlock() instanceof TurretBlock tb) {
            return tb.getTurretKind();
        }
        return 0;
    }

    public float getTurretYaw() { return this.turretYaw; }
    public float getTurretPitch() { return this.turretPitch; }
    public boolean isFiring() { return this.activeFiringTicks > 0; }
    public boolean isPowered() { return this.powered; }
    public int getKillsCount() { return this.killsCount; }
    public float getTotalDamageDealt() { return this.totalDamageDealt; }
    public Vec3d getTargetVec() { return new Vec3d(this.targetX, this.targetY, this.targetZ); }

    public TurretBlockEntity getMaster() {
        if (this.world == null) return this;
        BlockState st = getCachedState();
        if (st.getBlock() instanceof TurretBlock) {
            BlockPos masterPos = TurretBlock.getMasterPos(this.pos, st);
            if (this.world.getBlockEntity(masterPos) instanceof TurretBlockEntity m) {
                return m;
            }
        }
        return this;
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, TurretBlockEntity turret) {
        if (turret.activeFiringTicks > 0) {
            turret.activeFiringTicks--;
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, TurretBlockEntity turret) {
        if (world == null || world.isClient) return;

        int kind = turret.getTurretKind();
        boolean dirty = false;

        if (kind == 1) {
            boolean hasPower = PowerNetworkManager.isMachinePowered(world, pos);
            if (turret.powered != hasPower) {
                turret.powered = hasPower;
                dirty = true;
            }
        } else {
            turret.powered = true;
        }

        if (turret.fireCooldown > 0) {
            turret.fireCooldown--;
        }
        if (turret.activeFiringTicks > 0) {
            turret.activeFiringTicks--;
            if (turret.activeFiringTicks == 0) dirty = true;
        }

        boolean canAttack = switch (kind) {
            case 0 -> turret.ensureBulletLoaded();
            case 1 -> turret.powered;
            case 2 -> turret.fuelFluidMb >= 10;
            default -> false;
        };

        if (!canAttack) {
            if (dirty) turret.sync();
            return;
        }

        int range = (state.getBlock() instanceof TurretBlock tb) ? tb.getAttackRangeBlocks() : 18;
        Vec3d muzzlePos = new Vec3d(pos.getX() + 1.0, pos.getY() + 1.65, pos.getZ() + 1.0);
        LivingEntity target = findNearestHostileTarget(world, muzzlePos, range);

        if (target != null) {
            Vec3d targetCenter = target.getBoundingBox().getCenter();
            double dx = targetCenter.x - muzzlePos.x;
            double dy = targetCenter.y - muzzlePos.y;
            double dz = targetCenter.z - muzzlePos.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            float desiredYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float desiredPitch = (float) -(MathHelper.atan2(dy, horizDist) * (180.0 / Math.PI));

            turret.turretYaw = desiredYaw;
            turret.turretPitch = MathHelper.clamp(desiredPitch, -45.0f, 45.0f);
            turret.targetX = (float) targetCenter.x;
            turret.targetY = (float) targetCenter.y;
            turret.targetZ = (float) targetCenter.z;

            if (turret.fireCooldown <= 0 && world instanceof ServerWorld serverWorld) {
                turret.fireAtTarget(serverWorld, kind, muzzlePos, target);
                dirty = true;
            }
        }

        if (dirty) {
            turret.sync();
        }
    }

    @Nullable
    private static LivingEntity findNearestHostileTarget(World world, Vec3d center, int range) {
        Box searchBox = Box.of(center, range * 2.0, 18.0, range * 2.0);
        double maxDistSq = range * range;
        List<LivingEntity> hostiles = world.getEntitiesByClass(LivingEntity.class, searchBox, e ->
                e instanceof Monster && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= maxDistSq);
        if (hostiles.isEmpty()) return null;
        hostiles.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(center)));
        return hostiles.get(0);
    }

    private boolean ensureBulletLoaded() {
        if (this.currentMagazineRounds > 0) return true;
        ItemStack magStack = this.inventory.get(0);
        if (!magStack.isEmpty() && magStack.getItem() instanceof FactorioWeapons.AmmoMagazineItem mag) {
            this.currentMagazineRounds = mag.getRoundsPerMagazine();
            this.maxMagazineRounds = mag.getRoundsPerMagazine();
            return true;
        }
        return false;
    }

    private void fireAtTarget(ServerWorld world, int kind, Vec3d muzzlePos, LivingEntity target) {
        float healthBefore = target.getHealth();
        target.timeUntilRegen = 0;

        if (kind == 0) {
            // 1. Пулемётная турель (скорострельность 5 выстр./сек = каждые 4 тика)
            ItemStack magStack = this.inventory.get(0);
            float dmg = 5.0f;
            if (!magStack.isEmpty() && magStack.getItem() instanceof FactorioWeapons.AmmoMagazineItem mag) {
                dmg = mag.getDamagePerShot();
            }

            target.damage(world.getDamageSources().mobProjectile(null, null), dmg);
            this.currentMagazineRounds--;
            if (this.currentMagazineRounds <= 0 && !magStack.isEmpty()) {
                magStack.decrement(1);
                if (magStack.isEmpty()) {
                    this.inventory.set(0, ItemStack.EMPTY);
                } else if (magStack.getItem() instanceof FactorioWeapons.AmmoMagazineItem nextMag) {
                    this.currentMagazineRounds = nextMag.getRoundsPerMagazine();
                }
            }

            this.fireCooldown = 4;
            this.activeFiringTicks = 3;
            world.playSound(null, this.pos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 0.55f, 1.45f);
            world.spawnParticles(ParticleTypes.FLAME, muzzlePos.x, muzzlePos.y, muzzlePos.z, 3, 0.08, 0.08, 0.08, 0.02);
            world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 4, 0.15, 0.15, 0.15, 0.05);
        } else if (kind == 1) {
            // 2. Лазерная турель (урон 10 ед. каждые 8 тиков, 3D-луч рисуется на клиенте)
            target.damage(world.getDamageSources().magic(), 10.0f);
            this.fireCooldown = 8;
            this.activeFiringTicks = 6;
            world.playSound(null, this.pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 0.45f, 1.95f);
            world.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.5), target.getZ(), 4, 0.12, 0.12, 0.12, 0.03);
        } else if (kind == 2) {
            // 3. Огнемётная турель (бонус урона: Нефть 100%, Мазут 105%, Дизель 110%)
            float fuelMul = (this.fuelFluidKind == FluidKind.LIGHT_OIL) ? 1.10f
                    : (this.fuelFluidKind == FluidKind.HEAVY_OIL ? 1.05f : 1.00f);
            this.fuelFluidMb = Math.max(0, this.fuelFluidMb - 12);
            if (this.fuelFluidMb == 0) this.fuelFluidKind = FluidKind.NONE;

            Vec3d targetPos = target.getBoundingBox().getCenter();
            Vec3d dir = targetPos.subtract(muzzlePos).normalize();
            double dist = muzzlePos.distanceTo(targetPos);

            for (double d = 0.8; d <= dist; d += 1.2) {
                Vec3d p = muzzlePos.add(dir.multiply(d));
                world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.18, 0.12, 0.18, 0.02);
                if (d > 3.0) {
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.1, p.z, 1, 0.15, 0.1, 0.15, 0.01);
                }
            }

            // Поджигаем и наносим урон по огненному пятну в точке попадания
            Box fireSplash = Box.of(targetPos, 4.5, 3.0, 4.5);
            for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, fireSplash, e -> e instanceof Monster && e.isAlive())) {
                victim.timeUntilRegen = 0;
                victim.setOnFireFor(5);
                victim.damage(world.getDamageSources().onFire(), 8.0f * fuelMul);
            }

            this.fireCooldown = 5;
            this.activeFiringTicks = 5;
            world.playSound(null, this.pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.65f, 0.90f);
        }

        float dealt = Math.max(0.0f, healthBefore - Math.max(0.0f, target.getHealth()));
        this.totalDamageDealt += dealt;
        if (!target.isAlive()) {
            this.killsCount++;
        }
    }

    public void sync() {
        markDirty();
        if (this.world != null && !this.world.isClient) {
            this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    // === ЖИДКОСТНЫЙ ИНТЕРФЕЙС ДЛЯ ОГНЕМЁТНОЙ ТУРЕЛИ ===
    @Override public FluidKind getFluidKind() { return this.fuelFluidMb > 0 ? this.fuelFluidKind : FluidKind.NONE; }
    @Override public int getFluidAmount() { return this.fuelFluidMb; }
    @Override public int getFluidCapacity() { return getTurretKind() == 2 ? FLAME_CAPACITY_MB : 0; }
    @Override public int getTemperatureC() { return 25; }

    @Override
    public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
        if (getTurretKind() != 2) return false;
        boolean validFuel = (kind == FluidKind.CRUDE_OIL || kind == FluidKind.HEAVY_OIL || kind == FluidKind.LIGHT_OIL);
        return validFuel && (this.fuelFluidMb == 0 || (this.fuelFluidKind == kind && this.fuelFluidMb < FLAME_CAPACITY_MB));
    }

    @Override
    public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
        if (!canAcceptFluid(kind, fromSide) || maxAmount <= 0) return 0;
        int space = FLAME_CAPACITY_MB - this.fuelFluidMb;
        int add = Math.min(space, maxAmount);
        if (add > 0) {
            boolean wasEmpty = (this.fuelFluidMb == 0);
            this.fuelFluidKind = kind;
            this.fuelFluidMb += add;
            if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) sync();
            else markDirty();
        }
        return add;
    }

    // === ИНВЕНТАРЬ ДЛЯ ПУЛЕМЁТНОЙ ТУРЕЛИ (ПОДДЕРЖКА МАНИПУЛЯТОРОВ) ===
    @Override public int[] getAvailableSlots(Direction side) { return getTurretKind() == 0 ? SLOTS : new int[0]; }
    @Override public boolean isValid(int slot, ItemStack stack) { return getTurretKind() == 0 && slot == 0 && stack.getItem() instanceof FactorioWeapons.AmmoMagazineItem; }
    @Override public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) { return isValid(slot, stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }
    @Override public int size() { return 1; }
    @Override public boolean isEmpty() { return getMaster().inventory.get(0).isEmpty(); }
    @Override public ItemStack getStack(int slot) { return slot == 0 ? getMaster().inventory.get(0) : ItemStack.EMPTY; }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        TurretBlockEntity m = getMaster();
        ItemStack r = Inventories.splitStack(m.inventory, slot, amount);
        if (!r.isEmpty()) m.sync();
        return r;
    }

    @Override
    public ItemStack removeStack(int slot) {
        TurretBlockEntity m = getMaster();
        ItemStack r = Inventories.removeStack(m.inventory, slot);
        if (!r.isEmpty()) m.sync();
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        TurretBlockEntity m = getMaster();
        if (slot == 0) {
            m.inventory.set(0, stack);
            if (!stack.isEmpty() && m.currentMagazineRounds <= 0 && stack.getItem() instanceof FactorioWeapons.AmmoMagazineItem mag) {
                m.currentMagazineRounds = mag.getRoundsPerMagazine();
                m.maxMagazineRounds = mag.getRoundsPerMagazine();
            }
            m.sync();
        }
    }

    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override public void clear() { getMaster().inventory.clear(); getMaster().sync(); }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return getMaster().getPos();
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        TurretBlockEntity m = getMaster();
        return new TurretScreenHandler(syncId, playerInventory, m, m.propertyDelegate);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, this.inventory, registries);
        nbt.putInt("CurrentRounds", this.currentMagazineRounds);
        nbt.putInt("MaxRounds", this.maxMagazineRounds);
        nbt.putString("FuelKind", this.fuelFluidKind.name());
        nbt.putInt("FuelMb", this.fuelFluidMb);
        nbt.putFloat("TurretYaw", this.turretYaw);
        nbt.putFloat("TurretPitch", this.turretPitch);
        nbt.putInt("ActiveFiringTicks", this.activeFiringTicks);
        nbt.putBoolean("Powered", this.powered);
        nbt.putInt("KillsCount", this.killsCount);
        nbt.putFloat("TotalDamage", this.totalDamageDealt);
        nbt.putFloat("TargetX", this.targetX);
        nbt.putFloat("TargetY", this.targetY);
        nbt.putFloat("TargetZ", this.targetZ);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.inventory.clear();
        Inventories.readNbt(nbt, this.inventory, registries);
        this.currentMagazineRounds = nbt.getInt("CurrentRounds");
        this.maxMagazineRounds = Math.max(1, nbt.getInt("MaxRounds"));
        try {
            this.fuelFluidKind = FluidKind.valueOf(nbt.getString("FuelKind"));
        } catch (Exception e) {
            this.fuelFluidKind = FluidKind.NONE;
        }
        this.fuelFluidMb = nbt.getInt("FuelMb");
        this.turretYaw = nbt.getFloat("TurretYaw");
        this.turretPitch = nbt.getFloat("TurretPitch");
        this.activeFiringTicks = nbt.getInt("ActiveFiringTicks");
        this.powered = nbt.getBoolean("Powered");
        this.killsCount = nbt.getInt("KillsCount");
        this.totalDamageDealt = nbt.getFloat("TotalDamage");
        this.targetX = nbt.getFloat("TargetX");
        this.targetY = nbt.getFloat("TargetY");
        this.targetZ = nbt.getFloat("TargetZ");
    }

    @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
}