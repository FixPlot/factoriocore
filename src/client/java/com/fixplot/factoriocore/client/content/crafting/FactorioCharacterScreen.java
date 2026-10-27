package com.fixplot.factoriocore.client.content.crafting;

import com.fixplot.factoriocore.client.config.FactorioConfigScreen;
import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.network.RequestCraftPayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FactorioCharacterScreen extends Screen {

    private static final int COLS = 10;
    private static final int INV_ROWS = 6;
    private static final int CRAFT_ROWS = 8;
    private static final int SLOT_SIZE = 18;

    private static final int PANEL_W = 408;
    private static final int PANEL_H = 212;

    private FactorioRecipes.Category activeCategory = FactorioRecipes.Category.LOGISTICS;
    private boolean searchActive = false;
    private String searchQuery = "";

    public FactorioCharacterScreen() {
        super(Text.translatable("gui.factoriocore.character"));
        FactorioRecipes.init();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int mapMainGridToPacketSlot(int gridIdx) {
        if (gridIdx >= 0 && gridIdx < 9) {
            return 36 + gridIdx;
        }
        if (gridIdx >= 9 && gridIdx < 36) {
            return gridIdx;
        }
        return -1;
    }

    private int mapEquipIndexToPacketSlot(int equipIdx) {
        if (equipIdx >= 0 && equipIdx < 4) {
            return 5 + equipIdx;
        }
        if (equipIdx == 4) {
            return 45;
        }
        return -1;
    }

    private ItemStack getEquipStack(PlayerInventory inv, int equipIdx) {
        if (equipIdx >= 0 && equipIdx < 4) {
            return inv.armor.get(3 - equipIdx);
        }
        if (equipIdx == 4) {
            return inv.offHand.get(0);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        if (this.client == null || this.client.player == null) return;

        PlayerEntity player = this.client.player;
        PlayerInventory inv = player.getInventory();

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        int midX = x + PANEL_W / 2;

        // 1. Внешний корпус двухпанельного окна Factorio
        context.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFF121213);
        context.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF313235);
        context.fill(midX - 1, y + 1, midX + 1, y + PANEL_H - 1, 0xFF18191A);

        // 2. Шапка слева: "Персонаж — Ник"
        Text leftTitle = Text.translatable("gui.factoriocore.character")
                .copy()
                .append(Text.literal(" \u2014 " + player.getName().getString()).styled(s -> s.withColor(0xFFFFCC66)));
        drawFactorioHeader(context, x + 3, y + 3, PANEL_W / 2 - 5, leftTitle, 6);

        // 3. Шапка справа: "Создание" + поиск и настройки
        int rightHeaderX = midX + 2;
        int rightHeaderW = PANEL_W / 2 - 5;
        int rightReserved = this.searchActive ? 96 : 38;
        drawFactorioHeader(context, rightHeaderX, y + 3, rightHeaderW, Text.translatable("gui.factoriocore.crafting"), rightReserved);

        if (this.searchActive) {
            int boxW = 90;
            int boxH = 12;
            int boxX = x + PANEL_W - boxW - 5;
            int boxY = y + 4;
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF111112);
            context.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1E1F22);
            context.fill(boxX + 1, boxY + boxH - 2, boxX + boxW - 1, boxY + boxH - 1, 0xFFE59524);

            String cursor = ((System.currentTimeMillis() / 500L) % 2L == 0L) ? "_" : "";
            String shown = this.textRenderer.trimToWidth(this.searchQuery, boxW - 12) + cursor;
            context.drawText(this.textRenderer, shown, boxX + 4, boxY + 2, 0xFFFFEAAA, false);
        } else {
            int searchBtnX = x + PANEL_W - 19;
            int searchBtnY = y + 4;
            drawMagnifierButton(context, searchBtnX, searchBtnY, 14, 12, mouseX, mouseY);
            drawGearSettingsButton(context, searchBtnX - 16, searchBtnY, 14, 12, mouseX, mouseY);
        }

        // 4. Левая панель: Сетка инвентаря (10x6) + экипировка
        int leftGridX = x + 12;
        int leftGridY = y + 22;

        context.fill(leftGridX - 2, leftGridY - 2,
                leftGridX + COLS * SLOT_SIZE + 2, leftGridY + INV_ROWS * SLOT_SIZE + 2, 0xFF18181A);

        ItemStack hoveredInvStack = ItemStack.EMPTY;
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = c + r * COLS;
                int sx = leftGridX + c * SLOT_SIZE;
                int sy = leftGridY + r * SLOT_SIZE;
                boolean unlocked = (idx < 36);
                boolean hovered = unlocked && isHoveringSlot(mouseX, mouseY, sx, sy);

                drawFactorioSlot(context, sx, sy, !unlocked, hovered);
                if (unlocked) {
                    ItemStack stack = inv.main.get(idx);
                    if (!stack.isEmpty()) {
                        drawStackInSlot(context, stack, sx + 1, sy + 1);
                        if (hovered) {
                            hoveredInvStack = stack;
                        }
                    }
                }
            }
        }

        int divY = leftGridY + INV_ROWS * SLOT_SIZE + 8;
        context.fill(leftGridX - 2, divY, leftGridX + COLS * SLOT_SIZE + 2, divY + 1, 0xFF202123);
        context.fill(leftGridX - 2, divY + 1, leftGridX + COLS * SLOT_SIZE + 2, divY + 2, 0xFF3E4043);

        int equipY = y + PANEL_H - 28;
        context.fill(leftGridX - 2, equipY - 2, leftGridX + 4 * SLOT_SIZE + 2, equipY + SLOT_SIZE + 2, 0xFF18181A);
        int offhandX = leftGridX + 4 * SLOT_SIZE + 8;
        context.fill(offhandX - 2, equipY - 2, offhandX + SLOT_SIZE + 2, equipY + SLOT_SIZE + 2, 0xFF18181A);

        for (int e = 0; e < 5; e++) {
            int ex = (e < 4) ? (leftGridX + e * SLOT_SIZE) : offhandX;
            boolean hovered = isHoveringSlot(mouseX, mouseY, ex, equipY);
            drawFactorioSlot(context, ex, equipY, false, hovered);

            ItemStack eqStack = getEquipStack(inv, e);
            if (!eqStack.isEmpty()) {
                drawStackInSlot(context, eqStack, ex + 1, equipY + 1);
                if (hovered) {
                    hoveredInvStack = eqStack;
                }
            } else {
                drawEquipmentSilhouette(context, ex + 1, equipY + 1, e);
            }
        }

        // 5. Правая панель: 4 вкладки категорий и сетка крафта (10x8)
        int rightGridX = midX + 12;
        int tabBarY = y + 22;
        int tabW = (COLS * SLOT_SIZE + 4) / 4;
        int tabH = 30;

        FactorioRecipes.Category[] cats = FactorioRecipes.Category.values();
        ItemStack[] tabIcons = {
                new ItemStack(ModBlocks.BELT),
                new ItemStack(ModBlocks.STONE_FURNACE),
                new ItemStack(ModItems.ELECTRONIC_CIRCUIT),
                new ItemStack(ModItems.REPAIR_PACK)
        };

        for (int i = 0; i < 4; i++) {
            int tx = rightGridX - 2 + i * tabW;
            boolean selected = (cats[i] == this.activeCategory);
            boolean hovered = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabBarY && mouseY < tabBarY + tabH;

            int tabBg = selected ? 0xFF6B6D70 : (hovered ? 0xFF4A4C50 : 0xFF383A3D);
            context.fill(tx, tabBarY, tx + tabW - 1, tabBarY + tabH, 0xFF18181A);
            context.fill(tx + 1, tabBarY + 1, tx + tabW - 2, tabBarY + tabH - (selected ? 0 : 1), tabBg);

            context.getMatrices().push();
            context.getMatrices().translate(tx + tabW / 2.0f - 10.5f, tabBarY + 4.5f, 100.0f);
            context.getMatrices().scale(1.3f, 1.3f, 1.0f);
            context.drawItem(tabIcons[i], 0, 0);
            context.getMatrices().pop();
        }

        int rightGridY = y + 56;
        context.fill(rightGridX - 2, rightGridY - 2,
                rightGridX + COLS * SLOT_SIZE + 2, rightGridY + CRAFT_ROWS * SLOT_SIZE + 2, 0xFF18181A);

        for (int r = 0; r < CRAFT_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = rightGridX + c * SLOT_SIZE;
                int sy = rightGridY + r * SLOT_SIZE;
                context.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF1A1B1D);
                context.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF242528);
            }
        }

        FactorioRecipes.Recipe hoveredRecipe = null;
        String queryLower = this.searchQuery.toLowerCase(Locale.ROOT);
        List<List<FactorioRecipes.Recipe>> compactedRows = FactorioRecipes.getCompactedRows(this.activeCategory, queryLower, COLS);

        for (int r = 0; r < Math.min(CRAFT_ROWS, compactedRows.size()); r++) {
            List<FactorioRecipes.Recipe> rowRecipes = compactedRows.get(r);
            for (int c = 0; c < Math.min(COLS, rowRecipes.size()); c++) {
                FactorioRecipes.Recipe recipe = rowRecipes.get(c);
                int sx = rightGridX + c * SLOT_SIZE;
                int sy = rightGridY + r * SLOT_SIZE;

                int craftable = player.isCreative() ? 99 : FactorioRecipes.getCraftableCount(inv, recipe);
                boolean canCraft = craftable > 0;
                boolean hovered = isHoveringSlot(mouseX, mouseY, sx, sy);
                if (hovered) {
                    hoveredRecipe = recipe;
                }

                int borderCol = hovered ? 0xFFE59524 : 0xFF141414;
                int innerCol = hovered ? 0xFF525458 : (canCraft ? 0xFF3A3C3F : 0xFF28292C);
                context.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, borderCol);
                context.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, innerCol);

                context.drawItem(recipe.output(), sx + 1, sy + 1);

                if (craftable > 0) {
                    String cntStr = (craftable > 99) ? "99+" : String.valueOf(craftable);
                    context.getMatrices().push();
                    context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                    context.getMatrices().scale(0.65f, 0.65f, 1.0f);
                    int tw = this.textRenderer.getWidth(cntStr);
                    int tx = (int) ((sx + 17) / 0.65f) - tw;
                    int ty = (int) ((sy + 12) / 0.65f);
                    context.drawText(this.textRenderer, cntStr, tx, ty, 0xFFFFFFFF, true);
                    context.getMatrices().pop();
                }
            }
        }

        // 6. Предмет на курсоре
        ItemStack cursorStack = player.playerScreenHandler.getCursorStack();
        if (!cursorStack.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, 350.0f);
            context.drawItem(cursorStack, mouseX - 8, mouseY - 8);
            context.drawItemInSlot(this.textRenderer, cursorStack, mouseX - 8, mouseY - 8);
            context.getMatrices().pop();
        }

        // 7. Очередь крафта в левом нижнем углу экрана
        ClientCraftingQueueHud.renderBottomLeftQueue(context, true);

        // 8. Тултипы рецептов и предметов
        if (cursorStack.isEmpty()) {
            if (hoveredRecipe != null) {
                renderCustomFactorioRecipeCard(context, hoveredRecipe, inv, mouseX, mouseY);
            } else if (!hoveredInvStack.isEmpty()) {
                context.drawItemTooltip(this.textRenderer, hoveredInvStack, mouseX, mouseY);
            }
        }
    }

    public void renderCustomFactorioRecipeCard(DrawContext context, FactorioRecipes.Recipe recipe,
                                               PlayerInventory inv, int mouseX, int mouseY) {
        renderRecipeCard(context, this.textRenderer, this.width, this.height, recipe, inv, mouseX, mouseY, true);
    }

    /**
     * Общий статический рендер карточки рецепта в стиле Factorio (переиспользуется в CharacterScreen и AssemblerScreen).
     */
    public static void renderRecipeCard(DrawContext context, TextRenderer textRenderer,
                                        int screenWidth, int screenHeight,
                                        FactorioRecipes.Recipe recipe, PlayerInventory inv,
                                        int mouseX, int mouseY, boolean allowSubCraftHighlight) {
        Map<Item, Integer> invCounts = FactorioRecipes.snapshotInventory(inv);
        Map<Item, Integer> totalRaw = FactorioRecipes.calculateTotalRaw(recipe);

        String titleStr = recipe.output().getName().getString()
                + (recipe.output().getCount() > 1 ? " \u00d7" + recipe.output().getCount() : "");

        int cardW = Math.max(156, textRenderer.getWidth(titleStr) + 32);
        for (FactorioRecipes.Ingredient ing : recipe.ingredients()) {
            int have = invCounts.getOrDefault(ing.item(), 0);
            String line = ing.count() + " \u00d7 " + ing.item().getName().getString() + " (" + have + ")";
            cardW = Math.max(cardW, textRenderer.getWidth(line) + 28);
        }

        if (!totalRaw.isEmpty()) {
            int rawW = 16 + textRenderer.getWidth(Text.translatable("gui.factoriocore.total_raw"));
            for (Map.Entry<Item, Integer> entry : totalRaw.entrySet()) {
                rawW += 20 + textRenderer.getWidth(String.valueOf(entry.getValue()));
            }
            cardW = Math.max(cardW, rawW + 8);
        }

        int ingCount = recipe.ingredients().size();
        int rawSectionH = totalRaw.isEmpty() ? 0 : 28;
        int cardH = 24 + 14 + ingCount * 14 + rawSectionH + 6;

        int tx = mouseX + 12;
        int ty = mouseY - 8;
        if (tx + cardW > screenWidth - 6) {
            tx = mouseX - cardW - 12;
        }
        if (ty + cardH > screenHeight - 6) {
            ty = screenHeight - cardH - 6;
        }
        if (ty < 6) ty = 6;

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);

        context.fill(tx, ty, tx + cardW, ty + cardH, 0xF8111214);
        context.fill(tx + 1, ty + 1, tx + cardW - 1, ty + cardH - 1, 0xF426272A);

        context.fill(tx + 2, ty + 2, tx + cardW - 2, ty + 21, 0xFF3E301B);
        context.fill(tx + 2, ty + 20, tx + cardW - 2, ty + 21, 0xFFE59524);
        context.drawItem(recipe.output(), tx + 4, ty + 3);
        context.drawText(textRenderer, titleStr, tx + 24, ty + 7, 0xFFFFD670, true);

        int curY = ty + 25;

        drawClockIconStatic(context, tx + 6, curY + 1);
        context.drawText(textRenderer,
                Text.translatable("gui.factoriocore.craft_time", String.format(Locale.ROOT, "%.1f", recipe.getSeconds())),
                tx + 18, curY + 1, 0xFFCCCCCC, false);
        curY += 13;

        for (FactorioRecipes.Ingredient ing : recipe.ingredients()) {
            int have = invCounts.getOrDefault(ing.item(), 0);
            int textColor;
            if (have >= ing.count()) {
                textColor = 0xFF66EE66;
            } else if (allowSubCraftHighlight) {
                FactorioRecipes.Recipe sub = FactorioRecipes.getByOutput(ing.item());
                int canSub = (sub != null) ? FactorioRecipes.getCraftableCount(inv, sub) : 0;
                textColor = (have + canSub >= ing.count()) ? 0xFFFFCC44 : 0xFFFF5555;
            } else {
                textColor = 0xFFFF5555;
            }

            context.getMatrices().push();
            context.getMatrices().translate(tx + 5, curY, 0.0f);
            context.getMatrices().scale(0.75f, 0.75f, 1.0f);
            context.drawItem(new ItemStack(ing.item()), 0, 0);
            context.getMatrices().pop();

            String ingLine = ing.count() + " \u00d7 " + ing.item().getName().getString() + " (" + have + ")";
            context.drawText(textRenderer, ingLine, tx + 20, curY + 2, textColor, false);
            curY += 14;
        }

        if (!totalRaw.isEmpty()) {
            curY += 2;
            context.fill(tx + 4, curY, tx + cardW - 4, curY + 1, 0xFF18191B);
            context.fill(tx + 4, curY + 1, tx + cardW - 4, curY + 2, 0xFF3A3C40);
            curY += 5;

            Text rawLabel = Text.translatable("gui.factoriocore.total_raw");
            context.drawText(textRenderer, rawLabel, tx + 6, curY + 4, 0xFF999B9F, false);

            int iconX = tx + 10 + textRenderer.getWidth(rawLabel);
            for (Map.Entry<Item, Integer> entry : totalRaw.entrySet()) {
                context.getMatrices().push();
                context.getMatrices().translate(iconX, curY + 1, 0.0f);
                context.getMatrices().scale(0.8f, 0.8f, 1.0f);
                context.drawItem(new ItemStack(entry.getKey()), 0, 0);
                context.getMatrices().pop();

                String countText = String.valueOf(entry.getValue());
                context.drawText(textRenderer, countText, iconX + 14, curY + 4, 0xFFE6E6E6, true);
                iconX += 18 + textRenderer.getWidth(countText);
            }
        }

        context.getMatrices().pop();
    }

    private static void drawClockIconStatic(DrawContext context, int cx, int cy) {
        context.fill(cx + 1, cy, cx + 7, cy + 8, 0xFF141414);
        context.fill(cx, cy + 1, cx + 8, cy + 7, 0xFF141414);
        context.fill(cx + 1, cy + 1, cx + 7, cy + 7, 0xFFD8D8D8);
        context.fill(cx + 3, cy + 2, cx + 4, cy + 5, 0xFF232426);
        context.fill(cx + 4, cy + 4, cx + 6, cy + 5, 0xFF232426);
    }

    private void drawGearSettingsButton(DrawContext context, int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int bg = hovered ? 0xFFE59524 : 0xFF35373A;
        int col = hovered ? 0xFF141414 : 0xFFE6E6E6;
        context.fill(bx, by, bx + bw, by + bh, 0xFF141414);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);
        context.fill(bx + 4, by + 3, bx + 10, by + 9, col);
        context.fill(bx + 6, by + 2, bx + 8, by + 10, col);
        context.fill(bx + 3, by + 5, bx + 11, by + 7, col);
        context.fill(bx + 6, by + 5, bx + 8, by + 7, bg);
    }

    private void drawMagnifierButton(DrawContext context, int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int bg = hovered ? 0xFF4C4E52 : 0xFF35373A;
        context.fill(bx, by, bx + bw, by + bh, 0xFF141414);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);

        int lx = bx + 4;
        int ly = by + 2;
        int col = 0xFFE6E6E6;
        context.fill(lx + 1, ly, lx + 4, ly + 1, col);
        context.fill(lx, ly + 1, lx + 1, ly + 4, col);
        context.fill(lx + 4, ly + 1, lx + 5, ly + 4, col);
        context.fill(lx + 1, ly + 4, lx + 4, ly + 5, col);
        context.fill(lx + 4, ly + 5, lx + 6, ly + 7, col);
    }

    private void drawEquipmentSilhouette(DrawContext context, int sx, int sy, int equipIdx) {
        int c = 0xFF525458;
        switch (equipIdx) {
            case 0 -> {
                context.fill(sx + 4, sy + 3, sx + 12, sy + 6, c);
                context.fill(sx + 3, sy + 6, sx + 13, sy + 12, c);
                context.fill(sx + 5, sy + 8, sx + 11, sy + 12, 0xFF333538);
            }
            case 1 -> {
                context.fill(sx + 3, sy + 3, sx + 13, sy + 7, c);
                context.fill(sx + 6, sy + 3, sx + 10, sy + 5, 0xFF333538);
                context.fill(sx + 4, sy + 7, sx + 12, sy + 13, c);
            }
            case 2 -> {
                context.fill(sx + 4, sy + 3, sx + 12, sy + 6, c);
                context.fill(sx + 4, sy + 6, sx + 7, sy + 13, c);
                context.fill(sx + 9, sy + 6, sx + 12, sy + 13, c);
            }
            case 3 -> {
                context.fill(sx + 4, sy + 6, sx + 7, sy + 12, c);
                context.fill(sx + 3, sy + 10, sx + 7, sy + 13, c);
                context.fill(sx + 9, sy + 6, sx + 12, sy + 12, c);
                context.fill(sx + 9, sy + 10, sx + 13, sy + 13, c);
            }
            default -> {
                context.fill(sx + 4, sy + 3, sx + 12, sy + 10, c);
                context.fill(sx + 5, sy + 10, sx + 11, sy + 12, c);
                context.fill(sx + 6, sy + 12, sx + 10, sy + 14, c);
            }
        }
    }

    private void drawFactorioHeader(DrawContext context, int hx, int hy, int hw, Text title, int rightPadding) {
        context.fill(hx, hy, hx + hw, hy + 14, 0xFF232426);
        context.drawText(this.textRenderer, title, hx + 6, hy + 3, 0xFFE6E6E6, false);

        int startRibs = hx + 12 + this.textRenderer.getWidth(title);
        int endRibs = hx + hw - rightPadding;
        for (int rx = startRibs; rx < endRibs; rx += 3) {
            context.fill(rx, hy + 3, rx + 1, hy + 11, 0xFF18191B);
        }
    }

    private void drawFactorioSlot(DrawContext context, int sx, int sy, boolean locked, boolean hovered) {
        int border = hovered ? 0xFFE59524 : 0xFF141414;
        int inner = locked ? 0xFF202124 : (hovered ? 0xFF4A4C50 : 0xFF333538);
        context.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, border);
        context.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, inner);
    }

    private void drawStackInSlot(DrawContext context, ItemStack stack, int x, int y) {
        context.drawItem(stack, x, y);
        context.drawItemInSlot(this.textRenderer, stack, x, y);
    }

    private boolean isHoveringSlot(double mx, double my, int sx, int sy) {
        return mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.client == null || this.client.player == null || this.client.interactionManager == null) {
            return false;
        }

        if (ClientCraftingQueueHud.handleMouseClick(mouseX, mouseY, button, this.width, this.height)) {
            return true;
        }

        int syncId = this.client.player.playerScreenHandler.syncId;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;
        int midX = x + PANEL_W / 2;

        // 1. Кнопки поиска и настроек в шапке
        if (!this.searchActive) {
            int searchBtnX = x + PANEL_W - 19;
            int searchBtnY = y + 4;
            int gearBtnX = searchBtnX - 16;
            if (mouseX >= gearBtnX && mouseX < gearBtnX + 14 && mouseY >= searchBtnY && mouseY < searchBtnY + 12) {
                this.client.setScreen(new FactorioConfigScreen(this));
                return true;
            }
            if (mouseX >= searchBtnX && mouseX < searchBtnX + 14 && mouseY >= searchBtnY && mouseY < searchBtnY + 12) {
                this.searchActive = true;
                return true;
            }
        } else {
            int boxX = x + PANEL_W - 95;
            int boxY = y + 4;
            if (mouseX >= boxX && mouseX <= boxX + 90 && mouseY >= boxY && mouseY <= boxY + 12) {
                if (button == 1) {
                    this.searchQuery = "";
                }
                return true;
            } else if (this.searchQuery.isEmpty()) {
                this.searchActive = false;
            }
        }

        // 2. Клик по основной сетке инвентаря (36 открытых слотов в сетке INV_ROWS x COLS)
        int leftGridX = x + 12;
        int leftGridY = y + 22;
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = c + r * COLS;
                if (idx >= 36) break;
                int sx = leftGridX + c * SLOT_SIZE;
                int sy = leftGridY + r * SLOT_SIZE;
                if (isHoveringSlot(mouseX, mouseY, sx, sy)) {
                    int packetSlot = mapMainGridToPacketSlot(idx);
                    if (packetSlot >= 0) {
                        SlotActionType action = hasShiftDown() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
                        this.client.interactionManager.clickSlot(syncId, packetSlot, button, action, this.client.player);
                        return true;
                    }
                }
            }
        }

        // 3. Клик по 4 слотам брони и слоту левой руки
        int equipY = y + PANEL_H - 28;
        int offhandX = leftGridX + 4 * SLOT_SIZE + 8;
        for (int e = 0; e < 5; e++) {
            int ex = (e < 4) ? (leftGridX + e * SLOT_SIZE) : offhandX;
            if (isHoveringSlot(mouseX, mouseY, ex, equipY)) {
                int packetSlot = mapEquipIndexToPacketSlot(e);
                if (packetSlot >= 0) {
                    SlotActionType action = hasShiftDown() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
                    this.client.interactionManager.clickSlot(syncId, packetSlot, button, action, this.client.player);
                    return true;
                }
            }
        }

        // 4. Клик по 4 вкладкам категорий крафта
        int rightGridX = midX + 12;
        int tabBarY = y + 22;
        int tabW = (COLS * SLOT_SIZE + 4) / 4;
        int tabH = 30;
        FactorioRecipes.Category[] cats = FactorioRecipes.Category.values();
        for (int i = 0; i < 4; i++) {
            int tx = rightGridX - 2 + i * tabW;
            if (mouseX >= tx && mouseX < tx + tabW && mouseY >= tabBarY && mouseY < tabBarY + tabH) {
                this.activeCategory = cats[i];
                return true;
            }
        }

        // 5. Клик по рецепту крафта
        int rightGridY = y + 56;
        String queryLower = this.searchQuery.toLowerCase(Locale.ROOT);
        List<List<FactorioRecipes.Recipe>> compactedRows = FactorioRecipes.getCompactedRows(this.activeCategory, queryLower, COLS);
        for (int r = 0; r < Math.min(CRAFT_ROWS, compactedRows.size()); r++) {
            List<FactorioRecipes.Recipe> rowRecipes = compactedRows.get(r);
            for (int c = 0; c < Math.min(COLS, rowRecipes.size()); c++) {
                FactorioRecipes.Recipe recipe = rowRecipes.get(c);
                int sx = rightGridX + c * SLOT_SIZE;
                int sy = rightGridY + r * SLOT_SIZE;
                if (isHoveringSlot(mouseX, mouseY, sx, sy)) {
                    int craftAction = hasShiftDown() ? 999 : (button == 1 ? 5 : 1);
                    ClientPlayNetworking.send(new RequestCraftPayload(recipe.id(), craftAction));
                    return true;
                }
            }
        }

        // 6. Выброс предмета с курсора при клике за пределами окна
        if (mouseX < x || mouseX > x + PANEL_W || mouseY < y || mouseY > y + PANEL_H) {
            if (!this.client.player.playerScreenHandler.getCursorStack().isEmpty()) {
                this.client.interactionManager.clickSlot(syncId, -999, button, SlotActionType.PICKUP, this.client.player);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.searchActive && chr >= 32) {
            this.searchQuery += chr;
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

    @Override
    public void close() {
        if (this.client != null && this.client.player != null && this.client.interactionManager != null) {
            if (!this.client.player.playerScreenHandler.getCursorStack().isEmpty()) {
                int syncId = this.client.player.playerScreenHandler.syncId;
                this.client.interactionManager.clickSlot(syncId, -999, 0, SlotActionType.PICKUP, this.client.player);
            }
        }
        super.close();
    }
}