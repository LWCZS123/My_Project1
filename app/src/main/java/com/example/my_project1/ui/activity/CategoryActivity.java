package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.databinding.ActivityCategoryBinding;
import com.example.my_project1.ui.adapter.CategoryPagerAdapter;
import com.example.my_project1.ui.fragment.CategoryAddBottomSheetFragment;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;

import org.jetbrains.annotations.Nullable;

import cn.bmob.v3.BmobUser;

public class CategoryActivity extends AppCompatActivity {

    private ActivityCategoryBinding binding;
    private CategoryViewModel categoryViewModel;
    private String userId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 透明状态栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, top, 0, 0);
            return insets;
        });

        // 状态栏深色图标
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        // 获取用户ID
        BmobUser currentUser = BmobUser.getCurrentUser();
        userId = currentUser != null ? currentUser.getObjectId() : null;

        // 返回按钮
        binding.ivBack.setOnClickListener(v -> finish());

        // ViewPager2
        binding.viewPager.setAdapter(new CategoryPagerAdapter(this));

        // 初始化选中支出
        binding.tabExpense.setSelected(true);
        binding.tabExpense.setOnClickListener(v -> {
            binding.viewPager.setCurrentItem(0);
            updateTabStyle(0);
        });

        binding.tabIncome.setOnClickListener(v -> {
            binding.viewPager.setCurrentItem(1);
            updateTabStyle(1);
        });

        // 页面滑动同步
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabStyle(position);
            }
        });

        // 新建分类
        binding.fabNewCategory.setOnClickListener(v -> {
            int position = binding.viewPager.getCurrentItem();
            String type = position == 0 ? "expense" : "income";

            CategoryAddBottomSheetFragment fragment = new CategoryAddBottomSheetFragment();
            Bundle args = new Bundle();
            args.putString("title", "新建一级分类");
            fragment.setArguments(args);

            fragment.setOnCategoryAddedListener((name, excludeBudget, iconUrl) -> {
                Category category = new Category(userId, type, name, iconUrl, excludeBudget);
                categoryViewModel.insert(category);
                Toast.makeText(this, "已添加分类，后台自动同步中...", Toast.LENGTH_SHORT).show();
            });

            fragment.show(getSupportFragmentManager(), "category_add");
        });
    }

    // 更新顶部按钮样式
    private void updateTabStyle(int position) {
        if (position == 0) {
            binding.tabExpense.setSelected(true);
            binding.tabIncome.setSelected(false);

            binding.tabExpense.setBackgroundResource(R.drawable.tab_selected_bg);
            binding.tabExpense.setTextColor(0xFFFFFFFF);

            binding.tabIncome.setBackgroundResource(0);
            binding.tabIncome.setTextColor(0xFF666666);
        } else {
            binding.tabExpense.setSelected(false);
            binding.tabIncome.setSelected(true);

            binding.tabExpense.setBackgroundResource(0);
            binding.tabExpense.setTextColor(0xFF666666);

            binding.tabIncome.setBackgroundResource(R.drawable.tab_selected_bg);
            binding.tabIncome.setTextColor(0xFFFFFFFF);
        }
    }
}