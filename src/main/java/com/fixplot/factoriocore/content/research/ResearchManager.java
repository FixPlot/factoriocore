package com.fixplot.factoriocore.content.research;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import com.fixplot.factoriocore.registry.ModSounds;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import com.fixplot.factoriocore.network.SyncResearchStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ResearchManager {

    public enum TechStatus { RESEARCHED, AVAILABLE, LOCKED }

    public enum SciencePack {
        AUTOMATION(0xFFE53935, 0xFFFF8A80, "item.factoriocore.automation_science_pack"),
        LOGISTIC(0xFF43A047, 0xFF81C784, "item.factoriocore.logistic_science_pack"),
        MILITARY(0xFF546E7A, 0xFF90A4AE, "item.factoriocore.military_science_pack"),
        CHEMICAL(0xFF039BE5, 0xFF80D8FF, "item.factoriocore.chemical_science_pack");

        public final int bodyColor;
        public final int liquidHighlight;
        public final String displayName;

        SciencePack(int bodyColor, int liquidHighlight, String displayName) {
            this.bodyColor = bodyColor;
            this.liquidHighlight = liquidHighlight;
            this.displayName = displayName;
        }

        public Item getItem() {
            return switch (this) {
                case AUTOMATION -> ModItems.AUTOMATION_SCIENCE_PACK;
                case LOGISTIC -> ModItems.LOGISTIC_SCIENCE_PACK;
                case MILITARY -> ModItems.MILITARY_SCIENCE_PACK;
                case CHEMICAL -> ModItems.CHEMICAL_SCIENCE_PACK;
            };
        }
    }

    public static class TechNode {
        public final String id;
        public final String name;
        public final String badgeLevel;
        public final String description;
        public final ItemStack icon;
        public final int costCount;
        public final int timeSeconds;
        public final List<SciencePack> packs;
        public final ItemStack craftTrigger;
        public final List<String> prerequisites;
        public final List<ItemStack> unlocks;
        public final List<String> unlockedRecipeIds;
        public final int treeCol;
        public final int treeRow;
        public final TechStatus initialStatus;
        public TechStatus status;
        public int completedUnits = 0;

        public TechNode(String id, String name, String badgeLevel, String description, ItemStack icon,
                        int costCount, int timeSeconds, List<SciencePack> packs, ItemStack craftTrigger,
                        int treeCol, int treeRow, TechStatus initialStatus,
                        List<String> prerequisites, List<ItemStack> unlocks, List<String> unlockedRecipeIds) {
            this.id = id;
            this.name = name;
            this.badgeLevel = badgeLevel;
            this.description = description;
            this.icon = icon;
            this.costCount = costCount;
            this.timeSeconds = timeSeconds;
            this.packs = packs;
            this.craftTrigger = craftTrigger;
            this.treeCol = treeCol;
            this.treeRow = treeRow;
            this.initialStatus = initialStatus;
            this.status = initialStatus;
            this.prerequisites = prerequisites;
            this.unlocks = unlocks;
            this.unlockedRecipeIds = unlockedRecipeIds;
        }

        public String getLocalizedName() {
            return Text.translatableWithFallback("tech.factoriocore." + this.id + ".name", this.name).getString();
        }

        public String getLocalizedDescription() {
            return Text.translatableWithFallback("tech.factoriocore." + this.id + ".desc", this.description).getString();
        }
    }

    public static final List<TechNode> TECHS = new ArrayList<>();
    public static final List<String> RESEARCH_QUEUE = new CopyOnWriteArrayList<>();
    private static final Map<String, String> RECIPE_TO_TECH = new HashMap<>();
    private static int currentUnitTickProgress = 0;
    private static boolean initialized = false;
    private static boolean dirtyForSave = false;
    private static boolean needsClientSync = false;
    private static MinecraftServer loadedServer = null;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        TECHS.clear();
        RECIPE_TO_TECH.clear();

        // =========================================================================
        // УРОВЕНЬ 0: БАЗОВЫЕ ИССЛЕДОВАТЕЛЬСКИЕ ТРИГГЕРЫ (FFF-376 / Factorio 2.0)
        // =========================================================================
        register(new TechNode(
                "electronics", "Электроника", "",
                "Базовая обработка сигналов и микросхем. Открывается при создании медного кабеля.",
                new ItemStack(ModItems.ELECTRONIC_CIRCUIT), 1, 5, List.of(), new ItemStack(ModItems.COPPER_CABLE),
                -2, 0, TechStatus.AVAILABLE,
                List.of(),
                List.of(new ItemStack(ModItems.ELECTRONIC_CIRCUIT), new ItemStack(ModBlocks.INSERTER), new ItemStack(ModBlocks.SMALL_ELECTRIC_POLE), new ItemStack(ModBlocks.LAB)),
                List.of("electronic_circuit", "inserter", "small_electric_pole", "lab")
        ));

        register(new TechNode(
                "steam_power", "Паровая энергия", "",
                "Выработка электроэнергии с помощью водяного пара из бойлера. Открывается при создании трубы.",
                new ItemStack(ModBlocks.STEAM_ENGINE), 1, 5, List.of(), new ItemStack(ModBlocks.PIPE),
                2, 0, TechStatus.AVAILABLE,
                List.of(),
                List.of(new ItemStack(ModBlocks.OFFSHORE_PUMP), new ItemStack(ModBlocks.BOILER), new ItemStack(ModBlocks.STEAM_ENGINE)),
                List.of("offshore_pump", "boiler", "steam_engine")
        ));

        // =========================================================================
        // УРОВЕНЬ 1: ЭЛЕКТРОБУР И АВТОМАТИЗИРОВАННАЯ НАУКА (FFF-376)
        // =========================================================================
        register(new TechNode(
                "electric_mining_drill", "Электрический бур", "",
                "Позволяет добывать руду в зоне 5×5 с помощью электроэнергии. Открывается при создании электронной схемы.",
                new ItemStack(ModBlocks.ELECTRIC_MINING_DRILL), 1, 5, List.of(), new ItemStack(ModItems.ELECTRONIC_CIRCUIT),
                -3, 1, TechStatus.LOCKED,
                List.of("electronics"),
                List.of(new ItemStack(ModBlocks.ELECTRIC_MINING_DRILL)),
                List.of("electric_mining_drill")
        ));

        register(new TechNode(
                "automation_science_pack", "Автоматизированный исследовательский пакет", "",
                "Позволяет проводить исследования в лабораториях с помощью красных колбочек. Открывается при создании лаборатории.",
                new ItemStack(ModItems.AUTOMATION_SCIENCE_PACK), 1, 5, List.of(), new ItemStack(ModBlocks.LAB),
                0, 1, TechStatus.LOCKED,
                List.of("electronics", "steam_power"),
                List.of(new ItemStack(ModItems.AUTOMATION_SCIENCE_PACK)),
                List.of("automation_science_pack")
        ));

        // =========================================================================
        // УРОВЕНЬ 2: БАЗОВЫЕ ТЕХНОЛОГИИ КРАСНОЙ НАУКИ
        // =========================================================================
        register(new TechNode(
                "automation", "Автоматизация", "",
                "Сборочные автоматы и длинные манипуляторы для автоматического производства.",
                new ItemStack(ModBlocks.ASSEMBLER), 10, 10, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                -4, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModBlocks.ASSEMBLER), new ItemStack(ModBlocks.LONG_HANDED_INSERTER)),
                List.of("assembler", "long_handed_inserter")
        ));

        register(new TechNode(
                "logistics", "Логистика", "1",
                "Подземные конвейеры и разделители для гибкой маршрутизации потоков ресурсов.",
                new ItemStack(ModBlocks.SPLITTER), 20, 15, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                -2, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModBlocks.UNDERGROUND_BELT), new ItemStack(ModBlocks.SPLITTER)),
                List.of("underground_belt", "splitter")
        ));

        register(new TechNode(
                "steel_processing", "Производство стали", "",
                "Позволяет переплавлять железные слитки в стальные балки (5 к 1) в печах и создавать стальные сундуки.",
                new ItemStack(ModItems.STEEL_PLATE), 50, 5, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                0, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModItems.STEEL_PLATE), new ItemStack(ModBlocks.STEEL_CHEST)),
                List.of("steel_chest")
        ));

        register(new TechNode(
                "repair_pack", "Ремонтный комплект", "",
                "Инструменты для быстрого ремонта повреждённых машин, конвейеров и защитных стен.",
                new ItemStack(ModItems.REPAIR_PACK), 20, 15, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                2, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModItems.REPAIR_PACK)),
                List.of("repair_pack")
        ));

        register(new TechNode(
                "stone_wall", "Каменная стена", "",
                "Укреплённые каменные стены для защиты периметра вашей фабрики.",
                new ItemStack(ModBlocks.STONE_WALL), 10, 10, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                4, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModBlocks.STONE_WALL)),
                List.of("stone_wall")
        ));

        register(new TechNode(
                "military", "Военное дело 1", "1",
                "Автоматическое стрелковое оружие и дробовики для эффективной обороны базы.",
                new ItemStack(ModItems.SUBMACHINE_GUN), 10, 10, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                6, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModItems.SUBMACHINE_GUN), new ItemStack(ModItems.SHOTGUN), new ItemStack(ModItems.SHOTGUN_SHELL)),
                List.of("submachine_gun", "shotgun", "shotgun_shell")
        ));

        register(new TechNode(
                "gun_turret", "Пулемётные турели", "",
                "Автоматические оборонительные турели, которые можно снабжать патронами с конвейера.",
                new ItemStack(ModBlocks.GUN_TURRET), 10, 10, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                8, 2, TechStatus.LOCKED,
                List.of("automation_science_pack"),
                List.of(new ItemStack(ModBlocks.GUN_TURRET)),
                List.of("gun_turret")
        ));

        // =========================================================================
        // УРОВЕНЬ 3: БЫСТРЫЙ МАНИПУЛЯТОР, ЗЕЛЁНАЯ И ВОЕННАЯ НАУКА
        // =========================================================================
        register(new TechNode(
                "fast_inserter", "Быстрый манипулятор", "",
                "Высокоскоростные манипуляторы (синие) за счёт улучшенной управляющей электроники.",
                new ItemStack(ModBlocks.FAST_INSERTER), 30, 15, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                -4, 3, TechStatus.LOCKED,
                List.of("automation"),
                List.of(new ItemStack(ModBlocks.FAST_INSERTER)),
                List.of("fast_inserter")
        ));

        register(new TechNode(
                "logistic_science_pack", "Логистический исследовательский пакет", "",
                "Открывает доступ к зелёным исследовательским пакетам для изучения продвинутой логистики, энергетики и нефтепереработки.",
                new ItemStack(ModItems.LOGISTIC_SCIENCE_PACK), 30, 15, List.of(SciencePack.AUTOMATION), ItemStack.EMPTY,
                -1, 3, TechStatus.LOCKED,
                List.of("automation", "logistics"),
                List.of(new ItemStack(ModItems.LOGISTIC_SCIENCE_PACK)),
                List.of("logistic_science_pack")
        ));

        register(new TechNode(
                "military_2", "Военное дело 2", "2",
                "Бронебойные патроны с повышенным уроном и осколочные гранаты.",
                new ItemStack(ModItems.PIERCING_ROUNDS_MAGAZINE), 20, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                5, 3, TechStatus.LOCKED,
                List.of("military", "steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModItems.PIERCING_ROUNDS_MAGAZINE), new ItemStack(ModItems.GRENADE)),
                List.of("piercing_rounds_magazine", "grenade")
        ));

        register(new TechNode(
                "military_science_pack", "Военный исследовательский пакет", "",
                "Позволяет разрабатывать продвинутые оборонительные средства и турели.",
                new ItemStack(ModItems.MILITARY_SCIENCE_PACK), 25, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                7, 3, TechStatus.LOCKED,
                List.of("stone_wall", "military_2"),
                List.of(new ItemStack(ModItems.MILITARY_SCIENCE_PACK)),
                List.of("military_science_pack")
        ));

        // =========================================================================
        // УРОВЕНЬ 4: ТЕХНОЛОГИИ ЗЕЛЁНОЙ НАУКИ
        // =========================================================================
        register(new TechNode(
                "logistics_2", "Логистика 2", "2",
                "Быстрые красные конвейеры, подземные переходы и разделители (30 предм./с).",
                new ItemStack(ModBlocks.FAST_BELT), 40, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -5, 4, TechStatus.LOCKED,
                List.of("logistics", "logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.FAST_BELT), new ItemStack(ModBlocks.FAST_UNDERGROUND_BELT), new ItemStack(ModBlocks.FAST_SPLITTER)),
                List.of("fast_belt", "fast_underground_belt", "fast_splitter")
        ));

        register(new TechNode(
                "fluid_handling", "Обработка жидкостей", "",
                "Крупные резервуары ёмкостью 25 000 мБ для хранения воды, пара, нефти и продуктов нефтехимии.",
                new ItemStack(ModBlocks.STORAGE_TANK), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -3, 4, TechStatus.LOCKED,
                List.of("automation", "steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.STORAGE_TANK)),
                List.of("storage_tank")
        ));

        register(new TechNode(
                "engine", "Двигатель", "",
                "Преобразует топливо в механическое движение. Необходим для насосов, транспорта и синей науки.",
                new ItemStack(ModItems.ENGINE_UNIT), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -1, 4, TechStatus.LOCKED,
                List.of("steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModItems.ENGINE_UNIT)),
                List.of("engine_unit")
        ));

        register(new TechNode(
                "electric_energy_distribution_1", "Распределение электроэнергии 1", "1",
                "Средние и большие стальные опоры ЛЭП для передачи энергии на дальние дистанции.",
                new ItemStack(ModBlocks.MEDIUM_ELECTRIC_POLE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                1, 4, TechStatus.LOCKED,
                List.of("steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.MEDIUM_ELECTRIC_POLE), new ItemStack(ModBlocks.BIG_ELECTRIC_POLE)),
                List.of("medium_electric_pole", "big_electric_pole")
        ));

        register(new TechNode(
                "advanced_material_processing", "Продвинутая обработка материалов", "1",
                "Стальные печи с удвоенной скоростью выплавки руды и стали.",
                new ItemStack(ModBlocks.STEEL_FURNACE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                3, 4, TechStatus.LOCKED,
                List.of("steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.STEEL_FURNACE)),
                List.of("steel_furnace")
        ));

        register(new TechNode(
                "landfill", "Отсыпка территории", "",
                "Позволяет засыпать озёра и водоёмы камнем для расширения площади под застройку фабрики.",
                new ItemStack(ModBlocks.LANDFILL), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                5, 4, TechStatus.LOCKED,
                List.of("logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.LANDFILL)),
                List.of("landfill")
        ));

        register(new TechNode(
                "gate", "Ворота", "",
                "Автоматические бронированные ворота, опускающиеся при приближении игрока.",
                new ItemStack(ModBlocks.GATE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                7, 4, TechStatus.LOCKED,
                List.of("stone_wall", "steel_processing", "logistic_science_pack"),
                List.of(new ItemStack(ModBlocks.GATE)),
                List.of("gate")
        ));

        // =========================================================================
        // УРОВЕНЬ 5: НЕФТЕПЕРЕРАБОТКА, СОЛНЕЧНАЯ ЭНЕРГИЯ, БЕТОН, СКОРОСТЬ ЛАБОРАТОРИЙ
        // =========================================================================
        register(new TechNode(
                "oil_processing", "Добыча и переработка нефти", "",
                "Позволяет качать сырую нефть нефтяными вышками и перерабатывать её в попутный нефтяной газ и твёрдое топливо.",
                new ItemStack(ModBlocks.PUMPJACK), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -2, 5, TechStatus.LOCKED,
                List.of("fluid_handling", "engine"),
                List.of(new ItemStack(ModBlocks.PUMPJACK), new ItemStack(ModBlocks.OIL_REFINERY), new ItemStack(ModBlocks.CHEMICAL_PLANT), new ItemStack(ModItems.SOLID_FUEL)),
                List.of("pumpjack", "oil_refinery", "chemical_plant")
        ));

        register(new TechNode(
                "solar_energy", "Солнечная энергия", "",
                "Источник чистой бесплатной электроэнергии (60 кВт), работающий в дневное время.",
                new ItemStack(ModBlocks.SOLAR_PANEL), 60, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                1, 5, TechStatus.LOCKED,
                List.of("electric_energy_distribution_1", "steel_processing"),
                List.of(new ItemStack(ModBlocks.SOLAR_PANEL)),
                List.of("solar_panel")
        ));

        register(new TechNode(
                "concrete", "Бетон", "",
                "Высокопрочное покрытие для полов завода, значительно увеличивающее скорость передвижения инженера.",
                new ItemStack(ModBlocks.CONCRETE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                3, 5, TechStatus.LOCKED,
                List.of("advanced_material_processing"),
                List.of(new ItemStack(ModBlocks.CONCRETE), new ItemStack(ModBlocks.HAZARD_CONCRETE), new ItemStack(ModBlocks.REFINED_CONCRETE), new ItemStack(ModBlocks.REFINED_HAZARD_CONCRETE)),
                List.of("concrete", "hazard_concrete", "refined_concrete", "refined_hazard_concrete")
        ));

        register(new TechNode(
                "research_speed_1", "Скорость лабораторий 1", "1",
                "Ускоряет проведение научных исследований во всех лабораториях на +25%.",
                new ItemStack(ModBlocks.LAB), 40, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                5, 5, TechStatus.LOCKED,
                List.of("logistic_science_pack"),
                List.of(),
                List.of()
        ));

        // =========================================================================
        // УРОВЕНЬ 6: ПЛАСТМАССЫ, СЕРА, ВЗРЫВЧАТКА И ОГНЕМЁТНЫЕ ТУРЕЛИ
        // =========================================================================
        register(new TechNode(
                "plastics", "Пластмассы", "",
                "Синтез полимерных пластиковых брусков из попутного нефтяного газа и угля в химическом заводе.",
                new ItemStack(ModItems.PLASTIC_BAR), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -4, 6, TechStatus.LOCKED,
                List.of("oil_processing"),
                List.of(new ItemStack(ModItems.PLASTIC_BAR)),
                List.of()
        ));

        register(new TechNode(
                "sulfur_processing", "Переработка серы", "",
                "Производство кристаллической серы и серной кислоты в химическом заводе.",
                new ItemStack(ModItems.SULFUR), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                0, 6, TechStatus.LOCKED,
                List.of("oil_processing"),
                List.of(new ItemStack(ModItems.SULFUR)),
                List.of()
        ));

        register(new TechNode(
                "explosives", "Взрывчатка", "",
                "Синтез промышленной взрывчатки из серы, угля и воды в химическом заводе.",
                new ItemStack(ModItems.EXPLOSIVES), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                4, 6, TechStatus.LOCKED,
                List.of("sulfur_processing", "military_science_pack"),
                List.of(new ItemStack(ModItems.EXPLOSIVES)),
                List.of()
        ));

        register(new TechNode(
                "flamethrower", "Огнемётные турели", "",
                "Стационарные турели, сжигающие толпы врагов потоком горящей нефти, мазута или дизеля.",
                new ItemStack(ModBlocks.FLAMETHROWER_TURRET), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.MILITARY), ItemStack.EMPTY,
                7, 6, TechStatus.LOCKED,
                List.of("oil_processing", "military_science_pack"),
                List.of(new ItemStack(ModBlocks.FLAMETHROWER_TURRET)),
                List.of("flamethrower_turret")
        ));

        // =========================================================================
        // УРОВЕНЬ 7: ПРОДВИНУТАЯ ЭЛЕКТРОНИКА, БАТАРЕИ И МИНЫ
        // =========================================================================
        register(new TechNode(
                "advanced_electronics", "Продвинутая электроника", "",
                "Интегральные микросхемы (красные платы) из пластика, медного кабеля и электронных схем.",
                new ItemStack(ModItems.ADVANCED_CIRCUIT), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -3, 7, TechStatus.LOCKED,
                List.of("plastics"),
                List.of(new ItemStack(ModItems.ADVANCED_CIRCUIT)),
                List.of("advanced_circuit")
        ));

        register(new TechNode(
                "battery", "Аккумуляторные элементы", "",
                "Электрохимические элементы питания из железа, меди и серной кислоты в химическом заводе.",
                new ItemStack(ModItems.BATTERY), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                1, 7, TechStatus.LOCKED,
                List.of("sulfur_processing"),
                List.of(new ItemStack(ModItems.BATTERY)),
                List.of()
        ));

        register(new TechNode(
                "land_mine", "Мины", "",
                "Противопехотные фугасные мины, автоматически взрывающиеся при приближении врагов.",
                new ItemStack(ModBlocks.LAND_MINE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.MILITARY), ItemStack.EMPTY,
                4, 7, TechStatus.LOCKED,
                List.of("explosives", "military_science_pack"),
                List.of(new ItemStack(ModBlocks.LAND_MINE)),
                List.of("land_mine")
        ));

        // =========================================================================
        // УРОВЕНЬ 8: ХИМИЧЕСКИЙ ПАКЕТ, АККУМУЛЯТОРЫ И ЛАЗЕРНЫЕ ТУРЕЛИ
        // =========================================================================
        register(new TechNode(
                "chemical_science_pack", "Химический исследовательский пакет", "",
                "Позволяет исследовать высокотехнологичные процессы с помощью синих колбочек.",
                new ItemStack(ModItems.CHEMICAL_SCIENCE_PACK), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                -2, 8, TechStatus.LOCKED,
                List.of("advanced_electronics", "sulfur_processing", "engine"),
                List.of(new ItemStack(ModItems.CHEMICAL_SCIENCE_PACK)),
                List.of("chemical_science_pack")
        ));

        register(new TechNode(
                "electric_energy_accumulators", "Электрические аккумуляторы", "",
                "Стационарные накопители энергии ёмкостью 5 МДж (отдают до 300 кВт при дефиците генерации в сети).",
                new ItemStack(ModBlocks.ACCUMULATOR), 60, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC), ItemStack.EMPTY,
                1, 8, TechStatus.LOCKED,
                List.of("battery", "electric_energy_distribution_1"),
                List.of(new ItemStack(ModBlocks.ACCUMULATOR)),
                List.of("accumulator")
        ));

        register(new TechNode(
                "laser_turret", "Лазерные турели", "",
                "Мощные энергетические турели дальнего боя, требующие только электричества.",
                new ItemStack(ModBlocks.LASER_TURRET), 60, 20, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.MILITARY), ItemStack.EMPTY,
                6, 8, TechStatus.LOCKED,
                List.of("battery", "military_science_pack"),
                List.of(new ItemStack(ModBlocks.LASER_TURRET)),
                List.of("laser_turret")
        ));

        // =========================================================================
        // УРОВЕНЬ 9: ТЕХНОЛОГИИ СИНЕЙ НАУКИ
        // =========================================================================
        register(new TechNode(
                "advanced_oil_processing", "Продвинутая переработка нефти", "",
                "Фракционная перегонка нефти на мазут, дизельное топливо и попутный газ, сжижение угля и крекинг нефтепродуктов.",
                new ItemStack(ModBlocks.OIL_REFINERY), 75, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.CHEMICAL), ItemStack.EMPTY,
                -4, 9, TechStatus.LOCKED,
                List.of("chemical_science_pack"),
                List.of(new ItemStack(ModBlocks.OIL_REFINERY), new ItemStack(ModBlocks.CHEMICAL_PLANT), new ItemStack(ModItems.SOLID_FUEL)),
                List.of()
        ));

        register(new TechNode(
                "lubricant", "Смазочная жидкость", "",
                "Превращение тяжёлого мазута в смазочную жидкость для высокоскоростных механизмов.",
                new ItemStack(ModBlocks.PIPE), 50, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.CHEMICAL), ItemStack.EMPTY,
                -2, 9, TechStatus.LOCKED,
                List.of("advanced_oil_processing"),
                List.of(new ItemStack(ModBlocks.CHEMICAL_PLANT)),
                List.of()
        ));

        register(new TechNode(
                "logistics_3", "Логистика 3", "3",
                "Сверхскоростные синие экспресс-конвейеры, подземные переходы и разделители (45 предм./с).",
                new ItemStack(ModBlocks.EXPRESS_BELT), 75, 15, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.CHEMICAL), ItemStack.EMPTY,
                0, 9, TechStatus.LOCKED,
                List.of("logistics_2", "chemical_science_pack"),
                List.of(new ItemStack(ModBlocks.EXPRESS_BELT), new ItemStack(ModBlocks.EXPRESS_UNDERGROUND_BELT), new ItemStack(ModBlocks.EXPRESS_SPLITTER)),
                List.of("express_belt", "express_underground_belt", "express_splitter")
        ));

        register(new TechNode(
                "electric_energy_distribution_2", "Распределение электроэнергии 2", "2",
                "Высоковольтные подстанции 2×2 с огромной зоной энергоснабжения 18×18 блоков.",
                new ItemStack(ModBlocks.SUBSTATION), 100, 20, List.of(SciencePack.AUTOMATION, SciencePack.LOGISTIC, SciencePack.CHEMICAL), ItemStack.EMPTY,
                2, 9, TechStatus.LOCKED,
                List.of("electric_energy_distribution_1", "chemical_science_pack"),
                List.of(new ItemStack(ModBlocks.SUBSTATION)),
                List.of("substation")
        ));

        recalculateAvailability();
    }

    private static void register(TechNode node) {
        TECHS.add(node);
        for (String recId : node.unlockedRecipeIds) {
            RECIPE_TO_TECH.put(recId, node.id);
        }
    }

    public static void resetToDefaults() {
        init();
        RESEARCH_QUEUE.clear();
        currentUnitTickProgress = 0;
        for (TechNode t : TECHS) {
            t.status = t.initialStatus;
            t.completedUnits = (t.initialStatus == TechStatus.RESEARCHED) ? t.costCount : 0;
        }
        recalculateAvailability();
    }

    public static void tickServer(MinecraftServer server) {
        if (server == null) return;
        if (loadedServer != server) {
            loadedServer = server;
            loadForServer(server);
        }

        if ((server.getTicks() % 10) == 0) {
            checkCraftTriggersForPlayers(server);
        }

        if (dirtyForSave && (server.getTicks() % 40 == 0)) {
            saveForServer(server);
            dirtyForSave = false;
        }

        if (needsClientSync && (server.getTicks() % 10 == 0)) {
            needsClientSync = false;
            broadcastResearchState(server);
        }
    }

    /** Рассылает текущее состояние исследований всем онлайн-игрокам (S2C). */
    public static void broadcastResearchState(MinecraftServer server) {
        if (server == null) return;
        SyncResearchStatePayload payload = new SyncResearchStatePayload(toNbt());
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Клиентская сторона: применяет состояние, пришедшее с сервера. */
    public static void applyClientSync(NbtCompound nbt) {
        // Не трогаем loadedServer/dirtyForSave: на клиенте это серверные поля.
        fromNbt(nbt);
    }

    private static void checkCraftTriggersForPlayers(MinecraftServer server) {
        init();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        boolean anyTriggered = true;
        while (anyTriggered) {
            anyTriggered = false;
            for (TechNode tech : TECHS) {
                if (tech.status == TechStatus.AVAILABLE && tech.packs.isEmpty() && !tech.craftTrigger.isEmpty()) {
                    Item triggerItem = tech.craftTrigger.getItem();
                    for (ServerPlayerEntity player : players) {
                        if (hasItemInInventory(player.getInventory(), triggerItem)) {
                            completeImmediately(tech.id);
                            notifyTriggerTechCompleted(server, tech);
                            anyTriggered = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean hasItemInInventory(PlayerInventory inv, Item target) {
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack st = inv.main.get(i);
            if (!st.isEmpty() && st.isOf(target)) return true;
        }
        return false;
    }

    private static void notifyTriggerTechCompleted(MinecraftServer server, TechNode tech) {
        Text msg = Text.literal("§a[FactorioCore] §e")
                .append(Text.translatableWithFallback("tech.factoriocore." + tech.id + ".name", tech.name))
                .append(Text.literal(" §7("))
                .append(tech.craftTrigger.getName())
                .append(Text.literal(")"));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
            p.playSoundToPlayer(ModSounds.RESEARCH_COMPLETED, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
    }

    public static void loadForServer(MinecraftServer server) {
        resetToDefaults();
        try {
            Path dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data");
            File file = dataDir.resolve("factoriocore_research.nbt").toFile();
            if (file.exists()) {
                NbtCompound nbt = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                fromNbt(nbt);
            }
        } catch (Exception ignored) {
        }
    }

    public static void saveForServer(MinecraftServer server) {
        if (server == null) return;
        try {
            Path dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data");
            dataDir.toFile().mkdirs();
            File file = dataDir.resolve("factoriocore_research.nbt").toFile();
            NbtIo.writeCompressed(toNbt(), file.toPath());
        } catch (Exception ignored) {
        }
    }

    public static NbtCompound toNbt() {
        init();
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("UnitTickProgress", currentUnitTickProgress);

        NbtList techList = new NbtList();
        for (TechNode t : TECHS) {
            NbtCompound tc = new NbtCompound();
            tc.putString("Id", t.id);
            tc.putString("Status", t.status.name());
            tc.putInt("CompletedUnits", t.completedUnits);
            techList.add(tc);
        }
        nbt.put("Techs", techList);

        NbtList queueList = new NbtList();
        for (String qId : RESEARCH_QUEUE) {
            queueList.add(NbtString.of(qId));
        }
        nbt.put("Queue", queueList);
        return nbt;
    }

    public static void fromNbt(NbtCompound nbt) {
        init();
        currentUnitTickProgress = nbt.getInt("UnitTickProgress");

        if (nbt.contains("Techs", NbtElement.LIST_TYPE)) {
            NbtList techList = nbt.getList("Techs", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < techList.size(); i++) {
                NbtCompound tc = techList.getCompound(i);
                TechNode node = getById(tc.getString("Id"));
                if (node != null) {
                    try {
                        node.status = TechStatus.valueOf(tc.getString("Status"));
                    } catch (IllegalArgumentException ignored) {
                    }
                    node.completedUnits = Math.max(0, Math.min(node.costCount, tc.getInt("CompletedUnits")));
                }
            }
        }

        RESEARCH_QUEUE.clear();
        if (nbt.contains("Queue", NbtElement.LIST_TYPE)) {
            NbtList queueList = nbt.getList("Queue", NbtElement.STRING_TYPE);
            for (int i = 0; i < queueList.size(); i++) {
                String qId = queueList.getString(i);
                TechNode node = getById(qId);
                if (node != null && node.status != TechStatus.RESEARCHED && !RESEARCH_QUEUE.contains(qId)) {
                    RESEARCH_QUEUE.add(qId);
                }
            }
        }
        recalculateAvailability();
    }

    public static TechNode getById(String id) {
        init();
        for (TechNode t : TECHS) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public static TechNode getActiveTech() {
        init();
        if (RESEARCH_QUEUE.isEmpty()) return null;
        return getById(RESEARCH_QUEUE.get(0));
    }

    public static boolean isTechResearched(String techId) {
        TechNode t = getById(techId);
        return t != null && t.status == TechStatus.RESEARCHED;
    }

    public static boolean isRecipeUnlocked(String recipeId) {
        init();
        String reqTech = RECIPE_TO_TECH.get(recipeId);
        if (reqTech == null) return true;
        return isTechResearched(reqTech);
    }

    public static void recalculateAvailability() {
        init();
        for (TechNode t : TECHS) {
            if (t.status == TechStatus.RESEARCHED) continue;
            boolean allPreMet = true;
            for (String preId : t.prerequisites) {
                TechNode pre = getById(preId);
                if (pre == null || pre.status != TechStatus.RESEARCHED) {
                    allPreMet = false;
                    break;
                }
            }
            t.status = allPreMet ? TechStatus.AVAILABLE : TechStatus.LOCKED;
        }
    }

    public static void queueTech(String techId) {
        TechNode t = getById(techId);
        if (t == null || t.status != TechStatus.AVAILABLE) return;

        if (t.packs.isEmpty()) {
            completeImmediately(techId);
            return;
        }

        if (!RESEARCH_QUEUE.contains(techId)) {
            RESEARCH_QUEUE.add(techId);
            dirtyForSave = true;
            needsClientSync = true;
        }
    }

    public static void removeQueueAt(int index) {
        if (index >= 0 && index < RESEARCH_QUEUE.size()) {
            RESEARCH_QUEUE.remove(index);
            if (index == 0) currentUnitTickProgress = 0;
            dirtyForSave = true;
            needsClientSync = true;
        }
    }

    public static void completeImmediately(String techId) {
        TechNode t = getById(techId);
        if (t != null) {
            t.status = TechStatus.RESEARCHED;
            t.completedUnits = t.costCount;
            RESEARCH_QUEUE.remove(techId);
            currentUnitTickProgress = 0;
            recalculateAvailability();
            dirtyForSave = true;
            needsClientSync = true;
        }
    }

    public static int getTicksPerUnit(TechNode tech) {
        int baseTicks = Math.max(20, tech.timeSeconds * 4);
        if (isTechResearched("research_speed_1")) {
            baseTicks = Math.max(15, (int) (baseTicks * 0.75f));
        }
        return baseTicks;
    }

    public static boolean advanceResearchTickFromLab(TechNode activeTech) {
        if (activeTech == null || activeTech.status == TechStatus.RESEARCHED) return false;
        currentUnitTickProgress++;
        int neededTicks = getTicksPerUnit(activeTech);
        if (currentUnitTickProgress >= neededTicks) {
            currentUnitTickProgress = 0;
            activeTech.completedUnits++;
            if (activeTech.completedUnits >= activeTech.costCount) {
                activeTech.status = TechStatus.RESEARCHED;
                RESEARCH_QUEUE.remove(activeTech.id);
                recalculateAvailability();
            }
            dirtyForSave = true;
            needsClientSync = true;
            return true;
        }
        return false;
    }

    public static float getOverallProgressRatio() {
        TechNode active = getActiveTech();
        if (active == null || active.costCount <= 0) return 0.0f;
        float unitPart = currentUnitTickProgress / (float) getTicksPerUnit(active);
        return Math.min(1.0f, (active.completedUnits + unitPart) / (float) active.costCount);
    }
}