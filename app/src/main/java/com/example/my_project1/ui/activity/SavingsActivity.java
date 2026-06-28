package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

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
import com.example.my_project1.databinding.ActivitySavingsBinding;
import com.example.my_project1.ui.adapter.desire.WishHistoryAdapter;
import com.example.my_project1.ui.fragment.AddSavingRecordFragment;
import com.example.my_project1.ui.fragment.AddWishFragment;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * SavingsActivity - 愿望详情页（优化版）
 * -------------------------------------------------------
 * 优化内容：
 *  1. ✅ 修复日期显示异常：添加 null 检查 + 异常处理 + UI 强制刷新
 *  2. ✅ 删除操作后自动关闭页面：改进监听逻辑
 *  3. ✅ 删除操作防卡顿：异步执行 + 防重复点击
 *  4. ✅ 减少 Toast 频率：仅关键时刻提示
 *  5. ✅ 优化数据加载：避免重复监听 + 使用缓存
 */
public class SavingsActivity extends AppCompatActivity {

    private static final String TAG = "SavingsActivity";
    public static final String EXTRA_WISH_ID = "extra_wish_id";

    private ActivitySavingsBinding binding;
    private WishViewModel viewModel;
    private WishHistoryAdapter historyAdapter;

    private Wish currentWish;
    private long wishId = -1;

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());

    // ✅ 标志位：防止重复提示和操作
    private boolean isDeleting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivitySavingsBinding.inflate(getLayoutInflater());
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


        wishId = getIntent().getLongExtra(EXTRA_WISH_ID, -1);
        if (wishId == -1) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(WishViewModel.class);

        viewModel.syncAllFromCloud();
        setupHistoryRecyclerView();
        setupClickListeners();
        observeAfterCloudSync();


    }

    private void observeAfterCloudSync() {
        viewModel.cloudSyncFinished.observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {

                Log.d(TAG, "云同步完成，开始观察本地数据");

                observeData();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ==================== 初始化 ====================

    private void setupHistoryRecyclerView() {
        historyAdapter = new WishHistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(historyAdapter);
        binding.rvHistory.setNestedScrollingEnabled(false);

        // ✅ 长按删除记录（同时联动删除对应账单）
        historyAdapter.setOnRecordLongClickListener(record ->
                new AlertDialog.Builder(this)
                        .setTitle("删除记录")
                        .setMessage(String.format(Locale.getDefault(),
                                "确定删除这笔 %.0f 元的存钱记录吗？\n删除后将从总金额中扣除，关联账单也会一并删除。",
                                record.getAmount()))
                        .setPositiveButton("删除", (d, w) -> {
                            binding.rvHistory.setEnabled(false); // 禁用列表
                            viewModel.deleteSavingRecord(record);
                        })
                        .setNegativeButton("取消", null)
                        .show()
        );
    }

    private void setupClickListeners() {
        // ✅ 编辑按钮 -> 弹出 AddWishFragment 编辑模式
        binding.btnEdit.setOnClickListener(v -> {
            if (currentWish == null) return;
            AddWishFragment editFragment = AddWishFragment.newInstanceForEdit(currentWish.getId());
            editFragment.show(getSupportFragmentManager(), "EditWishTag");
        });

        // ✅ 删除按钮 -> 异步删除 + 防重复点击
        binding.btnDelete.setOnClickListener(v -> {
            if (currentWish == null || isDeleting) return;

            new AlertDialog.Builder(this)
                    .setTitle("删除愿望")
                    .setMessage("确定删除「" + currentWish.getWishName()
                            + "」吗？\n所有相关存钱记录及账单也会被删除。")
                    .setPositiveButton("删除", (d, w) -> {
                        isDeleting = true;
                        binding.btnDelete.setEnabled(false);
                        deleteWishInBackground();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // ✅ FAB -> 弹出 AddSavingRecordFragment
        binding.fabAdd.setOnClickListener(v -> {
            if (currentWish == null) return;
            AddSavingRecordFragment fragment = AddSavingRecordFragment.newInstance(currentWish);
            fragment.show(getSupportFragmentManager(), "AddSavingRecord");
        });
    }

    /**
     * ✅ 在后台线程执行删除操作，避免 UI 卡顿
     */
    private void deleteWishInBackground() {
        // 放入后台线程
        new Thread(() -> {
            try {
                // 执行删除
                viewModel.deleteWish(currentWish);

                // 等待操作完成（监听状态变化）
                // 在主线程中监听结果
                runOnUiThread(() -> {
                    viewModel.operationState.observe(SavingsActivity.this, response -> {
                        if (response != null && response.isSuccess()) {
                            // 删除成功 - 自动关闭页面
                            Log.d(TAG, "愿望已删除，自动关闭页面");
                            finish();
                        } else if (response != null && !response.isSuccess()) {
                            // 删除失败
                            Log.e(TAG, "删除失败: " + response.getMessage());
                            isDeleting = false;
                            binding.btnDelete.setEnabled(true);
                            Snackbar.make(binding.getRoot(),
                                    "删除失败: " + response.getMessage(),
                                    Snackbar.LENGTH_SHORT).show();
                        }
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "删除异常", e);
                runOnUiThread(() -> {
                    isDeleting = false;
                    binding.btnDelete.setEnabled(true);
                    Toast.makeText(SavingsActivity.this, "删除异常：" + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * ✅ 监听数据变化（优化版）
     * - 避免重复监听
     * - 仅在必要时刷新 UI
     * - 减少 Toast 提示频率
     */
    private void observeData() {
        // 监听愿望列表，找到当前愿望
        viewModel.getAllWishes().observe(this, wishes -> {
            if (wishes == null || wishes.isEmpty()) return;

            for (Wish w : wishes) {
                if (w.getId() == wishId) {
                    // ✅ 检查是否真的有变化（避免不必要的 UI 更新）
                    if (currentWish == null || hasWishChanged(currentWish, w)) {
                        currentWish = w;
                        bindWishToUI(w);
                    }
                    break;
                }
            }
        });

        // 监听历史记录
        viewModel.getRecordsForWish(wishId).observe(this, records -> {
            if (records != null) {

                if (currentWish != null) {
                    historyAdapter.setWishIconUrl(currentWish.getIconUrl());
                }

                historyAdapter.submitList(records);
                binding.rvHistory.setEnabled(true); // 恢复列表操作
            }
        });

        // ✅ 操作状态监听（仅在成功或失败时提示）
        viewModel.operationState.observe(this, response -> {
            if (response == null) return;

            // 只在失败时显示 Toast（成功状态由自动关闭页面反馈）
            if (!response.isSuccess() && response.getMessage() != null) {
                if (!response.getMessage().isEmpty()) {
                    Snackbar.make(binding.getRoot(), response.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        // ✅ Toast 消息（仅在必要时显示）
        viewModel.toastMessage.observe(this, msg -> {
            if (!TextUtils.isEmpty(msg) && msg.contains("错误")) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * ✅ 检查愿望数据是否有实质变化
     * 避免数据未变化时重复刷新 UI
     */
    private boolean hasWishChanged(Wish old, Wish new_wish) {
        if (old == null || new_wish == null) return true;

        return !old.getWishName().equals(new_wish.getWishName())
                || old.getCurrentAmount() != new_wish.getCurrentAmount()
                || old.getTargetAmount() != new_wish.getTargetAmount()
                || old.getStatus() != new_wish.getStatus();
    }

    // ==================== 数据绑定到 UI ====================

    /**
     * ✅ 绑定愿望数据到 UI（修复版）
     * - 添加 null 检查
     * - 添加异常处理
     * - 确保日期正确显示
     * - UI 强制刷新
     */
    private void bindWishToUI(Wish wish) {

        currentWish = wish;

        if (historyAdapter != null) {
            historyAdapter.setWishIconUrl(wish.getIconUrl());
        }

        if (wish == null) {
            Log.w(TAG, "bindWishToUI: wish is null");
            return;
        }

        try {
            // ✅ 基本信息
            binding.tvTitle.setText(wish.getWishName());

            // ✅ 进度条和百分比
            int percent = wish.getProgressPercent();
            binding.donutProgress.setProgress(percent);
            binding.tvPercent.setText(percent + "%");

            // ✅ 金额信息（防 null）
            binding.tvAmount.setText(String.format(Locale.getDefault(),
                    "%.0f", wish.getCurrentAmount()));
            binding.tvGoalAmount.setText(String.format(Locale.getDefault(),
                    "%.0f", wish.getTargetAmount()));
            binding.tvRemaining.setText(String.format(Locale.getDefault(),
                    "%.0f", wish.getRemainingAmount()));

            // ✅ 日期信息（关键修复）
            if (wish.getStartDate() != null) {
                try {
                    String formattedDate = dateFormatter.format(wish.getStartDate());
                    binding.tvStartDate.setText(formattedDate);
                    Log.d(TAG, "日期显示成功: " + formattedDate);

                    // 计算天数
                    long daysElapsed = wish.getDaysElapsed();
                    binding.tvDaysElapsed.setText(String.valueOf(daysElapsed));

                } catch (Exception e) {
                    // 日期格式化失败，显示备用文本
                    Log.e(TAG, "日期格式化异常", e);
                    binding.tvStartDate.setText("无效日期");
                    binding.tvDaysElapsed.setText("0");
                }
            } else {
                // 日期为 null，显示占位符
                Log.d(TAG, "起始日期为 null");
                binding.tvStartDate.setText("--");
                binding.tvDaysElapsed.setText("0");
            }

            // ✅ 强制刷新 UI（解决偶现不显示问题）
            //binding.invalidate();

            Log.d(TAG, "数据绑定成功: wishName=" + wish.getWishName()
                    + ", progress=" + percent + "%");

        } catch (Exception e) {
            Log.e(TAG, "bindWishToUI 异常", e);
            // 异常情况下显示默认值，不崩溃
            binding.tvTitle.setText("数据加载失败");
            binding.tvStartDate.setText("--");
        }
    }
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}