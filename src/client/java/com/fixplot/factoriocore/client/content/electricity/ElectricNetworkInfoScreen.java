package com.fixplot.factoriocore.client.content.electricity;

import com.fixplot.factoriocore.content.electricity.CreativeEnergySourceBlock;
import com.fixplot.factoriocore.content.electricity.ElectricPoleBlock;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ElectricNetworkInfoScreen extends Screen {

    private static final String[] TIMEFRAME_LABELS = {
            "gui.factoriocore.time.5s", "gui.factoriocore.time.1m", "gui.factoriocore.time.10m",
            "gui.factoriocore.time.1h", "gui.factoriocore.time.10h", "gui.factoriocore.time.50h",
            "gui.factoriocore.time.250h", "gui.factoriocore.time.1000h", "gui.factoriocore.time.all"
    };
    private static final float[] TIMEFRAME_MAX_NUM = {
            5.0f, 60.0f, 10.0f, 60.0f, 10.0f, 50.0f, 250.0f, 1000.0f, 100.0f
    };
    private static final int[] TICKS_PER_POINT = {
            1, 12, 120, 720, 7200, 36000, 180000, 720000, 240
    };

    public static final int GRAPH_POINTS = 100;
    private static final int RAW_BUFFER_SIZE = 12000;

    private static final Map<Block, long[]> CONSUMER_RAW_TICKS = new LinkedHashMap<>();
    private static final Map<Block, long[]> PRODUCER_RAW_TICKS = new LinkedHashMap<>();
    private static final Map<Block, long[]> ACCUMULATOR_RAW_TICKS = new LinkedHashMap<>();
    private static int rawWriteCursor = 0;
    private static int totalRecordedTicks = 0;
    private static long lastRecordedWorldTick = -1L;
    private static long lastNetworkScanTick = -100L;
    private static BlockPos lastViewedPolePos = null;
    private static ClientWorld lastTrackedWorld = null;
    private static PowerNetworkManager.NetworkSnapshot cachedSnapshot = null;

    private final BlockPos polePos;
    private int selectedTimeframe = 0;
    private Block filteredConsumerBlock = null;
    private Block filteredProducerBlock = null;
    private boolean searchActive = false;
    private String searchQuery = "";

    public ElectricNetworkInfoScreen(BlockPos polePos) {
        super(Text.translatable("gui.factoriocore.electric_network.title"));
        this.polePos = polePos;
        lastViewedPolePos = polePos;
        lastNetworkScanTick = -100L;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void resetHistory() {
        CONSUMER_RAW_TICKS.clear();
        PRODUCER_RAW_TICKS.clear();
        ACCUMULATOR_RAW_TICKS.clear();
        rawWriteCursor = 0;
        totalRecordedTicks = 0;
        lastRecordedWorldTick = -1L;
        lastNetworkScanTick = -100L;
        lastViewedPolePos = null;
        cachedSnapshot = null;
    }

    public static void tickClientHistory(MinecraftClient client) {
        if (client.world == null) {
            if (lastTrackedWorld != null) {
                lastTrackedWorld = null;
                resetHistory();
            }
            return;
        }
        if (client.world != lastTrackedWorld) {
            lastTrackedWorld = client.world;
            resetHistory();
        }
        if (client.isPaused()) return;

        long wt = client.world.getTime();
        if (wt == lastRecordedWorldTick) return;
        lastRecordedWorldTick = wt;

        boolean screenOpen = client.currentScreen instanceof ElectricNetworkInfoScreen;
        BlockPos target = lastViewedPolePos;
        if (screenOpen) {
            target = ((ElectricNetworkInfoScreen) client.currentScreen).polePos;
            lastViewedPolePos = target;
        } else if (target == null && client.player != null && wt % 40 == 0) {
            target = findNearbyPole(client.world, client.player.getBlockPos());
            if (target != null) lastViewedPolePos = target;
        }
        if (target == null) return;

        int scanInterval = screenOpen ? 5 : 20;
        if (cachedSnapshot == null || Math.abs(wt - lastNetworkScanTick) >= scanInterval) {
            cachedSnapshot = PowerNetworkManager.scanNetwork(client.world, target);
            lastNetworkScanTick = wt;
        }

        PowerNetworkManager.NetworkSnapshot snap = cachedSnapshot;
        if (snap == null) return;

        Set<Block> seenConsumers = new HashSet<>();
        for (PowerNetworkManager.MachineGroupStat stat : snap.consumers()) {
            seenConsumers.add(stat.block());
            long[] buf = CONSUMER_RAW_TICKS.computeIfAbsent(stat.block(), k -> new long[RAW_BUFFER_SIZE]);
            buf[rawWriteCursor] = stat.watts();
        }
        for (Map.Entry<Block, long[]> e : CONSUMER_RAW_TICKS.entrySet()) {
            if (!seenConsumers.contains(e.getKey())) {
                e.getValue()[rawWriteCursor] = 0L;
            }
        }

        Set<Block> seenProducers = new HashSet<>();
        for (PowerNetworkManager.MachineGroupStat stat : snap.producers()) {
            seenProducers.add(stat.block());
            long[] buf = PRODUCER_RAW_TICKS.computeIfAbsent(stat.block(), k -> new long[RAW_BUFFER_SIZE]);
            buf[rawWriteCursor] = stat.watts();
        }
        for (Map.Entry<Block, long[]> e : PRODUCER_RAW_TICKS.entrySet()) {
            if (!seenProducers.contains(e.getKey())) {
                e.getValue()[rawWriteCursor] = 0L;
            }
        }

        // Записываем историю заряда аккумуляторов (в Джоулях) для 3-й колонки
        long[] accBuf = ACCUMULATOR_RAW_TICKS.computeIfAbsent(ModBlocks.ACCUMULATOR, k -> new long[RAW_BUFFER_SIZE]);
        accBuf[rawWriteCursor] = snap.accumulatorStoredJoules();

        rawWriteCursor = (rawWriteCursor + 1) % RAW_BUFFER_SIZE;
        totalRecordedTicks++;
    }

    private static BlockPos findNearbyPole(World world, BlockPos center) {
        for (int dx = -12; dx <= 12; dx += 2) {
            for (int dz = -12; dz <= 12; dz += 2) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = center.add(dx, dy, dz);
                    Block b = world.getBlockState(p).getBlock();
                    if (b instanceof ElectricPoleBlock || b instanceof CreativeEnergySourceBlock) {
                        return p.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private PowerNetworkManager.NetworkSnapshot getOrScanSnapshot(World world) {
        if (cachedSnapshot == null) {
            cachedSnapshot = PowerNetworkManager.scanNetwork(world, this.polePos);
            lastNetworkScanTick = world.getTime();
        }
        return cachedSnapshot;
    }

    private List<PowerNetworkManager.MachineGroupStat> getFilteredStats(
            List<PowerNetworkManager.MachineGroupStat> stats,
            Map<Block, long[]> rawHistoryMap) {
        List<PowerNetworkManager.MachineGroupStat> displayList = new ArrayList<>();
        String queryLower = this.searchQuery.toLowerCase(Locale.ROOT);
        for (PowerNetworkManager.MachineGroupStat st : stats) {
            if (!queryLower.isBlank()) {
                String name = st.block().getName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(queryLower)) continue;
            }
            long avgWatts = computeTimeframeAverageWatts(rawHistoryMap, st.block(), st.watts());
            displayList.add(new PowerNetworkManager.MachineGroupStat(st.block(), st.count(), avgWatts, st.color()));
        }
        displayList.sort(Comparator.comparingLong(PowerNetworkManager.MachineGroupStat::watts).reversed());
        return displayList;
    }

    private long[] buildTimeframeSeries(Map<Block, long[]> rawMap, Block block) {
        long[] result = new long[GRAPH_POINTS];
        long[] raw = rawMap.get(block);
        if (raw == null || totalRecordedTicks <= 0) return result;

        int step = TICKS_PER_POINT[this.selectedTimeframe];
        if (this.selectedTimeframe == 8) {
            step = Math.max(1, Math.min(RAW_BUFFER_SIZE / GRAPH_POINTS, (totalRecordedTicks + GRAPH_POINTS - 1) / GRAPH_POINTS));
        }

        int sampleWindow = Math.min(step, 120);
        for (int i = 0; i < GRAPH_POINTS; i++) {
            int pointsAgo = (GRAPH_POINTS - 1) - i;
            long startTicksAgo = (long) pointsAgo * step;
            if (startTicksAgo >= totalRecordedTicks || startTicksAgo >= RAW_BUFFER_SIZE) {
                result[i] = 0L;
                continue;
            }

            long sum = 0L;
            int count = 0;
            for (int k = 0; k < sampleWindow; k++) {
                long tAgo = startTicksAgo + k;
                if (tAgo >= totalRecordedTicks || tAgo >= RAW_BUFFER_SIZE) break;
                int idx = Math.floorMod(rawWriteCursor - 1 - (int) tAgo, RAW_BUFFER_SIZE);
                sum += raw[idx];
                count++;
            }
            result[i] = (count > 0) ? (sum / count) : 0L;
        }
        return result;
    }

    private long computeTimeframeAverageWatts(Map<Block, long[]> rawMap, Block block, long fallbackCurrentWatts) {
        if (this.selectedTimeframe == 0) {
            return fallbackCurrentWatts;
        }
        long[] series = buildTimeframeSeries(rawMap, block);
        long sum = 0L;
        for (long v : series) {
            sum += v;
        }
        return sum / GRAPH_POINTS;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        if (this.client == null || this.client.world == null) return;

        PowerNetworkManager.NetworkSnapshot snap = getOrScanSnapshot(this.client.world);

        int panelW = Math.min(520, this.width - 12);
        int panelH = Math.min(254, this.height - 12);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        context.fill(x, y, x + panelW, y + panelH, 0xFF111213);
        context.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, 0xFF2D2E30);

        drawHeader(context, x + 3, y + 3, panelW - 6, mouseX, mouseY);

        int topBarY = y + 20;
        int innerW = panelW - 10;
        int colGap = 4;
        int colW = (innerW - colGap * 2) / 3;

        int col1X = x + 5;
        int col2X = col1X + colW + colGap;
        int col3X = col2X + colW + colGap;

        float satRatio = (snap.totalDemandWatts() > 0)
                ? MathHelper.clamp(snap.totalProductionWatts() / (float) snap.totalDemandWatts(), 0.0f, 1.0f)
                : (snap.maxProductionCapacityWatts() > 0 ? 1.0f : 0.0f);
        int satColor = (satRatio >= 0.99f) ? 0xFF33CC24 : (satRatio >= 0.5f ? 0xFFE59524 : 0xFFD93025);
        String satText = PowerNetworkManager.formatWatts(snap.totalProductionWatts())
                + " / " + PowerNetworkManager.formatWatts(snap.totalDemandWatts());
        drawTopSummaryBox(context, col1X, topBarY, colW,
                Text.translatable("gui.factoriocore.electric_network.satisfaction").getString(),
                satRatio, satColor, satText);

        float prodRatio = (snap.maxProductionCapacityWatts() > 0)
                ? MathHelper.clamp(snap.totalProductionWatts() / (float) snap.maxProductionCapacityWatts(), 0.0f, 1.0f)
                : 0.0f;
        String prodText = PowerNetworkManager.formatWatts(snap.totalProductionWatts())
                + " / " + PowerNetworkManager.formatWatts(snap.maxProductionCapacityWatts());
        drawTopSummaryBox(context, col2X, topBarY, colW,
                Text.translatable("gui.factoriocore.electric_network.production").getString(),
                prodRatio, 0xFF33CC24, prodText);

        float accRatio = (snap.accumulatorCapacityJoules() > 0L)
                ? MathHelper.clamp(snap.accumulatorStoredJoules() / (float) snap.accumulatorCapacityJoules(), 0.0f, 1.0f)
                : 0.0f;
        String accText = PowerNetworkManager.formatJoules(snap.accumulatorStoredJoules())
                + " / " + PowerNetworkManager.formatJoules(snap.accumulatorCapacityJoules());
        drawTopSummaryBox(context, col3X, topBarY, colW,
                Text.translatable("gui.factoriocore.electric_network.accumulator").getString(),
                accRatio, 0xFF33CC24, accText);

        int tabsY = y + 51;
        int tabCount = TIMEFRAME_LABELS.length;
        int tabW = innerW / tabCount;
        for (int i = 0; i < tabCount; i++) {
            int tx = col1X + i * tabW;
            int tw = (i == tabCount - 1) ? (col1X + innerW - tx) : (tabW - 1);
            boolean selected = (i == this.selectedTimeframe);
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= tabsY && mouseY < tabsY + 12;

            int bg = selected ? 0xFFF2B653 : (hovered ? 0xFF9EA0A3 : 0xFF7D7F82);
            context.fill(tx, tabsY, tx + tw, tabsY + 12, 0xFF141414);
            context.fill(tx + 1, tabsY + 1, tx + tw - 1, tabsY + 11, bg);

            drawScaledTextCentered(context, Text.translatable(TIMEFRAME_LABELS[i]).getString(),
                    tx + tw / 2, tabsY + 3, 0.75f, 0xFF141414, false);
        }

        int mainTopY = y + 66;
        int mainH = panelH - 71;

        renderColumnPanel(
                context, col1X, mainTopY, colW, mainH,
                Text.translatable("gui.factoriocore.electric_network.consumption").getString(),
                snap.consumers(), CONSUMER_RAW_TICKS, this.filteredConsumerBlock,
                Math.max(100_000L, snap.totalDemandWatts()), false, mouseX, mouseY
        );

        renderColumnPanel(
                context, col2X, mainTopY, colW, mainH,
                Text.translatable("gui.factoriocore.electric_network.production").getString(),
                snap.producers(), PRODUCER_RAW_TICKS, this.filteredProducerBlock,
                Math.max(900_000L, snap.totalProductionWatts()), false, mouseX, mouseY
        );

        // 3-я колонка: График и список подключённых к сети Аккумуляторов (в Джоулях: кДж / МДж)
        int accCount = snap.getAccumulatorCount();
        List<PowerNetworkManager.MachineGroupStat> accStats = (accCount > 0)
                ? List.of(new PowerNetworkManager.MachineGroupStat(
                ModBlocks.ACCUMULATOR, accCount, snap.accumulatorStoredJoules(), 0xFF29B6F6))
                : List.of();
        renderColumnPanel(
                context, col3X, mainTopY, colW, mainH,
                Text.translatable("gui.factoriocore.electric_network.accumulator").getString(),
                accStats, ACCUMULATOR_RAW_TICKS, null,
                Math.max(5_000_000L, snap.accumulatorCapacityJoules()), true, mouseX, mouseY
        );
    }

    private void drawHeader(DrawContext context, int hx, int hy, int hw, int mouseX, int mouseY) {
        context.fill(hx, hy, hx + hw, hy + 14, 0xFF222325);
        context.drawText(this.textRenderer, this.title, hx + 5, hy + 3, 0xFFFFDCA0, true);

        int closeBtnX = hx + hw - 14;
        int searchBtnX = closeBtnX - 15;
        int rightReserve = this.searchActive ? 98 : 34;

        int startRibs = hx + 10 + this.textRenderer.getWidth(this.title);
        int endRibs = hx + hw - rightReserve;
        for (int rx = startRibs; rx < endRibs; rx += 3) {
            context.fill(rx, hy + 3, rx + 1, hy + 11, 0xFF17181A);
        }

        if (this.searchActive) {
            int boxW = 78;
            int boxX = closeBtnX - boxW - 3;
            context.fill(boxX, hy + 1, boxX + boxW, hy + 13, 0xFF111112);
            context.fill(boxX + 1, hy + 2, boxX + boxW - 1, hy + 12, 0xFF1E1F22);
            context.fill(boxX + 1, hy + 11, boxX + boxW - 1, hy + 12, 0xFFE59524);
            String shown = this.textRenderer.trimToWidth(this.searchQuery, boxW - 10) + "_";
            context.drawText(this.textRenderer, shown, boxX + 3, hy + 3, 0xFFFFEAAA, false);
        } else {
            boolean hovSearch = mouseX >= searchBtnX && mouseX < searchBtnX + 13 && mouseY >= hy + 1 && mouseY < hy + 13;
            context.fill(searchBtnX, hy + 1, searchBtnX + 13, hy + 13, 0xFF141414);
            context.fill(searchBtnX + 1, hy + 2, searchBtnX + 12, hy + 12, hovSearch ? 0xFF4C4E52 : 0xFF35373A);
            drawSmallMagnifier(context, searchBtnX + 3, hy + 3);
        }

        boolean hovClose = mouseX >= closeBtnX && mouseX < closeBtnX + 13 && mouseY >= hy + 1 && mouseY < hy + 13;
        context.fill(closeBtnX, hy + 1, closeBtnX + 13, hy + 13, 0xFF141414);
        context.fill(closeBtnX + 1, hy + 2, closeBtnX + 12, hy + 12, hovClose ? 0xFFB83227 : 0xFF35373A);
        context.drawText(this.textRenderer, "x", closeBtnX + 4, hy + 2, 0xFFE6E6E6, false);
    }

    private void drawTopSummaryBox(DrawContext context, int bx, int by, int bw,
                                   String label, float ratio, int barColor, String rightValueText) {
        context.fill(bx, by, bx + bw, by + 28, 0xFF18191B);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + 27, 0xFF28292C);

        drawScaledText(context, label, bx + 5, by + 4, 0.78f, 0xFFE6E6E6, true);

        int labelW = Math.round(this.textRenderer.getWidth(label) * 0.78f);
        int infoX = bx + 8 + labelW;
        int infoY = by + 3;
        context.fill(infoX, infoY, infoX + 7, infoY + 7, 0xFF141414);
        context.fill(infoX + 1, infoY + 1, infoX + 6, infoY + 6, 0xFF3FA9F5);
        drawScaledText(context, "i", infoX + 2, infoY + 1, 0.65f, 0xFF111111, false);

        int barX = bx + 3;
        int barY = by + 14;
        int barW = bw - 6;
        int barH = 11;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF141414);
        context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF3E4044);

        int fillW = Math.round(MathHelper.clamp(ratio, 0.0f, 1.0f) * (barW - 2));
        if (fillW > 0) {
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, barColor);
        }

        int valW = Math.round(this.textRenderer.getWidth(rightValueText) * 0.72f);
        drawScaledText(context, rightValueText, barX + barW - valW - 4, barY + 3, 0.72f, 0xFFEAEAEA, true);
    }

    private void renderColumnPanel(DrawContext context, int cx, int cy, int cw, int ch,
                                   String title, List<PowerNetworkManager.MachineGroupStat> stats,
                                   Map<Block, long[]> rawHistoryMap, Block activeFilter,
                                   long fallbackMaxScale, boolean isJoulesColumn, int mouseX, int mouseY) {
        context.fill(cx, cy, cx + cw, cy + ch, 0xFF161719);
        context.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0xFF242528);

        drawScaledText(context, title, cx + 5, cy + 4, 0.78f, 0xFFE6E6E6, true);

        int resetBtnX = cx + cw - 14;
        int resetBtnY = cy + 2;
        boolean hovReset = mouseX >= resetBtnX && mouseX < resetBtnX + 11 && mouseY >= resetBtnY && mouseY < resetBtnY + 11;
        context.fill(resetBtnX, resetBtnY, resetBtnX + 11, resetBtnY + 11, 0xFF141414);
        context.fill(resetBtnX + 1, resetBtnY + 1, resetBtnX + 10, resetBtnY + 10, hovReset ? 0xFFFF5252 : 0xFFE04848);
        drawScaledText(context, "\u21bb", resetBtnX + 3, resetBtnY + 2, 0.70f, 0xFF141414, false);

        int graphLeft = cx + 22;
        int graphTop = cy + 15;
        int graphRight = cx + cw - 4;
        int graphBottom = cy + 78;
        int graphW = graphRight - graphLeft;
        int graphH = graphBottom - graphTop;

        context.fill(cx + 3, graphTop - 1, graphRight + 1, graphBottom + 9, 0xFF0A0B0C);

        Map<Block, long[]> sampledSeriesMap = new LinkedHashMap<>();
        long maxScale = isJoulesColumn ? fallbackMaxScale : 10_000L;
        for (PowerNetworkManager.MachineGroupStat st : stats) {
            if (activeFilter != null && st.block() != activeFilter) continue;
            long[] series = buildTimeframeSeries(rawHistoryMap, st.block());
            sampledSeriesMap.put(st.block(), series);
            for (long val : series) {
                if (val > maxScale) maxScale = val;
            }
        }
        if (!isJoulesColumn) {
            maxScale = Math.max(maxScale, fallbackMaxScale / 2);
            maxScale = (long) (Math.ceil(maxScale / 50_000.0) * 50_000L);
        }
        if (maxScale <= 0) maxScale = 100_000L;

        int hRows = 6;
        for (int r = 0; r <= hRows; r++) {
            int gy = graphTop + Math.round((r / (float) hRows) * graphH);
            context.fill(graphLeft, gy, graphRight, gy + 1, (r == hRows) ? 0xFF333538 : 0xFF181A1C);

            if (!stats.isEmpty()) {
                long rowVal = Math.round(((hRows - r) / (double) hRows) * maxScale);
                String yLbl = isJoulesColumn
                        ? PowerNetworkManager.formatAxisJoules(rowVal)
                        : PowerNetworkManager.formatAxisWatts(rowVal);
                int lblW = Math.round(this.textRenderer.getWidth(yLbl) * 0.55f);
                drawScaledText(context, yLbl, graphLeft - lblW - 2, gy - 2, 0.55f, 0xFF888A8E, false);
            }
        }

        int vCols = 10;
        float maxTimeNum = TIMEFRAME_MAX_NUM[this.selectedTimeframe];
        for (int c = 0; c <= vCols; c++) {
            int gx = graphLeft + Math.round((c / (float) vCols) * graphW);
            context.fill(gx, graphTop, gx + 1, graphBottom, 0xFF181A1C);

            float tVal = maxTimeNum * (1.0f - (c / (float) vCols));
            String tStr = (tVal == (int) tVal) ? String.valueOf((int) tVal) : String.format(Locale.ROOT, "%.1f", tVal);
            drawScaledTextCentered(context, tStr, gx, graphBottom + 2, 0.50f, 0xFF888A8E, false);
        }

        for (PowerNetworkManager.MachineGroupStat stat : stats) {
            if (activeFilter != null && stat.block() != activeFilter) continue;
            long[] series = sampledSeriesMap.get(stat.block());
            if (series == null) continue;

            int prevX = -1;
            int prevY = -1;
            for (int i = 0; i < GRAPH_POINTS; i++) {
                float xRatio = i / (float) (GRAPH_POINTS - 1);
                float yRatio = MathHelper.clamp(series[i] / (float) maxScale, 0.0f, 1.0f);
                int px = graphLeft + Math.round(xRatio * (graphW - 1));
                int py = graphBottom - 1 - Math.round(yRatio * (graphH - 2));

                if (prevX != -1 && (series[i] > 0 || series[Math.max(0, i - 1)] > 0)) {
                    int minX = Math.min(prevX, px);
                    int maxX = Math.max(prevX, px) + 1;
                    context.fill(minX, prevY, maxX, prevY + 1, stat.color());
                    int minY = Math.min(prevY, py);
                    int maxY = Math.max(prevY, py) + 1;
                    context.fill(px, minY, px + 1, maxY, stat.color());
                }
                prevX = px;
                prevY = py;
            }
        }

        int listTopY = graphBottom + 11;
        int cellGap = 2;
        int subColW = (cw - 8 - cellGap) / 2;
        int rowH = 14;
        int numRows = Math.max(4, (ch - (listTopY - cy) - 3) / (rowH + 1));

        List<PowerNetworkManager.MachineGroupStat> displayList = isJoulesColumn
                ? stats
                : getFilteredStats(stats, rawHistoryMap);

        long totalGroupValue = isJoulesColumn ? Math.max(1L, fallbackMaxScale) : 0L;
        if (!isJoulesColumn) {
            for (PowerNetworkManager.MachineGroupStat st : displayList) {
                totalGroupValue += st.watts();
            }
        }

        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < 2; c++) {
                int idx = r + c * numRows;
                int cellX = cx + 4 + c * (subColW + cellGap);
                int cellY = listTopY + r * (rowH + 1);

                if (idx < displayList.size()) {
                    PowerNetworkManager.MachineGroupStat st = displayList.get(idx);
                    boolean isSelected = (activeFilter == st.block());
                    boolean hovCell = mouseX >= cellX && mouseX < cellX + subColW && mouseY >= cellY && mouseY < cellY + rowH;

                    int cellBg = isSelected ? 0xFF484B50 : (hovCell ? 0xFF3C3E42 : 0xFF313235);
                    context.fill(cellX, cellY, cellX + subColW, cellY + rowH, 0xFF141414);
                    context.fill(cellX + 1, cellY + 1, cellX + subColW - 1, cellY + rowH - 1, cellBg);

                    context.fill(cellX + 1, cellY + 1, cellX + 13, cellY + rowH - 1, 0xFF1E1F22);
                    context.getMatrices().push();
                    context.getMatrices().translate(cellX + 1.5f, cellY + 1.0f, 100.0f);
                    context.getMatrices().scale(0.68f, 0.68f, 1.0f);
                    context.drawItem(new ItemStack(st.block().asItem()), 0, 0);
                    context.getMatrices().pop();

                    String cntStr = String.valueOf(st.count());
                    int cntW = Math.round(this.textRenderer.getWidth(cntStr) * 0.55f);
                    drawScaledText(context, cntStr, cellX + 13 - cntW, cellY + rowH - 5, 0.55f, 0xFFFFFFFF, true);

                    int barStartX = cellX + 15;
                    int barEndX = cellX + subColW - 34;
                    int barW = Math.max(10, barEndX - barStartX);
                    int barY = cellY + 5;
                    context.fill(barStartX, barY, barStartX + barW, barY + 4, 0xFF18191B);

                    float share = (totalGroupValue > 0) ? MathHelper.clamp(st.watts() / (float) totalGroupValue, 0.0f, 1.0f) : 0.0f;
                    int fillW = Math.round(share * barW);
                    if (fillW > 0) {
                        context.fill(barStartX, barY, barStartX + fillW, barY + 4, st.color());
                    }

                    String wStr = isJoulesColumn
                            ? PowerNetworkManager.formatJoules(st.watts())
                            : PowerNetworkManager.formatWatts(st.watts());
                    int wWidth = Math.round(this.textRenderer.getWidth(wStr) * 0.60f);
                    drawScaledText(context, wStr, cellX + subColW - wWidth - 2, cellY + 4, 0.60f, 0xFFE6E6E6, true);
                } else {
                    context.fill(cellX, cellY, cellX + subColW, cellY + rowH, 0xFF1B1C1E);
                    context.fill(cellX, cellY + rowH - 1, cellX + subColW, cellY + rowH, 0xFF121314);
                }
            }
        }
    }

    private void drawSmallMagnifier(DrawContext context, int lx, int ly) {
        int col = 0xFFE6E6E6;
        context.fill(lx + 1, ly, lx + 4, ly + 1, col);
        context.fill(lx, ly + 1, lx + 1, ly + 4, col);
        context.fill(lx + 4, ly + 1, lx + 5, ly + 4, col);
        context.fill(lx + 1, ly + 4, lx + 4, ly + 5, col);
        context.fill(lx + 4, ly + 5, lx + 6, ly + 7, col);
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
        if (this.client == null || this.client.world == null) return false;

        int panelW = Math.min(520, this.width - 12);
        int panelH = Math.min(254, this.height - 12);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        int hx = x + 3;
        int hy = y + 3;
        int hw = panelW - 6;
        int closeBtnX = hx + hw - 14;
        int searchBtnX = closeBtnX - 15;

        if (mouseX >= closeBtnX && mouseX < closeBtnX + 13 && mouseY >= hy + 1 && mouseY < hy + 13) {
            this.close();
            return true;
        }

        if (!this.searchActive && mouseX >= searchBtnX && mouseX < searchBtnX + 13 && mouseY >= hy + 1 && mouseY < hy + 13) {
            this.searchActive = true;
            return true;
        } else if (this.searchActive) {
            int boxX = closeBtnX - 81;
            if (mouseX >= boxX && mouseX < closeBtnX - 3 && mouseY >= hy + 1 && mouseY < hy + 13) {
                if (button == 1) this.searchQuery = "";
                return true;
            } else if (this.searchQuery.isEmpty()) {
                this.searchActive = false;
            }
        }

        int innerW = panelW - 10;
        int col1X = x + 5;
        int tabsY = y + 51;
        int tabCount = TIMEFRAME_LABELS.length;
        int tabW = innerW / tabCount;
        for (int i = 0; i < tabCount; i++) {
            int tx = col1X + i * tabW;
            int tw = (i == tabCount - 1) ? (col1X + innerW - tx) : (tabW - 1);
            if (mouseX >= tx && mouseX < tx + tw && mouseY >= tabsY && mouseY < tabsY + 12) {
                this.selectedTimeframe = i;
                if (this.client.player != null) {
                    this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.2f);
                }
                return true;
            }
        }

        int colGap = 4;
        int colW = (innerW - colGap * 2) / 3;
        int col2X = col1X + colW + colGap;
        int mainTopY = y + 66;

        if (mouseX >= col1X + colW - 14 && mouseX < col1X + colW - 3 && mouseY >= mainTopY + 2 && mouseY < mainTopY + 13) {
            this.filteredConsumerBlock = null;
            return true;
        }
        if (mouseX >= col2X + colW - 14 && mouseX < col2X + colW - 3 && mouseY >= mainTopY + 2 && mouseY < mainTopY + 13) {
            this.filteredProducerBlock = null;
            return true;
        }

        PowerNetworkManager.NetworkSnapshot snap = getOrScanSnapshot(this.client.world);
        List<PowerNetworkManager.MachineGroupStat> filteredConsumers = getFilteredStats(snap.consumers(), CONSUMER_RAW_TICKS);
        List<PowerNetworkManager.MachineGroupStat> filteredProducers = getFilteredStats(snap.producers(), PRODUCER_RAW_TICKS);

        int mainH = panelH - 71;
        int listTopY = mainTopY + 78 + 11;
        int subColW = (colW - 10) / 2;
        int rowH = 14;
        int numRows = Math.max(4, (mainH - (listTopY - mainTopY) - 3) / (rowH + 1));

        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < 2; c++) {
                int idx = r + c * numRows;
                int cellX1 = col1X + 4 + c * (subColW + 2);
                int cellY = listTopY + r * (rowH + 1);
                if (idx < filteredConsumers.size() && mouseX >= cellX1 && mouseX < cellX1 + subColW && mouseY >= cellY && mouseY < cellY + rowH) {
                    Block b = filteredConsumers.get(idx).block();
                    this.filteredConsumerBlock = (this.filteredConsumerBlock == b) ? null : b;
                    return true;
                }

                int cellX2 = col2X + 4 + c * (subColW + 2);
                if (idx < filteredProducers.size() && mouseX >= cellX2 && mouseX < cellX2 + subColW && mouseY >= cellY && mouseY < cellY + rowH) {
                    Block b = filteredProducers.get(idx).block();
                    this.filteredProducerBlock = (this.filteredProducerBlock == b) ? null : b;
                    return true;
                }
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
}