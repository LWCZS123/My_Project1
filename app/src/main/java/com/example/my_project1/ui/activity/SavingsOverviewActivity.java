package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ActivitySavingsOverviewBinding;
import com.example.my_project1.ui.adapter.desire.SavingsGoalAdapter;
import com.example.my_project1.ui.fragment.AddWishFragment;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * SavingsOverviewActivity - 存钱计划总览页
 * -------------------------------------------------------
 * 功能：
 *  - 顶部折线图：展示所有愿望的进度对比（MPAndroidChart LineChart）
 *  - 下方列表：展示所有愿望卡片（SavingsGoalAdapter）
 *  - 右上角 + 按钮：跳转新增愿望（AddWishFragment）
 *  - 右上角删除按钮：进入批量选择删除（长按 item 也可删除）
 *  - 全程 ViewBinding
 */
public class SavingsOverviewActivity extends AppCompatActivity {

    private static final String TAG = "SavingsOverviewActivity";

    private ActivitySavingsOverviewBinding binding;
    private WishViewModel viewModel;
    private SavingsGoalAdapter adapter;

    // 防抖相关
    private static final long SYNC_DEBOUNCE_MS = 1500; // 1.5 秒
    private long lastSyncTimeMs = 0;


    // 当前选中准备删除的愿望
    private Wish pendingDeleteWish = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivitySavingsOverviewBinding.inflate(getLayoutInflater());
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

        viewModel = new ViewModelProvider(this).get(WishViewModel.class);
        viewModel.syncWishesFromCloud();

        setupRecyclerView();
        setupChart();
        setupClickListeners();
        observeData();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ==================== 初始化 ====================

    private void setupRecyclerView() {
        adapter = new SavingsGoalAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        adapter.setOnWishClickListener(new SavingsGoalAdapter.OnWishClickListener() {
            @Override
            public void onWishClick(Wish wish) {
                // 跳转愿望详情页
                Intent intent = new Intent(SavingsOverviewActivity.this, SavingsActivity.class);
                intent.putExtra(SavingsActivity.EXTRA_WISH_ID, wish.getId());
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onWishLongClick(Wish wish) {
                // 长按弹出删除确认
                showDeleteDialog(wish);
            }
        });
    }

    private void setupClickListeners() {
        // 返回
        binding.ivBack.setOnClickListener(v -> finish());

        // 新增愿望
        binding.ivAdd.setOnClickListener(v -> {
            AddWishFragment addWishFragment = new AddWishFragment();
            addWishFragment.show(getSupportFragmentManager(), "AddWishTag");
        });

        // 删除（提示用户长按 item）
        binding.ivDelete.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("删除愿望")
                        .setMessage("请长按列表中的愿望卡片进行删除")
                        .setPositiveButton("知道了", null)
                        .show()
        );
    }

    private void observeData() {
        viewModel.cloudSyncFinished.observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {

                viewModel.getAllWishes().observe(this, wishes -> {
                    if (wishes == null) return;

                    adapter.submitList(wishes);
                    updateChart(wishes);
                });
            }
        });

        viewModel.operationState.observe(this, response -> {
            if (response == null) return;
            if (response.isSuccess() && pendingDeleteWish != null) {
                pendingDeleteWish = null;
            }
        });
    }

    // ==================== 删除 ====================

    private void showDeleteDialog(Wish wish) {
        pendingDeleteWish = wish;
        new AlertDialog.Builder(this)
                .setTitle("删除愿望")
                .setMessage("确定要删除「" + wish.getWishName() + "」吗？\n相关存钱记录也会一并删除。")
                .setPositiveButton("删除", (d, w) -> viewModel.deleteWish(wish))
                .setNegativeButton("取消", (d, w) -> pendingDeleteWish = null)
                .show();
    }

    // ==================== 图表 ====================

    /**
     * 初始化 LineChart 基础样式（无数据状态）
     */
    private void setupChart() {
        LineChart chart = binding.lineChart;

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(Color.parseColor("#8E8E93"));
        chart.getLegend().setTextSize(12f);
        chart.setNoDataText("暂无存钱计划数据");
        chart.setNoDataTextColor(Color.parseColor("#8E8E93"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#8E8E93"));
        xAxis.setTextSize(11f);
        xAxis.setGranularity(1f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#8E8E93"));
        leftAxis.setTextSize(11f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#F0F0F0"));

        chart.getAxisRight().setEnabled(false);
    }

    /**
     * 更新折线图：每条愿望一条折线，X轴为愿望序号，Y轴为进度百分比
     * 折线图展示：所有愿望的【当前进度%】对比
     */
    private void updateChart(List<Wish> wishes) {
        LineChart chart = binding.lineChart;

        if (wishes == null || wishes.isEmpty()) {
            chart.clear();
            chart.setNoDataText("暂无存钱计划，点击右上角 + 新增");
            chart.invalidate();
            return;
        }

        // 构建 X 轴标签（愿望名称，最多显示 6 个字）
        List<String> labels = new ArrayList<>();
        List<Entry> progressEntries = new ArrayList<>();
        List<Entry> targetEntries  = new ArrayList<>();  // 目标线（100%）

        for (int i = 0; i < wishes.size(); i++) {
            Wish w = wishes.get(i);
            String label = w.getWishName().length() > 4
                    ? w.getWishName().substring(0, 4) + "…"
                    : w.getWishName();
            labels.add(label);

            float progressPct = w.getTargetAmount() > 0
                    ? (float) (w.getCurrentAmount() / w.getTargetAmount() * 100f)
                    : 0f;
            progressEntries.add(new Entry(i, Math.min(progressPct, 100f)));
            targetEntries.add(new Entry(i, 100f));
        }

        // 进度折线
        LineDataSet progressSet = new LineDataSet(progressEntries, "存钱进度(%)");
        progressSet.setColor(Color.parseColor("#1C1C1E"));
        progressSet.setCircleColor(Color.parseColor("#1C1C1E"));
        progressSet.setLineWidth(2.5f);
        progressSet.setCircleRadius(4f);
        progressSet.setDrawValues(true);
        progressSet.setValueTextSize(10f);
        progressSet.setValueTextColor(Color.parseColor("#1C1C1E"));
        progressSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        progressSet.setDrawFilled(true);
        progressSet.setFillColor(Color.parseColor("#1C1C1E"));
        progressSet.setFillAlpha(20);

        // 目标线（虚线 100%）
        LineDataSet targetSet = new LineDataSet(targetEntries, "目标(100%)");
        targetSet.setColor(Color.parseColor("#E5E5EA"));
        targetSet.setLineWidth(1.5f);
        targetSet.setDrawCircles(false);
        targetSet.setDrawValues(false);
        targetSet.enableDashedLine(10f, 6f, 0f);

        LineData lineData = new LineData(progressSet, targetSet);

        // X 轴标签
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setLabelCount(Math.min(wishes.size(), 6));

        chart.setData(lineData);
        chart.animateX(600);
        chart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        triggerCloudSync();
    }

    private void triggerCloudSync() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTimeMs < SYNC_DEBOUNCE_MS) {
            return;
        }
        lastSyncTimeMs = now;

        viewModel.syncWishesFromCloud();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}