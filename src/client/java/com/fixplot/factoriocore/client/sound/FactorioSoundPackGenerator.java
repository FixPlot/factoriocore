package com.fixplot.factoriocore.client.sound;

import com.fixplot.factoriocore.FactorioCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class FactorioSoundPackGenerator {

    public static final String PACK_FOLDER_NAME = "FactorioCore_Sounds";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Таблица сопоставления наших SoundEvent -> оригинальных имён .ogg файлов в Factorio/data
    private static final Map<String, List<String>> SOUND_MAPPINGS = new LinkedHashMap<>();

    static {
        SOUND_MAPPINGS.put("machine_assembler", List.of("assembling-machine-t1-1.ogg", "assembling-machine-t1-2.ogg", "assembling-machine-1.ogg"));
        SOUND_MAPPINGS.put("machine_burner_drill", List.of("burner-mining-drill.ogg", "burner-mining-drill-1.ogg"));
        SOUND_MAPPINGS.put("machine_electric_drill", List.of("electric-mining-drill.ogg", "electric-mining-drill-1.ogg"));
        SOUND_MAPPINGS.put("machine_furnace", List.of("furnace.ogg", "stone-furnace.ogg", "steel-furnace.ogg"));
        SOUND_MAPPINGS.put("machine_boiler", List.of("boiler.ogg", "steam.ogg"));
        SOUND_MAPPINGS.put("machine_steam_engine", List.of("steam-engine-90bpm.ogg", "steam-engine.ogg"));
        SOUND_MAPPINGS.put("machine_pumpjack", List.of("pumpjack.ogg", "pumpjack-1.ogg"));
        SOUND_MAPPINGS.put("machine_oil_refinery", List.of("oil-refinery.ogg", "oil-refinery-1.ogg"));
        SOUND_MAPPINGS.put("machine_chemical_plant", List.of("chemical-plant.ogg", "chemical-plant-1.ogg"));
        SOUND_MAPPINGS.put("machine_lab", List.of("lab.ogg", "lab-1.ogg"));
        SOUND_MAPPINGS.put("machine_belt", List.of("transport-belt.ogg", "fast-transport-belt.ogg"));
        SOUND_MAPPINGS.put("machine_inserter", List.of("inserter-basic-1.ogg", "inserter-basic-2.ogg", "inserter-fast-1.ogg"));
        SOUND_MAPPINGS.put("gui_click", List.of("gui-click.ogg", "UIClick.ogg"));
        SOUND_MAPPINGS.put("craft_finished", List.of("crafting-finished.ogg"));
        SOUND_MAPPINGS.put("research_completed", List.of("research-completed.ogg"));
    }

    /**
     * Автоматически ищет установленную Factorio в стандартных путях Steam / GOG / Epic / Дисков C..F.
     */
    public static String autoDetectFactorioDataPath() {
        List<String> candidates = new ArrayList<>(List.of(
                "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Factorio\\data",
                "C:\\Program Files\\Steam\\steamapps\\common\\Factorio\\data",
                "D:\\SteamLibrary\\steamapps\\common\\Factorio\\data",
                "E:\\SteamLibrary\\steamapps\\common\\Factorio\\data",
                "F:\\SteamLibrary\\steamapps\\common\\Factorio\\data",
                "D:\\Steam\\steamapps\\common\\Factorio\\data",
                "C:\\Games\\Factorio\\data",
                "D:\\Games\\Factorio\\data",
                "C:\\Program Files\\Factorio\\data",
                "C:\\GOG Games\\Factorio\\data"
        ));

        for (String pathStr : candidates) {
            Path p = Paths.get(pathStr);
            if (Files.isDirectory(p) && (Files.isDirectory(p.resolve("base")) || Files.isDirectory(p.resolve("core")))) {
                return p.toAbsolutePath().toString();
            }
        }
        return "";
    }

    public static String buildAndActivateResourcePack(String rawInputPath) {
        if (rawInputPath == null || rawInputPath.isBlank()) {
            return "§cУкажите путь к папке Factorio/data!";
        }

        Path inputPath = Paths.get(rawInputPath.trim().replace("\"", ""));
        if (Files.isDirectory(inputPath.resolve("data"))) {
            inputPath = inputPath.resolve("data");
        }
        if (!Files.isDirectory(inputPath)) {
            return "§cПапка не найдена: " + inputPath;
        }

        Map<String, Path> foundOggFiles = new HashMap<>();
        try (Stream<Path> walk = Files.walk(inputPath, 6)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .forEach(p -> foundOggFiles.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p));
        } catch (IOException e) {
            return "§cОшибка чтения папки: " + e.getMessage();
        }

        if (foundOggFiles.isEmpty()) {
            return "§cВ указанной папке не найдено ни одного .ogg файла!";
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path packRoot = gameDir.resolve("resourcepacks").resolve(PACK_FOLDER_NAME);
        Path soundsDir = packRoot.resolve("assets").resolve(FactorioCore.MOD_ID).resolve("sounds");

        try {
            Files.createDirectories(soundsDir);

            // 1. Записываем pack.mcmeta (pack_format 34 для Minecraft 1.21.1)
            JsonObject packMeta = new JsonObject();
            JsonObject packSection = new JsonObject();
            packSection.addProperty("pack_format", 34);
            packSection.addProperty("description", "Official Factorio Audio (Local User Import)");
            packMeta.add("pack", packSection);
            Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(packMeta), StandardCharsets.UTF_8);

            // 2. Копируем найденные .ogg и формируем sounds.json
            JsonObject soundsJson = new JsonObject();
            int copiedCount = 0;

            for (Map.Entry<String, List<String>> entry : SOUND_MAPPINGS.entrySet()) {
                String soundEventKey = entry.getKey();
                Path matchedOgg = findBestOggMatch(foundOggFiles, entry.getValue());
                if (matchedOgg == null) continue;

                String targetFileName = soundEventKey + ".ogg";
                Files.copy(matchedOgg, soundsDir.resolve(targetFileName), StandardCopyOption.REPLACE_EXISTING);
                copiedCount++;

                JsonObject eventObj = new JsonObject();
                eventObj.addProperty("subtitle", "subtitles.factoriocore." + soundEventKey);
                JsonArray soundsArr = new JsonArray();
                soundsArr.add(FactorioCore.MOD_ID + ":" + soundEventKey);
                eventObj.add("sounds", soundsArr);
                soundsJson.add(soundEventKey, eventObj);
            }

            if (copiedCount == 0) {
                return "§cНайдены .ogg файлы, но имена не совпали с Factorio/data/base/sound!";
            }

            Path soundsJsonPath = packRoot.resolve("assets").resolve(FactorioCore.MOD_ID).resolve("sounds.json");
            Files.writeString(soundsJsonPath, GSON.toJson(soundsJson), StandardCharsets.UTF_8);

            // 3. Автоматически активируем ресурспак в клиенте и перезагружаем ресурсы!
            MinecraftClient client = MinecraftClient.getInstance();
            ResourcePackManager packManager = client.getResourcePackManager();
            packManager.scanPacks();

            String packProfileId = "file/" + PACK_FOLDER_NAME;
            List<String> enabled = new ArrayList<>(packManager.getEnabledIds());
            if (!enabled.contains(packProfileId)) {
                enabled.add(packProfileId);
                packManager.setEnabledProfiles(enabled);
                client.options.resourcePacks.add(packProfileId);
                client.options.write();
            }
            client.reloadResources();

            return "§aУспех! Импортировано звуков: " + copiedCount + ". Ресурспак включён!";
        } catch (Exception e) {
            e.printStackTrace();
            return "§cОшибка сборки ресурспака: " + e.getMessage();
        }
    }

    private static Path findBestOggMatch(Map<String, Path> index, List<String> candidates) {
        for (String cand : candidates) {
            Path exact = index.get(cand.toLowerCase(Locale.ROOT));
            if (exact != null) return exact;
        }
        for (String cand : candidates) {
            String prefix = cand.toLowerCase(Locale.ROOT).replace(".ogg", "").replace("-1", "");
            for (Map.Entry<String, Path> e : index.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}