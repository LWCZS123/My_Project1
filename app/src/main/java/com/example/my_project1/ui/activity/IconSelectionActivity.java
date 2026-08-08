package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ActivityIconSelectionBinding;
import com.example.my_project1.ui.adapter.icon.IconSelectionCategoryAdapter;
import com.example.my_project1.ui.adapter.icon.IconSelectionColorAdapter;
import com.example.my_project1.ui.adapter.icon.IconSelectionGridAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.example.my_project1.ui.viewmodel.icon.IconSelectionViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;

import cn.bmob.v3.BmobUser;

/**
 * IconSelectionActivity - 分类图标选择界面 (增强版)
 */
public class IconSelectionActivity extends AppCompatActivity {

    private static final String TAG = "IconSelectionActivity";

    public static final String EXTRA_MODE = "extra_mode"; // "add" or "modify"
    public static final String EXTRA_TYPE = "extra_type"; // "category" or "subcategory"
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_ICON_URI = "extra_icon_uri";
    public static final String EXTRA_ICON_BG_COLOR = "extra_icon_bg_color";
    public static final String EXTRA_EXCLUDE_BUDGET = "extra_exclude_budget";
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_PARENT_ID = "extra_parent_id";
    public static final String EXTRA_PARENT_CLOUD_ID = "extra_parent_cloud_id";
    public static final String EXTRA_CATEGORY_TYPE = "extra_category_type"; // "expense" or "income"

    private ActivityIconSelectionBinding binding;
    private IconSelectionViewModel viewModel;
    private CategoryViewModel categoryViewModel;
    private SubCategoryViewModel subCategoryViewModel;

    private IconSelectionCategoryAdapter categoryAdapter;
    private IconSelectionGridAdapter iconGridAdapter;
    private IconSelectionColorAdapter colorAdapter;

    private String userId;

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

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        viewModel = new ViewModelProvider(this).get(IconSelectionViewModel.class);
        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);
        subCategoryViewModel = new ViewModelProvider(this).get(SubCategoryViewModel.class);

        BmobUser currentUser = BmobUser.getCurrentUser();
        userId = currentUser != null ? currentUser.getObjectId() : null;

        handleIntent();
        initViews();
        setupListeners();
        observeData();

        viewModel.loadInitialData();
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String mode = intent.getStringExtra(EXTRA_MODE);
        if (mode == null) mode = "add";
        String type = intent.getStringExtra(EXTRA_TYPE);
        if (type == null) type = "category";
        long id = intent.getLongExtra(EXTRA_ID, -1L);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String name = intent.getStringExtra(EXTRA_NAME);
        String iconUri = intent.getStringExtra(EXTRA_ICON_URI);
        String iconBgColor = intent.getStringExtra(EXTRA_ICON_BG_COLOR);
        boolean excludeBudget = intent.getBooleanExtra(EXTRA_EXCLUDE_BUDGET, false);

        viewModel.setMode(mode, type, id);
        if (title != null) binding.tvTitle.setText(title);
        if (name != null) binding.etCategoryName.setText(name);
        if (iconBgColor != null) viewModel.selectColor(iconBgColor);
        if (iconUri != null) {
            // 这里可能需要一个方式来告诉 viewModel 初始选中的图标 URI，
            // 但目前的 IconSelectionViewModel 主要是根据分类加载图标。
            // 简单处理：仅预览显示。
            ImageLoaderUtils.load(this, iconUri, binding.ivSelectedIcon);
        }
        viewModel.setIncludeInBudget(!excludeBudget);
        binding.switchBudget.setChecked(!excludeBudget);
    }

    private void initViews() {
        categoryAdapter = new IconSelectionCategoryAdapter(category -> {
            viewModel.selectCategory(category);
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategories.setAdapter(categoryAdapter);

        iconGridAdapter = new IconSelectionGridAdapter(icon -> {
            viewModel.selectIcon(icon);
        });
        binding.rvIconGrid.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvIconGrid.setAdapter(iconGridAdapter);

        colorAdapter = new IconSelectionColorAdapter(color -> {
            viewModel.selectColor(color);
        });
        binding.rvColors.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvColors.setAdapter(colorAdapter);
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.ivConfirm.setOnClickListener(v -> handleConfirm());

        binding.etCategoryName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.ivClearName.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.ivClearName.setOnClickListener(v -> binding.etCategoryName.setText(""));

        binding.switchBudget.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setIncludeInBudget(isChecked);
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

    private void handleConfirm() {
        String name = binding.etCategoryName.getText().toString().trim();
        if (name.isEmpty()) {
            SnackbarUtils.showError(binding.getRoot(), "请输入类型名称");
            return;
        }

        IconItem selectedIcon = viewModel.selectedIcon.getValue();
        String iconUri = (selectedIcon != null) ? selectedIcon.getUrl() : getIntent().getStringExtra(EXTRA_ICON_URI);
        
        if (iconUri == null || iconUri.isEmpty()) {
            SnackbarUtils.showError(binding.getRoot(), "请选择一个图标");
            return;
        }

        String color = viewModel.selectedColor.getValue();
        boolean excludeBudget = !binding.switchBudget.isChecked();

        String mode = viewModel.mode.getValue();
        String type = viewModel.targetType.getValue();
        long id = viewModel.targetId.getValue();

        if ("add".equals(mode)) {
            if ("category".equals(type)) {
                String catType = getIntent().getStringExtra(EXTRA_CATEGORY_TYPE);
                Category category = new Category(userId, catType, name, iconUri, excludeBudget);
                category.setIconBackgroundColor(color);
                categoryViewModel.insert(category);
                Toast.makeText(this, "分类已添加", Toast.LENGTH_SHORT).show();
            } else {
                long parentId = getIntent().getLongExtra(EXTRA_PARENT_ID, -1L);
                String parentCloudId = getIntent().getStringExtra(EXTRA_PARENT_CLOUD_ID);
                SubCategory subCategory = new SubCategory(parentId, parentCloudId, userId, name, iconUri);
                subCategory.setIconBackgroundColor(color);
                subCategory.setExcludeBudget(excludeBudget);
                subCategoryViewModel.insert(subCategory);
                Toast.makeText(this, "子分类已添加", Toast.LENGTH_SHORT).show();
            }
        } else {
            if ("category".equals(type)) {
                categoryViewModel.updateCategorySafe(id, name, iconUri, color, excludeBudget);
                Toast.makeText(this, "分类已修改", Toast.LENGTH_SHORT).show();
            } else {
                subCategoryViewModel.updateSubCategorySafe(id, name, iconUri, color, excludeBudget);
                Toast.makeText(this, "子分类已修改", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private void observeData() {
        viewModel.categories.observe(this, list -> categoryAdapter.submitList(list));
        viewModel.selectedCategory.observe(this, category -> categoryAdapter.setSelectedCategory(category));
        viewModel.iconItems.observe(this, list -> iconGridAdapter.submitList(list));

        viewModel.selectedIcon.observe(this, icon -> {
            if (icon != null) {
                ImageLoaderUtils.load(this, icon.getUrl(), binding.ivSelectedIcon);
                binding.etCategoryName.setText(icon.getName());
            }
        });

        viewModel.selectedColor.observe(this, colorStr -> {
            if (colorStr == null) {
                binding.flSelectedIconBg.setBackgroundResource(R.drawable.bg_circle_grey);
                colorAdapter.setSelectedColor(null);
                return;
            }
            try {
                int color = Color.parseColor(colorStr);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(color);
                binding.flSelectedIconBg.setBackground(gd);
                colorAdapter.setSelectedColor(colorStr);
            } catch (Exception e) {
                Log.e(TAG, "Invalid color: " + colorStr);
                binding.flSelectedIconBg.setBackgroundResource(R.drawable.bg_circle_grey);
            }
        });

        viewModel.loading.observe(this, isLoading -> {
            binding.lavLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.rvIconGrid.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        });

        viewModel.errorMessage.observe(this, msg -> {
            if (msg != null) SnackbarUtils.showError(binding.getRoot(), msg);
        });
    }
}
