package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup LOGISTICS_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(FactorioCore.MOD_ID, "logistics"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.factoriocore.logistics"))
                    .icon(() -> new ItemStack(ModBlocks.BELT))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.WOODEN_CHEST);
                        entries.add(ModBlocks.IRON_CHEST);
                        entries.add(ModBlocks.STEEL_CHEST);
                        entries.add(ModBlocks.BELT);
                        entries.add(ModBlocks.FAST_BELT);
                        entries.add(ModBlocks.EXPRESS_BELT);
                        entries.add(ModBlocks.UNDERGROUND_BELT);
                        entries.add(ModBlocks.FAST_UNDERGROUND_BELT);
                        entries.add(ModBlocks.EXPRESS_UNDERGROUND_BELT);
                        entries.add(ModBlocks.SPLITTER);
                        entries.add(ModBlocks.FAST_SPLITTER);
                        entries.add(ModBlocks.EXPRESS_SPLITTER);
                        entries.add(ModBlocks.BURNER_INSERTER);
                        entries.add(ModBlocks.INSERTER);
                        entries.add(ModBlocks.LONG_HANDED_INSERTER);
                        entries.add(ModBlocks.FAST_INSERTER);
                        entries.add(ModBlocks.PIPE);
                        entries.add(ModBlocks.PIPE_TO_GROUND);
                        entries.add(ModBlocks.STORAGE_TANK);
                        entries.add(ModBlocks.SMALL_ELECTRIC_POLE);
                        entries.add(ModBlocks.MEDIUM_ELECTRIC_POLE);
                        entries.add(ModBlocks.BIG_ELECTRIC_POLE);
                        entries.add(ModBlocks.SUBSTATION);
                        entries.add(ModBlocks.LANDFILL);
                        entries.add(ModBlocks.STONE_PATH);
                        entries.add(ModBlocks.CONCRETE);
                        entries.add(ModBlocks.HAZARD_CONCRETE);
                        entries.add(ModBlocks.REFINED_CONCRETE);
                        entries.add(ModBlocks.REFINED_HAZARD_CONCRETE);
                    })
                    .build()
    );

    public static final ItemGroup PRODUCTION_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(FactorioCore.MOD_ID, "production"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.factoriocore.production"))
                    .icon(() -> new ItemStack(ModBlocks.STONE_FURNACE))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.REPAIR_PACK);
                        entries.add(ModBlocks.BURNER_MINING_DRILL);
                        entries.add(ModBlocks.ELECTRIC_MINING_DRILL);
                        entries.add(ModBlocks.PUMPJACK);
                        entries.add(ModBlocks.OFFSHORE_PUMP);
                        entries.add(ModBlocks.BOILER);
                        entries.add(ModBlocks.STEAM_ENGINE);
                        entries.add(ModBlocks.SOLAR_PANEL);
                        entries.add(ModBlocks.ACCUMULATOR);
                        entries.add(ModBlocks.STONE_FURNACE);
                        entries.add(ModBlocks.STEEL_FURNACE);
                        entries.add(ModBlocks.ASSEMBLER);
                        entries.add(ModBlocks.OIL_REFINERY);
                        entries.add(ModBlocks.CHEMICAL_PLANT);
                        entries.add(ModBlocks.LAB);
                    })
                    .build()
    );

    public static final ItemGroup ITEMS_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(FactorioCore.MOD_ID, "items"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.factoriocore.items"))
                    .icon(() -> new ItemStack(Items.IRON_INGOT))
                    .entries((displayContext, entries) -> {
                        entries.add(Items.COAL);
                        entries.add(ModItems.SOLID_FUEL);
                        entries.add(Items.RAW_IRON);
                        entries.add(Items.RAW_COPPER);
                        entries.add(Items.IRON_INGOT);
                        entries.add(Items.COPPER_INGOT);
                        entries.add(ModItems.STEEL_PLATE);
                        entries.add(ModItems.PLASTIC_BAR);
                        entries.add(ModItems.SULFUR);
                        entries.add(ModItems.BATTERY);
                        entries.add(ModItems.EXPLOSIVES);
                        entries.add(ModItems.IRON_STICK);
                        entries.add(ModItems.COPPER_CABLE);
                        entries.add(ModItems.IRON_GEAR_WHEEL);
                        entries.add(ModItems.ELECTRONIC_CIRCUIT);
                        entries.add(ModItems.ADVANCED_CIRCUIT);
                        entries.add(ModItems.ENGINE_UNIT);
                        entries.add(ModItems.AUTOMATION_SCIENCE_PACK);
                        entries.add(ModItems.LOGISTIC_SCIENCE_PACK);
                        entries.add(ModItems.MILITARY_SCIENCE_PACK);
                        entries.add(ModItems.CHEMICAL_SCIENCE_PACK);
                    })
                    .build()
    );

    public static final ItemGroup COMBAT_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(FactorioCore.MOD_ID, "combat"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.factoriocore.combat"))
                    .icon(() -> new ItemStack(ModBlocks.GUN_TURRET))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.PISTOL);
                        entries.add(ModItems.SUBMACHINE_GUN);
                        entries.add(ModItems.SHOTGUN);
                        entries.add(ModItems.FIREARM_MAGAZINE);
                        entries.add(ModItems.PIERCING_ROUNDS_MAGAZINE);
                        entries.add(ModItems.SHOTGUN_SHELL);
                        entries.add(ModItems.GRENADE);
                        entries.add(ModBlocks.LAND_MINE);
                        entries.add(ModBlocks.STONE_WALL);
                        entries.add(ModBlocks.GATE);
                        entries.add(ModBlocks.GUN_TURRET);
                        entries.add(ModBlocks.LASER_TURRET);
                        entries.add(ModBlocks.FLAMETHROWER_TURRET);
                        entries.add(ModBlocks.BITER_SPAWNER);
                    })
                    .build()
    );

    public static final ItemGroup DEV_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(FactorioCore.MOD_ID, "dev"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.factoriocore.dev"))
                    .icon(() -> new ItemStack(ModBlocks.IRON_ORE_DEPOSIT))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.ELECTRIC_ENERGY_INTERFACE);
                        entries.add(ModBlocks.IRON_ORE_DEPOSIT);
                        entries.add(ModBlocks.COPPER_ORE_DEPOSIT);
                        entries.add(ModBlocks.COAL_DEPOSIT);
                        entries.add(ModBlocks.STONE_DEPOSIT);
                        entries.add(ModBlocks.CRUDE_OIL_DEPOSIT);
                    })
                    .build()
    );

    public static void registerItemGroups() {
        FactorioCore.LOGGER.info("Registering 5 Creative Item Groups for " + FactorioCore.MOD_ID);
    }
}