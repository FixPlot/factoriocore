package com.fixplot.factoriocore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FactorioConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("factoriocore.json");

    public static FactorioConfig INSTANCE = new FactorioConfig();

    public boolean enableMinimap = true;
    public float minimapScale = 1.0f;
    public float infoPanelScale = 1.0f;
    public boolean enableTargetInfoPanel = true;
    public boolean enableCraftingQueueHud = true;
    public boolean replaceVanillaInventory = true;
    public boolean enableInventoryAutoSort = true;
    public boolean enablePavementSpeedBoost = true;
    public boolean showPoleSupplyWhenHoldingElectric = true;
    public boolean showDrillMiningArea = true;
    public boolean enableCopperWiresRender = true;
    public boolean enablePlacementParticles = true;
    public boolean enableMachineSounds = true;
    public float machineSoundVolume = 0.70f;
    public String factorioDataPath = "";
    
    // Настройки панели информации
    public boolean showInfoDescription = true;
    public boolean showInfoHealth = false;
    public boolean showInfoStatus = true;
    public boolean showInfoFluids = true;
    public boolean showInfoElectricity = true;

    public int poleSupplyColor = 0x26B4FF;
    public int drillAreaColor = 0x33E68C;
    public int validPlacementColor = 0x26FF40;
    public int invalidPlacementColor = 0xFF2626;
    public int wireColor = 0xE08538;
    public int altArrowColor = 0xFFE026;

    // ================== Кусаки (Biter) — балансные константы ==================
    /** Глобальный выключатель системы кусак. */
    public boolean enableBiters = true;
    /** Сколько загрязнения физический спавнер-гнездо съедает из сетки за секунду. */
    public float biterSpawnerDrainPerSecond = 3.0f;
    /** Штормовых единиц на одну «порцию» пула аналитического гнезда (в единицах заряда). */
    public float biterNestUnitsPerPoolCharge = 1.0f;
    /** Максимум одновременно виртуальных отрядов (RAM/TPS guard). */
    public int biterMaxAttackGroups = 16;
    /** Максимум материализуемых кусак на отряд (кламп волны). */
    public int biterMaxMaterializedPerGroup = 40;
    /** Шагов без градиента до «разочарования» отряда (рассеивание). */
    public int biterMaxLostSteps = 12;
    /** Порог загрязнения, ниже которого отряд не чувствует след. */
    public float biterGradientSniffThreshold = 0.25f;

    public void resetDefaults() {
        this.enableMinimap = true;
        this.minimapScale = 1.0f;
        this.infoPanelScale = 1.0f;
        this.enableTargetInfoPanel = true;
        this.enableCraftingQueueHud = true;
        this.replaceVanillaInventory = true;
        this.enableInventoryAutoSort = true;
        this.enablePavementSpeedBoost = true;
        this.showPoleSupplyWhenHoldingElectric = true;
        this.showDrillMiningArea = true;
        this.enableCopperWiresRender = true;
        this.enablePlacementParticles = true;
        
        this.showInfoDescription = true;
        this.showInfoHealth = false;
        this.showInfoStatus = true;
        this.showInfoFluids = true;
        this.showInfoElectricity = true;

        this.poleSupplyColor = 0x26B4FF;
        this.drillAreaColor = 0x33E68C;
        this.validPlacementColor = 0x26FF40;
        this.invalidPlacementColor = 0xFF2626;
        this.wireColor = 0xE08538;
        this.altArrowColor = 0xFFE026;
        this.enableMachineSounds = true; this.machineSoundVolume = 0.70f;
        save();
    }

    public static float getRed(int rgb) { return ((rgb >> 16) & 0xFF) / 255.0f; }
    public static float getGreen(int rgb) { return ((rgb >> 8) & 0xFF) / 255.0f; }
    public static float getBlue(int rgb) { return (rgb & 0xFF) / 255.0f; }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                FactorioConfig loaded = GSON.fromJson(reader, FactorioConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}