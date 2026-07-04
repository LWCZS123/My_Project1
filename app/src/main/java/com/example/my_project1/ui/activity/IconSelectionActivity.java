package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ActivityIconSelectionBinding;
import com.example.my_project1.ui.adapter.icon.IconSelectionCategoryAdapter;
import com.example.my_project1.ui.adapter.icon.IconSelectionColorAdapter;
import com.example.my_project1.ui.adapter.icon.IconSelectionGridAdapter;
import com.example.my_project1.ui.viewmodel.icon.IconSelectionViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * IconSelectionActivity - 分类图标选择界面
 * -------------------------------------------------------
 * 功能：
 * 1. 20个分类，每个分类最多50个图标
 * 2. 支持分类切换（左侧列表）
 * 3. 支持图标选择（右侧网格）
 * 4. 支持搜索图标
 * 5. 支持更改图标底色
 * 6. 支持输入类型名称
 */
public class IconSelectionActivity extends AppCompatActivity {

    private static final String TAG = "IconSelectionActivity";

    private ActivityIconSelectionBinding binding;
    private IconSelectionViewModel viewModel;

    private IconSelectionCategoryAdapter categoryAdapter;
    private IconSelectionGridAdapter iconGridAdapter;
    private IconSelectionColorAdapter colorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIconSelectionBinding.inflate(getLayoutInflater());
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

        viewModel = new ViewModelProvider(this).get(IconSelectionViewModel.class);

        initViews();
        setupListeners();
        observeData();

        // 首次进入加载数据
        viewModel.loadInitialData();
    }

    private void initViews() {
        // 1. 左侧分类列表
        categoryAdapter = new IconSelectionCategoryAdapter(category -> {
            viewModel.selectCategory(category);
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategories.setAdapter(categoryAdapter);

        // 2. 右侧图标网格
        iconGridAdapter = new IconSelectionGridAdapter(icon -> {
            viewModel.selectIcon(icon);
        });
        binding.rvIconGrid.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvIconGrid.setAdapter(iconGridAdapter);

        // 3. 颜色选择列表
        colorAdapter = new IconSelectionColorAdapter(color -> {
            viewModel.selectColor(color);
        });
        binding.rvColors.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvColors.setAdapter(colorAdapter);
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.ivConfirm.setOnClickListener(v -> {
            // 返回选择结果
            String name = binding.etCategoryName.getText().toString().trim();
            if (name.isEmpty()) {
                SnackbarUtils.showError(binding.getRoot(), "请输入类型名称");
                return;
            }
            // TODO: 处理确认逻辑，比如返回给上一个页面
            finish();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void observeData() {
        // 观察分类列表
        viewModel.categories.observe(this, list -> {
            categoryAdapter.submitList(list);
        });

        // 观察当前选中的分类
        viewModel.selectedCategory.observe(this, category -> {
            categoryAdapter.setSelectedCategory(category);
        });

        // 观察图标列表
        viewModel.iconItems.observe(this, list -> {
            iconGridAdapter.submitList(list);
        });

        // 观察选中的图标
        viewModel.selectedIcon.observe(this, icon -> {
            if (icon != null) {
                ImageLoaderUtils.load(this, icon.getUrl(), binding.ivSelectedIcon);
                // 修复 Bug：点击图标时，总是更新类型名称文本框
                binding.etCategoryName.setText(icon.getName());
            }
        });

        // 观察选中的颜色
        viewModel.selectedColor.observe(this, colorStr -> {
            try {
                int color = Color.parseColor(colorStr);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(color);
                gd.setCornerRadius(8 * getResources().getDisplayMetrics().density);
                binding.flSelectedIconBg.setBackground(gd);
                colorAdapter.setSelectedColor(colorStr);
            } catch (Exception e) {
                Log.e(TAG, "Invalid color: " + colorStr);
            }
        });

        // 观察加载状态
        viewModel.loading.observe(this, isLoading -> {
            // TODO: 显示/隐藏 Loading 状态
        });

        // 观察错误信息
        viewModel.errorMessage.observe(this, msg -> {
            if (msg != null) SnackbarUtils.showError(binding.getRoot(), msg);
        });
    }
}
