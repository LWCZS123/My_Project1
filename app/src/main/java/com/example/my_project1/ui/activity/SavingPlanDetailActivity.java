package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.databinding.ActivitySavingPlanDetailBinding;
import com.example.my_project1.ui.adapter.saving.SavingStepAdapter;
import com.example.my_project1.ui.fragment.AddSavingRecordFragmentForSaving;
import com.example.my_project1.ui.viewmodel.saving.SavingCardUiModel;
import com.example.my_project1.ui.viewmodel.saving.SavingViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 存钱计划详情页
 * 优化：使用单 RecyclerView 实现 Header + Grid 内容，提高大批量卡片加载性能
 */
public class SavingPlanDetailActivity extends AppCompatActivity {

    private ActivitySavingPlanDetailBinding binding;
    private SavingViewModel viewModel;
    private SavingStepAdapter adapter;
    private long planId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingPlanDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        planId = getIntent().getLongExtra(SavingPlanActivity.EXTRA_PLAN_ID, -1);
        if (planId == -1) {
            finish();
            return;
        }

        setupWindow();
        initViewModel();
        setupList();
        setupToolbar();
        observe();
    }

    private void setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.parseColor("#F7F6FA"));
        getWindow().setNavigationBarColor(Color.parseColor("#F7F6FA"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(SavingViewModel.class);
    }

    private void setupList() {
        adapter = new SavingStepAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == 0 ? 2 : 1;
            }
        });
        
        binding.rvSteps.setLayoutManager(layoutManager);
        binding.rvSteps.setAdapter(adapter);
        
        adapter.setOnStepClickListener(new SavingStepAdapter.OnStepClickListener() {
            @Override
            public void onStepClick(SavingCardUiModel step) {
                if (!step.isCompleted()) {
                    AddSavingRecordFragmentForSaving.newInstance(planId, step.getStepIndex(), step.getAmount())
                            .show(getSupportFragmentManager(), "add_record");
                } else {
                    confirmRecordDelete(step.getRecordId());
                }
            }

            @Override
            public void onRecordDelete(long recordId) {
                confirmRecordDelete(recordId);
            }
        });
    }

    private void confirmRecordDelete(long recordId) {
        if (recordId <= 0) return;
        new com.example.my_project1.ui.dialog.ConfirmDialog(this)
                .setTitle("删除记录")
                .setMessage("确定要删除这笔存钱记录吗？")
                .setConfirmListener(() -> viewModel.deleteRecord(recordId))
                .show();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.ivAddRecord.setOnClickListener(v -> AddSavingRecordFragmentForSaving.newInstance(planId, -1, 0)
                .show(getSupportFragmentManager(), "add_record"));
    }

    private void observe() {
        // 使用组合后的 UI 数据流，确保 Plan (Header) 和 Cards 同时更新，并使用 distinctUntilChanged 减少不必要的 UI 刷新
        androidx.lifecycle.LiveData<List<Object>> detailStream = viewModel.getSavingDetailStream(planId);
        
        detailStream.observe(this, items -> {
            if (items != null && !items.isEmpty()) {
                // 更新 Toolbar 标题（仅在变化时更新，防止 UI 抖动）
                Object first = items.get(0);
                if (first instanceof SavingPlan) {
                    String name = ((SavingPlan) first).getName();
                    if (!name.equals(binding.tvToolbarTitle.getText().toString())) {
                        binding.tvToolbarTitle.setText(name);
                    }
                }
                
                // 使用 ListAdapter 的 submitList 处理差异更新
                // 注意：items 已经在 ViewModel 中经过了 equals 校验
                adapter.submitList(new ArrayList<>(items));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
