package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeTransform;
import android.transition.TransitionSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
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
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.repository.icon.DownloadRepository;
import com.example.my_project1.databinding.ActivityIconMarketSearchBinding;
import com.example.my_project1.ui.adapter.icon.IconGridAdapter;
import com.example.my_project1.ui.adapter.icon.IconRowAdapter;
import com.example.my_project1.ui.adapter.icon.CategoryAdapter;
import com.example.my_project1.ui.fragment.SaveCategoryBottomSheet;
import com.example.my_project1.ui.viewmodel.icon.IconMarketSearchViewModel;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;

import java.util.ArrayList;
import java.util.List;

public class IconMarketSearchActivity extends AppCompatActivity {

    private static final long SEARCH_DELAY_MS = 260L;

    private ActivityIconMarketSearchBinding binding;
    private IconMarketSearchViewModel viewModel;
    private IconRowAdapter rowAdapter;
    private IconGridAdapter gridAdapter;
    private CategoryAdapter collectionAdapter;
    private boolean gridMode;
    private boolean loading;
    private int iconResultCount;
    private int collectionResultCount;
    private boolean searchFailed;

    private final Runnable searchRunnable = () -> {
        if (binding != null) viewModel.search(binding.etSearch.getText().toString());
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSharedElementTransition();
        postponeEnterTransition();
        binding = ActivityIconMarketSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupSystemBars();
        viewModel = new ViewModelProvider(this).get(IconMarketSearchViewModel.class);
        setupAdapters();
        setupControls();
        observeData();

        binding.getRoot().getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        binding.getRoot().getViewTreeObserver().removeOnPreDrawListener(this);
                        startPostponedEnterTransition();
                        binding.etSearch.postDelayed(() -> {
                            if (binding == null || isFinishing()) return;
                            binding.etSearch.requestFocus();
                            InputMethodManager imm = (InputMethodManager)
                                    getSystemService(INPUT_METHOD_SERVICE);
                            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
                        }, 220L);
                        return true;
                    }
                });
    }

    private void configureSharedElementTransition() {
        TransitionSet transition = new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new ChangeTransform())
                .addTransition(new ChangeClipBounds())
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .setDuration(280L);
        getWindow().setSharedElementEnterTransition(transition);
        getWindow().setSharedElementReturnTransition(transition);
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.market_page_bg));
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, top, 0, 0);
            binding.rvResults.setPadding(0, 0, 0, bottom + dp(16));
            return insets;
        });
    }

    private void setupAdapters() {
        rowAdapter = new IconRowAdapter(new IconRowAdapter.OnIconActionListener() {
            @Override public void onDownloadClick(IconItem item) { enqueueIconDownload(item); }
            @Override public void onItemClick(IconItem item) { openSavePanel(item); }
            @Override public void onSelectionChanged(int selectedCount) { }
        });

        gridAdapter = new IconGridAdapter(false);
        gridAdapter.setOnIconClickListener(this::openSavePanel);

        collectionAdapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onCategoryClick(IconCategory category) {
                Intent intent = new Intent(IconMarketSearchActivity.this,
                        IconDetailActivity.class);
                intent.putExtra("category", category);
                startActivity(intent);
            }

            @Override
            public void onPreviewClick(IconCategory category) {
                com.example.my_project1.utils.AppExecutors.get().networkIO().execute(() -> {
                    try {
                        List<IconItem> items = com.example.my_project1.data.repository.icon.IconRepository
                                .getInstance().getAllCategoryItemsSync(getAssets(), category);
                        runOnUiThread(() -> SaveCategoryBottomSheet.newInstance(items)
                                .show(getSupportFragmentManager(), "SaveCategory"));
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(IconMarketSearchActivity.this,
                                "加载合集失败", Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });
        collectionAdapter.setFlatStyle(true);
        DownloadRepository.getInstance(this).getAllRecords().observe(this, records -> {
            rowAdapter.updateDownloadRecords(records);
            collectionAdapter.updateDownloadRecords(records);
        });
        showIconList();
    }

    private void setupControls() {
        binding.btnBack.setOnClickListener(v -> finishAfterTransition());
        binding.btnClear.setOnClickListener(v -> binding.etSearch.setText(""));
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                binding.etSearch.removeCallbacks(searchRunnable);
                binding.etSearch.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                if (s.length() == 0) updateEmptyState();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            binding.etSearch.removeCallbacks(searchRunnable);
            viewModel.search(binding.etSearch.getText().toString());
            return false;
        });

        binding.tabIcons.setOnClickListener(v ->
                viewModel.setResultType(IconMarketSearchViewModel.ResultType.ICON));
        binding.tabCollections.setOnClickListener(v ->
                viewModel.setResultType(IconMarketSearchViewModel.ResultType.COLLECTION));
        binding.styleAll.setOnClickListener(v ->
                viewModel.setStyle(IconMarketViewModel.IconStyle.ALL));
        binding.styleLinear.setOnClickListener(v ->
                viewModel.setStyle(IconMarketViewModel.IconStyle.LINEAR));
        binding.styleColored.setOnClickListener(v ->
                viewModel.setStyle(IconMarketViewModel.IconStyle.COLORED));
        binding.styleFilled.setOnClickListener(v ->
                viewModel.setStyle(IconMarketViewModel.IconStyle.DEFAULT));
        binding.btnList.setOnClickListener(v -> {
            gridMode = false;
            showIconList();
        });
        binding.btnGrid.setOnClickListener(v -> {
            gridMode = true;
            showIconGrid();
        });
    }

    private void observeData() {
        viewModel.resultType.observe(this, type -> {
            boolean iconResults = type == IconMarketSearchViewModel.ResultType.ICON;
            selectTypeTab(binding.tabIcons, iconResults);
            selectTypeTab(binding.tabCollections, !iconResults);
            binding.layoutViewModes.setVisibility(iconResults ? View.VISIBLE : View.INVISIBLE);
            if (iconResults) {
                if (gridMode) showIconGrid(); else showIconList();
            } else {
                binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
                binding.rvResults.setAdapter(collectionAdapter);
            }
            updateSummaryAndEmpty();
        });

        viewModel.style.observe(this, style -> {
            selectStyleChip(binding.styleAll, style == IconMarketViewModel.IconStyle.ALL);
            selectStyleChip(binding.styleLinear, style == IconMarketViewModel.IconStyle.LINEAR);
            selectStyleChip(binding.styleColored, style == IconMarketViewModel.IconStyle.COLORED);
            selectStyleChip(binding.styleFilled, style == IconMarketViewModel.IconStyle.DEFAULT);
        });

        viewModel.icons.observe(this, icons -> {
            List<IconItem> safe = icons == null ? new ArrayList<>() : icons;
            iconResultCount = safe.size();
            rowAdapter.setData(safe);
            gridAdapter.submitList(new ArrayList<>(safe));
            updateSummaryAndEmpty();
        });

        viewModel.collections.observe(this, collections -> {
            List<IconCategory> safe = collections == null ? new ArrayList<>() : collections;
            collectionResultCount = safe.size();
            collectionAdapter.submitList(new ArrayList<>(safe), this::updateSummaryAndEmpty);
            updateSummaryAndEmpty();
        });

        viewModel.loading.observe(this, value -> {
            loading = Boolean.TRUE.equals(value);
            if (loading) {
                searchFailed = false;
                binding.tvEmpty.setText("没有找到匹配内容\n换个关键词试试");
            }
            binding.progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) binding.progressLoading.playAnimation();
            else binding.progressLoading.pauseAnimation();
            updateEmptyState();
        });

        viewModel.error.observe(this, message -> {
            if (message != null) {
                searchFailed = true;
                binding.emptyLottie.setAnimation(R.raw.error);
                binding.emptyLottie.setVisibility(View.VISIBLE);
                binding.emptyLottie.playAnimation();
                binding.tvEmpty.setText("搜索失败\n请稍后重试");
                binding.tvEmpty.setVisibility(View.VISIBLE);
                viewModel.clearError();
            }
        });
    }

    private void showIconList() {
        binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResults.setAdapter(rowAdapter);
        binding.btnList.setBackgroundResource(R.drawable.bg_search_mode_selected);
        binding.btnGrid.setBackgroundResource(R.drawable.bg_search_mode_unselected);
        binding.btnList.setColorFilter(ContextCompat.getColor(this, R.color.market_primary));
        binding.btnGrid.setColorFilter(ContextCompat.getColor(this, R.color.market_text_secondary));
    }

    private void showIconGrid() {
        binding.rvResults.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvResults.setAdapter(gridAdapter);
        binding.btnGrid.setBackgroundResource(R.drawable.bg_search_mode_selected);
        binding.btnList.setBackgroundResource(R.drawable.bg_search_mode_unselected);
        binding.btnGrid.setColorFilter(ContextCompat.getColor(this, R.color.market_primary));
        binding.btnList.setColorFilter(ContextCompat.getColor(this, R.color.market_text_secondary));
    }

    private void selectTypeTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_pill_selected_market
                : R.drawable.bg_pill_unselected_market);
        tab.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.white : R.color.market_text_secondary));
        tab.setTypeface(tab.getTypeface(), selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void selectStyleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_pill_selected_market
                : R.drawable.bg_pill_unselected_market);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.white : R.color.market_text_secondary));
    }

    private void updateSummaryAndEmpty() {
        String query = viewModel.getKeyword();
        if (query.isEmpty()) {
            binding.tvResultSummary.setText("输入关键词开始搜索");
        } else if (viewModel.resultType.getValue()
                == IconMarketSearchViewModel.ResultType.COLLECTION) {
            binding.tvResultSummary.setText("找到 " + collectionResultCount + " 个图标合集");
        } else {
            binding.tvResultSummary.setText("找到 " + iconResultCount + " 个图标");
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (binding == null) return;
        boolean hasQuery = !binding.etSearch.getText().toString().trim().isEmpty();
        boolean isCollections = viewModel.resultType.getValue()
                == IconMarketSearchViewModel.ResultType.COLLECTION;
        int count = isCollections ? collectionResultCount : iconResultCount;
        boolean showEmpty = hasQuery && !loading && (searchFailed || count == 0);
        binding.tvEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.emptyLottie.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty && !searchFailed) {
            binding.emptyLottie.setAnimation(R.raw.empty_search);
            binding.emptyLottie.playAnimation();
            binding.tvEmpty.setText("没有找到匹配内容\n换个关键词试试");
        }
        if (!showEmpty) binding.emptyLottie.pauseAnimation();
    }

    private void openSavePanel(IconItem item) {
        if (item == null) return;
        ArrayList<IconItem> items = new ArrayList<>();
        items.add(item);
        SaveCategoryBottomSheet.newInstance(items)
                .show(getSupportFragmentManager(), "SaveCategory");
    }

    private void enqueueIconDownload(IconItem item) {
        DownloadRepository.getInstance(this).startIconDownload(item, (enqueued, message) -> {
            if (binding == null) return;
            if (enqueued) rowAdapter.markDownloadQueued(item.getId());
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (binding != null) binding.etSearch.removeCallbacks(searchRunnable);
        binding = null;
        super.onDestroy();
    }
}
