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
import com.example.my_project1.ui.fragment.IconDetailFragment;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;

import java.util.ArrayList;
import java.util.List;

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

        // rootView 使用 binding.getRoot()，不再用 android.R.id.content
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);

            // 将底部导航栏高度设置为 RecyclerView 的 padding，防止遮挡最后一项
            binding.rvAllCategories.setPadding(
                    binding.rvAllCategories.getPaddingLeft(),
                    binding.rvAllCategories.getPaddingTop(),
                    binding.rvAllCategories.getPaddingRight(),
                    systemBars.bottom + (int) (16 * getResources().getDisplayMetrics().density)
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
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(category -> {
            viewModel.openCategory(category);
            IconDetailFragment fragment = new IconDetailFragment();
            fragment.show(getSupportFragmentManager(), "IconDetail");
        });

        binding.rvAllCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAllCategories.setAdapter(adapter);

        // 滚动加载更多
        binding.rvAllCategories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
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
