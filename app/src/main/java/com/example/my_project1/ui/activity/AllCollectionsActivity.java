package com.example.my_project1.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.my_project1.R;
import com.example.my_project1.databinding.ActivityAllCollectionsBinding;
import com.example.my_project1.ui.adapter.icon.CategoryAdapter;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;

import java.util.ArrayList;
import java.util.List;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.repository.icon.IconRepository;
import com.example.my_project1.ui.fragment.SaveCategoryBottomSheet;
import com.example.my_project1.ui.fragment.SimpleSelectBottomSheet;
import com.example.my_project1.utils.AppExecutors;
import android.content.Intent;
import android.widget.Toast;

/**
 * AllCollectionsActivity - 显示全部图标合集的独立页面 ViewBinding版本
 */
public class AllCollectionsActivity extends AppCompatActivity {

    private IconMarketViewModel viewModel;
    private CategoryAdapter adapter;
    private ActivityAllCollectionsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ViewBinding 绑定布局，替代 setContentView
        binding = ActivityAllCollectionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            float density = getResources().getDisplayMetrics().density;

            // 1. Header Padding - 增加偏移量避免被刘海遮挡
            int extraOffset = (int) (12 * density);
            binding.layoutHeader.setPadding(
                    binding.layoutHeader.getPaddingLeft(),
                    top + extraOffset,
                    binding.layoutHeader.getPaddingRight(),
                    binding.layoutHeader.getPaddingBottom()
            );

            // 2. 将底部导航栏高度设置为 RecyclerView 的 padding，防止遮挡最后一项
            binding.rvAllCategories.setPadding(
                    binding.rvAllCategories.getPaddingLeft(),
                    binding.rvAllCategories.getPaddingTop(),
                    binding.rvAllCategories.getPaddingRight(),
                    bottom + (int) (16 * density)
            );
            return insets;
        });

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        insetsController.setAppearanceLightNavigationBars(true);
        getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.market_page_bg));

        viewModel = new ViewModelProvider(this).get(IconMarketViewModel.class);

        // 处理传入的风格过滤
        String styleStr = getIntent().getStringExtra("style");
        if (styleStr != null) {
            try {
                IconMarketViewModel.IconStyle style = IconMarketViewModel.IconStyle.valueOf(styleStr);
                viewModel.switchStyle(style);
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        setupRecyclerView();
        observeData();

        if (viewModel.categories.getValue() == null || viewModel.categories.getValue().isEmpty()) {
            viewModel.loadCategories();
        }

        // 返回按钮点击
        binding.ivBack.setOnClickListener(v -> finish());

        // 筛选按钮点击
        binding.ivFilter.setOnClickListener(v -> {
            ArrayList<String> options = new ArrayList<>();
            options.add("根据名字排序 (A-Z)");
            options.add("按图标数量排序");
            options.add("已下载");
            options.add("未下载");
            options.add("全部");

            SimpleSelectBottomSheet bottomSheet =
                    SimpleSelectBottomSheet.newInstance("选择筛选排序方式", options);
            bottomSheet.setOnItemSelectedListener(item -> {
                switch (item) {
                    case "根据名字排序 (A-Z)":
                        viewModel.setSortType(IconMarketViewModel.SortType.NAME);
                        break;
                    case "按图标数量排序":
                        viewModel.setSortType(IconMarketViewModel.SortType.COUNT);
                        break;
                    case "已下载":
                        viewModel.setDownloadFilterType(IconMarketViewModel.DownloadFilterType.DOWNLOADED);
                        break;
                    case "未下载":
                        viewModel.setDownloadFilterType(IconMarketViewModel.DownloadFilterType.NOT_DOWNLOADED);
                        break;
                    case "全部":
                        viewModel.setSortType(IconMarketViewModel.SortType.DEFAULT);
                        viewModel.setDownloadFilterType(IconMarketViewModel.DownloadFilterType.ALL);
                        break;
                }
            });
            bottomSheet.show(getSupportFragmentManager(), "FilterBottomSheet");
        });
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onCategoryClick(IconCategory category) {
                Intent intent = new Intent(AllCollectionsActivity.this, IconDetailActivity.class);
                intent.putExtra("category", category);
                startActivity(intent);
            }

            @Override
            public void onPreviewClick(IconCategory category) {
                AppExecutors.get().networkIO().execute(() -> {
                    try {
                        List<IconItem> allItems = 
                                IconRepository.getInstance().getAllCategoryItemsSync(getAssets(), category);
                        AppExecutors.get().mainThread().execute(() -> {
                            if (allItems != null && !allItems.isEmpty()) {
                                SaveCategoryBottomSheet.newInstance(allItems)
                                        .show(getSupportFragmentManager(), "SaveCategory");
                            }
                        });
                    } catch (Exception e) {
                        AppExecutors.get().mainThread().execute(() -> 
                            Toast.makeText(AllCollectionsActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });

        binding.rvAllCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAllCategories.setAdapter(adapter);

        // 滚动加载更多
        binding.rvAllCategories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 4) {
                    viewModel.loadMoreCategories();
                }
            }
        });
    }

    private void observeData() {
        viewModel.categories.observe(this, categories -> {
            if (categories != null) {
                adapter.submitList(new ArrayList<>(categories));
            }
        });

        viewModel.categoryLoading.observe(this, loading -> {
            LottieAnimationView lottie = binding.lottieLoading;
            // 如果已经有数据了，就不显示全屏 Lottie
            List<?> current = viewModel.categories.getValue();
            if (loading && (current == null || current.isEmpty())) {
                lottie.setVisibility(android.view.View.VISIBLE);
            } else {
                lottie.setVisibility(android.view.View.GONE);
            }
        });
    }

    // 防止内存泄漏，销毁时清空binding
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
