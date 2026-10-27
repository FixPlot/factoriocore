package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.combat.RepairPackItem;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item COPPER_CABLE = register("copper_cable", new Item(new Item.Settings()));
    public static final Item IRON_STICK = register("iron_stick", new Item(new Item.Settings()));
    public static final Item IRON_GEAR_WHEEL = register("iron_gear_wheel", new Item(new Item.Settings()));
    public static final Item ELECTRONIC_CIRCUIT = register("electronic_circuit", new Item(new Item.Settings()));
    public static final Item ADVANCED_CIRCUIT = register("advanced_circuit", new Item(new Item.Settings()));
    public static final Item ENGINE_UNIT = register("engine_unit", new Item(new Item.Settings()));
    public static final Item STEEL_PLATE = register("steel_plate", new Item(new Item.Settings()));
    public static final Item PLASTIC_BAR = register("plastic_bar", new Item(new Item.Settings()));
    public static final Item SULFUR = register("sulfur", new Item(new Item.Settings()));
    public static final Item SOLID_FUEL = register("solid_fuel", new Item(new Item.Settings()));
    public static final Item BATTERY = register("battery", new Item(new Item.Settings()));
    public static final Item EXPLOSIVES = register("explosives", new Item(new Item.Settings()));
    public static final Item REPAIR_PACK = register("repair_pack", new RepairPackItem(new Item.Settings().maxCount(64)));

    // Научные исследовательские пакеты (колбочки)
    public static final Item AUTOMATION_SCIENCE_PACK = register("automation_science_pack", new Item(new Item.Settings()));
    public static final Item LOGISTIC_SCIENCE_PACK = register("logistic_science_pack", new Item(new Item.Settings()));
    public static final Item MILITARY_SCIENCE_PACK = register("military_science_pack", new Item(new Item.Settings()));
    public static final Item CHEMICAL_SCIENCE_PACK = register("chemical_science_pack", new Item(new Item.Settings()));

    // Оружие и боеприпасы
    public static final Item FIREARM_MAGAZINE = register("firearm_magazine",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.AmmoMagazineItem(new Item.Settings().maxCount(64), 5.0f, 10, false));
    public static final Item PIERCING_ROUNDS_MAGAZINE = register("piercing_rounds_magazine",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.AmmoMagazineItem(new Item.Settings().maxCount(64), 8.0f, 10, true));
    public static final Item SHOTGUN_SHELL = register("shotgun_shell",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.AmmoMagazineItem(new Item.Settings().maxCount(64), 5.0f, 10, false));

    public static final Item PISTOL = register("pistol",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem(
                    new Item.Settings().maxCount(1), com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem.GunKind.PISTOL));
    public static final Item SUBMACHINE_GUN = register("submachine_gun",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem(
                    new Item.Settings().maxCount(1), com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem.GunKind.SUBMACHINE_GUN));
    public static final Item SHOTGUN = register("shotgun",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem(
                    new Item.Settings().maxCount(1), com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.FactorioGunItem.GunKind.SHOTGUN));
    public static final Item GRENADE = register("grenade",
            new com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.GrenadeItem(new Item.Settings().maxCount(64)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(FactorioCore.MOD_ID, name), item);
    }

    public static void register() {
        FuelRegistry.INSTANCE.add(SOLID_FUEL, 4800);
    }

    public static void init() {
        register();
    }

    public static void registerModItems() {
        register();
    }
}