package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ActivityIconDetailBinding;
import com.example.my_project1.ui.adapter.icon.IconRowAdapter;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IconDetailActivity extends AppCompatActivity {

    private ActivityIconDetailBinding binding;
    private IconMarketViewModel viewModel;
    private IconRowAdapter adapter;
    private boolean hasUpdatedPreview = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIconDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(IconMarketViewModel.class);

        setupWindowInsets();
        setupRecyclerView();
        observeViewModel();
        setupListeners();

        IconCategory category = (IconCategory) getIntent().getSerializableExtra("category");
        if (category != null) {
            viewModel.openCategory(category);
        }
    }

    private void setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            float density = getResources().getDisplayMetrics().density;

            // 1. Toolbar Padding - 增加偏移量避免被刘海遮挡或挤压
            int extraOffset = (int) (12 * density);
            binding.layoutToolbar.setPadding(0, top + extraOffset, 0, 0);

            // 2. 内容区顶部间距同步调整
            // 确保 NestedScrollView 里面的内容不会被 Toolbar 遮挡
            // 这里调整 NestedScrollView 的 paddingTop 或者里面 spacing view 的高度
//            binding.viewSpacing.getLayoutParams().height = (int) (20 * density);
//            binding.viewSpacing.requestLayout();

            return insets;
        });

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(false);
    }

    private void setupRecyclerView() {
        binding.rvIcons.setLayoutManager(new LinearLayoutManager(this));
        adapter = new IconRowAdapter(item -> {
            Toast.makeText(this, "开始下载 " + item.getName(), Toast.LENGTH_SHORT).show();
        });
        binding.rvIcons.setAdapter(adapter);
        // NestedScrollView 嵌套 RecyclerView 需禁用滑动冲突
        binding.rvIcons.setNestedScrollingEnabled(false);
    }

    private void observeViewModel() {
        viewModel.selectedCategory.observe(this, category -> {
            if (category != null) {
                updateCategoryInfo(category);
            }
        });

        viewModel.detailPageStates.observe(this, states -> {
            if (states == null) return;
            List<IconItem> allItems = new ArrayList<>();
            for (int i = 0; i < states.size(); i++) {
                IconMarketViewModel.PageState state = states.valueAt(i);
                if (state != null && state.items != null) {
                    allItems.addAll(state.items);
                }
            }
            adapter.setData(allItems);
            updateSummaryText();

            // 随机更新预览框中的4个图标 (仅在初次加载出数据时更新一次，避免反复随机跳动)
            if (!allItems.isEmpty() && !hasUpdatedPreview) {
                updatePreviewGrid(allItems);
                hasUpdatedPreview = true;
            }
        });
    }

    private void updateCategoryInfo(IconCategory category) {
        binding.tvTitle.setText(category.getCategory());
        binding.tvTabIcons.setText("图标 (" + category.getCount() + ")");
        binding.tvStatCount.setText(String.valueOf(category.getCount()));
        binding.tvStatDownload.setText((category.getCount() * 3) + " 下载");
        binding.tvStatRating.setText("4.9");
        binding.tvStatFormat.setText("4种");
    }

    private void updatePreviewGrid(List<IconItem> allItems) {
        if (allItems.size() < 4) return;
        
        List<IconItem> pool = new ArrayList<>(allItems);
        Collections.shuffle(pool);
        
        for (int i = 0; i < 4 && i < pool.size(); i++) {
            View child = binding.glPreview.getChildAt(i);
            if (child instanceof ImageView) {
                GlideImageLoader.loadThumbnail(this, pool.get(i).getThumbUrl(), (ImageView) child);
            }
        }
    }

    private void setupListeners() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivHeart.setOnClickListener(v -> Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show());
        binding.ivShare.setOnClickListener(v -> Toast.makeText(this, "分享链接已复制", Toast.LENGTH_SHORT).show());
        binding.btnBatchDownload.setOnClickListener(v -> Toast.makeText(this, "准备批量下载...", Toast.LENGTH_SHORT).show());

        binding.nestedScrollView.setOnScrollChangeListener((View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // 阈值设为 180dp 左右开始完全变白，根据布局调整
            float density = getResources().getDisplayMetrics().density;
            float threshold = 140 * density; 
            float alpha = Math.min(1f, (float) scrollY / threshold);
            
            // 动态设置 Toolbar 背景色 (带透明度的白色)
            binding.layoutToolbar.setBackgroundColor(Color.argb((int) (alpha * 255), 255, 255, 255));
            // 标题文字也随之显现
            binding.tvToolbarTitle.setAlpha(alpha);
            
            // 动态切换状态栏图标颜色
            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                // 当背景变白时，图标变黑 (true)；背景为蓝色时，图标保持白色 (false)
                insetsController.setAppearanceLightStatusBars(alpha > 0.5f);
            }
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s.toString());
                    updateSummaryText();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateSummaryText() {
        if (adapter != null) {
            binding.tvCountSummary.setText("共 " + adapter.getFilteredCount() + " 枚图标");
        }
    }
}
