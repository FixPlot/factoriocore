package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.combat.biters.BiterEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Типы сущностей кусак: один класс BiterEntity на четыре тира с разными
 * атрибутами (HP / урон / скорость) и размером хитбокса.
 *
 * Спавн через BiterAttackDirector при материализации штурмовых групп;
 * ванильный natural spawn не используется (SpawnGroup.MONSTER — чтобы
 * входить в турельные фильтры instanceof Monster и считать для моб-капа).
 */
public class BiterEntityTypes {

    private static EntityType<BiterEntity> register(String name, EntityType.EntityFactory<BiterEntity> factory,
                                                    EntityDimensions dims, float hp, float damage, float speed) {
        EntityType<BiterEntity> type = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(FactorioCore.MOD_ID, name),
                EntityType.Builder.create(factory, SpawnGroup.MONSTER)
                        .dimensions(dims.width(), dims.height())
                        .build(name));
        FabricDefaultAttributeRegistry.register(type,
                BiterEntity.createBiterAttributes(hp, damage, speed, 24.0));
        return type;
    }

    public static final EntityType<BiterEntity> BITER_SMALL = register("biter_small",
            (type, world) -> new BiterEntity(type, world),
            EntityDimensions.changing(0.5f, 0.5f), 15.0f, 3.0f, 0.30f);

    public static final EntityType<BiterEntity> BITER_MEDIUM = register("biter_medium",
            (type, world) -> new BiterEntity(type, world),
            EntityDimensions.changing(0.7f, 0.7f), 75.0f, 8.0f, 0.33f);

    public static final EntityType<BiterEntity> BITER_BIG = register("biter_big",
            (type, world) -> new BiterEntity(type, world),
            EntityDimensions.changing(1.1f, 1.1f), 375.0f, 22.0f, 0.30f);

    public static final EntityType<BiterEntity> BITER_BEHEMOTH = register("biter_behemoth",
            (type, world) -> new BiterEntity(type, world),
            EntityDimensions.changing(1.6f, 1.6f), 3000.0f, 90.0f, 0.28f);

    /** Тип по индексу тира (0..3). */
    public static EntityType<BiterEntity> forTier(int tier) {
        return switch (Math.floorMod(tier, 4)) {
            case 1 -> BITER_MEDIUM;
            case 2 -> BITER_BIG;
            case 3 -> BITER_BEHEMOTH;
            default -> BITER_SMALL;
        };
    }

    /** Индекс тира для типа (устойчив к чужому типу). */
    public static int tierOf(EntityType<?> type) {
        if (type == BITER_BEHEMOTH) return 3;
        if (type == BITER_BIG) return 2;
        if (type == BITER_MEDIUM) return 1;
        return 0;
    }

    public static void registerBiterEntityTypes() {
        // статическая инициализация полей выше (вызывается из ModItems/ModBlocks-порядка)
    }
}
