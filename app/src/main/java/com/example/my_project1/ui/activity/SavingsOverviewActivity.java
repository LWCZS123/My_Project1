package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ActivitySavingsOverviewBinding;
import com.example.my_project1.ui.adapter.desire.SavingsGoalAdapter;
import com.example.my_project1.ui.fragment.AddWishFragment;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;

public class SavingsOverviewActivity extends AppCompatActivity {

    private ActivitySavingsOverviewBinding binding;
    private WishViewModel viewModel;
    private SavingsGoalAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingsOverviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(WishViewModel.class);

        setupRecyclerView();
        setupClickListeners();
        observeData();
    }

    private void setupRecyclerView() {
        adapter = new SavingsGoalAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        adapter.setOnWishClickListener(new SavingsGoalAdapter.OnWishClickListener() {
            @Override
            public void onWishClick(Wish wish) {
                Intent intent = new Intent(SavingsOverviewActivity.this, SavingsActivity.class);
                intent.putExtra("extra_wish_id", wish.getId());
                startActivity(intent);
            }

            @Override
            public void onWishLongClick(Wish wish) {
                // 长按删除逻辑
            }
        });
    }

    private void setupClickListeners() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivAdd.setOnClickListener(v -> {
            AddWishFragment addWishFragment = new AddWishFragment();
            addWishFragment.show(getSupportFragmentManager(), "AddWishTag");
        });
    }

    private void observeData() {
        // 从 ViewModel 获取用户 ID 并查询所有愿望
        // String userId = ...;
        // viewModel.getAllWishes(userId).observe(this, wishes -> adapter.submitList(wishes));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
