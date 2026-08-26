package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ActivitySavingsOverviewBinding;
import com.example.my_project1.ui.adapter.desire.SavingsGoalAdapter;
import com.example.my_project1.ui.fragment.AddWishFragment;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;

import java.util.Collections;

public class SavingsOverviewActivity extends AppCompatActivity {

    public static final String EXTRA_WISH_ID = "extra_wish_id";

    private ActivitySavingsOverviewBinding binding;
    private WishViewModel viewModel;
    private SavingsGoalAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingsOverviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#F7F6FA"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F7F6FA"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
        viewModel = new ViewModelProvider(this).get(WishViewModel.class);
        setupList();
        binding.ivAdd.setOnClickListener(v ->
                AddWishFragment.newInstance().show(getSupportFragmentManager(), "wish_form"));
        observe();
        viewModel.syncNow();
    }

    private void setupList() {
        adapter = new SavingsGoalAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setAdapter(adapter);
        adapter.setOnWishClickListener(new SavingsGoalAdapter.OnWishClickListener() {
            @Override
            public void onWishClick(Wish wish) {
                startActivity(new Intent(SavingsOverviewActivity.this, SavingsActivity.class)
                        .putExtra(EXTRA_WISH_ID, wish.getId()));
            }

            @Override
            public void onWishLongClick(Wish wish) {
                confirmDelete(wish);
            }
        });
    }

    private void observe() {
        viewModel.getAllWishes().observe(this, wishes -> {
            if (wishes == null) wishes = Collections.emptyList();
            adapter.submitList(wishes);
            binding.emptyState.setVisibility(wishes.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getOperationState().observe(this, state -> {
            binding.progressLoading.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            if (state.isTerminal() && state.getMessage() != null) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                binding.getRoot().post(viewModel::resetOperationState);
            }
        });
    }

    private void confirmDelete(Wish wish) {
        new ConfirmDialog(this)
                .setTitle("删除愿望")
                .setMessage("将同时删除该愿望的全部存钱记录，确定继续吗？")
                .setConfirmListener(() -> viewModel.deleteWish(wish.getId()))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
