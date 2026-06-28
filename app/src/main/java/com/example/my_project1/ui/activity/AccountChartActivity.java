package com.example.my_project1.ui.activity;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ActivityAccountChartBinding;
import com.example.my_project1.ui.adapter.account.AccountGroupPickerAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AccountChartActivity extends AppCompatActivity {

    private static final String TAG = "AccountChartActivity";

    private ActivityAccountChartBinding binding;
    private AccountViewModel viewModel;

    private String currentGroupId = null;
    private String timeType = "month";
    private boolean showAsset = true;
    private boolean isMergeDisplay = false;

    private List<AccountGroup> allGroups = new ArrayList<>();
    private Map<String, List<Account>> allGroupAccounts = new HashMap<>();

    private static final int[] GROUP_LINE_COLORS = {
            Color.parseColor("#2196F3"), Color.parseColor("#FF6B6B"),
            Color.parseColor("#4CAF50"), Color.parseColor("#FF9800"),
            Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4"),
            Color.parseColor("#FFEB3B"), Color.parseColor("#E91E63"),
            Color.parseColor("#795548"), Color.parseColor("#607D8B")
    };

    private static final int[] BLUE_COLORS = {
            Color.parseColor("#FF6B6B"), // 鲜红
            Color.parseColor("#FF9F1C"), // 活力橙
            Color.parseColor("#FFCB47"), // 柠檬黄
            Color.parseColor("#4ECDC4"), // 青绿
            Color.parseColor("#1A9FFF"), // 天空蓝
            Color.parseColor("#845EC2"), // 霓虹紫
            Color.parseColor("#FF5E78"), // 粉红
            Color.parseColor("#00C9A7"), // 亮绿青
            Color.parseColor("#2AB7CA"), // 青蓝
            Color.parseColor("#F76C6C")  // 珊瑚红
    };

    private com.example.my_project1.ui.custom.CustomMarkerView markerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding = ActivityAccountChartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        initUI();
        observeViewModel();
        loadInitialData();
    }

    private void initUI() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnYear.setOnClickListener(v -> changeTimeType("year"));
        binding.btnMonth.setOnClickListener(v -> changeTimeType("month"));
        binding.btnDay.setOnClickListener(v -> changeTimeType("day"));
        binding.btnAsset.setOnClickListener(v -> changePieType(true));
        binding.btnLiability.setOnClickListener(v -> changePieType(false));
        binding.btnAccountPicker.setOnClickListener(v -> showAccountGroupPicker());
        binding.layoutMergeDisplay.setOnClickListener(v -> toggleMergeDisplay());

        initLineChart();
        initPieChart();
    }

    private void loadInitialData() {
        viewModel.getAccountGroups().observe(this, groups -> {
            if (groups != null && !groups.isEmpty()) {
                allGroups = groups;
                loadAllGroupAccounts();

                if (currentGroupId == null) {
                    currentGroupId = groups.get(0).getObjectId();
                    loadGroupAccounts(currentGroupId);
                }
            }
        });
    }

    private void loadAllGroupAccounts() {
        if (allGroups == null) return;

        for (AccountGroup group : allGroups) {
            String groupId = group.getObjectId();
            if (!allGroupAccounts.containsKey(groupId)) {
                loadGroupAccounts(groupId);
            }
        }
    }

    private void observeViewModel() {
        viewModel.groupAccountsUpdate.observe(this, map -> {
            if (map == null) return;

            allGroupAccounts.putAll(map);
            updatePieChart();

            if (isMergeDisplay) {
                updateChartsWithMergedData();
            } else if (currentGroupId != null && map.containsKey(currentGroupId)) {
                updateCharts(map.get(currentGroupId));
            }
        });
    }

    private void loadGroupAccounts(String groupId) {
        if (groupId == null) return;

        viewModel.getAccountsByGroupId(groupId).observe(this, accounts -> {
            if (accounts != null) {
                allGroupAccounts.put(groupId, accounts);
                if (!isMergeDisplay && groupId.equals(currentGroupId)) {
                    updateCharts(accounts);
                }
            }
        });
    }

    private void updateCharts(List<Account> accounts) {
        Log.d(TAG, "更新图表,账户数量: " + (accounts != null ? accounts.size() : 0));
        updateLineChart(accounts);
    }

    private List<Account> getAllAccountsForPie() {
        List<Account> allAccounts = new ArrayList<>();

        if (allGroups != null) {
            for (AccountGroup group : allGroups) {
                String groupId = group.getObjectId();
                List<Account> groupAccounts = allGroupAccounts.get(groupId);

                if (groupAccounts != null) {
                    allAccounts.addAll(groupAccounts);
                }
            }
        }

        return allAccounts;
    }

    private void updateChartsWithMergedData() {
        updateMultiLineChart();
    }

    // 🔴 修复崩溃：折线图初始化
    private void initLineChart() {
        LineChart chart = binding.lineChart;

        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        chart.getAxisRight().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#666666"));
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setAvoidFirstLastClipping(true);

        chart.getAxisLeft().setTextColor(Color.parseColor("#666666"));
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));
        chart.getAxisLeft().setLabelCount(5, false);
        chart.getAxisLeft().setGranularityEnabled(true);
        chart.getAxisLeft().setGranularity(1f);

        // 🔴 优化：Legend 设置在上方，横向排列
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Color.parseColor("#666666"));
        legend.setTextSize(10f); // 缩小文字
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(12f); // 横向间距
        legend.setYEntrySpace(4f);
        legend.setFormSize(8f); // 图例图标大小
        legend.setWordWrapEnabled(true); // 自动换行
        legend.setMaxSizePercent(0.95f); // 最大宽度

        markerView = new com.example.my_project1.ui.custom.CustomMarkerView(
                this, R.layout.custom_marker_view);
        markerView.setChartView(chart);
        chart.setMarker(markerView);

        chart.animateX(700, Easing.EaseInOutQuad);
    }

    // 🔴 优化：饼图初始化
    private void initPieChart() {
        PieChart pie = binding.pieChart;

        pie.setUsePercentValues(true);
        pie.getDescription().setEnabled(false);
        pie.setDrawHoleEnabled(true);
        pie.setHoleRadius(45f); // 增大中心孔
        pie.setTransparentCircleRadius(50f);
        pie.setHoleColor(Color.TRANSPARENT);
        pie.setDrawCenterText(true);
        pie.setCenterTextSize(12f); // 缩小中心文字
        pie.setCenterTextColor(Color.parseColor("#333333"));
        pie.setRotationEnabled(true);
        pie.setHighlightPerTapEnabled(true);
        pie.setDrawEntryLabels(false);
        pie.setExtraOffsets(10, 10, 10, 10);


        Legend legend = pie.getLegend();
        legend.setEnabled(true);
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.CENTER);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setTextColor(Color.parseColor("#666666"));
        legend.setTextSize(10f); // 缩小图例文字
        legend.setWordWrapEnabled(true);
        legend.setXEntrySpace(8f);
        legend.setYEntrySpace(3f);
        legend.setFormSize(8f); // 图例图标大小
    }

    // 🔴 修复崩溃：更新折线图
    private void updateLineChart(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            showLineChartEmpty();
            return;
        }

        binding.layoutLineNoData.setVisibility(View.GONE);
        binding.lineChart.setVisibility(View.VISIBLE);

        Map<String, NetAssetData> timeNetAssetMap = aggregateNetAssetByTime(accounts, timeType);

        if (timeNetAssetMap.isEmpty()) {
            showLineChartEmpty();
            return;
        }

        List<String> labels = new ArrayList<>(timeNetAssetMap.keySet());
        List<Entry> entries = new ArrayList<>();

        if (labels.size() == 1) {
            String singleLabel = labels.get(0);
            double netAsset = timeNetAssetMap.get(singleLabel).netAsset;

            entries.add(new Entry(0, (float) netAsset));
            entries.add(new Entry(1, (float) netAsset));
            entries.add(new Entry(2, (float) netAsset));

            labels.add(singleLabel);
            labels.add(singleLabel);
        } else {
            for (int i = 0; i < labels.size(); i++) {
                double netAsset = timeNetAssetMap.get(labels.get(i)).netAsset;
                entries.add(new Entry(i, (float) netAsset));
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "净资产走势");
        dataSet.setColor(Color.parseColor("#2196F3"));
        dataSet.setLineWidth(3f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawHighlightIndicators(true);
        dataSet.setHighLightColor(Color.parseColor("#FF6B6B"));
        dataSet.setHighlightLineWidth(2f);
        dataSet.setValueTextColor(Color.parseColor("#666666"));
        dataSet.setValueTextSize(0f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#802196F3"));

        LineData lineData = new LineData(dataSet);

        binding.lineChart.setData(lineData);

        binding.lineChart.getAxisLeft().setLabelCount(5, false);
        binding.lineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (Math.abs(value) >= 10000) {
                    return String.format(Locale.getDefault(), "%.1fw", value / 10000);
                } else if (Math.abs(value) >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fk", value / 1000);
                } else {
                    return String.format(Locale.getDefault(), "%.0f", value);
                }
            }
        });

        binding.lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.lineChart.getXAxis().setLabelCount(Math.min(labels.size(), 8), false);
        binding.lineChart.getXAxis().setLabelRotationAngle(-45f);

        if (markerView != null) {
            markerView.setTimeLabels(labels);
        }

        binding.lineChart.invalidate();
    }

    // 🔴 修复崩溃：更新多条折线
    private void updateMultiLineChart() {
        if (allGroups == null || allGroups.isEmpty()) {
            showLineChartEmpty();
            return;
        }

        binding.layoutLineNoData.setVisibility(View.GONE);
        binding.lineChart.setVisibility(View.VISIBLE);

        Map<String, Boolean> allTimeLabelsMap = new TreeMap<>();

        for (AccountGroup group : allGroups) {
            List<Account> accounts = allGroupAccounts.get(group.getObjectId());
            if (accounts != null && !accounts.isEmpty()) {
                Map<String, NetAssetData> timeMap = aggregateNetAssetByTime(accounts, timeType);
                for (String key : timeMap.keySet()) {
                    allTimeLabelsMap.put(key, true);
                }
            }
        }

        if (allTimeLabelsMap.isEmpty()) {
            showLineChartEmpty();
            return;
        }

        List<String> allTimeLabels = new ArrayList<>(allTimeLabelsMap.keySet());

        if (allTimeLabels.size() == 1) {
            String singleLabel = allTimeLabels.get(0);
            allTimeLabels.add(singleLabel);
            allTimeLabels.add(singleLabel);
        }

        List<LineDataSet> dataSets = new ArrayList<>();

        for (int groupIndex = 0; groupIndex < allGroups.size(); groupIndex++) {
            AccountGroup group = allGroups.get(groupIndex);
            List<Account> accounts = allGroupAccounts.get(group.getObjectId());

            if (accounts == null || accounts.isEmpty()) {
                continue;
            }

            Map<String, NetAssetData> timeNetAssetMap = aggregateNetAssetByTime(accounts, timeType);

            if (timeNetAssetMap.isEmpty()) {
                continue;
            }

            List<Entry> entries = new ArrayList<>();

            // 🔴 对于不存在的时间点，使用前一个时间点的累积值
            double lastNetAsset = 0;
            for (int i = 0; i < allTimeLabels.size(); i++) {
                String timeLabel = allTimeLabels.get(i);
                NetAssetData data = timeNetAssetMap.get(timeLabel);

                if (data != null) {
                    lastNetAsset = data.netAsset;
                    entries.add(new Entry(i, (float) lastNetAsset));
                } else {
                    // 如果当前时间点没有数据，使用上一个时间点的累积值
                    entries.add(new Entry(i, (float) lastNetAsset));
                }
            }

            if (entries.isEmpty()) {
                continue;
            }

            LineDataSet dataSet = new LineDataSet(entries, group.getName());
            int color = GROUP_LINE_COLORS[groupIndex % GROUP_LINE_COLORS.length];

            dataSet.setColor(color);
            dataSet.setLineWidth(3f);
            dataSet.setDrawCircles(false);
            dataSet.setDrawCircleHole(false);
            dataSet.setHighLightColor(Color.parseColor("#FF6B6B"));
            dataSet.setDrawHighlightIndicators(true);
            dataSet.setHighlightLineWidth(2f);
            dataSet.setValueTextColor(Color.parseColor("#666666"));
            dataSet.setValueTextSize(0f);
            dataSet.setMode(LineDataSet.Mode.LINEAR);
            dataSet.setDrawFilled(false);

            dataSets.add(dataSet);
        }

        if (dataSets.isEmpty()) {
            showLineChartEmpty();
            return;
        }

        LineData lineData = new LineData();
        for (LineDataSet dataSet : dataSets) {
            lineData.addDataSet(dataSet);
        }

        binding.lineChart.setData(lineData);

        binding.lineChart.getAxisLeft().setLabelCount(5, false);
        binding.lineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (Math.abs(value) >= 10000) {
                    return String.format(Locale.getDefault(), "%.1fw", value / 10000);
                } else if (Math.abs(value) >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fk", value / 1000);
                } else {
                    return String.format(Locale.getDefault(), "%.0f", value);
                }
            }
        });

        binding.lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(allTimeLabels));
        binding.lineChart.getXAxis().setLabelCount(Math.min(allTimeLabels.size(), 8), false);
        binding.lineChart.getXAxis().setLabelRotationAngle(-45f);

        if (markerView != null) {
            markerView.setTimeLabels(allTimeLabels);
        }

        binding.lineChart.invalidate();
    }

    // 🔴 修复崩溃：清空图表时清除 Marker
    private void showLineChartEmpty() {
        binding.layoutLineNoData.setVisibility(View.VISIBLE);
        binding.lineChart.setVisibility(View.GONE);
        binding.lineChart.clear();
        // 🔴 关键：清除 MarkerView 避免崩溃
        binding.lineChart.setMarker(null);
    }

    private Map<String, NetAssetData> aggregateNetAssetByTime(List<Account> accounts, String timeType) {
        Map<String, NetAssetData> result = new TreeMap<>();
        SimpleDateFormat sdf;

        switch (timeType) {
            case "year":
                sdf = new SimpleDateFormat("yyyy", Locale.getDefault());
                break;
            case "day":
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                break;
            case "month":
            default:
                sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
                break;
        }

        // 🔴 按时间分组账户
        Map<String, List<Account>> accountsByTime = new TreeMap<>();
        for (Account account : accounts) {
            Date date = account.getCreatedAt();
            if (date == null) {
                continue;
            }

            String timeKey = sdf.format(date);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                accountsByTime.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(account);
            }
        }

        // 🔴 累积计算净资产
        double cumulativeAsset = 0;
        double cumulativeLiability = 0;

        for (Map.Entry<String, List<Account>> entry : accountsByTime.entrySet()) {
            String timeKey = entry.getKey();
            List<Account> timeAccounts = entry.getValue();

            NetAssetData data = new NetAssetData();

            // 计算当前时间点新增的资产和负债
            for (Account account : timeAccounts) {
                double balance = account.getBalance();

                if (balance >= 0) {
                    data.totalAsset += balance;
                } else {
                    data.totalLiability += balance;
                }
            }

            // 🔴 累加到总资产和总负债
            cumulativeAsset += data.totalAsset;
            cumulativeLiability += data.totalLiability;

            // 🔴 存储累积值
            data.totalAsset = cumulativeAsset;
            data.totalLiability = cumulativeLiability;
            data.netAsset = cumulativeAsset + cumulativeLiability;

            result.put(timeKey, data);
        }

        return result;
    }

    private static class NetAssetData {
        double totalAsset = 0;
        double totalLiability = 0;
        double netAsset = 0;
    }

    // 🔴 优化：饼图文字更小
    private void updatePieChart() {
        List<Account> accounts = getAllAccountsForPie();
        if (accounts == null || accounts.isEmpty()) {
            showPieChartEmpty();
            return;
        }

        binding.layoutPieNoData.setVisibility(View.GONE);
        binding.pieChart.setVisibility(View.VISIBLE);

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        double totalValue = 0;

        List<Account> filteredAccounts = new ArrayList<>();
        for (Account account : accounts) {
            double balance = account.getBalance();
            if ((showAsset && balance > 0) || (!showAsset && balance < 0)) {
                filteredAccounts.add(account);
                totalValue += Math.abs(balance);
            }
        }

        if (filteredAccounts.isEmpty()) {
            showPieChartEmpty();
            return;
        }

        for (int i = 0; i < filteredAccounts.size(); i++) {
            Account account = filteredAccounts.get(i);
            double val = Math.abs(account.getBalance());
            entries.add(new PieEntry((float) val, account.getName()));
            colors.add(BLUE_COLORS[i % BLUE_COLORS.length]);
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(8f);
        dataSet.setValueTextSize(9f); // 🔴 缩小数值文字

        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1Length(0.25f);
        dataSet.setValueLinePart2Length(0.35f);
        dataSet.setValueLineColor(Color.GRAY);
        dataSet.setValueTextColors(colors);

        PieData data = new PieData(dataSet);

        double finalTotalValue = totalValue;
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPieLabel(float value, PieEntry pieEntry) {
                double percent = pieEntry.getValue() / finalTotalValue * 100;
                return String.format(Locale.getDefault(), "%s\n%.1f%%", pieEntry.getLabel(), percent);
            }
        });

        binding.pieChart.setData(data);

        String typeText = showAsset ? "资产合计" : "负债合计";
        String centerText = String.format(Locale.getDefault(), "%s\n%.2f", typeText, totalValue);
        binding.pieChart.setCenterText(centerText);

        binding.pieChart.getLegend().setEnabled(false);
        binding.pieChart.animateY(700, Easing.EaseInOutQuad);
        binding.pieChart.invalidate();
    }

    private void showPieChartEmpty() {
        binding.layoutPieNoData.setVisibility(View.VISIBLE);
        binding.pieChart.setVisibility(View.GONE);
        binding.pieChart.clear();
    }

    private void changeTimeType(String type) {
        if (timeType.equals(type)) return;

        timeType = type;
        resetTimeButtons();

        switch (type) {
            case "year":
                binding.btnYear.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.btnYear.setTextColor(Color.WHITE);
                break;
            case "day":
                binding.btnDay.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.btnDay.setTextColor(Color.WHITE);
                break;
            case "month":
            default:
                binding.btnMonth.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.btnMonth.setTextColor(Color.WHITE);
                break;
        }

        if (isMergeDisplay) {
            updateChartsWithMergedData();
        } else if (currentGroupId != null) {
            List<Account> accounts = allGroupAccounts.get(currentGroupId);
            if (accounts != null) {
                updateLineChart(accounts);
            }
        }
    }

    private void resetTimeButtons() {
        int grayColor = Color.parseColor("#666666");

        binding.btnYear.setBackground(null);
        binding.btnMonth.setBackground(null);
        binding.btnDay.setBackground(null);

        binding.btnYear.setTextColor(grayColor);
        binding.btnMonth.setTextColor(grayColor);
        binding.btnDay.setTextColor(grayColor);
    }

    private void changePieType(boolean asset) {
        if (showAsset == asset) return;

        showAsset = asset;

        int grayColor = Color.parseColor("#666666");

        binding.btnAsset.setTextColor(asset ? Color.WHITE : grayColor);
        binding.btnLiability.setTextColor(asset ? grayColor : Color.WHITE);

        binding.btnAsset.setBackground(asset ?
                getDrawable(R.drawable.bg_tab_selected) : null);
        binding.btnLiability.setBackground(!asset ?
                getDrawable(R.drawable.bg_tab_selected) : null);

        updatePieChart();
    }

    private void showAccountGroupPicker() {
        if (allGroups == null || allGroups.isEmpty()) {
            Toast.makeText(this, "暂无账户组", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_account_group_picker);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        RecyclerView rvAccountGroups = dialog.findViewById(R.id.rvAccountGroups);
        rvAccountGroups.setLayoutManager(new LinearLayoutManager(this));

        AccountGroupPickerAdapter adapter = new AccountGroupPickerAdapter(
                this, allGroups, currentGroupId);

        adapter.setOnGroupClickListener(group -> {
            currentGroupId = group.getObjectId();
            isMergeDisplay = false;

            updateMergeDisplayIcon();

            if (!allGroupAccounts.containsKey(currentGroupId)) {
                loadGroupAccounts(currentGroupId);
            } else {
                updateCharts(allGroupAccounts.get(currentGroupId));
            }

            dialog.dismiss();
        });

        rvAccountGroups.setAdapter(adapter);

        dialog.show();
    }

    private void toggleMergeDisplay() {
        isMergeDisplay = !isMergeDisplay;
        updateMergeDisplayIcon();

        if (isMergeDisplay) {
            for (AccountGroup group : allGroups) {
                if (!allGroupAccounts.containsKey(group.getObjectId())) {
                    loadGroupAccounts(group.getObjectId());
                }
            }
            updateChartsWithMergedData();
            Toast.makeText(this, "已显示所有账户组的折线", Toast.LENGTH_SHORT).show();
        } else {
            if (currentGroupId != null) {
                List<Account> accounts = allGroupAccounts.get(currentGroupId);
                updateCharts(accounts);
            }
            Toast.makeText(this, "已切换到单组显示", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMergeDisplayIcon() {
        int iconRes = R.drawable.ic_merge;
        binding.ivMergeDisplay.setImageResource(iconRes);

        int color = isMergeDisplay ?
                Color.parseColor("#2196F3") :
                Color.parseColor("#666666");
        binding.ivMergeDisplay.setColorFilter(color);
        binding.tvMergeDisplay.setTextColor(color);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void finish() {
        super.finish();
        // 左进右出的动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

}