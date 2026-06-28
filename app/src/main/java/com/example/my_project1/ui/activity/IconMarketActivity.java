package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ActivityIconMarketBinding;
import com.example.my_project1.ui.adapter.icon.CategoryAdapter;
import com.example.my_project1.ui.adapter.icon.IconGridAdapter;
import com.example.my_project1.ui.fragment.IconDetailFragment;
import com.example.my_project1.ui.fragment.SaveCategoryBottomSheet;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.example.my_project1.ui.viewmodel.icon.SelectionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.annotations.Nullable;

/**
 * 图标市场页面
 * 包含分类展示、搜索、图标预览、多选保存功能
 */
public class IconMarketActivity extends AppCompatActivity {

    private static final String TAG = "IconMarketActivity";
    private static final long SEARCH_DEBOUNCE_MS = 300L;

    private ActivityIconMarketBinding binding;
    private IconMarketViewModel viewModel;

    private CategoryAdapter categoryAdapter;
    private IconGridAdapter searchAdapter;
    private List<IconItem> currentSearchResults = new ArrayList<>();

    // 搜索防抖
    private final Runnable searchDebounceRunnable = () -> {
        String keyword = binding.etSearch.getText() == null ? "" : binding.etSearch.getText().toString().trim();
        viewModel.search(keyword);
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityIconMarketBinding.inflate(getLayoutInflater());
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


        viewModel = new ViewModelProvider(this).get(IconMarketViewModel.class);

        initCategoryRecyclerView();
        initSearchRecyclerView();
        initSearchBar();
        initSearchMultiSelectToolbar();
        observeViewModel();
        observeSearchMultiSelect();

        if (viewModel.categories.getValue() == null || viewModel.categories.getValue().isEmpty()) {
            viewModel.loadCategories();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.etSearch.removeCallbacks(searchDebounceRunnable);
    }

    /**
     * 初始化分类列表
     */
    private void initCategoryRecyclerView() {
        categoryAdapter = new CategoryAdapter(category -> {
            viewModel.openCategory(category);
            IconDetailFragment fragment = new IconDetailFragment();
            fragment.show(getSupportFragmentManager(), "IconDetail");
        });

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        binding.rvCategories.setLayoutManager(layoutManager);
        binding.rvCategories.setAdapter(categoryAdapter);

        // 滚动加载更多
        binding.rvCategories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                if (lm.findLastVisibleItemPosition() >= lm.getItemCount() - 4) {
                    viewModel.loadMoreCategories();
                }
            }
        });
    }

    /**
     * 初始化搜索结果列表
     * 处理单击、长按多选、选中切换事件
     */
    private void initSearchRecyclerView() {
        searchAdapter = new IconGridAdapter(false);

        // 单击图标，直接打开保存面板
        searchAdapter.setOnIconClickListener(item -> {
            if (Boolean.TRUE.equals(viewModel.selectionManager.multiSelectMode.getValue())) {
                return;
            }

            // 从 thumb 字段处理真实图标 URL，去除 OSS 压缩参数
            if (item.getUrl() == null || item.getUrl().isEmpty()) {
                String thumbUrl = item.getThumb();
                if (thumbUrl != null && !thumbUrl.isEmpty()) {
                    int qIndex = thumbUrl.indexOf("?");
                    if (qIndex > 0) {
                        item.setUrl(thumbUrl.substring(0, qIndex));
                    } else {
                        item.setUrl(thumbUrl);
                    }
                }
            }

            List<IconItem> single = new ArrayList<>();
            single.add(item);
            showSaveBottomSheet(single);
        });

        // 长按进入多选模式
        searchAdapter.setOnIconLongClickListener(item -> {
            viewModel.onIconLongClick(item.getId());
            return true;
        });

        // 多选模式下点击切换选中状态
        searchAdapter.setOnSelectionClickListener((item, isSelected) ->
                viewModel.onIconClick(item.getId()));

        GridLayoutManager layoutManager = new GridLayoutManager(this, 5);
        binding.rvSearch.setLayoutManager(layoutManager);
        binding.rvSearch.setItemAnimator(null);
        binding.rvSearch.setAdapter(searchAdapter);

        // 搜索列表滚动加载更多
        binding.rvSearch.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                if (lm.findLastVisibleItemPosition() >= lm.getItemCount() - 6) {
                    viewModel.loadMoreSearchResults();
                }
            }
        });
    }

    /**
     * 初始化搜索输入框
     * 输入变化时触发搜索，清空时恢复分类视图
     */
    private void initSearchBar() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s.toString().trim();
                if (keyword.isEmpty()) {
                    viewModel.exitMultiSelectMode();
                    showNormalToolbar();
                    showCategoryView();
                } else {
                    showSearchView();
                }
                binding.etSearch.removeCallbacks(searchDebounceRunnable);
                binding.etSearch.postDelayed(searchDebounceRunnable, SEARCH_DEBOUNCE_MS);
            }
        });

        // 清空搜索
        binding.ivClearSearch.setOnClickListener(v -> {
            binding.etSearch.setText("");
            viewModel.exitMultiSelectMode();
            showNormalToolbar();
            showCategoryView();
        });
    }

    /**
     * 初始化搜索页多选工具栏
     * 全选、取消、确认保存
     */
    private void initSearchMultiSelectToolbar() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.searchBtnSelectAll.setOnClickListener(v -> {
            List<String> allIds = getAllSearchResultIds();
            viewModel.toggleSelectAll(allIds);
        });

        binding.searchBtnCancelSelect.setOnClickListener(v ->
                viewModel.exitMultiSelectMode());

        binding.searchBtnConfirmSelect.setOnClickListener(v -> {
            int count = viewModel.selectionManager.getCount();
            if (count == 0) {
                Toast.makeText(this, "请先选择图标", Toast.LENGTH_SHORT).show();
                return;
            }
            showSaveBottomSheet(collectSelectedSearchItems());
        });
    }

    /**
     * 观察多选状态变化
     * 同步工具栏与列表选中状态
     */
    private void observeSearchMultiSelect() {
        SelectionManager sm = viewModel.selectionManager;

        sm.multiSelectMode.observe(this, isMultiSelect -> {
            boolean active = Boolean.TRUE.equals(isMultiSelect);
            Set<String> ids = sm.selectedIds.getValue();
            if (ids == null) ids = Collections.emptySet();

            boolean searchVisible = binding.rvSearch.getVisibility() == View.VISIBLE;
            if (searchVisible) {
                if (active) showMultiSelectToolbar();
                else showNormalToolbar();
            }

            searchAdapter.setMultiSelectMode(active, ids);
            if (!active) {
                binding.searchTvSelectCount.setText("选择图标");
            }
        });

        sm.selectedIds.observe(this, ids -> {
            if (ids == null) ids = Collections.emptySet();
            searchAdapter.updateSelectedIds(ids);
        });

        sm.selectedCount.observe(this, count -> {
            int c = count != null ? count : 0;
            binding.searchTvSelectCount.setText(c > 0 ? "已选 " + c + " 个" : "选择图标");

            boolean isAll = sm.isAllSelected(getAllSearchResultIds());
            binding.searchBtnSelectAll.setText(isAll ? "取消全选" : "全选");

            binding.searchBtnConfirmSelect.setEnabled(c > 0);
            binding.searchBtnConfirmSelect.setAlpha(c > 0 ? 1.0f : 0.4f);
        });

        viewModel.saveResult.observe(this, result -> {
            if (result == null) return;
            viewModel.clearSaveResult();
        });
    }

    /**
     * 观察页面数据
     * 分类、搜索结果、加载状态、错误信息
     */
    private void observeViewModel() {
        viewModel.categories.observe(this, categories -> {
            if (categories != null) {
                categoryAdapter.submitList(new ArrayList<>(categories));
            }
        });

        viewModel.categoryLoading.observe(this, loading ->
                binding.progressCategory.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.categoryLoadingMore.observe(this, loading ->
                binding.progressCategoryMore.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.categoryError.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                viewModel.clearCategoryError();
            }
        });

        viewModel.searchResults.observe(this, results -> {
            if (results != null) {
                currentSearchResults = new ArrayList<>(results);
                searchAdapter.submitList(currentSearchResults);

                String keyword = binding.etSearch.getText() == null ? "" : binding.etSearch.getText().toString().trim();
                binding.tvSearchEmpty.setVisibility(!keyword.isEmpty() && results.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.searchLoading.observe(this, loading ->
                binding.progressSearch.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.searchLoadingMore.observe(this, loading ->
                binding.progressSearchMore.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.searchError.observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                viewModel.clearSearchError();
            }
        });
    }

    /**
     * 切换到分类展示视图
     */
    private void showCategoryView() {
        binding.rvCategories.setVisibility(View.VISIBLE);
        binding.rvSearch.setVisibility(View.GONE);
        binding.tvSearchEmpty.setVisibility(View.GONE);
        binding.ivClearSearch.setVisibility(View.GONE);
    }

    /**
     * 切换到搜索结果视图
     */
    private void showSearchView() {
        binding.rvCategories.setVisibility(View.GONE);
        binding.rvSearch.setVisibility(View.VISIBLE);
        binding.ivClearSearch.setVisibility(View.VISIBLE);
    }

    /**
     * 显示普通工具栏
     */
    private void showNormalToolbar() {
        binding.toolbarNormal.setVisibility(View.VISIBLE);
        binding.searchToolbarMultiSelect.setVisibility(View.GONE);
    }

    /**
     * 显示多选工具栏
     */
    private void showMultiSelectToolbar() {
        binding.toolbarNormal.setVisibility(View.GONE);
        binding.searchToolbarMultiSelect.setVisibility(View.VISIBLE);
    }

    /**
     * 获取当前搜索结果所有图标ID，用于全选操作
     */
    private List<String> getAllSearchResultIds() {
        List<String> ids = new ArrayList<>();
        for (IconItem item : currentSearchResults) {
            ids.add(item.getId());
        }
        return ids;
    }

    /**
     * 从搜索结果中收集已选中的图标
     * 处理URL，去除OSS参数，保证保存时图标正常显示
     */
    private List<IconItem> collectSelectedSearchItems() {
        Set<String> selectedIds = viewModel.selectionManager.selectedIds.getValue();
        if (selectedIds == null || selectedIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<IconItem> selectedItems = new ArrayList<>();
        for (IconItem item : currentSearchResults) {
            if (selectedIds.contains(item.getId())) {
                // 处理真实图标URL
                String thumbUrl = item.getThumb();
                if (thumbUrl != null && !thumbUrl.isEmpty()) {
                    int questionIndex = thumbUrl.indexOf("?");
                    if (questionIndex > 0) {
                        String realUrl = thumbUrl.substring(0, questionIndex);
                        item.setUrl(realUrl);
                    } else {
                        item.setUrl(thumbUrl);
                    }
                }
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    /**
     * 显示图标保存分类面板
     */
    private void showSaveBottomSheet(List<IconItem> items) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(this, "请先选择图标", Toast.LENGTH_SHORT).show();
            return;
        }
        SaveCategoryBottomSheet sheet = SaveCategoryBottomSheet.newInstance(items);
        sheet.show(getSupportFragmentManager(), "SaveCategory");
    }

    @Override
    public void finish() {
        super.finish();
        // 退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}