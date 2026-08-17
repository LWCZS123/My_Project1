package com.example.my_project1.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.databinding.ActivitySavingsBinding;
import com.example.my_project1.ui.adapter.desire.WishHistoryAdapter;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;

public class SavingsActivity extends AppCompatActivity {

    private ActivitySavingsBinding binding;
    private WishViewModel viewModel;
    private WishHistoryAdapter historyAdapter;
    private long wishId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        wishId = getIntent().getLongExtra("extra_wish_id", -1);
        viewModel = new ViewModelProvider(this).get(WishViewModel.class);

        setupRecyclerView();
        observeData();
    }

    private void setupRecyclerView() {
        historyAdapter = new WishHistoryAdapter();
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(historyAdapter);
    }

    private void observeData() {
        if (wishId != -1) {
            viewModel.getWishById(wishId).observe(this, wish -> {
                if (wish != null) {
                    // 更新 UI
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
