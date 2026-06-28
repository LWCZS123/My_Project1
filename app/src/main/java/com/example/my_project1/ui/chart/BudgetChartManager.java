package com.example.my_project1.ui.chart;

import android.content.Context;
import android.graphics.Color;

import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * BudgetChartManager
 * ─────────────────────────────────────────────────────────────
 * 封装 MPAndroidChart 折线图与饼图的初始化与数据填充。
 *
 * 折线图规则：
 *  - X 轴：当前周期内的日期序号（1 … N）
 *  - Y 轴：每日累计支出
 *  - 预算参考线（LimitLine）：水平虚线，标注总预算金额
 *  - 超出预算时曲线段变红（通过两段 LineDataSet 实现）
 *
 * 饼图规则：
 *  - 各分类预算占总预算比例
 *  - 最后一项为"未分配"（灰色）
 *  - 使用百分比标签
 *
 * 使用方式：
 *  1. 在 Activity / Fragment 中持有 BudgetChartManager 实例
 *  2. observe ViewModel 的 dailyAccumulatedLive / pieSlicesLive
 *  3. 数据变更时调用 updateLineChart / updatePieChart
 */
public class BudgetChartManager {

    // ── 折线图颜色 ────────────────────────────────────────────
    private static final int COLOR_NORMAL   = 0xFF5B8DEF; // 蓝色：未超预算
    private static final int COLOR_OVER     = 0xFFFF5252; // 红色：超预算
    private static final int COLOR_LIMIT    = 0xFF4CAF50; // 绿色：预算参考线
    private static final int COLOR_FILL_NOR = 0x285B8DEF; // 填充透明蓝
    private static final int COLOR_FILL_OVR = 0x28FF5252; // 填充透明红

    private final Context context;

    public BudgetChartManager(Context context) {
        this.context = context;
    }

    // ════════════════════════════════════════════════════════
    //  折线图初始化（在 onViewCreated 中调用一次）
    // ════════════════════════════════════════════════════════

    /**
     * 配置折线图基础样式，不填数据。
     *
     * @param chart       MPAndroidChart LineChart 实例
     * @param totalDays   当前周期总天数（月预算=月天数，年预算=365/366）
     */
    public void initLineChart(LineChart chart, int totalDays) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        chart.setExtraOffsets(8f, 12f, 8f, 8f);

        // X 轴
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(1f);
        xAxis.setAxisMaximum(totalDays);
        xAxis.setTextColor(0xFF888888);
        xAxis.setTextSize(10f);
        xAxis.setLabelCount(Math.min(totalDays, 7), true); // 最多显示 7 个标签

        // Y 轴（左）
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0xFFEEEEEE);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(0xFF888888);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawZeroLine(false);

        chart.getAxisRight().setEnabled(false);

        // 图例
        Legend legend = chart.getLegend();
        legend.setEnabled(false); // 图例由 XML 中自定义 View 展示

        chart.invalidate();
    }

    /**
     * 填充折线图数据，并根据预算参考线动态切换曲线颜色。
     *
     * @param chart          MPAndroidChart LineChart
     * @param dailyData      每日累计支出数组（index 0 = 第 1 天）
     * @param totalBudget    总预算金额（用于参考线和颜色判断）
     * @param currentYear    当前年
     * @param currentMonth   当前月（1-12），年预算传 0
     */
    public void updateLineChart(LineChart chart, double[] dailyData,
                                double totalBudget, int currentYear, int currentMonth) {
        if (dailyData == null || dailyData.length == 0) return;

        // ✅ 修复 NPE：chart 首次使用时 data 为 null，clearValues() 会崩溃。
        // clear() 内部会先判空再清理，安全。
        chart.clear();
        chart.getAxisLeft().removeAllLimitLines();

        // 预算参考线（虚线）
        LimitLine limitLine = new LimitLine((float) totalBudget, "预算 ¥" + (int) totalBudget);
        limitLine.setLineWidth(1.5f);
        limitLine.enableDashedLine(10f, 5f, 0f);
        limitLine.setLineColor(COLOR_LIMIT);
        limitLine.setTextColor(COLOR_LIMIT);
        limitLine.setTextSize(9f);
        limitLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        chart.getAxisLeft().addLimitLine(limitLine);

        // 分段：未超预算（蓝）和超预算（红）
        List<Entry> normalEntries = new ArrayList<>();
        List<Entry> overEntries   = new ArrayList<>();

        boolean hasOver = false;
        for (int i = 0; i < dailyData.length; i++) {
            float x   = i + 1;
            float val = (float) dailyData[i];
            if (val <= totalBudget) {
                normalEntries.add(new Entry(x, val));
                // 曲线过渡：在超预算临界点，两段共享端点
                if (i + 1 < dailyData.length && dailyData[i + 1] > totalBudget) {
                    overEntries.add(new Entry(x, val));
                }
            } else {
                overEntries.add(new Entry(x, val));
                hasOver = true;
            }
        }

        List<LineDataSet> dataSets = new ArrayList<>();

        if (!normalEntries.isEmpty()) {
            LineDataSet ds = buildLineDataSet(normalEntries, "支出", COLOR_NORMAL, COLOR_FILL_NOR);
            dataSets.add(ds);
        }

        if (hasOver && !overEntries.isEmpty()) {
            LineDataSet dsOver = buildLineDataSet(overEntries, "超支", COLOR_OVER, COLOR_FILL_OVR);
            dataSets.add(dsOver);
        }

        // X 轴日期标签
        String[] labels = buildDayLabels(dailyData.length, currentYear, currentMonth);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));

        // ✅ 修复 ClassCastException：LineData 需要 List<ILineDataSet>，
        // 不能将 List<LineDataSet> 直接强转为单个 ILineDataSet。
        List<ILineDataSet> iDataSets = new ArrayList<>(dataSets);
        chart.setData(new LineData(iDataSets));
        chart.animateX(600);
        chart.invalidate();
    }

    // ════════════════════════════════════════════════════════
    //  饼图颜色盘（水果主题，按顺序循环分配）
    // ════════════════════════════════════════════════════════

    /**
     * 水果主题色盘（8色，循环使用）：
     *  草莓红 / 橙子橙 / 柠檬黄 / 青苹果绿 /
     *  蓝莓蓝 / 葡萄紫 / 西瓜粉 / 奇异果绿
     */
    private static final int[] PIE_COLORS = {
            0xFFFF4C4C,   // 草莓红
            0xFFFF9500,   // 橙子橙
            0xFFFFD700,   // 柠檬黄
            0xFF00C853,   // 青苹果绿
            0xFF2979FF,   // 蓝莓蓝
            0xFFD500F9,   // 葡萄紫
            0xFFFF69B4,   // 西瓜粉
            0xFF32CD32,   // 奇异果绿
    };

    /** 未分配扇区固定使用浅灰色 */
    private static final int COLOR_UNALLOCATED = 0xFFDDDDDD;

    // ════════════════════════════════════════════════════════
    //  饼图初始化
    // ════════════════════════════════════════════════════════

    /**
     * 配置饼图基础样式，不填数据。
     */
    public void initPieChart(PieChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setUsePercentValues(true);

        // 圆环样式
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(44f);
        chart.setTransparentCircleRadius(48f);
        chart.setHoleColor(Color.WHITE);
        chart.setTransparentCircleColor(Color.WHITE);
        chart.setTransparentCircleAlpha(80);

        chart.setRotationAngle(-90f);   // 从 12 点方向起始
        chart.setRotationEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setBackgroundColor(Color.WHITE);

        // 四周留白，为外侧引线 + 标签腾出空间
        chart.setExtraOffsets(24f, 24f, 24f, 24f);

        // 中心文字
        chart.setDrawCenterText(true);
        chart.setCenterTextSize(12f);
        chart.setCenterTextColor(0xFF333333);

        // 关闭底部图例（标签已通过引线标注在扇区旁）
        chart.getLegend().setEnabled(false);

        chart.invalidate();
    }

    /**
     * 填充饼图数据。
     * 颜色取自水果主题色盘；每个扇区通过引线在外侧显示「分类名 XX%」。
     */
    public void updatePieChart(PieChart chart,
                               List<BudgetViewModel.PieSlice> slices,
                               double totalBudget) {
        if (slices == null || slices.isEmpty()) return;

        List<PieEntry> entries = new ArrayList<>();
        List<Integer>  colors  = new ArrayList<>();

        int colorIdx = 0;
        for (BudgetViewModel.PieSlice s : slices) {
            // PieEntry(value, label)：label 会跟随引线显示在外侧
            entries.add(new PieEntry(s.value, s.label));

            if ("未分配".equals(s.label)) {
                colors.add(COLOR_UNALLOCATED);
            } else {
                colors.add(PIE_COLORS[colorIdx % PIE_COLORS.length]);
                colorIdx++;
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2.5f);
        dataSet.setSelectionShift(6f);

        // ── 百分比数值：外侧显示，跟引线一起 ──────────────────
        //dataSet.setValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        // 引线样式
        dataSet.setValueLinePart1OffsetPercentage(88f); // 引线起点靠近扇区边缘
        dataSet.setValueLinePart1Length(0.38f);          // 第一段斜线
        dataSet.setValueLinePart2Length(0.45f);          // 第二段水平线
        dataSet.setValueLineColor(0xFFAAAAAA);
        dataSet.setValueLineWidth(1f);
        dataSet.setUsingSliceColorAsValueLineColor(true); // 引线颜色与扇区一致

        // 百分比文字样式
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(0xFF444444);
        dataSet.setValueFormatter(new PercentFormatter(chart));

        PieData data = new PieData(dataSet);
        chart.setData(data);

        // 分类名称标签（即 PieEntry.label）显示在引线末端
        chart.setDrawEntryLabels(true);
        chart.setEntryLabelColor(0xFF333333);
        chart.setEntryLabelTextSize(10f);

        // 中心文字：总预算金额
        chart.setCenterText("总预算\n¥" + String.format("%.0f", totalBudget));

        chart.animateY(700);
        chart.invalidate();
    }

    // ════════════════════════════════════════════════════════
    //  内部工具
    // ════════════════════════════════════════════════════════

    private LineDataSet buildLineDataSet(List<Entry> entries, String label,
                                         int lineColor, int fillColor) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(lineColor);
        ds.setLineWidth(2f);
        ds.setCircleColor(lineColor);
        ds.setCircleRadius(3f);
        ds.setDrawCircleHole(false);
        ds.setValueTextSize(0f); // 不显示数值，避免杂乱
        ds.setDrawFilled(true);
        ds.setFillColor(fillColor);
        ds.setFillAlpha(40);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setCubicIntensity(0.2f);
        return ds;
    }

    /**
     * 生成 X 轴日期标签，格式为 "1" "2" … "N"；
     * 月预算超过 15 天时每隔 5 天打一个可读标签，其余空串。
     */
    private String[] buildDayLabels(int days, int year, int month) {
        String[] labels = new String[days + 1]; // index 0 占位
        labels[0] = "";
        boolean sparse = days > 15;
        for (int i = 1; i <= days; i++) {
            if (!sparse || i == 1 || i % 5 == 0 || i == days) {
                labels[i] = String.valueOf(i);
            } else {
                labels[i] = "";
            }
        }
        return labels;
    }
}