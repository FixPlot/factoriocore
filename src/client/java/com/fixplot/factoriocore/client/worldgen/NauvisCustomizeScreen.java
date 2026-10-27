package com.fixplot.factoriocore.client.worldgen;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import com.fixplot.factoriocore.worldgen.NauvisConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NauvisCustomizeScreen extends Screen {

    public static boolean customConfigTouched = false;

    private static final int LEFT_W = 304;
    private static final int PREVIEW_W = 216;
    private static final int PANEL_H = 276;

    private static final int MAP_RES = 100;
    private static final int MAP_PX_SCALE = 2;
    private static final int MAP_SIZE_PX = MAP_RES * MAP_PX_SCALE; // 200x200

    private static final String[] PRESET_KEYS = {
            "gui.factoriocore.nauvis.preset.default",
            "gui.factoriocore.nauvis.preset.rich",
            "gui.factoriocore.nauvis.preset.death",
            "gui.factoriocore.nauvis.preset.rail",
            "gui.factoriocore.nauvis.preset.island"
    };

    private static final String[] PRESET_DESC_KEYS = {
            "gui.factoriocore.nauvis.preset.default.desc",
            "gui.factoriocore.nauvis.preset.rich.desc",
            "gui.factoriocore.nauvis.preset.death.desc",
            "gui.factoriocore.nauvis.preset.rail.desc",
            "gui.factoriocore.nauvis.preset.island.desc"
    };

    private final Screen parent;
    private int currentTab = 0; // 0 = Ресурсы, 1 = Ландшафт, 2 = Враг, 3 = Продвинутые
    private boolean showPreview = true;
    private int mapZoom = 4;    // 1, 2, 4, 8, 16
    private double mapPanX = 0.0;
    private double mapPanZ = 0.0;
    private boolean draggingMap = false;

    private boolean editingSeed = false;
    private String seedText = "";

    private final int[] previewPixels = new int[MAP_RES * MAP_RES];
    private boolean mapDirty = true;

    private record SliderBox(
            int tab,
            int x, int y, int w,
            float min, float max, float step,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {}

    private record CheckboxBox(
            int tab,
            int x, int y,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {}

    private final List<SliderBox> activeSliders = new ArrayList<>();
    private final List<CheckboxBox> activeCheckboxes = new ArrayList<>();
    private SliderBox draggingSlider = null;

    public NauvisCustomizeScreen(Screen parent) {
        super(Text.translatable("gui.factoriocore.nauvis.title"));
        this.parent = parent;
        customConfigTouched = true;
        this.seedText = String.valueOf(Math.abs(NauvisConfig.ACTIVE.worldSeed));
        rebuildMapPreview();
    }

    private int getTotalPanelWidth() {
        return this.showPreview ? (LEFT_W + PREVIEW_W) : LEFT_W;
    }

    private void rebuildMapPreview() {
        int half = MAP_RES / 2;
        int centerBlockX = (int) Math.round(this.mapPanX);
        int centerBlockZ = (int) Math.round(this.mapPanZ);

        for (int gz = 0; gz < MAP_RES; gz++) {
            int wz = centerBlockZ + (gz - half) * this.mapZoom;
            int rowOff = gz * MAP_RES;
            for (int gx = 0; gx < MAP_RES; gx++) {
                int wx = centerBlockX + (gx - half) * this.mapZoom;
                previewPixels[rowOff + gx] = samplePreviewColorAt(wx, wz, this.mapZoom);
            }
        }
        this.mapDirty = false;
    }

    private static int samplePreviewColorAt(int wx, int wz, int zoom) {
        double elev = NauvisChunkGenerator.evaluateElevation(wx, wz);
        if (elev < 0.0) {
            return (elev > -0.09) ? 0xFF1F6E9C : 0xFF124666;
        }

        // 1. Вражеские базы (ярко-красные пятна ульев кусак, как на карте Factorio)
        if (NauvisConfig.hasEnemyBasePreviewAt(wx, wz, elev)) {
            return 0xFFE52424;
        }

        // 2. Месторождения сырой нефти
        if (NauvisConfig.ACTIVE.oilEnabled && NauvisChunkGenerator.hasCrudeOilPreviewAt(wx, wz, elev, Math.max(1, zoom))) {
            return 0xFFD81B60;
        }

        // 3. Месторождения руд
        NauvisChunkGenerator.OreSample ore = NauvisChunkGenerator.sampleOreAt(wx, wz, elev);
        if (ore != null) {
            return switch (ore.oreType()) {
                case 0 -> 0xFF5B9BD5; // Железо
                case 1 -> 0xFFED7D31; // Медь
                case 2 -> 0xFF18181A; // Уголь
                default -> 0xFFD2C290; // Камень
            };
        }

        // 4. Скалы и леса
        if (NauvisConfig.ACTIVE.cliffsEnabled && NauvisChunkGenerator.evaluateCliffHeight(wx, wz, elev) > 0) {
            return 0xFF566573;
        }
        if (NauvisConfig.ACTIVE.treesEnabled && NauvisChunkGenerator.hasForestAt(wx, wz, elev)) {
            return 0xFF1E5622;
        }

        // 5. Биомы поверхности
        return switch (NauvisChunkGenerator.getBiomeAt(wx, wz)) {
            case LUSH_GRASS -> 0xFF3E6B2C;
            case DRY_GRASS -> 0xFF527532;
            case DRY_DIRT -> 0xFF7E5A3B;
            case SAND_DESERT -> 0xFFC2A661;
            case RED_DESERT -> 0xFF9C5238;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        if (this.mapDirty) {
            rebuildMapPreview();
        }

        activeSliders.clear();
        activeCheckboxes.clear();

        int totalW = getTotalPanelWidth();
        int x = (this.width - totalW) / 2;
        int y = (this.height - PANEL_H) / 2;

        // Внешний тёмно-стальной корпус окна Factorio 2.0
        context.fill(x, y, x + totalW, y + PANEL_H, 0xFF111214);
        context.fill(x + 1, y + 1, x + totalW - 1, y + PANEL_H - 1, 0xFF2D2E32);

        // Шапка с ребристыми полосками
        context.fill(x + 3, y + 3, x + totalW - 3, y + 17, 0xFF222326);
        context.drawText(this.textRenderer, this.title, x + 8, y + 6, 0xFFFFDCA0, true);
        int startRibs = x + 14 + this.textRenderer.getWidth(this.title);
        for (int rx = startRibs; rx < x + totalW - 8; rx += 3) {
            context.fill(rx, y + 5, rx + 1, y + 14, 0xFF17181A);
        }

        // Строка Пресета ("По умолчанию ▼") + "Ключ генератора" [ 3743465100 ] [🔀]
        int topRowY = y + 20;
        NauvisConfig cfg = NauvisConfig.ACTIVE;

        // Выпадающий список пресетов (клик переключает пресет)
        int presetX = x + 6;
        int presetW = 112;
        boolean hovPreset = isHovering(mouseX, mouseY, presetX, topRowY, presetW, 14);
        context.fill(presetX, topRowY, presetX + presetW, topRowY + 14, 0xFF141414);
        context.fill(presetX + 1, topRowY + 1, presetX + presetW - 1, topRowY + 13, hovPreset ? 0xFFE59524 : 0xFFD4D6D8);
        String presetName = Text.translatable(PRESET_KEYS[cfg.presetIndex % PRESET_KEYS.length]).getString();
        drawScaledText(context, presetName, presetX + 5, topRowY + 4, 0.72f, 0xFF141414, false);
        drawScaledText(context, "\u25bc", presetX + presetW - 10, topRowY + 4, 0.65f, 0xFF141414, false);

        // Кнопка сброса пресета (красный квадратик со стрелкой как на скрине 022049)
        int resetPresetX = presetX + presetW + 2;
        boolean hovResetP = isHovering(mouseX, mouseY, resetPresetX, topRowY, 14, 14);
        context.fill(resetPresetX, topRowY, resetPresetX + 14, topRowY + 14, 0xFF141414);
        context.fill(resetPresetX + 1, topRowY + 1, resetPresetX + 13, topRowY + 13, hovResetP ? 0xFFB83227 : 0xFF5A1D1D);
        drawScaledTextCentered(context, "\u21ba", resetPresetX + 7, topRowY + 3, 0.75f, 0xFFFF9999, false);

        // Надпись "Ключ генератора" + Поле ввода + Кнопка рандома [🔀]
        String seedLbl = Text.translatable("gui.factoriocore.nauvis.seed_label").getString();
        int lblW = Math.round(this.textRenderer.getWidth(seedLbl) * 0.68f);
        int seedBoxW = 64;
        int randBtnX = x + LEFT_W - 20;
        int seedBoxX = randBtnX - seedBoxW - 2;
        drawScaledText(context, seedLbl, seedBoxX - lblW - 4, topRowY + 4, 0.68f, 0xFFCCCCCC, false);

        context.fill(seedBoxX, topRowY, seedBoxX + seedBoxW, topRowY + 14, this.editingSeed ? 0xFFE59524 : 0xFF141414);
        context.fill(seedBoxX + 1, topRowY + 1, seedBoxX + seedBoxW - 1, topRowY + 13, 0xFF8C8E92);
        String shownSeed = this.editingSeed ? (this.seedText + "_") : String.valueOf(Math.abs(cfg.worldSeed));
        drawScaledText(context, this.textRenderer.trimToWidth(shownSeed, 78), seedBoxX + 4, topRowY + 4, 0.72f, 0xFF111111, false);

        boolean hovRand = isHovering(mouseX, mouseY, randBtnX, topRowY, 14, 14);
        context.fill(randBtnX, topRowY, randBtnX + 14, topRowY + 14, 0xFF141414);
        context.fill(randBtnX + 1, topRowY + 1, randBtnX + 13, topRowY + 13, hovRand ? 0xFFE59524 : 0xFF9EA0A4);
        drawScaledTextCentered(context, "\u2928", randBtnX + 7, topRowY + 3, 0.80f, 0xFF141414, false);

        // Плашка описания выбранного пресета
        int descBoxY = y + 36;
        context.fill(x + 6, descBoxY, x + LEFT_W - 6, descBoxY + 16, 0xFF1D1E21);
        String presetDesc = Text.translatable(PRESET_DESC_KEYS[cfg.presetIndex % PRESET_DESC_KEYS.length]).getString();
        drawScaledText(context, presetDesc, x + 10, descBoxY + 5, 0.68f, 0xFFCCCCCC, false);

        // 4 Вкладки: [Ресурсы] [Ландшафт] [Враг] [Продвинутые]
        int tabY = y + 55;
        String[] tabKeys = {
                "gui.factoriocore.nauvis.tab.resources",
                "gui.factoriocore.nauvis.tab.terrain",
                "gui.factoriocore.nauvis.tab.enemy",
                "gui.factoriocore.nauvis.tab.advanced"
        };
        int tabX = x + 6;
        for (int i = 0; i < 4; i++) {
            String tName = Text.translatable(tabKeys[i]).getString();
            int tw = Math.max(54, Math.round(this.textRenderer.getWidth(tName) * 0.75f) + 14);
            drawFactorioTab(context, tabX, tabY, tw, 15, tName, mouseX, mouseY, this.currentTab == i);
            tabX += tw + 1;
        }

        // Внутренняя область настроек вкладки
        int bodyX = x + 6;
        int bodyY = y + 70;
        int bodyW = LEFT_W - 12;
        int bodyH = PANEL_H - 98;
        context.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, 0xFF161719);
        context.fill(bodyX + 1, bodyY + 1, bodyX + bodyW - 1, bodyY + bodyH - 1, 0xFF28292D);

        if (this.currentTab == 0) {
            renderResourcesTab(context, bodyX, bodyY, bodyW, cfg);
        } else if (this.currentTab == 1) {
            renderTerrainTab(context, bodyX, bodyY, bodyW, cfg, mouseX, mouseY);
        } else if (this.currentTab == 2) {
            renderEnemyTab(context, bodyX, bodyY, bodyW, cfg);
        } else {
            renderAdvancedTab(context, bodyX, bodyY, bodyW, cfg, mouseX, mouseY);
        }

        // Нижняя панель кнопок: [Назад]  [Предпросмотр]  [Играть (Зелёная)] (БЕЗ "Настройки модов"!)
        int botY = y + PANEL_H - 23;
        drawGrayButton(context, x + 6, botY, 64, 18, Text.translatable("gui.factoriocore.nauvis.back").getString(), mouseX, mouseY, false);
        drawGrayButton(context, x + 114, botY, 88, 18, Text.translatable("gui.factoriocore.nauvis.preview_btn").getString(), mouseX, mouseY, this.showPreview);
        drawGreenPlayButton(context, x + 206, botY, 92, 18, Text.translatable("gui.factoriocore.nauvis.play").getString(), mouseX, mouseY);

        // ПРАВАЯ ПАНЕЛЬ: Интерактивная карта предпросмотра (ПКМ/ЛКМ — осмотр, Колёсико — масштаб)
        if (this.showPreview) {
            int mapBoxX = x + LEFT_W + 4;
            int mapBoxY = y + 21;
            context.fill(x + LEFT_W, y + 18, x + LEFT_W + 1, y + PANEL_H - 2, 0xFF141414);
            context.fill(mapBoxX - 2, mapBoxY - 2, mapBoxX + MAP_SIZE_PX + 2, mapBoxY + MAP_SIZE_PX + 2, 0xFF111214);

            for (int gz = 0; gz < MAP_RES; gz++) {
                int py = mapBoxY + gz * MAP_PX_SCALE;
                int rowOff = gz * MAP_RES;
                for (int gx = 0; gx < MAP_RES; gx++) {
                    int px = mapBoxX + gx * MAP_PX_SCALE;
                    context.fill(px, py, px + MAP_PX_SCALE, py + MAP_PX_SCALE, previewPixels[rowOff + gx]);
                }
            }

            // Перекрестие точки спавна (0, 0) с учётом смещения камеры mapPanX / mapPanZ
            int spawnPxX = mapBoxX + MAP_SIZE_PX / 2 - (int) Math.round((this.mapPanX / this.mapZoom) * MAP_PX_SCALE);
            int spawnPxY = mapBoxY + MAP_SIZE_PX / 2 - (int) Math.round((this.mapPanZ / this.mapZoom) * MAP_PX_SCALE);
            if (spawnPxX >= mapBoxX + 3 && spawnPxX <= mapBoxX + MAP_SIZE_PX - 4
                    && spawnPxY >= mapBoxY + 3 && spawnPxY <= mapBoxY + MAP_SIZE_PX - 4) {
                context.fill(spawnPxX - 3, spawnPxY, spawnPxX + 4, spawnPxY + 1, 0xFFFFFFFF);
                context.fill(spawnPxX, spawnPxY - 3, spawnPxX + 1, spawnPxY + 4, 0xFFFFFFFF);
            }

            // Верхний оверлей масштаба: [- ] Zoom 1:4 [ +] [⌖]
            context.fill(mapBoxX + 3, mapBoxY + 3, mapBoxX + 96, mapBoxY + 15, 0xD0141414);
            drawScaledTextCentered(context, "-", mapBoxX + 9, mapBoxY + 5, 0.80f, 0xFFFFCC66, false);
            String zoomStr = Text.translatable("gui.factoriocore.nauvis.zoom", String.valueOf(this.mapZoom)).getString();
            drawScaledTextCentered(context, zoomStr, mapBoxX + 43, mapBoxY + 5, 0.70f, 0xFFE6E6E6, false);
            drawScaledTextCentered(context, "+", mapBoxX + 76, mapBoxY + 5, 0.80f, 0xFFFFCC66, false);
            drawScaledTextCentered(context, "\u2316", mapBoxX + 89, mapBoxY + 5, 0.75f, 0xFF80D8FF, false);

            // Подсказка управления картой и легенда под картой
            int legY = mapBoxY + MAP_SIZE_PX + 4;
            int lx = mapBoxX;
            lx = drawLegendItem(context, lx, legY, 0xFF5B9BD5, Text.translatable("gui.factoriocore.nauvis.iron_short").getString());
            lx = drawLegendItem(context, lx, legY, 0xFFED7D31, Text.translatable("gui.factoriocore.nauvis.copper_short").getString());
            lx = drawLegendItem(context, lx, legY, 0xFF18181A, Text.translatable("gui.factoriocore.nauvis.coal_short").getString());
            lx = drawLegendItem(context, lx, legY, 0xFFD2C290, Text.translatable("gui.factoriocore.nauvis.stone_short").getString());
            lx = drawLegendItem(context, lx, legY, 0xFFD81B60, Text.translatable("gui.factoriocore.nauvis.oil_short").getString());
            drawLegendItem(context, lx, legY, 0xFFE52424, Text.translatable("gui.factoriocore.nauvis.enemy_short").getString());

            String panHint = Text.translatable("gui.factoriocore.nauvis.pan_hint").getString();
            drawScaledTextCentered(context, panHint, mapBoxX + MAP_SIZE_PX / 2, legY + 11, 0.65f, 0xFF999B9F, false);
        }
    }

    // =========================================================================
    // ВКЛАДКА 0: РЕСУРСЫ (Таблица 3 колонки: Частота | Размер | Богатство)
    // =========================================================================
    private void renderResourcesTab(DrawContext context, int bx, int by, int bw, NauvisConfig cfg) {
        // Шапка колонок таблицы
        context.fill(bx + 2, by + 2, bx + bw - 2, by + 16, 0xFF202124);
        int col1X = bx + 118;
        int col2X = bx + 176;
        int col3X = bx + 234;
        int sliderW = 52;

        drawColumnHeaderWithInfo(context, col1X + sliderW / 2, by + 5, Text.translatable("gui.factoriocore.nauvis.col.freq").getString());
        drawColumnHeaderWithInfo(context, col2X + sliderW / 2, by + 5, Text.translatable("gui.factoriocore.nauvis.col.size").getString());
        drawColumnHeaderWithInfo(context, col3X + sliderW / 2, by + 5, Text.translatable("gui.factoriocore.nauvis.col.rich").getString());

        int ry = by + 18;
        int rowH = 26;

        drawResourceRow(context, bx, ry, bw, new ItemStack(ModBlocks.IRON_ORE_DEPOSIT),
                Text.translatable("block.factoriocore.iron_ore_deposit").getString(),
                () -> cfg.ironEnabled, v -> cfg.ironEnabled = v,
                () -> cfg.ironFreq, v -> cfg.ironFreq = v,
                () -> cfg.ironSize, v -> cfg.ironSize = v,
                () -> cfg.ironRichness, v -> cfg.ironRichness = v);
        ry += rowH;

        drawResourceRow(context, bx, ry, bw, new ItemStack(ModBlocks.COPPER_ORE_DEPOSIT),
                Text.translatable("block.factoriocore.copper_ore_deposit").getString(),
                () -> cfg.copperEnabled, v -> cfg.copperEnabled = v,
                () -> cfg.copperFreq, v -> cfg.copperFreq = v,
                () -> cfg.copperSize, v -> cfg.copperSize = v,
                () -> cfg.copperRichness, v -> cfg.copperRichness = v);
        ry += rowH;

        drawResourceRow(context, bx, ry, bw, new ItemStack(ModBlocks.STONE_DEPOSIT),
                Text.translatable("block.factoriocore.stone_deposit").getString(),
                () -> cfg.stoneEnabled, v -> cfg.stoneEnabled = v,
                () -> cfg.stoneFreq, v -> cfg.stoneFreq = v,
                () -> cfg.stoneSize, v -> cfg.stoneSize = v,
                () -> cfg.stoneRichness, v -> cfg.stoneRichness = v);
        ry += rowH;

        drawResourceRow(context, bx, ry, bw, new ItemStack(ModBlocks.COAL_DEPOSIT),
                Text.translatable("block.factoriocore.coal_deposit").getString(),
                () -> cfg.coalEnabled, v -> cfg.coalEnabled = v,
                () -> cfg.coalFreq, v -> cfg.coalFreq = v,
                () -> cfg.coalSize, v -> cfg.coalSize = v,
                () -> cfg.coalRichness, v -> cfg.coalRichness = v);
        ry += rowH;

        drawResourceRow(context, bx, ry, bw, new ItemStack(ModBlocks.CRUDE_OIL_DEPOSIT),
                Text.translatable("block.factoriocore.crude_oil_deposit").getString(),
                () -> cfg.oilEnabled, v -> cfg.oilEnabled = v,
                () -> cfg.oilFreq, v -> cfg.oilFreq = v,
                () -> cfg.oilSize, v -> cfg.oilSize = v,
                () -> cfg.oilRichness, v -> cfg.oilRichness = v);
    }

    private void drawResourceRow(DrawContext context, int bx, int ry, int bw, ItemStack icon, String name,
                                 Supplier<Boolean> enGet, Consumer<Boolean> enSet,
                                 Supplier<Float> fGet, Consumer<Float> fSet,
                                 Supplier<Float> sGet, Consumer<Float> sSet,
                                 Supplier<Float> rGet, Consumer<Float> rSet) {
        context.fill(bx + 2, ry, bx + bw - 2, ry + 25, 0xFF232428);
        context.fill(bx + 2, ry + 24, bx + bw - 2, ry + 25, 0xFF18191B);

        // Вертикальные разделители колонок (как на скрине 022049)
        context.fill(bx + 114, ry, bx + 115, ry + 24, 0xFF18191B);
        context.fill(bx + 172, ry, bx + 173, ry + 24, 0xFF18191B);
        context.fill(bx + 230, ry, bx + 231, ry + 24, 0xFF18191B);

        registerAndDrawCheckbox(context, 0, bx + 6, ry + 8, enGet, enSet);

        context.getMatrices().push();
        context.getMatrices().translate(bx + 18, ry + 5, 100.0f);
        context.getMatrices().scale(0.85f, 0.85f, 1.0f);
        context.drawItem(icon, 0, 0);
        context.getMatrices().pop();

        String shortName = this.textRenderer.trimToWidth(name, 110);
        drawScaledText(context, shortName, bx + 34, ry + 9, 0.70f, enGet.get() ? 0xFFE6E6E6 : 0xFF777777, false);

        registerAndDrawNotchedSlider(context, 0, bx + 118, ry + 8, 50, 0.1f, 2.5f, 0.1f, fGet, fSet);
        registerAndDrawNotchedSlider(context, 0, bx + 176, ry + 8, 50, 0.1f, 2.5f, 0.1f, sGet, sSet);
        registerAndDrawNotchedSlider(context, 0, bx + 234, ry + 8, 50, 0.1f, 2.5f, 0.1f, rGet, rSet);
    }

    // =========================================================================
    // ВКЛАДКА 1: ЛАНДШАФТ (как на скрине 022052.jpg)
    // =========================================================================
    private void renderTerrainTab(DrawContext context, int bx, int by, int bw, NauvisConfig cfg, int mouseX, int mouseY) {
        int ry = by + 3;
        // Строка "Тип карты": [Уровень моря как на Наувисе / Остров ▼]
        context.fill(bx + 2, ry, bx + bw - 2, ry + 18, 0xFF232428);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.map_type").getString(), bx + 6, ry + 6, 0.72f, 0xFFE6E6E6, false);

        int ddX = bx + 132;
        int ddW = bw - 138;
        boolean hovMapType = isHovering(mouseX, mouseY, ddX, ry + 2, ddW, 14);
        context.fill(ddX, ry + 2, ddX + ddW, ry + 16, 0xFF141414);
        context.fill(ddX + 1, ry + 3, ddX + ddW - 1, ry + 15, hovMapType ? 0xFFE59524 : 0xFFD4D6D8);
        String mapTypeStr = Text.translatable(cfg.mapType == 0
                ? "gui.factoriocore.nauvis.map_type.nauvis"
                : "gui.factoriocore.nauvis.map_type.island").getString();
        drawScaledText(context, this.textRenderer.trimToWidth(mapTypeStr, 200), ddX + 5, ry + 6, 0.68f, 0xFF141414, false);
        ry += 20;

        int col1X = bx + 174;
        int col2X = bx + 234;
        int sw = 50;

        // Заголовок секции 1: Масштаб | Покрытие
        context.fill(bx + 2, ry, bx + bw - 2, ry + 12, 0xFF1E1F22);
        drawColumnHeaderWithInfo(context, col1X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.scale").getString());
        drawColumnHeaderWithInfo(context, col2X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.coverage").getString());
        ry += 13;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.water").getString(),
                () -> cfg.waterEnabled, v -> cfg.waterEnabled = v,
                () -> cfg.waterScale, v -> cfg.waterScale = v, 0.25f, 2.0f,
                () -> cfg.waterCoverage, v -> cfg.waterCoverage = v, 0.0f, 2.0f);
        ry += 19;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.trees").getString(),
                () -> cfg.treesEnabled, v -> cfg.treesEnabled = v,
                () -> cfg.treeScale, v -> cfg.treeScale = v, 0.25f, 2.0f,
                () -> cfg.treeCoverage, v -> cfg.treeCoverage = v, 0.0f, 2.0f);
        ry += 19;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.rocks").getString(),
                () -> cfg.rocksEnabled, v -> cfg.rocksEnabled = v,
                () -> cfg.boulderFreq, v -> cfg.boulderFreq = v, 0.0f, 2.0f,
                () -> cfg.boulderSize, v -> cfg.boulderSize = v, 0.25f, 2.0f);
        ry += 20;

        // Заголовок секции 2: Скалы (Частота | Непрерывность)
        context.fill(bx + 2, ry, bx + bw - 2, ry + 12, 0xFF1E1F22);
        drawColumnHeaderWithInfo(context, col1X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.freq").getString());
        drawColumnHeaderWithInfo(context, col2X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.continuity").getString());
        ry += 13;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.cliffs").getString(),
                () -> cfg.cliffsEnabled, v -> cfg.cliffsEnabled = v,
                () -> cfg.cliffFreq, v -> cfg.cliffFreq = v, 0.0f, 2.0f,
                () -> cfg.cliffContinuity, v -> cfg.cliffContinuity = v, 0.25f, 2.0f);
        ry += 20;

        // Заголовок секции 3: Климат (Масштаб | Значение)
        context.fill(bx + 2, ry, bx + bw - 2, ry + 12, 0xFF1E1F22);
        drawColumnHeaderWithInfo(context, col1X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.scale").getString());
        drawColumnHeaderWithInfo(context, col2X + sw / 2, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.bias").getString());
        ry += 13;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.moisture").getString(),
                null, null,
                () -> cfg.moistureScale, v -> cfg.moistureScale = v, 0.5f, 2.0f,
                () -> cfg.moistureBias, v -> cfg.moistureBias = v, -0.5f, 0.5f);
        ry += 19;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.row.aux").getString(),
                null, null,
                () -> cfg.auxScale, v -> cfg.auxScale = v, 0.5f, 2.0f,
                () -> cfg.auxBias, v -> cfg.auxBias = v, -0.5f, 0.5f);
    }

    private void drawTwoColTerrainRow(DrawContext context, int bx, int ry, int bw, String label,
                                      Supplier<Boolean> cbGet, Consumer<Boolean> cbSet,
                                      Supplier<Float> s1Get, Consumer<Float> s1Set, float min1, float max1,
                                      Supplier<Float> s2Get, Consumer<Float> s2Set, float min2, float max2) {
        context.fill(bx + 2, ry, bx + bw - 2, ry + 18, 0xFF232428);
        context.fill(bx + 170, ry, bx + 171, ry + 18, 0xFF18191B);
        context.fill(bx + 230, ry, bx + 231, ry + 18, 0xFF18191B);

        int textX = bx + 6;
        if (cbGet != null && cbSet != null) {
            registerAndDrawCheckbox(context, 1, bx + 6, ry + 5, cbGet, cbSet);
            textX = bx + 18;
        }
        drawScaledText(context, label, textX, ry + 6, 0.70f, 0xFFE6E6E6, false);

        registerAndDrawNotchedSlider(context, 1, bx + 174, ry + 5, 50, min1, max1, 0.05f, s1Get, s1Set);
        registerAndDrawNotchedSlider(context, 1, bx + 234, ry + 5, 50, min2, max2, 0.05f, s2Get, s2Set);
    }

    // =========================================================================
    // ВКЛАДКА 2: ВРАГ (как на скрине 022055.jpg)
    // =========================================================================
    private void renderEnemyTab(DrawContext context, int bx, int by, int bw, NauvisConfig cfg) {
        int ry = by + 3;
        int col1X = bx + 174;
        int col2X = bx + 234;

        context.fill(bx + 2, ry, bx + bw - 2, ry + 12, 0xFF1E1F22);
        drawColumnHeaderWithInfo(context, col1X + 25, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.freq").getString());
        drawColumnHeaderWithInfo(context, col2X + 25, ry + 3, Text.translatable("gui.factoriocore.nauvis.col.size").getString());
        ry += 13;

        drawTwoColTerrainRow(context, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.enemy_bases").getString(),
                () -> cfg.enemyBasesEnabled, v -> cfg.enemyBasesEnabled = v,
                () -> cfg.enemyBaseFreq, v -> cfg.enemyBaseFreq = v, 0.0f, 2.5f,
                () -> cfg.enemyBaseSize, v -> cfg.enemyBaseSize = v, 0.0f, 2.5f);
        ry += 20;

        // Чекбоксы "Без врагов" и "Мирный режим" + ползунок "Размер начальной области"
        context.fill(bx + 2, ry, bx + bw - 2, ry + 16, 0xFF232428);
        registerAndDrawCheckbox(context, 2, bx + 6, ry + 4, () -> cfg.noEnemies, v -> cfg.noEnemies = v);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.no_enemies").getString(), bx + 18, ry + 5, 0.68f, 0xFFE6E6E6, false);

        registerAndDrawCheckbox(context, 2, bx + 115, ry + 4, () -> cfg.peacefulMode, v -> cfg.peacefulMode = v);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.peaceful").getString(), bx + 127, ry + 5, 0.68f, 0xFFE6E6E6, false);
        ry += 18;

        context.fill(bx + 2, ry, bx + bw - 2, ry + 18, 0xFF232428);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.starting_area").getString(), bx + 6, ry + 6, 0.70f, 0xFFE6E6E6, false);
        registerAndDrawNotchedSlider(context, 2, bx + 204, ry + 5, 78, 0.25f, 2.5f, 0.25f, () -> cfg.startingAreaSize, v -> cfg.startingAreaSize = v);
        ry += 21;

        // Секция "Экспансия врагов"
        registerAndDrawCheckbox(context, 2, bx + 6, ry + 1, () -> cfg.enemyExpansionEnabled, v -> cfg.enemyExpansionEnabled = v);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.expansion_header").getString(), bx + 18, ry + 2, 0.72f, 0xFFFFCC66, true);
        ry += 12;

        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.exp_min_dist").getString(),
                2, 10, 1, () -> (float) cfg.expansionMinDist, v -> cfg.expansionMinDist = Math.round(v), String.valueOf(cfg.expansionMinDist));
        ry += 14;
        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.exp_max_dist").getString(),
                2, 15, 1, () -> (float) cfg.expansionMaxDist, v -> cfg.expansionMaxDist = Math.round(v), String.valueOf(cfg.expansionMaxDist));
        ry += 14;
        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.exp_group").getString(),
                1, 25, 1, () -> (float) cfg.expansionGroupSize, v -> cfg.expansionGroupSize = Math.round(v), String.valueOf(cfg.expansionGroupSize));
        ry += 16;

        // Секция "Эволюция"
        registerAndDrawCheckbox(context, 2, bx + 6, ry + 1, () -> cfg.evolutionEnabled, v -> cfg.evolutionEnabled = v);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.evolution_header").getString(), bx + 18, ry + 2, 0.72f, 0xFFFFCC66, true);
        ry += 12;

        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.evo_time").getString(),
                0, 200, 5, () -> (float) cfg.evolutionTimeFactor, v -> cfg.evolutionTimeFactor = Math.round(v), String.valueOf(cfg.evolutionTimeFactor));
        ry += 14;
        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.evo_destroy").getString(),
                0, 500, 10, () -> (float) cfg.evolutionDestroyFactor, v -> cfg.evolutionDestroyFactor = Math.round(v), String.valueOf(cfg.evolutionDestroyFactor));
        ry += 14;
        drawValueSliderRow(context, 2, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.evo_pollution").getString(),
                0, 50, 1, () -> (float) cfg.evolutionPollutionFactor, v -> cfg.evolutionPollutionFactor = Math.round(v), String.valueOf(cfg.evolutionPollutionFactor));
    }

    // =========================================================================
    // ВКЛАДКА 3: ПРОДВИНУТЫЕ (как на скрине 022057.jpg)
    // =========================================================================
    private void renderAdvancedTab(DrawContext context, int bx, int by, int bw, NauvisConfig cfg, int mouseX, int mouseY) {
        int ry = by + 5;

        // Секция "Карта"
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.adv_map").getString(), bx + 6, ry, 0.75f, 0xFFFFCC66, true);
        ry += 11;
        context.fill(bx + 2, ry, bx + bw - 2, ry + 18, 0xFF232428);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.adv_infinite").getString(), bx + 8, ry + 6, 0.70f, 0xFFCCCCCC, false);
        ry += 22;

        // Секция "Технологии" -> Множитель стоимости
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.adv_tech").getString(), bx + 6, ry, 0.75f, 0xFFFFCC66, true);
        ry += 11;
        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.adv_tech_mult").getString(),
                1, 10, 1, () -> (float) cfg.techPriceMultiplier, v -> cfg.techPriceMultiplier = Math.max(1, Math.round(v)),
                cfg.techPriceMultiplier + "x");
        ry += 20;

        // Секция "Загрязнение"
        registerAndDrawCheckbox(context, 3, bx + 6, ry + 1, () -> cfg.pollutionEnabled, v -> cfg.pollutionEnabled = v);
        drawScaledText(context, Text.translatable("gui.factoriocore.nauvis.adv_pollution").getString(), bx + 18, ry + 2, 0.75f, 0xFFFFCC66, true);
        ry += 14;

        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.poll_absorb").getString(),
                0.1f, 3.0f, 0.1f, () -> cfg.pollutionAbsorptionMod, v -> cfg.pollutionAbsorptionMod = v,
                Math.round(cfg.pollutionAbsorptionMod * 100) + "%");
        ry += 16;

        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.poll_attack").getString(),
                0.1f, 3.0f, 0.1f, () -> cfg.pollutionAttackCostMod, v -> cfg.pollutionAttackCostMod = v,
                Math.round(cfg.pollutionAttackCostMod * 100) + "%");
        ry += 16;

        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.poll_min_tree").getString(),
                10, 200, 10, () -> (float) cfg.pollutionMinTreeDamage, v -> cfg.pollutionMinTreeDamage = Math.round(v),
                String.valueOf(cfg.pollutionMinTreeDamage));
        ry += 16;

        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.poll_per_tree").getString(),
                1, 50, 1, () -> (float) cfg.pollutionAbsorbedPerTree, v -> cfg.pollutionAbsorbedPerTree = Math.round(v),
                String.valueOf(cfg.pollutionAbsorbedPerTree));
        ry += 16;

        drawValueSliderRow(context, 3, bx, ry, bw, Text.translatable("gui.factoriocore.nauvis.poll_diffusion").getString(),
                0, 10, 1, () -> (float) cfg.pollutionDiffusionPercent, v -> cfg.pollutionDiffusionPercent = Math.round(v),
                cfg.pollutionDiffusionPercent + "%");
    }

    private void drawValueSliderRow(DrawContext context, int tab, int bx, int ry, int bw, String label,
                                    float min, float max, float step,
                                    Supplier<Float> getter, Consumer<Float> setter, String boxValueText) {
        drawScaledText(context, label, bx + 6, ry + 3, 0.68f, 0xFFE6E6E6, false);
        registerAndDrawNotchedSlider(context, tab, bx + 174, ry + 2, 68, min, max, step, getter, setter);

        // Светло-серая плашка со значением справа (как на скринах 022055 и 022057)
        int valBoxX = bx + 248;
        int valBoxW = 38;
        context.fill(valBoxX, ry, valBoxX + valBoxW, ry + 11, 0xFF141414);
        context.fill(valBoxX + 1, ry + 1, valBoxX + valBoxW - 1, ry + 10, 0xFF888A8E);
        drawScaledTextCentered(context, boxValueText, valBoxX + valBoxW / 2, ry + 3, 0.65f, 0xFF111111, false);
    }

    private void registerAndDrawNotchedSlider(DrawContext context, int tab, int sx, int sy, int sw,
                                              float min, float max, float step,
                                              Supplier<Float> getter, Consumer<Float> setter) {
        activeSliders.add(new SliderBox(tab, sx, sy, sw, min, max, step, getter, setter));

        // Риски делений над дорожкой ползунка (как в оригинальной Factorio!)
        for (int i = 0; i <= 8; i++) {
            int tx = sx + 2 + Math.round((i / 8.0f) * (sw - 5));
            context.fill(tx, sy - 2, tx + 1, sy, 0xFF111111);
        }

        // Тёмная дорожка + оранжевое заполнение слева от бегунка
        context.fill(sx, sy + 1, sx + sw, sy + 5, 0xFF111214);
        float val = getter.get();
        float ratio = MathHelper.clamp((val - min) / (max - min), 0.0f, 1.0f);
        int knobX = sx + Math.round(ratio * (sw - 6));
        if (knobX > sx + 1) {
            context.fill(sx + 1, sy + 2, knobX, sy + 4, 0xFFE59524);
        }

        // Светло-серый металлический бегунок Factorio
        context.fill(knobX, sy - 1, knobX + 6, sy + 7, 0xFF141414);
        context.fill(knobX + 1, sy, knobX + 5, sy + 6, 0xFFC2C4C8);
    }

    private void registerAndDrawCheckbox(DrawContext context, int tab, int cx, int cy,
                                         Supplier<Boolean> getter, Consumer<Boolean> setter) {
        activeCheckboxes.add(new CheckboxBox(tab, cx, cy, getter, setter));
        boolean checked = getter.get();
        context.fill(cx, cy, cx + 9, cy + 9, 0xFF111111);
        context.fill(cx + 1, cy + 1, cx + 8, cy + 8, checked ? 0xFFE59524 : 0xFF424448);
        if (checked) {
            context.fill(cx + 2, cy + 4, cx + 4, cy + 6, 0xFF141414);
            context.fill(cx + 4, cy + 2, cx + 7, cy + 5, 0xFF141414);
        }
    }

    private void drawColumnHeaderWithInfo(DrawContext context, int centerX, int y, String title) {
        int tw = Math.round(this.textRenderer.getWidth(title) * 0.68f);
        int startX = centerX - (tw + 8) / 2;
        drawScaledText(context, title, startX, y, 0.68f, 0xFFE6E6E6, true);
        int ix = startX + tw + 2;
        context.fill(ix, y, ix + 6, y + 6, 0xFF3FA9F5);
        drawScaledText(context, "i", ix + 2, y + 1, 0.55f, 0xFF111111, false);
    }

    private int drawLegendItem(DrawContext context, int lx, int ly, int color, String name) {
        context.fill(lx, ly + 1, lx + 6, ly + 7, 0xFF111111);
        context.fill(lx + 1, ly + 2, lx + 5, ly + 6, color);
        drawScaledText(context, name, lx + 8, ly + 1, 0.68f, 0xFFDDDDDD, false);
        return lx + 11 + Math.round(this.textRenderer.getWidth(name) * 0.68f);
    }

    private void drawFactorioTab(DrawContext context, int bx, int by, int bw, int bh, String label, int mx, int my, boolean active) {
        boolean hov = isHovering(mx, my, bx, by, bw, bh);
        int bg = active ? 0xFF4A4C52 : (hov ? 0xFF3A3C40 : 0xFF222326);
        context.fill(bx, by, bx + bw, by + bh, 0xFF111214);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - (active ? 0 : 1), bg);
        drawScaledTextCentered(context, label, bx + bw / 2, by + 4, 0.72f, active ? 0xFFFFFFFF : 0xFFBBBBBB, true);
    }

    private void drawGrayButton(DrawContext context, int bx, int by, int bw, int bh, String label, int mx, int my, boolean active) {
        boolean hov = isHovering(mx, my, bx, by, bw, bh);
        int bg = active ? 0xFFB8BABC : (hov ? 0xFFD4D6D8 : 0xFF9EA0A4);
        context.fill(bx, by, bx + bw, by + bh, 0xFF111214);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);
        drawScaledTextCentered(context, label, bx + bw / 2, by + 6, 0.75f, 0xFF141414, false);
    }

    private void drawGreenPlayButton(DrawContext context, int bx, int by, int bw, int bh, String label, int mx, int my) {
        boolean hov = isHovering(mx, my, bx, by, bw, bh);
        int bg = hov ? 0xFF66BB6A : 0xFF4CAF50;
        context.fill(bx, by, bx + bw, by + bh, 0xFF111214);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + 3, 0xFF81C784);
        drawScaledTextCentered(context, label, bx + bw / 2, by + 6, 0.80f, 0xFF111214, false);
    }

    private boolean isHovering(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private void drawScaledText(DrawContext context, String text, int x, int y, float scale, int color, boolean shadow) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 200.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(this.textRenderer, text, 0, 0, color, shadow);
        context.getMatrices().pop();
    }

    private void drawScaledTextCentered(DrawContext context, String text, int centerX, int y, float scale, int color, boolean shadow) {
        int w = Math.round(this.textRenderer.getWidth(text) * scale);
        drawScaledText(context, text, centerX - w / 2, y, scale, color, shadow);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalW = getTotalPanelWidth();
        int x = (this.width - totalW) / 2;
        int y = (this.height - PANEL_H) / 2;
        this.editingSeed = false;

        int topRowY = y + 20;
        // 1. Клик по выпадающему списку пресетов
        if (isHovering(mouseX, mouseY, x + 6, topRowY, 112, 14)) {
            int nextPreset = (NauvisConfig.ACTIVE.presetIndex + (button == 1 ? PRESET_KEYS.length - 1 : 1)) % PRESET_KEYS.length;
            NauvisConfig.ACTIVE.applyPreset(nextPreset);
            this.mapDirty = true;
            playClick();
            return true;
        }
        // Сброс к "По умолчанию"
        if (isHovering(mouseX, mouseY, x + 120, topRowY, 14, 14)) {
            NauvisConfig.ACTIVE.resetDefaults();
            this.mapDirty = true;
            playClick();
            return true;
        }
        // Поле ввода Seed
        int randBtnX = x + LEFT_W - 20;
        int seedBoxX = randBtnX - 66;
        if (isHovering(mouseX, mouseY, seedBoxX, topRowY, 64, 14)) {
            this.editingSeed = true;
            if (button == 1) {
                this.seedText = "";
            }
            return true;
        }
        // Кнопка случайного Seed [🔀]
        if (isHovering(mouseX, mouseY, randBtnX, topRowY, 14, 14)) {
            NauvisConfig.ACTIVE.randomizeSeed();
            this.seedText = String.valueOf(Math.abs(NauvisConfig.ACTIVE.worldSeed));
            this.mapDirty = true;
            playClick();
            return true;
        }

        // 2. Переключение 4 вкладок
        int tabY = y + 55;
        String[] tabKeys = {
                "gui.factoriocore.nauvis.tab.resources",
                "gui.factoriocore.nauvis.tab.terrain",
                "gui.factoriocore.nauvis.tab.enemy",
                "gui.factoriocore.nauvis.tab.advanced"
        };
        int tabX = x + 6;
        for (int i = 0; i < 4; i++) {
            String tName = Text.translatable(tabKeys[i]).getString();
            int tw = Math.max(54, Math.round(this.textRenderer.getWidth(tName) * 0.75f) + 14);
            if (isHovering(mouseX, mouseY, tabX, tabY, tw, 15)) {
                this.currentTab = i;
                playClick();
                return true;
            }
            tabX += tw + 1;
        }

        // 3. Переключение типа карты (Наувис / Остров) во вкладке Ландшафт
        if (this.currentTab == 1 && isHovering(mouseX, mouseY, x + 138, y + 75, LEFT_W - 150, 14)) {
            NauvisConfig.ACTIVE.mapType = (NauvisConfig.ACTIVE.mapType == 0) ? 1 : 0;
            this.mapDirty = true;
            playClick();
            return true;
        }

        // 4. Клик по чекбоксам текущей вкладки
        for (CheckboxBox cb : activeCheckboxes) {
            if (cb.tab == this.currentTab && isHovering(mouseX, mouseY, cb.x - 1, cb.y - 1, 11, 11)) {
                cb.setter.accept(!cb.getter.get());
                this.mapDirty = true;
                playClick();
                return true;
            }
        }

        // 5. Клик по ползункам текущей вкладки
        for (SliderBox s : activeSliders) {
            if (s.tab == this.currentTab && isHovering(mouseX, mouseY, s.x - 3, s.y - 3, s.w + 6, 12)) {
                this.draggingSlider = s;
                updateSliderFromMouse(s, mouseX);
                return true;
            }
        }

        // 6. Кнопки нижней панели: [Назад]  [Предпросмотр]  [Играть]
        int botY = y + PANEL_H - 23;
        if (isHovering(mouseX, mouseY, x + 6, botY, 64, 18) || isHovering(mouseX, mouseY, x + 206, botY, 92, 18)) {
            playClick();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
            return true;
        }
        if (isHovering(mouseX, mouseY, x + 114, botY, 88, 18)) {
            this.showPreview = !this.showPreview;
            playClick();
            return true;
        }

        // 7. Клик по карте предпросмотра: кнопки [-], [+], [⌖] ИЛИ зажатие ПКМ/ЛКМ для панорамирования!
        if (this.showPreview) {
            int mapBoxX = x + LEFT_W + 4;
            int mapBoxY = y + 21;
            if (isHovering(mouseX, mouseY, mapBoxX + 3, mapBoxY + 3, 16, 12)) {
                if (this.mapZoom < 16) {
                    this.mapZoom *= 2;
                    this.mapDirty = true;
                    playClick();
                }
                return true;
            }
            if (isHovering(mouseX, mouseY, mapBoxX + 68, mapBoxY + 3, 14, 12)) {
                if (this.mapZoom > 1) {
                    this.mapZoom /= 2;
                    this.mapDirty = true;
                    playClick();
                }
                return true;
            }
            if (isHovering(mouseX, mouseY, mapBoxX + 82, mapBoxY + 3, 14, 12)) {
                this.mapPanX = 0.0;
                this.mapPanZ = 0.0;
                this.mapDirty = true;
                playClick();
                return true;
            }
            if (isHovering(mouseX, mouseY, mapBoxX, mapBoxY, MAP_SIZE_PX, MAP_SIZE_PX)) {
                this.draggingMap = true;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateSliderFromMouse(SliderBox s, double mouseX) {
        float ratio = MathHelper.clamp((float) (mouseX - s.x) / (float) s.w, 0.0f, 1.0f);
        float raw = s.min + ratio * (s.max - s.min);
        float stepped = Math.round(raw / s.step) * s.step;
        s.setter.accept(MathHelper.clamp(stepped, s.min, s.max));
        this.mapDirty = true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingSlider != null) {
            updateSliderFromMouse(this.draggingSlider, mouseX);
            return true;
        }
        if (this.draggingMap) {
            // Плавно двигаем карту предпросмотра мышью (ПКМ или ЛКМ)
            double blocksPerPixel = (double) this.mapZoom / MAP_PX_SCALE;
            this.mapPanX -= deltaX * blocksPerPixel;
            this.mapPanZ -= deltaY * blocksPerPixel;
            this.mapDirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingSlider = null;
        this.draggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.showPreview) {
            int totalW = getTotalPanelWidth();
            int x = (this.width - totalW) / 2;
            int y = (this.height - PANEL_H) / 2;
            int mapBoxX = x + LEFT_W + 4;
            int mapBoxY = y + 21;
            if (isHovering(mouseX, mouseY, mapBoxX, mapBoxY, MAP_SIZE_PX, MAP_SIZE_PX)) {
                if (verticalAmount > 0 && this.mapZoom > 1) {
                    this.mapZoom /= 2;
                    this.mapDirty = true;
                } else if (verticalAmount < 0 && this.mapZoom < 16) {
                    this.mapZoom *= 2;
                    this.mapDirty = true;
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.editingSeed && (Character.isDigit(chr) || (chr == '-' && this.seedText.isEmpty()))) {
            if (this.seedText.length() < 18) {
                this.seedText += chr;
                applyTypedSeed();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.editingSeed) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.seedText.isEmpty()) {
                this.seedText = this.seedText.substring(0, this.seedText.length() - 1);
                applyTypedSeed();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.editingSeed = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applyTypedSeed() {
        try {
            if (!this.seedText.isBlank() && !this.seedText.equals("-")) {
                NauvisConfig.ACTIVE.worldSeed = Long.parseLong(this.seedText);
                this.mapDirty = true;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void playClick() {
        if (this.client != null && this.client.player != null) {
            this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.2f);
        }
    }
}