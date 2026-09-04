package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.databinding.ActivitySavingPlanBinding;
import com.example.my_project1.ui.adapter.saving.SavingPlanAdapter;
import com.example.my_project1.ui.fragment.AddSavingPlanFragment;
import com.example.my_project1.ui.viewmodel.saving.SavingViewModel;

/**
 * 存钱计划主界面
 * -------------------------------------------------------
 * ✅ 重构版：将 NestedScrollView 替换为单 RecyclerView 多类型实现
 * ✅ 消除闪烁：通过统一数据流和禁用默认动画提升流畅度
 * ✅ 预加载：优化 ViewModel 观察逻辑，结合 Room 缓存实现秒开
 */
public class SavingPlanActivity extends AppCompatActivity {

    public static final String EXTRA_PLAN_ID = "extra_plan_id";
    private static final long SYNC_INTERVAL = 5 * 60 * 1000;
    
    private ActivitySavingPlanBinding binding;
    private SavingViewModel viewModel;
    private SavingPlanAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindow();
        initViewModel();
        setupList();
        observe();
        
        checkAndEnqueueSync();
    }

    private void checkAndEnqueueSync() {
        android.content.SharedPreferences sp = getSharedPreferences("saving_prefs", MODE_PRIVATE);
        long lastSync = sp.getLong("last_full_sync", 0);
        long now = System.currentTimeMillis();
        
        if (now - lastSync > SYNC_INTERVAL) {
            com.example.my_project1.work.SavingSyncWorker.enqueue(this);
            sp.edit().putLong("last_full_sync", now).apply();
        }
    }

    private void setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.parseColor("#F7F6FA"));
        getWindow().setNavigationBarColor(Color.parseColor("#F7F6FA"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        if (bars != null) {
            bars.setAppearanceLightStatusBars(true);
            bars.setAppearanceLightNavigationBars(true);
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(SavingViewModel.class);
    }

    private void setupList() {
        adapter = new SavingPlanAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // 🚀 增加预加载项数量，减少滚动时的创建开销
        layoutManager.setInitialPrefetchItemCount(8);
        
        binding.rvPlans.setLayoutManager(layoutManager);
        binding.rvPlans.setAdapter(adapter);
        // 🚀 禁用动画以消除数据加载时的 Item 闪烁
        if (binding.rvPlans.getItemAnimator() != null) {
            binding.rvPlans.setItemAnimator(null);
        }
        
        adapter.setOnPlanActionListener(new SavingPlanAdapter.OnPlanActionListener() {
            @Override
            public void onPlanClick(SavingPlan plan) {
                Intent intent = new Intent(SavingPlanActivity.this, SavingPlanDetailActivity.class);
                intent.putExtra(EXTRA_PLAN_ID, plan.getId());
                startActivity(intent);
            }

            @Override
            public void onPlanEdit(SavingPlan plan) {
                AddSavingPlanFragment.newInstanceForEdit(plan.getId())
                        .show(getSupportFragmentManager(), "edit_plan");
            }

            @Override
            public void onPlanDelete(SavingPlan plan) {
                new com.example.my_project1.ui.dialog.SavingPlanDeleteDialog(SavingPlanActivity.this, 
                        plan.getName(), deleteBills -> viewModel.deletePlan(plan.getId(), deleteBills))
                        .show();
            }

            @Override
            public void onPlanArchive(SavingPlan plan) {
                viewModel.toggleArchivePlan(plan);
            }

            @Override
            public void onMethodClick(int type) {
                AddSavingPlanFragment.newInstance(type).show(getSupportFragmentManager(), "add_plan");
            }

            @Override
            public void onViewArchived() {
                startActivity(new Intent(SavingPlanActivity.this, ArchivedSavingPlansActivity.class));
            }
        });
    }

    private void observe() {
        // 🚀 使用 ViewModel 组合后的统一数据流，确保 Header 和计划同步渲染
        viewModel.getMainSavingStream().observe(this, items -> {
            if (items != null) {
                adapter.submitList(items);
            }
        });

        // 观察同步状态提示
        viewModel.getOperationState().observe(this, state -> {
            if (state != null && state.isTerminal() && state.getMessage() != null) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                viewModel.resetOperationState();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
