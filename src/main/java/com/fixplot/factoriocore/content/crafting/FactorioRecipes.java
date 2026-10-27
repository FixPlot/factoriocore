package com.fixplot.factoriocore.content.crafting;

import com.fixplot.factoriocore.content.research.ResearchManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

public class FactorioRecipes {

    public enum Category {
        LOGISTICS,
        PRODUCTION,
        INTERMEDIATE,
        COMBAT
    }

    public record Ingredient(Item item, int count) {
    }

    public record Recipe(String id, Category category, int row, ItemStack output, int timeTicks, List<Ingredient> ingredients) {
        public float getSeconds() {
            return timeTicks / 20.0f;
        }
    }

    public record QueueStep(Recipe recipe, boolean deliverToPlayer) {
    }

    public static final List<Recipe> ALL = new ArrayList<>();
    private static final Map<String, Recipe> BY_ID = new LinkedHashMap<>();
    private static final Map<Item, Recipe> BY_OUTPUT_ITEM = new HashMap<>();

    public static void init() {
        if (!ALL.isEmpty()) return;

        // === ВКЛАДКА 2: ПРОМЕЖУТОЧНЫЕ ДЕТАЛИ И НАУКА (INTERMEDIATE) ===
        register("iron_stick", Category.INTERMEDIATE, 0, new ItemStack(ModItems.IRON_STICK, 2), 10,
                new Ingredient(Items.IRON_INGOT, 1));
        register("copper_cable", Category.INTERMEDIATE, 0, new ItemStack(ModItems.COPPER_CABLE, 2), 10,
                new Ingredient(Items.COPPER_INGOT, 1));
        register("iron_gear_wheel", Category.INTERMEDIATE, 0, new ItemStack(ModItems.IRON_GEAR_WHEEL, 1), 10,
                new Ingredient(Items.IRON_INGOT, 2));
        register("electronic_circuit", Category.INTERMEDIATE, 0, new ItemStack(ModItems.ELECTRONIC_CIRCUIT, 1), 10,
                new Ingredient(Items.IRON_INGOT, 1),
                new Ingredient(ModItems.COPPER_CABLE, 3));
        register("advanced_circuit", Category.INTERMEDIATE, 0, new ItemStack(ModItems.ADVANCED_CIRCUIT, 1), 120,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 2),
                new Ingredient(ModItems.PLASTIC_BAR, 2),
                new Ingredient(ModItems.COPPER_CABLE, 4));
        register("engine_unit", Category.INTERMEDIATE, 0, new ItemStack(ModItems.ENGINE_UNIT, 1), 200,
                new Ingredient(ModItems.STEEL_PLATE, 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1),
                new Ingredient(ModBlocks.PIPE.asItem(), 2));

        register("automation_science_pack", Category.INTERMEDIATE, 1, new ItemStack(ModItems.AUTOMATION_SCIENCE_PACK, 1), 100,
                new Ingredient(Items.COPPER_INGOT, 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1));
        register("logistic_science_pack", Category.INTERMEDIATE, 1, new ItemStack(ModItems.LOGISTIC_SCIENCE_PACK, 1), 120,
                new Ingredient(ModBlocks.INSERTER.asItem(), 1),
                new Ingredient(ModBlocks.BELT.asItem(), 1));
        register("military_science_pack", Category.INTERMEDIATE, 1, new ItemStack(ModItems.MILITARY_SCIENCE_PACK, 2), 200,
                new Ingredient(ModBlocks.STONE_WALL.asItem(), 2),
                new Ingredient(ModItems.PIERCING_ROUNDS_MAGAZINE, 1),
                new Ingredient(ModItems.GRENADE, 1));
        register("chemical_science_pack", Category.INTERMEDIATE, 1, new ItemStack(ModItems.CHEMICAL_SCIENCE_PACK, 2), 240,
                new Ingredient(ModItems.ADVANCED_CIRCUIT, 3),
                new Ingredient(ModItems.ENGINE_UNIT, 2),
                new Ingredient(ModItems.SULFUR, 1));

        // === ВКЛАДКА 0: ЛОГИСТИКА (LOGISTICS — компактные ряды как в Factorio 2.0) ===
        // Ряд 0: Сундуки
        register("wooden_chest", Category.LOGISTICS, 0, new ItemStack(ModBlocks.WOODEN_CHEST, 1), 10,
                new Ingredient(Items.OAK_LOG, 2));
        register("iron_chest", Category.LOGISTICS, 0, new ItemStack(ModBlocks.IRON_CHEST, 1), 10,
                new Ingredient(Items.IRON_INGOT, 8));
        register("steel_chest", Category.LOGISTICS, 0, new ItemStack(ModBlocks.STEEL_CHEST, 1), 10,
                new Ingredient(ModItems.STEEL_PLATE, 8));

        // Ряд 1: Все конвейеры, подземные конвейеры и разделители (9 предметов в 1 ряду!)
        register("belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.BELT, 2), 10,
                new Ingredient(Items.IRON_INGOT, 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1));
        register("fast_belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.FAST_BELT, 1), 10,
                new Ingredient(ModBlocks.BELT.asItem(), 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 5));
        register("express_belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.EXPRESS_BELT, 1), 10,
                new Ingredient(ModBlocks.FAST_BELT.asItem(), 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10));
        register("underground_belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.UNDERGROUND_BELT, 2), 20,
                new Ingredient(Items.IRON_INGOT, 10),
                new Ingredient(ModBlocks.BELT.asItem(), 5));
        register("fast_underground_belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.FAST_UNDERGROUND_BELT, 2), 40,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 40),
                new Ingredient(ModBlocks.UNDERGROUND_BELT.asItem(), 2));
        register("express_underground_belt", Category.LOGISTICS, 1, new ItemStack(ModBlocks.EXPRESS_UNDERGROUND_BELT, 2), 40,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 80),
                new Ingredient(ModBlocks.FAST_UNDERGROUND_BELT.asItem(), 2));
        register("splitter", Category.LOGISTICS, 1, new ItemStack(ModBlocks.SPLITTER, 1), 20,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 5),
                new Ingredient(Items.IRON_INGOT, 5),
                new Ingredient(ModBlocks.BELT.asItem(), 4));
        register("fast_splitter", Category.LOGISTICS, 1, new ItemStack(ModBlocks.FAST_SPLITTER, 1), 40,
                new Ingredient(ModBlocks.SPLITTER.asItem(), 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 10));
        register("express_splitter", Category.LOGISTICS, 1, new ItemStack(ModBlocks.EXPRESS_SPLITTER, 1), 40,
                new Ingredient(ModBlocks.FAST_SPLITTER.asItem(), 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(ModItems.ADVANCED_CIRCUIT, 10));

        // Ряд 2: Манипуляторы и Опоры ЛЭП
        register("burner_inserter", Category.LOGISTICS, 2, new ItemStack(ModBlocks.BURNER_INSERTER, 1), 10,
                new Ingredient(Items.IRON_INGOT, 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1));
        register("inserter", Category.LOGISTICS, 2, new ItemStack(ModBlocks.INSERTER, 1), 10,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1),
                new Ingredient(Items.IRON_INGOT, 1));
        register("long_handed_inserter", Category.LOGISTICS, 2, new ItemStack(ModBlocks.LONG_HANDED_INSERTER, 1), 10,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1),
                new Ingredient(Items.IRON_INGOT, 1),
                new Ingredient(ModBlocks.INSERTER.asItem(), 1));
        register("fast_inserter", Category.LOGISTICS, 2, new ItemStack(ModBlocks.FAST_INSERTER, 1), 10,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 2),
                new Ingredient(Items.IRON_INGOT, 2),
                new Ingredient(ModBlocks.INSERTER.asItem(), 1));
        register("small_electric_pole", Category.LOGISTICS, 2, new ItemStack(ModBlocks.SMALL_ELECTRIC_POLE, 2), 10,
                new Ingredient(Items.OAK_LOG, 1),
                new Ingredient(ModItems.COPPER_CABLE, 2));
        register("medium_electric_pole", Category.LOGISTICS, 2, new ItemStack(ModBlocks.MEDIUM_ELECTRIC_POLE, 1), 10,
                new Ingredient(ModItems.STEEL_PLATE, 2),
                new Ingredient(ModItems.IRON_STICK, 4),
                new Ingredient(ModItems.COPPER_CABLE, 2));
        register("big_electric_pole", Category.LOGISTICS, 2, new ItemStack(ModBlocks.BIG_ELECTRIC_POLE, 1), 10,
                new Ingredient(ModItems.STEEL_PLATE, 5),
                new Ingredient(ModItems.IRON_STICK, 8),
                new Ingredient(Items.COPPER_INGOT, 5));
        register("substation", Category.LOGISTICS, 2, new ItemStack(ModBlocks.SUBSTATION, 1), 10,
                new Ingredient(ModItems.STEEL_PLATE, 10),
                new Ingredient(ModItems.ADVANCED_CIRCUIT, 5),
                new Ingredient(Items.COPPER_INGOT, 5));

        // Ряд 3: Трубы и Резервуар
        register("pipe", Category.LOGISTICS, 3, new ItemStack(ModBlocks.PIPE, 1), 10,
                new Ingredient(Items.IRON_INGOT, 1));
        register("pipe_to_ground", Category.LOGISTICS, 3, new ItemStack(ModBlocks.PIPE_TO_GROUND, 2), 10,
                new Ingredient(ModBlocks.PIPE.asItem(), 10),
                new Ingredient(Items.IRON_INGOT, 5));
        register("storage_tank", Category.LOGISTICS, 3, new ItemStack(ModBlocks.STORAGE_TANK, 1), 60,
                new Ingredient(Items.IRON_INGOT, 20),
                new Ingredient(ModItems.STEEL_PLATE, 5));

        // Ряд 4: Покрытия и Отсыпка территории
        register("stone_path", Category.LOGISTICS, 4, new ItemStack(ModBlocks.STONE_PATH, 4), 10,
                new Ingredient(Items.COBBLESTONE, 4));
        register("concrete", Category.LOGISTICS, 4, new ItemStack(ModBlocks.CONCRETE, 10), 20,
                new Ingredient(ModBlocks.STONE_PATH.asItem(), 5),
                new Ingredient(Items.RAW_IRON, 1));
        register("hazard_concrete", Category.LOGISTICS, 4, new ItemStack(ModBlocks.HAZARD_CONCRETE, 10), 5,
                new Ingredient(ModBlocks.CONCRETE.asItem(), 10));
        register("refined_concrete", Category.LOGISTICS, 4, new ItemStack(ModBlocks.REFINED_CONCRETE, 10), 30,
                new Ingredient(ModBlocks.CONCRETE.asItem(), 20),
                new Ingredient(ModItems.STEEL_PLATE, 1),
                new Ingredient(ModItems.IRON_STICK, 8));
        register("refined_hazard_concrete", Category.LOGISTICS, 4, new ItemStack(ModBlocks.REFINED_HAZARD_CONCRETE, 10), 5,
                new Ingredient(ModBlocks.REFINED_CONCRETE.asItem(), 10));
        register("landfill", Category.LOGISTICS, 4, new ItemStack(ModBlocks.LANDFILL, 1), 10,
                new Ingredient(Items.COBBLESTONE, 20));

        // === ВКЛАДКА 1: ПРОИЗВОДСТВО И ЭНЕРГЕТИКА (PRODUCTION) ===
        // Ряд 0: Ремкомплект, Бойлер, Паровой двигатель, Солнечная панель, Аккумулятор
        register("repair_pack", Category.PRODUCTION, 0, new ItemStack(ModItems.REPAIR_PACK, 1), 10,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 2),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 2));
        register("boiler", Category.PRODUCTION, 0, new ItemStack(ModBlocks.BOILER, 1), 10,
                new Ingredient(ModBlocks.STONE_FURNACE.asItem(), 1),
                new Ingredient(ModBlocks.PIPE.asItem(), 4));
        register("steam_engine", Category.PRODUCTION, 0, new ItemStack(ModBlocks.STEAM_ENGINE, 1), 10,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 8),
                new Ingredient(ModBlocks.PIPE.asItem(), 5),
                new Ingredient(Items.IRON_INGOT, 10));
        register("solar_panel", Category.PRODUCTION, 0, new ItemStack(ModBlocks.SOLAR_PANEL, 1), 200,
                new Ingredient(ModItems.STEEL_PLATE, 5),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 15),
                new Ingredient(Items.COPPER_INGOT, 5));
        register("accumulator", Category.PRODUCTION, 0, new ItemStack(ModBlocks.ACCUMULATOR, 1), 200,
                new Ingredient(Items.IRON_INGOT, 2),
                new Ingredient(ModItems.BATTERY, 5));

        // Ряд 1: Буры, Помпа, Вышка и Печи (на старте Каменная печь и Твердотопливный бур стоят рядом!)
        register("burner_mining_drill", Category.PRODUCTION, 1, new ItemStack(ModBlocks.BURNER_MINING_DRILL, 1), 40,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 3),
                new Ingredient(ModBlocks.STONE_FURNACE.asItem(), 1),
                new Ingredient(Items.IRON_INGOT, 3));
        register("electric_mining_drill", Category.PRODUCTION, 1, new ItemStack(ModBlocks.ELECTRIC_MINING_DRILL, 1), 40,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 3),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 5),
                new Ingredient(Items.IRON_INGOT, 10));
        register("offshore_pump", Category.PRODUCTION, 1, new ItemStack(ModBlocks.OFFSHORE_PUMP, 1), 10,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 2),
                new Ingredient(ModBlocks.PIPE.asItem(), 1),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 1));
        register("pumpjack", Category.PRODUCTION, 1, new ItemStack(ModBlocks.PUMPJACK, 1), 100,
                new Ingredient(ModItems.STEEL_PLATE, 5),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 5),
                new Ingredient(ModBlocks.PIPE.asItem(), 10));
        register("stone_furnace", Category.PRODUCTION, 1, new ItemStack(ModBlocks.STONE_FURNACE, 1), 10,
                new Ingredient(Items.COBBLESTONE, 5));
        register("steel_furnace", Category.PRODUCTION, 1, new ItemStack(ModBlocks.STEEL_FURNACE, 1), 60,
                new Ingredient(ModItems.STEEL_PLATE, 6),
                new Ingredient(Items.COBBLESTONE, 10));

        // Ряд 2: Сборочный автомат, НПЗ, Химзавод, Лаборатория
        register("assembler", Category.PRODUCTION, 2, new ItemStack(ModBlocks.ASSEMBLER, 1), 10,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 3),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 5),
                new Ingredient(Items.IRON_INGOT, 9));
        register("oil_refinery", Category.PRODUCTION, 2, new ItemStack(ModBlocks.OIL_REFINERY, 1), 160,
                new Ingredient(ModItems.STEEL_PLATE, 15),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(Items.COBBLESTONE, 10),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 10),
                new Ingredient(ModBlocks.PIPE.asItem(), 10));
        register("chemical_plant", Category.PRODUCTION, 2, new ItemStack(ModBlocks.CHEMICAL_PLANT, 1), 100,
                new Ingredient(ModItems.STEEL_PLATE, 5),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 5),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 5),
                new Ingredient(ModBlocks.PIPE.asItem(), 5));
        register("lab", Category.PRODUCTION, 2, new ItemStack(ModBlocks.LAB, 1), 40,
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 10),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(ModBlocks.BELT.asItem(), 4));

        // === ВКЛАДКА 3: ОБОРОНА И ОРУЖИЕ (COMBAT) ===
        // Ряд 0: Оружие и Боеприпасы (на старте Пистолет и Магазин стоят рядом!)
        register("pistol", Category.COMBAT, 0, new ItemStack(ModItems.PISTOL, 1), 100,
                new Ingredient(Items.COPPER_INGOT, 5),
                new Ingredient(Items.IRON_INGOT, 5));
        register("submachine_gun", Category.COMBAT, 0, new ItemStack(ModItems.SUBMACHINE_GUN, 1), 200,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(Items.COPPER_INGOT, 5),
                new Ingredient(Items.IRON_INGOT, 10));
        register("shotgun", Category.COMBAT, 0, new ItemStack(ModItems.SHOTGUN, 1), 200,
                new Ingredient(Items.IRON_INGOT, 15),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 5),
                new Ingredient(Items.COPPER_INGOT, 10),
                new Ingredient(Items.OAK_LOG, 5));
        register("firearm_magazine", Category.COMBAT, 0, new ItemStack(ModItems.FIREARM_MAGAZINE, 1), 20,
                new Ingredient(Items.IRON_INGOT, 4));
        register("piercing_rounds_magazine", Category.COMBAT, 0, new ItemStack(ModItems.PIERCING_ROUNDS_MAGAZINE, 1), 60,
                new Ingredient(ModItems.FIREARM_MAGAZINE, 1),
                new Ingredient(ModItems.STEEL_PLATE, 1),
                new Ingredient(Items.COPPER_INGOT, 5));
        register("shotgun_shell", Category.COMBAT, 0, new ItemStack(ModItems.SHOTGUN_SHELL, 1), 60,
                new Ingredient(Items.COPPER_INGOT, 2),
                new Ingredient(Items.IRON_INGOT, 2));
        register("grenade", Category.COMBAT, 0, new ItemStack(ModItems.GRENADE, 1), 160,
                new Ingredient(Items.IRON_INGOT, 5),
                new Ingredient(Items.COAL, 10));

        // Ряд 1: Стены, Ворота, Мины и Турели
        register("stone_wall", Category.COMBAT, 1, new ItemStack(ModBlocks.STONE_WALL, 1), 10,
                new Ingredient(Items.COBBLESTONE, 5));
        register("gate", Category.COMBAT, 1, new ItemStack(ModBlocks.GATE, 1), 10,
                new Ingredient(ModBlocks.STONE_WALL.asItem(), 1),
                new Ingredient(ModItems.STEEL_PLATE, 2),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 2));
        register("land_mine", Category.COMBAT, 1, new ItemStack(ModBlocks.LAND_MINE, 4), 50,
                new Ingredient(ModItems.STEEL_PLATE, 1),
                new Ingredient(ModItems.EXPLOSIVES, 2));
        register("gun_turret", Category.COMBAT, 1, new ItemStack(ModBlocks.GUN_TURRET, 1), 160,
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 10),
                new Ingredient(Items.COPPER_INGOT, 10),
                new Ingredient(Items.IRON_INGOT, 20));
        register("laser_turret", Category.COMBAT, 1, new ItemStack(ModBlocks.LASER_TURRET, 1), 400,
                new Ingredient(ModItems.STEEL_PLATE, 20),
                new Ingredient(ModItems.ELECTRONIC_CIRCUIT, 20),
                new Ingredient(ModItems.BATTERY, 12));
        register("flamethrower_turret", Category.COMBAT, 1, new ItemStack(ModBlocks.FLAMETHROWER_TURRET, 1), 400,
                new Ingredient(ModItems.STEEL_PLATE, 30),
                new Ingredient(ModItems.IRON_GEAR_WHEEL, 15),
                new Ingredient(ModItems.ENGINE_UNIT, 5),
                new Ingredient(ModBlocks.PIPE.asItem(), 10));
    }

    private static void register(String id, Category cat, int row, ItemStack out, int ticks, Ingredient... ings) {
        Recipe r = new Recipe(id, cat, row, out, ticks, List.of(ings));
        ALL.add(r);
        BY_ID.put(id, r);
        BY_OUTPUT_ITEM.put(out.getItem(), r);
    }

    public static int getRecipeIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        init();
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    public static String getRecipeIdByIndex(int index) {
        init();
        if (index >= 0 && index < ALL.size()) {
            return ALL.get(index).id();
        }
        return "";
    }

    public static Recipe getById(String id) {
        init();
        return BY_ID.get(id);
    }

    public static Recipe getByOutput(Item item) {
        init();
        return BY_OUTPUT_ITEM.get(item);
    }

    public static List<Recipe> getByCategory(Category category) {
        init();
        List<Recipe> list = new ArrayList<>();
        for (Recipe r : ALL) {
            if (r.category() == category && ResearchManager.isRecipeUnlocked(r.id())) {
                list.add(r);
            }
        }
        return list;
    }

    /**
     * Группирует открытые рецепты по строкам без пустых строк-пробелов!
     * Если в начале игры в категории открыто мало рецептов или строка пуста,
     * строки автоматически сдвигаются вверх без дыр.
     */
    public static List<List<Recipe>> getCompactedRows(Category category, String searchQueryLower, int maxColsPerRow) {
        List<Recipe> unlocked = getByCategory(category);
        Map<Integer, List<Recipe>> byOriginalRow = new TreeMap<>();

        for (Recipe r : unlocked) {
            if (searchQueryLower != null && !searchQueryLower.isBlank()) {
                String nameLower = r.output().getName().getString().toLowerCase(Locale.ROOT);
                if (!nameLower.contains(searchQueryLower)) {
                    continue;
                }
            }
            byOriginalRow.computeIfAbsent(r.row(), k -> new ArrayList<>()).add(r);
        }

        List<List<Recipe>> rows = new ArrayList<>();
        List<Recipe> currentBuffer = new ArrayList<>();

        for (List<Recipe> group : byOriginalRow.values()) {
            // Если в предыдущей строке всего 1 предмет, а новая группа помещается рядом — объединяем их, чтобы не было одиноких иконок!
            if (!currentBuffer.isEmpty() && currentBuffer.size() <= 2 && currentBuffer.size() + group.size() <= maxColsPerRow) {
                currentBuffer.addAll(group);
            } else {
                if (!currentBuffer.isEmpty()) {
                    rows.add(currentBuffer);
                }
                currentBuffer = new ArrayList<>(group);
            }
        }
        if (!currentBuffer.isEmpty()) {
            rows.add(currentBuffer);
        }
        return rows;
    }

    public static Map<Item, Integer> snapshotInventory(PlayerInventory inv) {
        Map<Item, Integer> map = new HashMap<>();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (!s.isEmpty()) {
                map.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        return map;
    }

    public static int getCraftableCount(PlayerInventory inv, Recipe recipe) {
        Map<Item, Integer> pool = snapshotInventory(inv);
        int crafts = 0;
        List<QueueStep> dummy = new ArrayList<>();
        while (crafts < 999 && tryConsumeOrSubcraftBatch(pool, recipe, 1, false, dummy, 0)) {
            crafts += recipe.output().getCount();
        }
        return crafts;
    }

    public static List<QueueStep> planAndConsume(PlayerInventory inv, Recipe targetRecipe, int requestedCrafts) {
        Map<Item, Integer> initialSnapshot = snapshotInventory(inv);

        int maxAffordable = 0;
        for (int test = 1; test <= requestedCrafts; test++) {
            Map<Item, Integer> testPool = new HashMap<>(initialSnapshot);
            List<QueueStep> testSteps = new ArrayList<>();
            if (tryConsumeOrSubcraftBatch(testPool, targetRecipe, test, true, testSteps, 0)) {
                maxAffordable = test;
            } else {
                break;
            }
        }

        if (maxAffordable <= 0) {
            return Collections.emptyList();
        }

        Map<Item, Integer> simulatedPool = new HashMap<>(initialSnapshot);
        List<QueueStep> rawSteps = new ArrayList<>();
        tryConsumeOrSubcraftBatch(simulatedPool, targetRecipe, maxAffordable, true, rawSteps, 0);

        List<QueueStep> groupedSteps = consolidateStepsByFirstAppearance(rawSteps);

        for (Map.Entry<Item, Integer> entry : initialSnapshot.entrySet()) {
            Item item = entry.getKey();
            int before = entry.getValue();
            int after = simulatedPool.getOrDefault(item, 0);
            int toRemove = before - after;
            if (toRemove > 0) {
                removeFromPlayerInventory(inv, item, toRemove);
            }
        }

        for (Map.Entry<Item, Integer> entry : simulatedPool.entrySet()) {
            Item item = entry.getKey();
            int before = initialSnapshot.getOrDefault(item, 0);
            int after = entry.getValue();
            int surplus = after - before;
            if (surplus > 0) {
                inv.offerOrDrop(new ItemStack(item, surplus));
            }
        }

        return groupedSteps;
    }

    public static List<QueueStep> consolidateStepsByFirstAppearance(List<QueueStep> steps) {
        Map<String, List<QueueStep>> intermediatesById = new LinkedHashMap<>();
        List<QueueStep> finalTargets = new ArrayList<>();

        for (QueueStep step : steps) {
            if (step.deliverToPlayer()) {
                finalTargets.add(step);
            } else {
                intermediatesById.computeIfAbsent(step.recipe().id(), k -> new ArrayList<>()).add(step);
            }
        }

        List<QueueStep> result = new ArrayList<>(steps.size());
        for (List<QueueStep> group : intermediatesById.values()) {
            result.addAll(group);
        }
        result.addAll(finalTargets);
        return result;
    }

    private static boolean tryConsumeOrSubcraftBatch(Map<Item, Integer> pool, Recipe recipe, int batchesNeeded,
                                                     boolean isTopLevelTarget, List<QueueStep> outSteps, int depth) {
        if (depth > 8 || batchesNeeded <= 0) return false;

        for (Ingredient ing : recipe.ingredients()) {
            int needed = ing.count() * batchesNeeded;
            int have = pool.getOrDefault(ing.item(), 0);

            if (have >= needed) {
                pool.put(ing.item(), have - needed);
            } else {
                int missing = needed - have;
                pool.put(ing.item(), 0);

                Recipe subRecipe = getByOutput(ing.item());
                if (subRecipe == null) {
                    return false;
                }

                int subOutCount = subRecipe.output().getCount();
                int subBatchesNeeded = (missing + subOutCount - 1) / subOutCount;

                if (!tryConsumeOrSubcraftBatch(pool, subRecipe, subBatchesNeeded, false, outSteps, depth + 1)) {
                    return false;
                }

                int produced = subBatchesNeeded * subOutCount;
                int leftover = produced - missing;
                if (leftover > 0) {
                    pool.merge(ing.item(), leftover, Integer::sum);
                }
            }
        }

        for (int i = 0; i < batchesNeeded; i++) {
            outSteps.add(new QueueStep(recipe, isTopLevelTarget));
        }
        return true;
    }

    private static void removeFromPlayerInventory(PlayerInventory inv, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.main.size() && remaining > 0; i++) {
            ItemStack s = inv.main.get(i);
            if (!s.isEmpty() && s.isOf(item)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                if (s.isEmpty()) {
                    inv.main.set(i, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
        inv.markDirty();
    }

    public static Map<Item, Integer> calculateTotalRaw(Recipe recipe) {
        Map<Item, Integer> raw = new LinkedHashMap<>();
        accumulateRaw(recipe, 1.0, raw, 0);
        return raw;
    }

    private static void accumulateRaw(Recipe recipe, double multiplier, Map<Item, Integer> raw, int depth) {
        if (depth > 6) return;
        for (Ingredient ing : recipe.ingredients()) {
            Recipe sub = getByOutput(ing.item());
            if (sub != null) {
                double batches = (ing.count() * multiplier) / sub.output().getCount();
                accumulateRaw(sub, batches, raw, depth + 1);
            } else {
                int add = Math.max(1, (int) Math.ceil(ing.count() * multiplier));
                raw.merge(ing.item(), add, Integer::sum);
            }
        }
    }
}