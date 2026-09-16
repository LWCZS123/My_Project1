package com.example.my_project1.ui.activity;

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
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.repository.icon.IconRepository;
import com.example.my_project1.databinding.ActivityIconMarketBinding;
import com.example.my_project1.ui.adapter.icon.CategoryAdapter;
import com.example.my_project1.ui.adapter.icon.HotIconAdapter;
import com.example.my_project1.ui.adapter.icon.IconGridAdapter;
import com.example.my_project1.ui.fragment.IconDetailFragment;
import com.example.my_project1.ui.fragment.IconSearchFilterBottomSheet;
import com.example.my_project1.ui.fragment.SaveCategoryBottomSheet;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.example.my_project1.ui.viewmodel.icon.SelectionManager;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.annotations.Nullable;

/**
 * 图标市场页面
 */
public class IconMarketActivity extends AppCompatActivity {

    private static final long SEARCH_DEBOUNCE_MS = 300L;
    private static final int HOME_CATEGORY_LIMIT = 50;

    private ActivityIconMarketBinding binding;
    private IconMarketViewModel viewModel;

    private CategoryAdapter categoryAdapter;
    private HotIconAdapter hotIconAdapter;
    private IconGridAdapter searchAdapter;
    private List<IconItem> currentSearchResults = new ArrayList<>();
    private boolean categoryLoading, categoryLoadingMore, searchLoading, searchLoadingMore;
    private boolean destroyed;
    private boolean styleSwitching;

    private final Runnable searchDebounceRunnable = () -> {
        if (destroyed || binding == null || viewModel == null) return;
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

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        insetsController.setAppearanceLightNavigationBars(true);
        getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.market_page_bg));

        viewModel = new ViewModelProvider(this).get(IconMarketViewModel.class);

        initCategoryRecyclerView();
        initHotIconsRecyclerView();
        initSearchRecyclerView();
        initSearchBar();
        initStyleSelector();
        initSearchMultiSelectToolbar();
        initNavigation();

        observeViewModel();
        observeSearchMultiSelect();

        if (viewModel.categories.getValue() == null || viewModel.categories.getValue().isEmpty()) {
            viewModel.loadCategories();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (binding != null) {
            binding.etSearch.removeCallbacks(searchDebounceRunnable);
        }
        binding = null;
        super.onDestroy();
    }

    private void initCategoryRecyclerView() {
        categoryAdapter = new CategoryAdapter(category -> {
            android.content.Intent intent = new android.content.Intent(this, IconDetailActivity.class);
            intent.putExtra("category", category);
            startActivity(intent);
        });

        binding.rvCategories.setLayoutManager(new GridLayoutManager(this, 1));
        // Metadata and thumbnails arrive incrementally; suppress change animations
        // so an existing card is updated in place instead of flashing.
        binding.rvCategories.setItemAnimator(null);
        binding.rvCategories.setAdapter(categoryAdapter);

        binding.rvCategories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 4) {
                    viewModel.loadMoreCategories();
                }
            }
        });
    }

    private void initHotIconsRecyclerView() {
        hotIconAdapter = new HotIconAdapter();
        hotIconAdapter.setOnItemClickListener(item -> {
            List<IconItem> list = new ArrayList<>();
            list.add(item);
            showSaveBottomSheet(list);
        });
        binding.rvHotIcons.setAdapter(hotIconAdapter);
    }

    private void initStyleSelector() {
        binding.tvStyleAll.setOnClickListener(v -> switchStyle(IconMarketViewModel.IconStyle.ALL));
        binding.tvStyleLinear.setOnClickListener(v -> switchStyle(IconMarketViewModel.IconStyle.LINEAR));
        binding.tvStyleColored.setOnClickListener(v -> switchStyle(IconMarketViewModel.IconStyle.COLORED));
        binding.tvStyleDefault.setOnClickListener(v -> switchStyle(IconMarketViewModel.IconStyle.DEFAULT));
    }

    private void switchStyle(IconMarketViewModel.IconStyle style) {
        if (viewModel.currentStyle.getValue() == style) return;
        styleSwitching = true;
        categoryAdapter.setMetadataLoading(true);
        viewModel.switchStyle(style);
    }

    private void updateStyleUI(IconMarketViewModel.IconStyle style) {
        resetStyleTab(binding.tvStyleAll);
        resetStyleTab(binding.tvStyleLinear);
        resetStyleTab(binding.tvStyleColored);
        resetStyleTab(binding.tvStyleDefault);

        switch (style) {
            case ALL: setSelectedTab(binding.tvStyleAll); break;
            case LINEAR: setSelectedTab(binding.tvStyleLinear); break;
            case COLORED: setSelectedTab(binding.tvStyleColored); break;
            case DEFAULT: setSelectedTab(binding.tvStyleDefault); break;
        }
    }

    private void resetStyleTab(View v) {
        if (v instanceof android.widget.TextView) {
            v.setBackgroundResource(R.drawable.bg_pill_unselected_market);
            ((android.widget.TextView) v).setTextColor(ContextCompat.getColor(this, R.color.market_text_secondary));
        }
    }

    private void setSelectedTab(View v) {
        if (v instanceof android.widget.TextView) {
            v.setBackgroundResource(R.drawable.bg_pill_selected_market);
            ((android.widget.TextView) v).setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void initSearchRecyclerView() {
        searchAdapter = new IconGridAdapter(false);
        searchAdapter.setOnIconClickListener(item -> {
            if (Boolean.TRUE.equals(viewModel.selectionManager.multiSelectMode.getValue())) return;
            if (item.getUrl() == null || item.getUrl().isEmpty()) {
                String thumbUrl = item.getThumb();
                if (thumbUrl != null && !thumbUrl.isEmpty()) {
                    int qIndex = thumbUrl.indexOf("?");
                    item.setUrl(qIndex > 0 ? thumbUrl.substring(0, qIndex) : thumbUrl);
                }
            }
            List<IconItem> single = new ArrayList<>();
            single.add(item);
            showSaveBottomSheet(single);
        });

        searchAdapter.setOnIconLongClickListener(item -> {
            viewModel.onIconLongClick(item.getId());
            return true;
        });

        searchAdapter.setOnSelectionClickListener((item, isSelected) -> viewModel.onIconClick(item.getId()));

        binding.rvSearch.setLayoutManager(new GridLayoutManager(this, 5));
        binding.rvSearch.setItemAnimator(null);
        binding.rvSearch.setAdapter(searchAdapter);

        binding.rvSearch.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= lm.getItemCount() - 6) {
                    viewModel.loadMoreSearchResults();
                }
            }
        });
    }

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

        binding.ivClearSearch.setOnClickListener(v -> {
            binding.etSearch.setText("");
            viewModel.exitMultiSelectMode();
            showNormalToolbar();
            showCategoryView();
        });

        binding.btnFilter.setOnClickListener(v -> {
            IconSearchFilterBottomSheet fragment = new IconSearchFilterBottomSheet();
            fragment.show(getSupportFragmentManager(), "IconSearchFilter");
        });
    }

    private void initSearchMultiSelectToolbar() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.searchBtnSelectAll.setOnClickListener(v -> viewModel.toggleSelectAll(getAllSearchResultIds()));
        binding.searchBtnCancelSelect.setOnClickListener(v -> viewModel.exitMultiSelectMode());
        binding.searchBtnConfirmSelect.setOnClickListener(v -> {
            if (viewModel.selectionManager.getCount() == 0) {
                Toast.makeText(this, "请先选择图标", Toast.LENGTH_SHORT).show();
                return;
            }
            showSaveBottomSheet(collectSelectedSearchItems());
        });
    }

    private void initNavigation() {
        binding.tvHotAll.setOnClickListener(v -> openAllCollections());
        binding.tvColAll.setOnClickListener(v -> openAllCollections());
    }

    private void openAllCollections() {
        android.content.Intent intent = new android.content.Intent(this, AllCollectionsActivity.class);
        IconMarketViewModel.IconStyle style = viewModel.currentStyle.getValue();
        if (style != null) intent.putExtra("style", style.name());
        startActivity(intent);
    }

    private void observeSearchMultiSelect() {
        SelectionManager sm = viewModel.selectionManager;
        sm.multiSelectMode.observe(this, isMultiSelect -> {
            boolean active = Boolean.TRUE.equals(isMultiSelect);
            Set<String> ids = sm.selectedIds.getValue();
            if (ids == null) ids = Collections.emptySet();

            if (binding.rvSearch.getVisibility() == View.VISIBLE) {
                if (active) showMultiSelectToolbar(); else showNormalToolbar();
            }
            searchAdapter.setMultiSelectMode(active, ids);
            if (!active) binding.searchTvSelectCount.setText("选择图标");
        });

        sm.selectedIds.observe(this, ids -> {
            if (ids == null) ids = Collections.emptySet();
            searchAdapter.updateSelectedIds(ids);
        });

        sm.selectedCount.observe(this, count -> {
            int c = count != null ? count : 0;
            binding.searchTvSelectCount.setText(c > 0 ? "已选 " + c + " 个" : "选择图标");
            binding.searchBtnSelectAll.setText(sm.isAllSelected(getAllSearchResultIds()) ? "取消全选" : "全选");
            binding.searchBtnConfirmSelect.setEnabled(c > 0);
            binding.searchBtnConfirmSelect.setAlpha(c > 0 ? 1.0f : 0.4f);
        });

        viewModel.saveResult.observe(this, result -> {
            if (result == null) return;
            viewModel.clearSaveResult();
        });
    }

    private void observeViewModel() {
        viewModel.currentStyle.observe(this, this::updateStyleUI);
        viewModel.categories.observe(this, categories -> {
            if (categories != null) {
                List<IconCategory> homeList = (categories.size() > HOME_CATEGORY_LIMIT)
                        ? new ArrayList<>(categories.subList(0, HOME_CATEGORY_LIMIT))
                        : categories;
                categoryAdapter.submitList(homeList, categoryAdapter::refreshThumbnailContent);
                if (styleSwitching && binding != null) {
                    binding.getRoot().postDelayed(() -> {
                        if (!destroyed && categoryAdapter != null) {
                            styleSwitching = false;
                            categoryAdapter.setMetadataLoading(false);
                        }
                    }, 180L);
                } else {
                    categoryAdapter.setMetadataLoading(false);
                }
                // Hide the full-page loader as soon as static JSON metadata arrives;
                // thumbnail enrichment continues in-place without blocking the list.
                updateLoadingIndicator();

                // 首页需展示 HOME_CATEGORY_LIMIT 个合集，但数据按页加载（每页约 30 个），
                // 数量不足且未处于搜索状态时，自动继续加载下一页，直到填满或没有更多数据
                String keyword = viewModel.currentKeyword.getValue();
                boolean searching = keyword != null && !keyword.isEmpty();
                if (!categories.isEmpty()
                        && !searching
                        && categories.size() < HOME_CATEGORY_LIMIT
                        && Boolean.TRUE.equals(viewModel.categoryHasMore.getValue())) {
                    viewModel.loadMoreCategories();
                }
            }
        });

        viewModel.statistics.observe(this, stats -> {
            binding.tvIconTotal.setText(String.format(java.util.Locale.getDefault(), "%,d+", stats.totalIcons));
            binding.tvCollectionTotal.setText(String.format(java.util.Locale.getDefault(), "%d+", stats.totalCollections));
            binding.tvNewTotal.setText(String.valueOf(stats.newThisWeek));
        });

        viewModel.hotCategory.observe(this, this::bindHotCategory);

        viewModel.categoryLoading.observe(this, loading -> {
            categoryLoading = Boolean.TRUE.equals(loading);
            categoryAdapter.setMetadataLoading(categoryLoading && isCategoryListEmpty());
            updateLoadingIndicator();
        });

        viewModel.categoryLoadingMore.observe(this, loading -> {
            categoryLoadingMore = Boolean.TRUE.equals(loading);
            updateLoadingIndicator();
        });

        viewModel.categoryError.observe(this, error -> {
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); viewModel.clearCategoryError(); }
        });

        viewModel.searchResults.observe(this, results -> {
            if (results != null) {
                currentSearchResults = new ArrayList<>(results);
                searchAdapter.submitList(currentSearchResults);
                String keyword = binding.etSearch.getText().toString().trim();
                binding.tvSearchEmpty.setVisibility(!keyword.isEmpty() && results.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.categorySearchResults.observe(this, results -> {
            if (results != null) {
                categoryAdapter.submitList(new ArrayList<>(results));
                binding.tvSearchEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.searchScope.observe(this, scope -> {
            if (scope == 0) {
                binding.rvSearch.setVisibility(View.VISIBLE);
                binding.rvCategories.setVisibility(View.GONE);
            } else {
                binding.rvSearch.setVisibility(View.GONE);
                binding.rvCategories.setVisibility(View.VISIBLE);
            }
            String keyword = binding.etSearch.getText().toString().trim();
            if (!keyword.isEmpty()) showSearchView();
        });

        viewModel.searchLoading.observe(this, loading -> {
            searchLoading = Boolean.TRUE.equals(loading);
            updateLoadingIndicator();
        });
        viewModel.searchLoadingMore.observe(this, loading -> {
            searchLoadingMore = Boolean.TRUE.equals(loading);
            updateLoadingIndicator();
        });
        viewModel.searchError.observe(this, error -> {
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_SHORT).show(); viewModel.clearSearchError(); }
        });
    }

    private boolean isCategoryListEmpty() {
        List<IconCategory> current = viewModel.categories.getValue();
        return current == null || current.isEmpty();
    }

    /** A single animation is shared by category/search and initial/pagination loading. */
    private void updateLoadingIndicator() {
        if (binding == null) return;
        boolean visible = (categoryLoading && isCategoryListEmpty())
                || categoryLoadingMore || searchLoading || searchLoadingMore;
        binding.progressLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            if (!binding.progressLoading.isAnimating()) binding.progressLoading.playAnimation();
        } else {
            binding.progressLoading.pauseAnimation();
        }
    }

    private void bindHotCategory(IconCategory category) {
        if (category == null) { binding.mvCardHot.setVisibility(View.GONE); return; }
        binding.mvCardHot.setVisibility(View.VISIBLE);
        binding.tvHotLabel.setText("热门：" + category.getCategory());
        hotIconAdapter.submitList(new ArrayList<>());
        IconRepository.Callback<List<IconItem>> cb = new IconRepository.Callback<List<IconItem>>() {
            @Override public void onSuccess(List<IconItem> data) {
                if (data != null && !data.isEmpty()) {
                    AppExecutors.get().mainThread().execute(() -> hotIconAdapter.submitList(data.size() > 10 ? data.subList(0, 10) : data));
                }
            }
            @Override public void onError(String message) { Log.e("IconMarket", "Hot load fail: " + message); }
        };
        String style = category.getStyle();
        if ("line".equals(style)) IconRepository.getInstance().getAssetCategoryDetail(getAssets(), "freeicon_line.json", category, 0, cb);
        else if ("lineal-color".equals(style)) IconRepository.getInstance().getAssetCategoryDetail(getAssets(), "线性色.json", category, 0, cb);
        else IconRepository.getInstance().getCategoryDetail(category, 0, cb);

        binding.mvCardHot.setOnClickListener(v -> {
            viewModel.openCategory(category);
            new IconDetailFragment().show(getSupportFragmentManager(), "IconDetail");
        });
    }

    private void showCategoryView() {
        binding.rvCategories.setVisibility(View.VISIBLE);
        binding.rvSearch.setVisibility(View.GONE);
        binding.tvSearchEmpty.setVisibility(View.GONE);
        binding.ivClearSearch.setVisibility(View.GONE);
    }

    private void showSearchView() {
        int scope = viewModel.searchScope.getValue() != null ? viewModel.searchScope.getValue() : 0;
        binding.rvCategories.setVisibility(scope == 1 ? View.VISIBLE : View.GONE);
        binding.rvSearch.setVisibility(scope == 0 ? View.VISIBLE : View.GONE);
        binding.ivClearSearch.setVisibility(View.VISIBLE);
    }

    private void showNormalToolbar() {
        binding.toolbarNormal.setVisibility(View.VISIBLE);
        binding.layoutStyleSelector.setVisibility(View.VISIBLE);
        binding.searchToolbarMultiSelect.setVisibility(View.GONE);
    }

    private void showMultiSelectToolbar() {
        binding.toolbarNormal.setVisibility(View.GONE);
        binding.layoutStyleSelector.setVisibility(View.GONE);
        binding.searchToolbarMultiSelect.setVisibility(View.VISIBLE);
    }

    private List<String> getAllSearchResultIds() {
        List<String> ids = new ArrayList<>();
        for (IconItem item : currentSearchResults) ids.add(item.getId());
        return ids;
    }

    private List<IconItem> collectSelectedSearchItems() {
        Set<String> selectedIds = viewModel.selectionManager.selectedIds.getValue();
        if (selectedIds == null || selectedIds.isEmpty()) return new ArrayList<>();
        List<IconItem> selectedItems = new ArrayList<>();
        for (IconItem item : currentSearchResults) {
            if (selectedIds.contains(item.getId())) {
                String thumbUrl = item.getThumb();
                if (thumbUrl != null && !thumbUrl.isEmpty()) {
                    int qIndex = thumbUrl.indexOf("?");
                    item.setUrl(qIndex > 0 ? thumbUrl.substring(0, qIndex) : thumbUrl);
                }
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    private void showSaveBottomSheet(List<IconItem> items) {
        if (items == null || items.isEmpty()) { Toast.makeText(this, "请先选择图标", Toast.LENGTH_SHORT).show(); return; }
        SaveCategoryBottomSheet.newInstance(items).show(getSupportFragmentManager(), "SaveCategory");
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
