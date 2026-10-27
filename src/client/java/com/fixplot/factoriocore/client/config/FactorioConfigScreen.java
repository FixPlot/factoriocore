package com.fixplot.factoriocore.client.config;

import com.fixplot.factoriocore.client.FactorioCoreClient;
import com.fixplot.factoriocore.client.hud.AltModeRenderer;
import com.fixplot.factoriocore.client.hud.FactorioHudOverlay;
import com.fixplot.factoriocore.client.sound.FactorioSoundPackGenerator;
import com.fixplot.factoriocore.config.FactorioConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FactorioConfigScreen extends Screen {

    private static final int PANEL_W = 480;
    private static final int PANEL_H = 260;

    private static final int[] COLOR_PRESETS = { 0x26B4FF, 0x33E68C, 0x26FF40, 0xFFE026, 0xE08538, 0xFF2626, 0xB84DFF, 0xFFFFFF };

    private final Screen parent;
    private int currentTab = 0;

    private record ToggleEntry(int tab, int rx, int ry, String label, String desc, Supplier<Boolean> getter, Consumer<Boolean> setter) {}
    private record FloatSliderEntry(int tab, int rx, int ry, String label, Supplier<Float> getter, Consumer<Float> setter, float min, float max) {}
    private record ColorEntry(int ry, String label, Supplier<Integer> getter, Consumer<Integer> setter) {}
    private record KeybindEntry(int ry, String label, KeyBinding binding) {}

    private final List<ToggleEntry> toggles = new ArrayList<>();
    private final List<FloatSliderEntry> sliders = new ArrayList<>();
    private final List<ColorEntry> colors = new ArrayList<>();
    private final List<KeybindEntry> keybinds = new ArrayList<>();

    private int activeColorIndex = -1;
    private int draggingRgbChannel = -1;
    private FloatSliderEntry activeFloatSlider = null;
    private KeyBinding listeningKeybind = null;

    private boolean editingPathField = false;
    private String soundPackStatusMessage = "";

    public FactorioConfigScreen(Screen parent) {
        super(Text.translatable("config.factoriocore.title"));
        this.parent = parent;
        if (FactorioConfig.INSTANCE.factorioDataPath == null || FactorioConfig.INSTANCE.factorioDataPath.isEmpty()) {
            String detected = FactorioSoundPackGenerator.autoDetectFactorioDataPath();
            if (!detected.isEmpty()) {
                FactorioConfig.INSTANCE.factorioDataPath = detected;
            }
        }
        initEntries();
    }

    private void initEntries() {
        FactorioConfig cfg = FactorioConfig.INSTANCE;
        toggles.clear(); sliders.clear(); colors.clear(); keybinds.clear();

        sliders.add(new FloatSliderEntry(0, 10, 10, Text.translatable("config.factoriocore.minimap_scale.name").getString(), () -> cfg.minimapScale, v -> cfg.minimapScale = v, 0.5f, 2.0f));
        toggles.add(new ToggleEntry(0, 10, 44, Text.translatable("config.factoriocore.minimap.name").getString(), Text.translatable("config.factoriocore.minimap.desc").getString(), () -> cfg.enableMinimap, v -> cfg.enableMinimap = v));
        toggles.add(new ToggleEntry(0, 240, 44, Text.translatable("config.factoriocore.crafting_queue.name").getString(), Text.translatable("config.factoriocore.crafting_queue.desc").getString(), () -> cfg.enableCraftingQueueHud, v -> cfg.enableCraftingQueueHud = v));
        toggles.add(new ToggleEntry(0, 10, 78, Text.translatable("config.factoriocore.vanilla_inv.name").getString(), Text.translatable("config.factoriocore.vanilla_inv.desc").getString(), () -> cfg.replaceVanillaInventory, v -> cfg.replaceVanillaInventory = v));
        toggles.add(new ToggleEntry(0, 240, 78, Text.translatable("config.factoriocore.auto_sort.name").getString(), Text.translatable("config.factoriocore.auto_sort.desc").getString(), () -> cfg.enableInventoryAutoSort, v -> cfg.enableInventoryAutoSort = v));

        sliders.add(new FloatSliderEntry(1, 10, 10, Text.translatable("config.factoriocore.info_scale.name").getString(), () -> cfg.infoPanelScale, v -> cfg.infoPanelScale = v, 0.5f, 2.0f));
        toggles.add(new ToggleEntry(1, 10, 44, Text.translatable("config.factoriocore.info_panel.name").getString(), Text.translatable("config.factoriocore.info_panel.desc").getString(), () -> cfg.enableTargetInfoPanel, v -> cfg.enableTargetInfoPanel = v));
        toggles.add(new ToggleEntry(1, 240, 44, Text.translatable("config.factoriocore.info_desc.name").getString(), Text.translatable("config.factoriocore.info_desc.desc").getString(), () -> cfg.showInfoDescription, v -> cfg.showInfoDescription = v));
        toggles.add(new ToggleEntry(1, 10, 78, Text.translatable("config.factoriocore.info_health.name").getString(), Text.translatable("config.factoriocore.info_health.desc").getString(), () -> cfg.showInfoHealth, v -> cfg.showInfoHealth = v));
        toggles.add(new ToggleEntry(1, 240, 78, Text.translatable("config.factoriocore.info_status.name").getString(), Text.translatable("config.factoriocore.info_status.desc").getString(), () -> cfg.showInfoStatus, v -> cfg.showInfoStatus = v));
        toggles.add(new ToggleEntry(1, 10, 112, Text.translatable("config.factoriocore.info_fluids.name").getString(), Text.translatable("config.factoriocore.info_fluids.desc").getString(), () -> cfg.showInfoFluids, v -> cfg.showInfoFluids = v));
        toggles.add(new ToggleEntry(1, 240, 112, Text.translatable("config.factoriocore.info_energy.name").getString(), Text.translatable("config.factoriocore.info_energy.desc").getString(), () -> cfg.showInfoElectricity, v -> cfg.showInfoElectricity = v));

        toggles.add(new ToggleEntry(2, 10, 10, Text.translatable("config.factoriocore.speed_boost.name").getString(), Text.translatable("config.factoriocore.speed_boost.desc").getString(), () -> cfg.enablePavementSpeedBoost, v -> cfg.enablePavementSpeedBoost = v));
        toggles.add(new ToggleEntry(2, 240, 10, Text.translatable("config.factoriocore.pole_supply.name").getString(), Text.translatable("config.factoriocore.pole_supply.desc").getString(), () -> cfg.showPoleSupplyWhenHoldingElectric, v -> cfg.showPoleSupplyWhenHoldingElectric = v));
        toggles.add(new ToggleEntry(2, 10, 44, Text.translatable("config.factoriocore.drill_area.name").getString(), Text.translatable("config.factoriocore.drill_area.desc").getString(), () -> cfg.showDrillMiningArea, v -> cfg.showDrillMiningArea = v));
        toggles.add(new ToggleEntry(2, 240, 44, Text.translatable("config.factoriocore.copper_wires.name").getString(), Text.translatable("config.factoriocore.copper_wires.desc").getString(), () -> cfg.enableCopperWiresRender, v -> cfg.enableCopperWiresRender = v));
        toggles.add(new ToggleEntry(2, 10, 78, Text.translatable("config.factoriocore.particles.name").getString(), Text.translatable("config.factoriocore.particles.desc").getString(), () -> cfg.enablePlacementParticles, v -> cfg.enablePlacementParticles = v));

        colors.add(new ColorEntry(6, Text.translatable("config.factoriocore.color_pole").getString(), () -> cfg.poleSupplyColor, v -> cfg.poleSupplyColor = v));
        colors.add(new ColorEntry(35, Text.translatable("config.factoriocore.color_drill").getString(), () -> cfg.drillAreaColor, v -> cfg.drillAreaColor = v));
        colors.add(new ColorEntry(64, Text.translatable("config.factoriocore.color_valid").getString(), () -> cfg.validPlacementColor, v -> cfg.validPlacementColor = v));
        colors.add(new ColorEntry(93, Text.translatable("config.factoriocore.color_invalid").getString(), () -> cfg.invalidPlacementColor, v -> cfg.invalidPlacementColor = v));
        colors.add(new ColorEntry(122, Text.translatable("config.factoriocore.color_wire").getString(), () -> cfg.wireColor, v -> cfg.wireColor = v));
        colors.add(new ColorEntry(151, Text.translatable("config.factoriocore.color_arrow").getString(), () -> cfg.altArrowColor, v -> cfg.altArrowColor = v));

        int keyY = 6;
        if (FactorioCoreClient.getOpenCraftingKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.crafting").getString(), FactorioCoreClient.getOpenCraftingKey())); keyY += 27; }
        if (AltModeRenderer.getAltModeKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.alt_mode").getString(), AltModeRenderer.getAltModeKey())); keyY += 27; }
        if (FactorioHudOverlay.getToggleMapKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.map_toggle").getString(), FactorioHudOverlay.getToggleMapKey())); keyY += 27; }
        if (FactorioHudOverlay.getZoomInKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.map_zoom_in").getString(), FactorioHudOverlay.getZoomInKey())); keyY += 27; }
        if (FactorioHudOverlay.getZoomOutKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.map_zoom_out").getString(), FactorioHudOverlay.getZoomOutKey())); keyY += 27; }
        if (FactorioCoreClient.getOpenSettingsKey() != null) { keybinds.add(new KeybindEntry(keyY, Text.translatable("config.factoriocore.key.settings").getString(), FactorioCoreClient.getOpenSettingsKey())); }

        // Вкладка 5: Звуки и генератор ресурспака из Factorio/data
        sliders.add(new FloatSliderEntry(5, 10, 10, "Громкость механизмов завода", () -> cfg.machineSoundVolume, v -> cfg.machineSoundVolume = v, 0.0f, 1.5f));
        toggles.add(new ToggleEntry(5, 10, 36, "Включить 3D-звуки завода", "", () -> cfg.enableMachineSounds, v -> cfg.enableMachineSounds = v));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        context.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFF111214);
        context.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF2E3033);
        context.fill(x + 3, y + 3, x + PANEL_W - 3, y + 17, 0xFF222325);
        context.drawText(this.textRenderer, this.title, x + 8, y + 6, 0xFFFFCC66, true);

        int closeX = x + PANEL_W - 17;
        boolean hovClose = mouseX >= closeX && mouseX <= closeX + 13 && mouseY >= y + 4 && mouseY <= y + 16;
        context.fill(closeX, y + 4, closeX + 13, y + 16, 0xFF141414);
        context.fill(closeX + 1, y + 5, closeX + 12, y + 15, hovClose ? 0xFFB83227 : 0xFF3A3C3F);
        context.drawText(this.textRenderer, "x", closeX + 4, y + 6, 0xFFFFFFFF, false);

        int tabY = y + 21;
        int tabW = (PANEL_W - 16) / 6;
        String[] tabNames = {
                Text.translatable("config.factoriocore.tab.hud").getString(),
                Text.translatable("config.factoriocore.tab.info").getString(),
                Text.translatable("config.factoriocore.tab.graphics").getString(),
                Text.translatable("config.factoriocore.tab.colors").getString(),
                Text.translatable("config.factoriocore.tab.keys").getString(),
                "Звуки"
        };
        for (int i = 0; i < 6; i++) {
            drawTabButton(context, x + 8 + i * tabW, tabY, tabW - (i == 5 ? 0 : 2), 16, tabNames[i], mouseX, mouseY, currentTab == i);
        }

        int cx = x + 8, cy = y + 39, cw = PANEL_W - 16, ch = PANEL_H - 68;
        context.fill(cx, cy, cx + cw, cy + ch, 0xFF18191B);
        context.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0xFF242528);

        if (currentTab < 3) {
            for (FloatSliderEntry s : sliders) if (s.tab == currentTab) drawFloatSlider(context, cx + s.rx, cy + s.ry, cw - 20, s.getter.get(), s.min, s.max, s.label);
            for (ToggleEntry t : toggles) if (t.tab == currentTab) drawToggle(context, cx + t.rx, cy + t.ry, (cw / 2) - 14, t.label, t.getter.get(), mouseX, mouseY);
        } else if (currentTab == 3) {
            renderColorsTab(context, cx, cy, cw, mouseX, mouseY);
        } else if (currentTab == 4) {
            renderKeybindsTab(context, cx, cy, cw, mouseX, mouseY);
        } else {
            renderAudioPackTab(context, cx, cy, cw, mouseX, mouseY);
        }

        int bottomY = y + PANEL_H - 24;
        drawFactorioButton(context, x + 8, bottomY, 156, 18, Text.translatable("config.factoriocore.reset").getString(), mouseX, mouseY, false);
        drawFactorioButton(context, x + PANEL_W - 134, bottomY, 126, 18, Text.translatable("config.factoriocore.save").getString(), mouseX, mouseY, true);
    }

    private void renderAudioPackTab(DrawContext context, int cx, int cy, int cw, int mouseX, int mouseY) {
        for (FloatSliderEntry s : sliders) if (s.tab == 5) drawFloatSlider(context, cx + s.rx, cy + s.ry, cw - 20, s.getter.get(), s.min, s.max, s.label);
        for (ToggleEntry t : toggles) if (t.tab == 5) drawToggle(context, cx + t.rx, cy + t.ry, cw - 20, t.label, t.getter.get(), mouseX, mouseY);

        int boxY = cy + 74;
        context.drawText(this.textRenderer, "Импорт оригинальных звуков (.ogg) из установленной Factorio:", cx + 10, boxY, 0xFFFFCC66, false);
        context.drawText(this.textRenderer, "Укажите путь к папке Factorio/data (поддерживается Ctrl+V):", cx + 10, boxY + 12, 0xFFAAAAAA, false);

        int fieldX = cx + 10;
        int fieldY = boxY + 24;
        int fieldW = cw - 20;
        int fieldH = 18;
        context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, this.editingPathField ? 0xFFE59524 : 0xFF111111);
        context.fill(fieldX + 1, fieldY + 1, fieldX + fieldW - 1, fieldY + fieldH - 1, 0xFF141517);

        String pathText = FactorioConfig.INSTANCE.factorioDataPath != null ? FactorioConfig.INSTANCE.factorioDataPath : "";
        String cursor = (this.editingPathField && (System.currentTimeMillis() / 500L) % 2L == 0L) ? "_" : "";
        String trimmed = this.textRenderer.trimToWidth(pathText, fieldW - 14) + cursor;
        context.drawText(this.textRenderer, trimmed, fieldX + 5, fieldY + 5, 0xFFE6E6E6, false);

        int btnY = fieldY + 24;
        drawFactorioButton(context, cx + 10, btnY, 165, 20, "Автопоиск Steam Factorio", mouseX, mouseY, false);
        drawFactorioButton(context, cx + 185, btnY, cw - 195, 20, "Собрать и включить ресурспак (.ogg)", mouseX, mouseY, true);

        if (!this.soundPackStatusMessage.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal(this.soundPackStatusMessage), cx + 10, btnY + 26, 0xFFFFFFFF, true);
        } else {
            context.drawText(this.textRenderer, "Ресурспак создаётся локально в папке resourcepacks/FactorioCore_Sounds", cx + 10, btnY + 26, 0xFF888A8E, false);
        }
    }

    private void drawFloatSlider(DrawContext context, int sx, int sy, int sw, float val, float min, float max, String label) {
        String display = String.format("%s: %.2fx", label, val);
        context.drawText(this.textRenderer, display, sx, sy, 0xFFBBBBBB, false);
        int trackY = sy + 11;
        context.fill(sx, trackY, sx + sw, trackY + 5, 0xFF141414);
        float ratio = MathHelper.clamp((val - min) / (max - min), 0.0f, 1.0f);
        int fillW = Math.round(ratio * (sw - 2));
        if (fillW > 0) context.fill(sx + 1, trackY + 1, sx + 1 + fillW, trackY + 4, 0xFFE59524);
        int knobX = sx + Math.round(ratio * (sw - 4));
        context.fill(knobX, trackY - 1, knobX + 4, trackY + 6, 0xFFEAEAEA);
    }

    private void drawToggle(DrawContext context, int bx, int by, int bw, String label, boolean checked, int mx, int my) {
        boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + 30;
        context.fill(bx, by, bx + bw, by + 30, 0xFF141414);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + 29, hovered ? 0xFF36383C : 0xFF2D2E32);

        context.fill(bx + 6, by + 9, bx + 17, by + 20, 0xFF141414);
        if (checked) {
            context.fill(bx + 7, by + 10, bx + 16, by + 19, 0xFFE59524);
            context.fill(bx + 9, by + 14, bx + 11, by + 17, 0xFF141414);
            context.fill(bx + 11, by + 12, bx + 14, by + 15, 0xFF141414);
        } else {
            context.fill(bx + 7, by + 10, bx + 16, by + 19, 0xFF3E4044);
        }
        context.drawText(this.textRenderer, label, bx + 22, by + 10, checked ? 0xFFFFE082 : 0xFFCCCCCC, false);
    }

    private void renderColorsTab(DrawContext context, int cx, int cy, int cw, int mouseX, int mouseY) {
        for (int i = 0; i < colors.size(); i++) {
            ColorEntry entry = colors.get(i);
            int ry = cy + entry.ry;
            int rgb = entry.getter.get();

            context.fill(cx + 6, ry, cx + cw - 6, ry + 26, 0xFF141414);
            context.fill(cx + 7, ry + 1, cx + cw - 7, ry + 25, 0xFF2D2E32);

            context.fill(cx + 11, ry + 4, cx + 29, ry + 22, 0xFF111111);
            context.fill(cx + 12, ry + 5, cx + 28, ry + 21, 0xFF000000 | rgb);
            context.drawText(this.textRenderer, entry.label, cx + 34, ry + 9, 0xFFE6E6E6, false);

            int slidersStartX = cx + 204;
            drawRgbSlider(context, slidersStartX, ry + 4, 42, (rgb >> 16) & 0xFF, 0xFFE53935, "R");
            drawRgbSlider(context, slidersStartX + 46, ry + 4, 42, (rgb >> 8) & 0xFF, 0xFF43A047, "G");
            drawRgbSlider(context, slidersStartX + 92, ry + 4, 42, rgb & 0xFF, 0xFF1E88E5, "B");

            int presetStartX = cx + 346;
            for (int p = 0; p < COLOR_PRESETS.length; p++) {
                int px = presetStartX + p * 11;
                int pCol = COLOR_PRESETS[p];
                context.fill(px, ry + 8, px + 9, ry + 18, (rgb == pCol) ? 0xFFFFFFFF : 0xFF141414);
                context.fill(px + 1, ry + 9, px + 8, ry + 17, 0xFF000000 | pCol);
            }
        }
    }

    private void drawRgbSlider(DrawContext context, int sx, int sy, int sw, int val255, int barColor, String label) {
        context.drawText(this.textRenderer, label + ":" + val255, sx, sy, 0xFFBBBBBB, false);
        context.fill(sx, sy + 11, sx + sw, sy + 16, 0xFF141414);
        int fillW = Math.round((val255 / 255.0f) * (sw - 2));
        if (fillW > 0) context.fill(sx + 1, sy + 12, sx + 1 + fillW, sy + 15, barColor);
        int knobX = sx + Math.round((val255 / 255.0f) * (sw - 4));
        context.fill(knobX, sy + 10, knobX + 4, sy + 17, 0xFFEAEAEA);
    }

    private void renderKeybindsTab(DrawContext context, int cx, int cy, int cw, int mouseX, int mouseY) {
        for (KeybindEntry entry : keybinds) {
            int ry = cy + entry.ry;
            context.fill(cx + 6, ry, cx + cw - 6, ry + 24, 0xFF141414);
            context.fill(cx + 7, ry + 1, cx + cw - 7, ry + 23, 0xFF2D2E32);
            context.drawText(this.textRenderer, entry.label, cx + 14, ry + 8, 0xFFE6E6E6, false);

            boolean isListening = (this.listeningKeybind == entry.binding);
            String keyLabel = isListening ? Text.translatable("config.factoriocore.keybind.listen").getString() : entry.binding.getBoundKeyLocalizedText().getString();
            drawFactorioButton(context, cx + cw - 152, ry + 3, 92, 18, keyLabel, mouseX, mouseY, isListening);
            drawFactorioButton(context, cx + cw - 56, ry + 3, 50, 18, Text.translatable("config.factoriocore.keybind.reset").getString(), mouseX, mouseY, !entry.binding.isDefault());
        }
    }

    private void drawTabButton(DrawContext context, int bx, int by, int bw, int bh, String label, int mouseX, int mouseY, boolean active) {
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = active ? 0xFFE59524 : (hovered ? 0xFF4A4C50 : 0xFF35373B);
        context.fill(bx, by, bx + bw, by + bh, 0xFF141414);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - (active ? 0 : 1), bg);
        int tw = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label, bx + (bw - tw) / 2, by + 4, active ? 0xFF141414 : 0xFFE6E6E6, false);
    }

    private void drawFactorioButton(DrawContext context, int bx, int by, int bw, int bh, String label, int mouseX, int mouseY, boolean highlighted) {
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = highlighted ? 0xFFD4881E : (hovered ? 0xFF4A4C50 : 0xFF383A3D);
        context.fill(bx, by, bx + bw, by + bh, 0xFF141414);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);
        int tw = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label, bx + (bw - tw) / 2, by + (bh - 8) / 2, highlighted ? 0xFF141414 : 0xFFEAEAEA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.listeningKeybind != null && button > 1) {
            this.listeningKeybind.setBoundKey(InputUtil.Type.MOUSE.createFromCode(button));
            KeyBinding.updateKeysByCode();
            if (this.client != null) this.client.options.write();
            this.listeningKeybind = null;
            return true;
        }
        this.listeningKeybind = null;
        this.editingPathField = false;

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        if (mouseX >= x + PANEL_W - 17 && mouseX <= x + PANEL_W - 4 && mouseY >= y + 4 && mouseY <= y + 16
                || mouseX >= x + PANEL_W - 134 && mouseX <= x + PANEL_W - 8 && mouseY >= y + PANEL_H - 24 && mouseY <= y + PANEL_H - 6) {
            FactorioConfig.save();
            this.close();
            return true;
        }

        if (mouseX >= x + 8 && mouseX <= x + 164 && mouseY >= y + PANEL_H - 24 && mouseY <= y + PANEL_H - 6) {
            FactorioConfig.INSTANCE.resetDefaults();
            for (KeybindEntry ke : keybinds) ke.binding.setBoundKey(ke.binding.getDefaultKey());
            KeyBinding.updateKeysByCode();
            if (this.client != null) this.client.options.write();
            playClick();
            return true;
        }

        int tabY = y + 21;
        int tabW = (PANEL_W - 16) / 6;
        for (int i = 0; i < 6; i++) {
            if (mouseX >= x + 8 + i * tabW && mouseX <= x + 8 + i * tabW + tabW && mouseY >= tabY && mouseY <= tabY + 16) {
                this.currentTab = i;
                playClick();
                return true;
            }
        }

        int cx = x + 8, cy = y + 39, cw = PANEL_W - 16;
        if (currentTab < 3 || currentTab == 5) {
            for (FloatSliderEntry s : sliders) {
                if (s.tab == currentTab && mouseX >= cx + s.rx && mouseX <= cx + s.rx + cw - 20 && mouseY >= cy + s.ry + 10 && mouseY <= cy + s.ry + 17) {
                    this.activeFloatSlider = s;
                    updateFloatSlider(s, mouseX, cx + s.rx, cw - 20);
                    return true;
                }
            }
            for (ToggleEntry t : toggles) {
                int toggleW = (currentTab == 5) ? (cw - 20) : ((cw / 2) - 14);
                if (t.tab == currentTab && mouseX >= cx + t.rx && mouseX <= cx + t.rx + toggleW && mouseY >= cy + t.ry && mouseY <= cy + t.ry + 30) {
                    t.setter.accept(!t.getter.get());
                    FactorioConfig.save();
                    playClick();
                    return true;
                }
            }

            if (currentTab == 5) {
                int fieldX = cx + 10;
                int fieldY = cy + 98;
                int fieldW = cw - 20;
                if (mouseX >= fieldX && mouseX <= fieldX + fieldW && mouseY >= fieldY && mouseY <= fieldY + 18) {
                    this.editingPathField = true;
                    if (button == 1) {
                        FactorioConfig.INSTANCE.factorioDataPath = "";
                    }
                    return true;
                }

                int btnY = fieldY + 24;
                if (mouseX >= cx + 10 && mouseX <= cx + 175 && mouseY >= btnY && mouseY <= btnY + 20) {
                    String found = FactorioSoundPackGenerator.autoDetectFactorioDataPath();
                    if (!found.isEmpty()) {
                        FactorioConfig.INSTANCE.factorioDataPath = found;
                        FactorioConfig.save();
                        this.soundPackStatusMessage = "§aНайдена папка: " + found;
                    } else {
                        this.soundPackStatusMessage = "§eПапка Steam не найдена автоматически. Вставьте путь вручную (Ctrl+V).";
                    }
                    playClick();
                    return true;
                }
                if (mouseX >= cx + 185 && mouseX <= cx + cw - 10 && mouseY >= btnY && mouseY <= btnY + 20) {
                    FactorioConfig.save();
                    this.soundPackStatusMessage = FactorioSoundPackGenerator.buildAndActivateResourcePack(FactorioConfig.INSTANCE.factorioDataPath);
                    playClick();
                    return true;
                }
            }
        } else if (currentTab == 3) {
            for (int i = 0; i < colors.size(); i++) {
                ColorEntry entry = colors.get(i);
                int ry = cy + entry.ry;
                for (int ch = 0; ch < 3; ch++) {
                    int sx = cx + 204 + ch * 46;
                    if (mouseX >= sx - 2 && mouseX <= sx + 44 && mouseY >= ry + 14 && mouseY <= ry + 21) {
                        this.activeColorIndex = i;
                        this.draggingRgbChannel = ch;
                        updateColorChannelFromMouse(entry, ch, mouseX, sx, 42);
                        return true;
                    }
                }
                for (int p = 0; p < COLOR_PRESETS.length; p++) {
                    int px = cx + 346 + p * 11;
                    if (mouseX >= px && mouseX <= px + 10 && mouseY >= ry + 8 && mouseY <= ry + 18) {
                        entry.setter.accept(COLOR_PRESETS[p]);
                        FactorioConfig.save();
                        playClick();
                        return true;
                    }
                }
            }
        } else if (currentTab == 4) {
            for (KeybindEntry entry : keybinds) {
                int ry = cy + entry.ry;
                if (mouseX >= cx + cw - 152 && mouseX <= cx + cw - 60 && mouseY >= ry + 3 && mouseY <= ry + 21) {
                    this.listeningKeybind = entry.binding;
                    playClick();
                    return true;
                }
                if (mouseX >= cx + cw - 56 && mouseX <= cx + cw - 6 && mouseY >= ry + 3 && mouseY <= ry + 21) {
                    entry.binding.setBoundKey(entry.binding.getDefaultKey());
                    KeyBinding.updateKeysByCode();
                    if (this.client != null) this.client.options.write();
                    playClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateColorChannelFromMouse(ColorEntry entry, int channel, double mouseX, int sliderX, int sliderW) {
        int val = MathHelper.clamp(Math.round(((float) (mouseX - sliderX) / sliderW) * 255.0f), 0, 255);
        int rgb = entry.getter.get();
        int r = (channel == 0) ? val : ((rgb >> 16) & 0xFF);
        int g = (channel == 1) ? val : ((rgb >> 8) & 0xFF);
        int b = (channel == 2) ? val : (rgb & 0xFF);
        entry.setter.accept((r << 16) | (g << 8) | b);
        FactorioConfig.save();
    }

    private void updateFloatSlider(FloatSliderEntry s, double mouseX, int sx, int sw) {
        float ratio = MathHelper.clamp((float) (mouseX - sx) / sw, 0.0f, 1.0f);
        float val = s.min + ratio * (s.max - s.min);
        val = Math.round(val * 20.0f) / 20.0f;
        s.setter.accept(val);
        FactorioConfig.save();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int x = (this.width - PANEL_W) / 2;
        int cx = x + 8, cw = PANEL_W - 16;
        if (this.activeFloatSlider != null) {
            updateFloatSlider(this.activeFloatSlider, mouseX, cx + this.activeFloatSlider.rx, cw - 20);
            return true;
        }
        if (this.activeColorIndex >= 0 && this.draggingRgbChannel >= 0) {
            updateColorChannelFromMouse(colors.get(this.activeColorIndex), this.draggingRgbChannel, mouseX, cx + 204 + this.draggingRgbChannel * 46, 42);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.activeColorIndex = -1;
        this.draggingRgbChannel = -1;
        this.activeFloatSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.editingPathField && chr >= 32) {
            FactorioConfig.INSTANCE.factorioDataPath += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.editingPathField) {
            if (Screen.isPaste(keyCode) && this.client != null) {
                String clip = this.client.keyboard.getClipboard();
                if (clip != null) {
                    FactorioConfig.INSTANCE.factorioDataPath = clip.trim().replace("\"", "");
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !FactorioConfig.INSTANCE.factorioDataPath.isEmpty()) {
                String cur = FactorioConfig.INSTANCE.factorioDataPath;
                FactorioConfig.INSTANCE.factorioDataPath = cur.substring(0, cur.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.editingPathField = false;
                FactorioConfig.save();
                return true;
            }
            return true;
        }

        if (this.listeningKeybind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) this.listeningKeybind.setBoundKey(InputUtil.UNKNOWN_KEY);
            else this.listeningKeybind.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
            KeyBinding.updateKeysByCode();
            if (this.client != null) this.client.options.write();
            this.listeningKeybind = null;
            playClick();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        if (this.client != null && this.client.player != null) {
            this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.2f);
        }
    }
}