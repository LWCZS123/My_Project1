package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.databinding.ActivityArchivedSavingPlansBinding;
import com.example.my_project1.ui.adapter.saving.SavingPlanAdapter;
import com.example.my_project1.ui.viewmodel.saving.SavingViewModel;

/**
 * 归档存钱计划页面
 */
public class ArchivedSavingPlansActivity extends AppCompatActivity {

    private ActivityArchivedSavingPlansBinding binding;
    private SavingViewModel viewModel;
    private SavingPlanAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArchivedSavingPlansBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SavingViewModel.class);

        setupWindow();
        setupUI();
        observeData();
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

    private void setupUI() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.tvTitle.setText("已归档计划");

        adapter = new SavingPlanAdapter();
        binding.rvPlans.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlans.setAdapter(adapter);

        adapter.setOnPlanActionListener(new SavingPlanAdapter.OnPlanActionListener() {
            @Override
            public void onPlanClick(SavingPlan plan) {
                android.content.Intent intent = new android.content.Intent(ArchivedSavingPlansActivity.this, SavingPlanDetailActivity.class);
                intent.putExtra(SavingPlanActivity.EXTRA_PLAN_ID, plan.getId());
                startActivity(intent);
            }

            @Override
            public void onPlanEdit(SavingPlan plan) {
                com.example.my_project1.ui.fragment.AddSavingPlanFragment.newInstanceForEdit(plan.getId())
                        .show(getSupportFragmentManager(), "edit_plan");
            }

            @Override
            public void onPlanDelete(SavingPlan plan) {
                new com.example.my_project1.ui.dialog.SavingPlanDeleteDialog(ArchivedSavingPlansActivity.this, 
                        plan.getName(), deleteBills -> viewModel.deletePlan(plan.getId(), deleteBills))
                        .show();
            }

            @Override
            public void onPlanArchive(SavingPlan plan) {
                // 这里的归档按钮实际效果是“恢复”
                viewModel.toggleArchivePlan(plan);
                Toast.makeText(ArchivedSavingPlansActivity.this, "计划已恢复", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMethodClick(int type) {}

            @Override
            public void onViewArchived() {}
        });
    }

    private void observeData() {
        viewModel.getArchivedPlans().observe(this, plans -> {
            if (plans != null) {
                adapter.submitList(new java.util.ArrayList<>(plans));
            }
            // 这里可以添加空状态显示逻辑
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
