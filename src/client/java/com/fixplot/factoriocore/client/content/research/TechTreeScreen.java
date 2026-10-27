package com.fixplot.factoriocore.client.content.research;

import com.fixplot.factoriocore.client.content.crafting.FactorioCharacterScreen;
import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.content.research.ResearchManager;
import com.fixplot.factoriocore.content.research.ResearchManager.SciencePack;
import com.fixplot.factoriocore.content.research.ResearchManager.TechNode;
import com.fixplot.factoriocore.content.research.ResearchManager.TechStatus;
import com.fixplot.factoriocore.network.ResearchActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TechTreeScreen extends Screen {

    @Deprecated
    public static void tickClientResearch() {
        // No-op: состояние исследований синхронизируется сервером (SyncResearchStatePayload).
        // Локальная инициализация здесь затирала бы серверные данные в singleplayer.
    }

    private TechNode selectedTech;
    private double canvasOffsetX = 0;
    private double canvasOffsetY = 0;
    private float zoom = 1.0f;
    private boolean isDraggingCanvas = false;
    private boolean showFocusedBranchOnly = true;
    private int gridScrollRow = 0;
    private boolean searchActive = false;
    private String searchQuery = "";

    public TechTreeScreen() {
        super(Text.translatable("gui.factoriocore.tech_tree"));
        ResearchManager.init();
        this.selectedTech = ResearchManager.getById("automation");
        if (this.selectedTech == null && !ResearchManager.TECHS.isEmpty()) {
            this.selectedTech = ResearchManager.TECHS.get(0);
        }
        centerCanvasOnSelected();
    }

    private void centerCanvasOnSelected() {
        if (this.selectedTech != null && !this.showFocusedBranchOnly) {
            this.canvasOffsetX = -this.selectedTech.treeCol * 54 * this.zoom;
            this.canvasOffsetY = -(this.selectedTech.treeRow - 2) * 82 * this.zoom;
        } else {
            this.canvasOffsetX = 0;
            this.canvasOffsetY = 0;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private List<TechNode> getSortedFilteredList() {
        List<TechNode> list = new ArrayList<>();
        String queryLower = this.searchQuery.toLowerCase(Locale.ROOT);
        for (TechNode t : ResearchManager.TECHS) {
            if (!queryLower.isBlank() && !t.getLocalizedName().toLowerCase(Locale.ROOT).contains(queryLower)) {
                continue;
            }
            list.add(t);
        }
        list.sort(Comparator.comparingInt(t -> switch (t.status) {
            case AVAILABLE -> 0;
            case LOCKED -> 1;
            case RESEARCHED -> 2;
        }));
        return list;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int screenW = this.width;
        int screenH = this.height;
        int leftW = 218;

        context.fill(0, 0, leftW + 4, screenH, 0xFF141517);
        context.fill(leftW + 4, 0, screenW, 20, 0xFF141517);

        int canvasX = leftW + 4;
        int canvasY = 20;
        int canvasW = screenW - canvasX - 3;
        int canvasH = screenH - canvasY - 3;

        context.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xD8121315);
        context.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasH, 0xFF0A0A0B);

        TechNode hoveredTech = null;
        ItemStack hoveredRecipeUnlock = ItemStack.EMPTY;

        context.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        TechNode graphHover = renderRightTechGraph(context, canvasX, canvasY, canvasW, canvasH, mouseX, mouseY);
        if (graphHover != null) hoveredTech = graphHover;
        context.disableScissor();

        String cbLabel = Text.translatableWithFallback("gui.factoriocore.tech.focused_branch", "Show focused technology branch only").getString();
        int cbLabelW = Math.round(this.textRenderer.getWidth(cbLabel) * 0.75f);
        int cbX = screenW - cbLabelW - 22;
        int cbY = canvasY + 6;
        context.fill(cbX - 3, cbY - 2, screenW - 6, cbY + 10, 0xAA141517);
        drawFactorioCheckbox(context, cbX, cbY, this.showFocusedBranchOnly);
        drawScaledText(context, cbLabel, cbX + 12, cbY + 1, 0.75f, 0xFFD8D8D8, true);

        drawFactorioHeader(context, 3, 3, leftW - 1,
                Text.translatableWithFallback("gui.factoriocore.tech.queue_title", "Research Queue").getString(), 6);
        drawFactorioHeader(context, canvasX, 3, canvasW,
                Text.translatableWithFallback("gui.factoriocore.tech_tree", "Technology Tree").getString(), 34);

        int closeX = screenW - 17;
        boolean hovClose = mouseX >= closeX && mouseX <= closeX + 13 && mouseY >= 4 && mouseY <= 16;
        context.fill(closeX, 4, closeX + 13, 16, 0xFF111111);
        context.fill(closeX + 1, 5, closeX + 12, 15, hovClose ? 0xFFB83227 : 0xFF35373A);
        context.drawText(this.textRenderer, "x", closeX + 4, 6, 0xFFFFFFFF, false);

        ItemStack unlockHover = renderLeftPanels(context, 3, 20, leftW - 1, screenH - 23, mouseX, mouseY);
        if (!unlockHover.isEmpty()) hoveredRecipeUnlock = unlockHover;

        TechNode gridHover = getHoveredGridTech(3, 20, leftW - 1, screenH - 23, mouseX, mouseY);
        if (gridHover != null) hoveredTech = gridHover;

        if (!hoveredRecipeUnlock.isEmpty() && this.client != null && this.client.player != null) {
            FactorioRecipes.Recipe r = FactorioRecipes.getByOutput(hoveredRecipeUnlock.getItem());
            if (r != null) {
                FactorioCharacterScreen.renderRecipeCard(
                        context, this.textRenderer, this.width, this.height,
                        r, this.client.player.getInventory(), mouseX, mouseY, false
                );
            } else {
                context.drawItemTooltip(this.textRenderer, hoveredRecipeUnlock, mouseX, mouseY);
            }
        } else if (hoveredTech != null) {
            renderFactorioTechTooltip(context, hoveredTech, mouseX, mouseY);
        }
    }

    private ItemStack renderLeftPanels(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        ItemStack hoveredUnlock = ItemStack.EMPTY;

        int qBoxH = 38;
        context.fill(x, y, x + w, y + qBoxH, 0xFF101112);
        context.fill(x + 1, y + 1, x + w - 1, y + qBoxH - 1, 0xFF232427);

        if (ResearchManager.RESEARCH_QUEUE.isEmpty()) {
            drawScaledText(context,
                    Text.translatableWithFallback("gui.factoriocore.tech.queue_empty", "Queue is empty").getString(),
                    x + 6, y + 15, 0.75f, 0xFF888A8E, false);
        } else {
            for (int i = 0; i < ResearchManager.RESEARCH_QUEUE.size() && i < 6; i++) {
                TechNode qt = ResearchManager.getById(ResearchManager.RESEARCH_QUEUE.get(i));
                if (qt == null) continue;
                int qx = x + 4 + i * 34;
                int qy = y + 3;
                drawMiniTechTile(context, qt, qx, qy, 32, 32, false, mouseX, mouseY);
                if (i == 0) {
                    float ratio = ResearchManager.getOverallProgressRatio();
                    int fillW = Math.round(ratio * 30);
                    context.fill(qx + 1, qy + 23, qx + 31, qy + 31, 0xFF141414);
                    if (fillW > 0) context.fill(qx + 1, qy + 23, qx + 1 + fillW, qy + 31, 0xFF43A047);
                    String pct = Math.round(ratio * 100) + "%";
                    drawScaledTextCentered(context, pct, qx + 16, qy + 24, 0.60f, 0xFFFFFFFF, true);
                }
            }
        }

        int infoY = y + qBoxH + 3;
        int infoH = 98;
        context.fill(x, infoY, x + w, infoY + infoH, 0xFF101112);
        context.fill(x + 1, infoY + 1, x + w - 1, infoY + infoH - 1, 0xFF28292C);

        if (this.selectedTech != null) {
            TechNode st = this.selectedTech;
            String statusWord = switch (st.status) {
                case RESEARCHED -> Text.translatableWithFallback("gui.factoriocore.tech.status.researched", "(Researched)").getString();
                case AVAILABLE -> Text.translatableWithFallback("gui.factoriocore.tech.status.available", "(Available)").getString();
                case LOCKED -> Text.translatableWithFallback("gui.factoriocore.tech.status.locked", "(Locked)").getString();
            };
            context.fill(x + 2, infoY + 2, x + w - 2, infoY + 15, 0xFF1E1F22);
            String fullHeader = st.getLocalizedName() + " " + statusWord;
            drawScaledText(context, this.textRenderer.trimToWidth(fullHeader, Math.round((w - 10) / 0.78f)),
                    x + 5, infoY + 5, 0.78f, 0xFFFFDCA0, true);

            drawBigSelectedTechTile(context, st, x + 5, infoY + 18, 44, 56);

            int rx = x + 54;
            int ry = infoY + 18;

            if (!st.craftTrigger.isEmpty()) {
                drawScaledText(context,
                        Text.translatableWithFallback("gui.factoriocore.tech.trigger_craft", "Research Trigger: Craft item").getString(),
                        rx, ry, 0.75f, 0xFFBBBBBB, false);
                ry += 9;
                context.fill(rx, ry, rx + w - 58, ry + 16, 0xFF1D1E21);
                context.getMatrices().push();
                context.getMatrices().translate(rx + 2, ry + 1, 100.0f);
                context.getMatrices().scale(0.85f, 0.85f, 1.0f);
                context.drawItem(st.craftTrigger, 0, 0);
                context.getMatrices().pop();
                drawScaledText(context, st.craftTrigger.getName().getString(), rx + 18, ry + 5, 0.70f, 0xFF76FF76, false);
                ry += 19;
            } else {
                String costHeader = Text.translatableWithFallback("gui.factoriocore.tech.cost", "Cost").getString()
                        + " (" + st.completedUnits + "/" + st.costCount + ")";
                drawScaledText(context, costHeader, rx, ry, 0.75f, 0xFFBBBBBB, false);
                ry += 9;
                context.fill(rx, ry, rx + w - 58, ry + 16, 0xFF1D1E21);
                drawClockIcon(context, rx + 3, ry + 4);
                drawScaledText(context, String.valueOf(st.timeSeconds), rx + 13, ry + 5, 0.75f, 0xFFE6E6E6, true);

                int fx = rx + 28;
                for (SciencePack sp : st.packs) {
                    drawScienceFlask(context, fx, ry + 3, sp, 1.0f);
                    fx += 11;
                }
                drawScaledText(context, "\u00d7 " + st.costCount, fx + 3, ry + 5, 0.78f, 0xFFE6E6E6, true);
                ry += 19;
            }

            if (!st.unlocks.isEmpty()) {
                drawScaledText(context,
                        Text.translatableWithFallback("gui.factoriocore.tech.effects", "Effects").getString(),
                        rx, ry, 0.75f, 0xFFBBBBBB, false);
                ry += 8;
                for (int i = 0; i < st.unlocks.size() && i < 6; i++) {
                    ItemStack ul = st.unlocks.get(i);
                    int ux = rx + i * 18;
                    boolean hovU = mouseX >= ux && mouseX < ux + 17 && mouseY >= ry && mouseY < ry + 17;
                    context.fill(ux, ry, ux + 17, ry + 17, 0xFF141414);
                    context.fill(ux + 1, ry + 1, ux + 16, ry + 16, hovU ? 0xFF525458 : 0xFF35373A);
                    context.getMatrices().push();
                    context.getMatrices().translate(ux + 1.5f, ry + 1.5f, 100.0f);
                    context.getMatrices().scale(0.85f, 0.85f, 1.0f);
                    context.drawItem(ul, 0, 0);
                    context.getMatrices().pop();
                    if (hovU) hoveredUnlock = ul;
                }
            }

            List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal(st.getLocalizedDescription()), Math.round((w - 98) / 0.65f));
            for (int l = 0; l < Math.min(2, descLines.size()); l++) {
                context.getMatrices().push();
                context.getMatrices().translate(x + 5, infoY + 77 + l * 8, 100.0f);
                context.getMatrices().scale(0.65f, 0.65f, 1.0f);
                context.drawText(this.textRenderer, descLines.get(l), 0, 0, 0xFFAAAAAA, false);
                context.getMatrices().pop();
            }

            int btnW = 86;
            int btnH = 15;
            int btnX = x + w - btnW - 4;
            int btnY = infoY + infoH - btnH - 4;
            boolean alreadyQueued = ResearchManager.RESEARCH_QUEUE.contains(st.id);
            boolean canStart = st.status == TechStatus.AVAILABLE && !alreadyQueued;
            boolean hovBtn = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

            int btnBg = canStart ? (hovBtn ? 0xFF38B828 : 0xFF288A1E) : 0xFF35373A;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF111111);
            context.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, btnBg);
            String btnLbl = st.status == TechStatus.RESEARCHED
                    ? Text.translatableWithFallback("gui.factoriocore.tech.btn.researched", "Researched").getString()
                    : (alreadyQueued
                    ? Text.translatableWithFallback("gui.factoriocore.tech.btn.queued", "In Queue").getString()
                    : Text.translatableWithFallback("gui.factoriocore.tech.btn.start", "Start Research").getString());
            drawScaledTextCentered(context, btnLbl, btnX + btnW / 2, btnY + 4, 0.68f, canStart ? 0xFFFFFFFF : 0xFFAAAAAA, true);
        }

        int listY = infoY + infoH + 3;
        int listH = (y + h) - listY;
        context.fill(x, listY, x + w, listY + listH, 0xFF101112);
        context.fill(x + 1, listY + 1, x + w - 1, listY + listH - 1, 0xFF1E1F22);

        drawFactorioHeader(context, x + 2, listY + 2, w - 4,
                Text.translatableWithFallback("gui.factoriocore.tech.list_title", "Technology List").getString(),
                this.searchActive ? 78 : 20);
        if (this.searchActive) {
            int sbW = 72;
            int sbX = x + w - sbW - 4;
            context.fill(sbX, listY + 3, sbX + sbW, listY + 14, 0xFF111112);
            context.fill(sbX + 1, listY + 13, sbX + sbW - 1, listY + 14, 0xFFE59524);
            drawScaledText(context, this.searchQuery + "_", sbX + 3, listY + 5, 0.70f, 0xFFFFE082, false);
        } else {
            drawIconButton(context, x + w - 17, listY + 3, 13, 12, "\u2315", mouseX, mouseY, false);
        }

        List<TechNode> gridTechs = getSortedFilteredList();
        int cols = 6;
        int tileW = 33;
        int tileH = 42;
        int gap = 2;
        int gridStartX = x + 5;
        int gridStartY = listY + 18;
        int visibleRows = Math.max(1, (listH - 20) / (tileH + gap));

        context.enableScissor(x + 2, gridStartY, x + w - 2, listY + listH - 2);
        for (int i = 0; i < gridTechs.size(); i++) {
            int col = i % cols;
            int row = (i / cols) - this.gridScrollRow;
            if (row < 0 || row >= visibleRows + 1) continue;
            int tx = gridStartX + col * (tileW + gap);
            int ty = gridStartY + row * (tileH + gap);
            drawMiniTechTile(context, gridTechs.get(i), tx, ty, tileW, tileH, gridTechs.get(i) == this.selectedTech, mouseX, mouseY);
        }
        context.disableScissor();

        return hoveredUnlock;
    }

    private void drawMiniTechTile(DrawContext context, TechNode tech, int tx, int ty, int tw, int th,
                                  boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + th;
        int borderCol = selected ? 0xFFFFCC44 : (hovered ? 0xFFE6E6E6 : 0xFF0E0E0F);

        int topBg = switch (tech.status) {
            case RESEARCHED -> hovered ? 0xFF38A838 : 0xFF2B872B;
            case AVAILABLE -> hovered ? 0xFFB89228 : 0xFF96751E;
            case LOCKED -> hovered ? 0xFF942E2E : 0xFF752222;
        };
        int topHighlight = switch (tech.status) {
            case RESEARCHED -> 0xFF48C848;
            case AVAILABLE -> 0xFFD4AC36;
            case LOCKED -> 0xFFB03A3A;
        };

        int footerH = (th >= 38) ? 12 : 8;
        int bodyH = th - footerH;

        context.fill(tx, ty, tx + tw, ty + th, borderCol);
        context.fill(tx + 1, ty + 1, tx + tw - 1, ty + bodyH, topBg);
        context.fill(tx + 1, ty + 1, tx + tw - 1, ty + 2, topHighlight);

        float iconScale = (tw >= 40) ? 1.35f : 1.0f;
        int iconPx = Math.round(16 * iconScale);
        context.getMatrices().push();
        context.getMatrices().translate(tx + (tw - iconPx) / 2.0f, ty + (bodyH - iconPx) / 2.0f, 100.0f);
        context.getMatrices().scale(iconScale, iconScale, 1.0f);
        context.drawItem(tech.icon, 0, 0);
        context.getMatrices().pop();

        if (!tech.badgeLevel.isEmpty()) {
            drawScaledText(context, tech.badgeLevel, tx + tw - 7, ty + 2, 0.65f, 0xFFFFE066, true);
        }

        context.fill(tx + 1, ty + bodyH, tx + tw - 1, ty + th - 1, 0xFF1B1C1E);
        if (th >= 38) {
            int fx = tx + 2;
            for (SciencePack sp : tech.packs) {
                drawScienceFlask(context, fx, ty + bodyH + 1, sp, 0.55f);
                fx += 6;
            }
            drawScaledTextCentered(context, "\u00d7" + tech.costCount, tx + tw / 2 + 4, ty + bodyH + 3, 0.58f, 0xFFDDDDDD, false);
        }
    }

    private void drawBigSelectedTechTile(DrawContext context, TechNode tech, int tx, int ty, int tw, int th) {
        int topBg = switch (tech.status) {
            case RESEARCHED -> 0xFF2B872B;
            case AVAILABLE -> 0xFF96751E;
            case LOCKED -> 0xFF752222;
        };
        int footerH = 14;
        int bodyH = th - footerH;

        context.fill(tx, ty, tx + tw, ty + th, 0xFF111111);
        context.fill(tx + 1, ty + 1, tx + tw - 1, ty + bodyH, topBg);

        context.getMatrices().push();
        context.getMatrices().translate(tx + 6.0f, ty + 6.0f, 100.0f);
        context.getMatrices().scale(2.0f, 2.0f, 1.0f);
        context.drawItem(tech.icon, 0, 0);
        context.getMatrices().pop();

        if (!tech.badgeLevel.isEmpty()) {
            drawScaledText(context, tech.badgeLevel, tx + tw - 9, ty + 3, 0.85f, 0xFFFFE066, true);
        }

        context.fill(tx + 1, ty + bodyH, tx + tw - 1, ty + th - 1, 0xFF1A1B1D);
        int fx = tx + 3;
        for (SciencePack sp : tech.packs) {
            drawScienceFlask(context, fx, ty + bodyH + 1, sp, 0.65f);
            fx += 7;
        }
        drawScaledTextCentered(context, "\u00d7 " + tech.costCount, tx + tw / 2 + 4, ty + bodyH + 5, 0.62f, 0xFFE6E6E6, false);
    }

    private void drawScienceFlask(DrawContext context, int x, int y, SciencePack pack, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 150.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.fill(3, 0, 5, 1, 0xFF8D6E63);
        context.fill(3, 1, 5, 4, 0xFFD0E8F2);
        context.fill(2, 4, 6, 6, pack.bodyColor);
        context.fill(1, 6, 7, 9, pack.bodyColor);
        context.fill(2, 5, 4, 8, pack.liquidHighlight);
        context.fill(1, 9, 7, 10, 0xFF141414);
        context.getMatrices().pop();
    }

    private TechNode renderRightTechGraph(DrawContext context, int canvasX, int canvasY, int canvasW, int canvasH, int mouseX, int mouseY) {
        TechNode hovered = null;
        int centerX = canvasX + canvasW / 2 + (int) this.canvasOffsetX;
        int centerY = canvasY + canvasH / 2 + (int) this.canvasOffsetY;

        if (this.showFocusedBranchOnly && this.selectedTech != null) {
            TechNode focus = this.selectedTech;
            List<TechNode> parents = new ArrayList<>();
            for (String pid : focus.prerequisites) {
                TechNode p = ResearchManager.getById(pid);
                if (p != null) parents.add(p);
            }
            List<TechNode> children = new ArrayList<>();
            for (TechNode t : ResearchManager.TECHS) {
                if (t.prerequisites.contains(focus.id)) children.add(t);
            }

            int focusW = Math.round(58 * zoom);
            int focusH = Math.round(74 * zoom);
            int smallW = Math.round(38 * zoom);
            int smallH = Math.round(48 * zoom);
            int gapX = Math.round(50 * zoom);
            int rowGapY = Math.round(82 * zoom);

            int fx = centerX - focusW / 2;
            int fy = centerY - focusH / 2;

            for (int i = 0; i < parents.size(); i++) {
                TechNode p = parents.get(i);
                int px = centerX + (int) ((i - (parents.size() - 1) / 2.0f) * gapX) - smallW / 2;
                int py = fy - rowGapY - smallH / 2;
                drawOrthogonalLink(context, px + smallW / 2, py + smallH, fx + focusW / 2, fy, true);
                drawMiniTechTile(context, p, px, py, smallW, smallH, false, mouseX, mouseY);
                if (mouseX >= px && mouseX < px + smallW && mouseY >= py && mouseY < py + smallH) hovered = p;
            }

            for (int i = 0; i < children.size(); i++) {
                TechNode ch = children.get(i);
                int cx = centerX + (int) ((i - (children.size() - 1) / 2.0f) * gapX) - smallW / 2;
                int cy = fy + focusH + Math.round(34 * zoom);
                drawOrthogonalLink(context, fx + focusW / 2, fy + focusH, cx + smallW / 2, cy, focus.status == TechStatus.RESEARCHED);
                drawMiniTechTile(context, ch, cx, cy, smallW, smallH, false, mouseX, mouseY);
                if (mouseX >= cx && mouseX < cx + smallW && mouseY >= cy && mouseY < cy + smallH) hovered = ch;
            }

            drawMiniTechTile(context, focus, fx, fy, focusW, focusH, true, mouseX, mouseY);
            if (mouseX >= fx && mouseX < fx + focusW && mouseY >= fy && mouseY < fy + focusH) hovered = focus;
        } else {
            int stepX = Math.round(54 * zoom);
            int stepY = Math.round(82 * zoom);
            int nodeW = Math.round(38 * zoom);
            int nodeH = Math.round(48 * zoom);

            for (TechNode t : ResearchManager.TECHS) {
                int tx = centerX + t.treeCol * stepX - nodeW / 2;
                int ty = centerY + (t.treeRow - 2) * stepY - nodeH / 2;
                for (String preId : t.prerequisites) {
                    TechNode pre = ResearchManager.getById(preId);
                    if (pre != null) {
                        int px = centerX + pre.treeCol * stepX;
                        int py = centerY + (pre.treeRow - 2) * stepY + nodeH / 2;
                        drawOrthogonalLink(context, px, py, tx + nodeW / 2, ty, pre.status == TechStatus.RESEARCHED);
                    }
                }
            }

            for (TechNode t : ResearchManager.TECHS) {
                boolean isSel = (t == this.selectedTech);
                int w = isSel ? Math.round(46 * zoom) : nodeW;
                int h = isSel ? Math.round(58 * zoom) : nodeH;
                int tx = centerX + t.treeCol * stepX - w / 2;
                int ty = centerY + (t.treeRow - 2) * stepY - h / 2;
                drawMiniTechTile(context, t, tx, ty, w, h, isSel, mouseX, mouseY);
                if (mouseX >= tx && mouseX < tx + w && mouseY >= ty && mouseY < ty + h) hovered = t;
            }
        }
        return hovered;
    }

    private void drawOrthogonalLink(DrawContext context, int x1, int y1, int x2, int y2, boolean activeLine) {
        int col = activeLine ? 0xFFB8BABC : 0xFF686A6E;
        int midY = (y1 + y2) / 2;
        context.fill(x1 - 1, Math.min(y1, midY), x1 + 1, Math.max(y1, midY) + 1, col);
        context.fill(Math.min(x1, x2), midY - 1, Math.max(x1, x2) + 1, midY + 1, col);
        context.fill(x2 - 1, Math.min(midY, y2), x2 + 1, Math.max(midY, y2) + 1, col);
        context.fill(x2 - 2, y2 - 2, x2 + 2, y2, 0xFFFFCC44);
    }

    private void renderFactorioTechTooltip(DrawContext context, TechNode tech, int mouseX, int mouseY) {
        String locName = tech.getLocalizedName();
        int cardW = Math.max(176, this.textRenderer.getWidth(locName) + 24);
        List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal(tech.getLocalizedDescription()), cardW - 14);
        int unlockRows = tech.unlocks.isEmpty() ? 0 : 1;
        int cardH = 22 + descLines.size() * 10 + 26 + unlockRows * 30 + 6;

        int tx = mouseX + 12;
        int ty = mouseY + 8;
        if (tx + cardW > this.width - 6) tx = mouseX - cardW - 12;
        if (ty + cardH > this.height - 6) ty = this.height - cardH - 6;
        if (ty < 6) ty = 6;

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        context.fill(tx, ty, tx + cardW, ty + cardH, 0xF8111214);
        context.fill(tx + 1, ty + 1, tx + cardW - 1, ty + cardH - 1, 0xF4252629);
        context.fill(tx + 2, ty + 2, tx + cardW - 2, ty + 18, 0xFF42331C);
        context.fill(tx + 2, ty + 18, tx + cardW - 2, ty + 19, 0xFFE59524);
        context.drawText(this.textRenderer, locName, tx + 6, ty + 6, 0xFFFFDCA0, true);

        int curY = ty + 23;
        for (OrderedText line : descLines) {
            context.drawText(this.textRenderer, line, tx + 6, curY, 0xFFCCCCCC, false);
            curY += 10;
        }

        curY += 3;
        context.fill(tx + 4, curY, tx + cardW - 4, curY + 20, 0xFF1B1C1F);
        drawClockIcon(context, tx + 8, curY + 6);
        drawScaledText(context, String.valueOf(tech.timeSeconds), tx + 18, curY + 7, 0.80f, 0xFFFFFFFF, true);
        int fx = tx + 36;
        for (SciencePack sp : tech.packs) {
            drawScienceFlask(context, fx, curY + 5, sp, 1.0f);
            fx += 11;
        }
        drawScaledText(context, "\u00d7 " + tech.costCount, fx + 4, curY + 7, 0.80f, 0xFFFFFFFF, true);
        curY += 23;

        if (!tech.unlocks.isEmpty()) {
            drawScaledText(context,
                    Text.translatableWithFallback("gui.factoriocore.tech.effects", "Effects").getString(),
                    tx + 6, curY, 0.75f, 0xFFFFCC66, false);
            curY += 9;
            for (int i = 0; i < tech.unlocks.size() && i < 8; i++) {
                int ux = tx + 6 + i * 19;
                context.fill(ux, curY, ux + 18, curY + 18, 0xFF141414);
                context.fill(ux + 1, curY + 1, ux + 17, curY + 17, 0xFF383A3E);
                context.drawItem(tech.unlocks.get(i), ux + 1, curY + 1);
            }
        }
        context.getMatrices().pop();
    }

    private TechNode getHoveredGridTech(int x, int y, int w, int h, double mouseX, double mouseY) {
        int listY = y + 38 + 3 + 98 + 3;
        int listH = (y + h) - listY;
        int gridStartX = x + 5;
        int gridStartY = listY + 18;
        if (mouseX < gridStartX || mouseX > x + w - 2 || mouseY < gridStartY || mouseY > listY + listH - 2) return null;
        List<TechNode> gridTechs = getSortedFilteredList();
        int cols = 6, tileW = 33, tileH = 42, gap = 2;

        for (int i = 0; i < gridTechs.size(); i++) {
            int col = i % cols;
            int row = (i / cols) - this.gridScrollRow;
            int tx = gridStartX + col * (tileW + gap);
            int ty = gridStartY + row * (tileH + gap);
            if (mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + tileH) return gridTechs.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int screenW = this.width, screenH = this.height, leftW = 218;
        int canvasX = leftW + 4, canvasY = 20, canvasW = screenW - canvasX - 3, canvasH = screenH - canvasY - 3;

        if (mouseX >= screenW - 17 && mouseX <= screenW - 4 && mouseY >= 4 && mouseY <= 16) {
            this.close();
            return true;
        }

        if (mouseX >= screenW - 170 && mouseX <= screenW - 6 && mouseY >= canvasY + 4 && mouseY <= canvasY + 18) {
            this.showFocusedBranchOnly = !this.showFocusedBranchOnly;
            centerCanvasOnSelected();
            playClick();
            return true;
        }

        if (!ResearchManager.RESEARCH_QUEUE.isEmpty() && mouseX >= 7 && mouseX <= leftW && mouseY >= 23 && mouseY <= 55) {
            int idx = (int) ((mouseX - 7) / 34);
            if (idx >= 0 && idx < ResearchManager.RESEARCH_QUEUE.size()) {
                // Источник истины — сервер: отправляем действие, локально НЕ мутируем.
                ClientPlayNetworking.send(new ResearchActionPayload(ResearchActionPayload.ACTION_REMOVE_AT, "", idx));
                playClick();
            }
            return true;
        }

        int infoY = 20 + 38 + 3, infoH = 98, btnW = 86, btnH = 15;
        int btnX = 3 + (leftW - 1) - btnW - 4;
        int btnY = infoY + infoH - btnH - 4;
        if (this.selectedTech != null && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (hasShiftDown() && this.selectedTech.status != TechStatus.RESEARCHED) {
                ClientPlayNetworking.send(new ResearchActionPayload(ResearchActionPayload.ACTION_COMPLETE_NOW, this.selectedTech.id, 0));
                playClick();
            } else if (this.selectedTech.status == TechStatus.AVAILABLE) {
                ClientPlayNetworking.send(new ResearchActionPayload(ResearchActionPayload.ACTION_QUEUE, this.selectedTech.id, 0));
                playClick();
            }
            return true;
        }

        int listY = infoY + infoH + 3;
        if (!this.searchActive && mouseX >= leftW - 16 && mouseX <= leftW && mouseY >= listY + 3 && mouseY <= listY + 15) {
            this.searchActive = true;
            return true;
        } else if (this.searchActive && mouseX >= leftW - 76 && mouseX <= leftW && mouseY >= listY + 3 && mouseY <= listY + 15) {
            if (button == 1) this.searchQuery = "";
            return true;
        } else if (this.searchActive && this.searchQuery.isEmpty()) {
            this.searchActive = false;
        }

        TechNode gridClicked = getHoveredGridTech(3, 20, leftW - 1, screenH - 23, mouseX, mouseY);
        if (gridClicked != null) {
            if (button == 0 && this.selectedTech == gridClicked && gridClicked.status == TechStatus.AVAILABLE) {
                ClientPlayNetworking.send(new ResearchActionPayload(ResearchActionPayload.ACTION_QUEUE, gridClicked.id, 0));
            }
            this.selectedTech = gridClicked;
            centerCanvasOnSelected();
            playClick();
            return true;
        }

        if (mouseX >= canvasX) {
            TechNode graphClicked = findClickedGraphNode(canvasX, canvasY, canvasW, canvasH, (int) mouseX, (int) mouseY);
            if (graphClicked != null && button == 0) {
                this.selectedTech = graphClicked;
                centerCanvasOnSelected();
                playClick();
                return true;
            }
            this.isDraggingCanvas = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private TechNode findClickedGraphNode(int canvasX, int canvasY, int canvasW, int canvasH, int mouseX, int mouseY) {
        int centerX = canvasX + canvasW / 2 + (int) this.canvasOffsetX;
        int centerY = canvasY + canvasH / 2 + (int) this.canvasOffsetY;

        if (this.showFocusedBranchOnly && this.selectedTech != null) {
            TechNode focus = this.selectedTech;
            int smallW = Math.round(38 * zoom), smallH = Math.round(48 * zoom);
            int focusW = Math.round(58 * zoom), focusH = Math.round(74 * zoom);
            int gapX = Math.round(50 * zoom), rowGapY = Math.round(82 * zoom);
            int fx = centerX - focusW / 2, fy = centerY - focusH / 2;

            if (mouseX >= fx && mouseX < fx + focusW && mouseY >= fy && mouseY < fy + focusH) {
                return focus;
            }

            List<TechNode> parents = new ArrayList<>();
            for (String pid : focus.prerequisites) {
                TechNode p = ResearchManager.getById(pid);
                if (p != null) parents.add(p);
            }
            for (int i = 0; i < parents.size(); i++) {
                TechNode p = parents.get(i);
                int px = centerX + (int) ((i - (parents.size() - 1) / 2.0f) * gapX) - smallW / 2;
                int py = fy - rowGapY - smallH / 2;
                if (mouseX >= px && mouseX < px + smallW && mouseY >= py && mouseY < py + smallH) return p;
            }

            List<TechNode> children = new ArrayList<>();
            for (TechNode t : ResearchManager.TECHS) {
                if (t.prerequisites.contains(focus.id)) children.add(t);
            }
            for (int i = 0; i < children.size(); i++) {
                TechNode ch = children.get(i);
                int cx = centerX + (int) ((i - (children.size() - 1) / 2.0f) * gapX) - smallW / 2;
                int cy = fy + focusH + Math.round(34 * zoom);
                if (mouseX >= cx && mouseX < cx + smallW && mouseY >= cy && mouseY < cy + smallH) return ch;
            }
            return null;
        } else {
            int stepX = Math.round(54 * zoom), stepY = Math.round(82 * zoom);
            int nodeW = Math.round(38 * zoom), nodeH = Math.round(48 * zoom);
            for (TechNode t : ResearchManager.TECHS) {
                boolean isSel = (t == this.selectedTech);
                int w = isSel ? Math.round(46 * zoom) : nodeW;
                int h = isSel ? Math.round(58 * zoom) : nodeH;
                int tx = centerX + t.treeCol * stepX - w / 2;
                int ty = centerY + (t.treeRow - 2) * stepY - h / 2;
                if (mouseX >= tx && mouseX < tx + w && mouseY >= ty && mouseY < ty + h) return t;
            }
            return null;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingCanvas = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDraggingCanvas) {
            this.canvasOffsetX += deltaX;
            this.canvasOffsetY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < 220) {
            int maxRows = Math.max(0, (getSortedFilteredList().size() + 5) / 6 - 3);
            this.gridScrollRow = MathHelper.clamp(this.gridScrollRow - (int) Math.signum(verticalAmount), 0, maxRows);
            return true;
        } else {
            this.zoom = MathHelper.clamp(this.zoom + (float) verticalAmount * 0.1f, 0.65f, 1.65f);
            return true;
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.searchActive && chr >= 32) {
            this.searchQuery += chr;
            this.gridScrollRow = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchActive) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchActive = false;
                this.searchQuery = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchActive = false;
                return true;
            }
            return true;
        }
        if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawFactorioHeader(DrawContext context, int hx, int hy, int hw, String title, int rightPadding) {
        context.fill(hx, hy, hx + hw, hy + 14, 0xFF232426);
        drawScaledText(context, title, hx + 5, hy + 4, 0.82f, 0xFFFFDCA0, true);
        int titleW = Math.round(this.textRenderer.getWidth(title) * 0.82f);
        for (int rx = hx + 10 + titleW; rx < hx + hw - rightPadding; rx += 3) {
            context.fill(rx, hy + 3, rx + 1, hy + 11, 0xFF17181A);
        }
    }

    private void drawFactorioCheckbox(DrawContext context, int cx, int cy, boolean checked) {
        context.fill(cx, cy, cx + 9, cy + 9, 0xFF111111);
        context.fill(cx + 1, cy + 1, cx + 8, cy + 8, checked ? 0xFFE59524 : 0xFF3E4044);
        if (checked) {
            context.fill(cx + 2, cy + 4, cx + 4, cy + 6, 0xFF141414);
            context.fill(cx + 4, cy + 2, cx + 7, cy + 5, 0xFF141414);
        }
    }

    private void drawIconButton(DrawContext context, int bx, int by, int bw, int bh, String sym, int mouseX, int mouseY, boolean active) {
        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        context.fill(bx, by, bx + bw, by + bh, 0xFF111111);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, active ? 0xFFE59524 : (hov ? 0xFF4C4E52 : 0xFF35373A));
        drawScaledTextCentered(context, sym, bx + bw / 2, by + 3, 0.75f, active ? 0xFF111111 : 0xFFE6E6E6, false);
    }

    private void drawClockIcon(DrawContext context, int cx, int cy) {
        context.fill(cx + 1, cy, cx + 7, cy + 8, 0xFF141414);
        context.fill(cx, cy + 1, cx + 8, cy + 7, 0xFF141414);
        context.fill(cx + 1, cy + 1, cx + 7, cy + 7, 0xFFD8D8D8);
        context.fill(cx + 3, cy + 2, cx + 4, cy + 5, 0xFF232426);
        context.fill(cx + 4, cy + 4, cx + 6, cy + 5, 0xFF232426);
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

    private void playClick() {
        if (this.client != null && this.client.player != null) {
            this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.15f);
        }
    }
}