package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.databinding.ActivitySearchBinding;
import com.example.my_project1.ui.adapter.bill.BillAdapter;
import com.example.my_project1.ui.adapter.search.SearchSummaryAdapter;
import com.example.my_project1.ui.fragment.SearchFilterBottomSheet;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.SearchViewModel;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * SearchActivity - 搜索模块主页面 (优化版)
 * -------------------------------------------------------
 * ✅ 对接分页加载，支持大数据量搜索
 * ✅ 优化 Loading 状态切换，确保 Lottie 动画流畅
 * ✅ 使用 ListAdapter 高效更新汇总卡片
 */
public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;
    private SearchViewModel viewModel;
    private BillAdapter billAdapter;
    private SearchSummaryAdapter summaryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, top, 0, 0);
            return insets;
        });

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        initViews();
        observeData();
    }

    private void initViews() {
        billAdapter = new BillAdapter(this, new BillAdapter.OnBillClickListener() {
            @Override
            public void onBillClick(long localId, String objectId, View itemView) {
                openBillDetail(localId, objectId);
            }
            @Override public void onPhotoClick(String url, int pos) {}
            @Override public void onBillDelete(BillUiModel bill) {}
            @Override public void onBillEdit(BillUiModel bill) {}
            @Override public void onBillRefund(BillUiModel bill) {}
        }, true);
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBills.setAdapter(billAdapter);

        // 分页加载监听
        binding.rvBills.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !viewModel.isLoading() && !viewModel.isLastPage()) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        summaryAdapter = new SearchSummaryAdapter();
        binding.rvSummary.setAdapter(summaryAdapter);
        binding.rvSummary.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s.toString();
                viewModel.setKeyword(keyword);
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                
                if (s.length() == 0) {
                    binding.layoutResults.setVisibility(View.GONE);
                    binding.layoutPreSearch.setVisibility(View.VISIBLE);
                    binding.layoutEmpty.setVisibility(View.GONE);
                    binding.layoutLoading.setVisibility(View.GONE);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.performSearch();
                return true;
            }
            return false;
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));
        binding.tvCancel.setOnClickListener(v -> finish());
        binding.tvClearHistory.setOnClickListener(v -> viewModel.clearHistory());
        binding.fabFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    private void observeData() {
        viewModel.searchHistory.observe(this, this::updateHistoryChips);
        viewModel.suggestions.observe(this, this::updateSuggestionChips);
        
        // 观察 UI 分组后的列表
        viewModel.uiGroups.observe(this, groups -> {
            billAdapter.submitList(groups); // 始终提交，允许清空
            
            if (groups != null && !groups.isEmpty()) {
                binding.layoutResults.setVisibility(View.VISIBLE);
                binding.layoutPreSearch.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.layoutLoading.setVisibility(View.GONE);
            }
        });

        viewModel.searchSummary.observe(this, summary -> {
            if (summary != null) {
                summaryAdapter.setSummary(summary);
                binding.tvSearchResultCount.setText(String.format("共搜索出%d条明细", summary.getBillCount()));
            }
        });

        viewModel.searchState.observe(this, state -> {
            if (state.isLoading()) {
                // 立即切换到加载布局，隐藏其他布局，确保 Lottie 能够第一时间显示
                binding.layoutResults.setVisibility(View.GONE);
                binding.layoutPreSearch.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.layoutLoading.setVisibility(View.VISIBLE);
                binding.lottieLoading.playAnimation();
            } else if (state.isEmpty()) {
                binding.layoutResults.setVisibility(View.GONE);
                binding.layoutPreSearch.setVisibility(View.GONE);
                binding.layoutEmpty.setVisibility(View.VISIBLE);
                binding.layoutLoading.setVisibility(View.GONE);
                binding.lottieEmpty.playAnimation();
            } else if (state.isError()) {
                binding.layoutLoading.setVisibility(View.GONE);
            }
            // 重要：isSuccess 状态不在这里隐藏 Loading！
            // 搜索大量数据时，isSuccess 表示数据加载回来了，但 UI 转换（mapBillsToUiGroups）可能还在后台进行。
            // 我们在 uiGroups 观察者中，等数据真正渲染到列表后再隐藏 Loading。
        });
    }

    private void updateHistoryChips(List<SearchHistory> histories) {
        binding.chipGroupHistory.removeAllViews();
        if (histories == null || histories.isEmpty()) {
            binding.layoutHistoryHeader.setVisibility(View.GONE);
            binding.chipGroupHistory.setVisibility(View.GONE);
            return;
        }
        binding.layoutHistoryHeader.setVisibility(View.VISIBLE);
        binding.chipGroupHistory.setVisibility(View.VISIBLE);
        for (SearchHistory h : histories) {
            Chip chip = createChip(h.getKeyword());
            chip.setOnClickListener(v -> {
                binding.etSearch.setText(h.getKeyword());
                binding.etSearch.setSelection(h.getKeyword().length());
                viewModel.performSearch();
            });
            binding.chipGroupHistory.addView(chip);
        }
    }

    private void updateSuggestionChips(List<String> suggestions) {
        binding.chipGroupSuggestions.removeAllViews();
        if (suggestions == null || suggestions.isEmpty()) {
            binding.chipGroupSuggestions.setVisibility(View.GONE);
            binding.tvSuggestionTitle.setVisibility(View.GONE);
            return;
        }
        binding.chipGroupSuggestions.setVisibility(View.VISIBLE);
        binding.tvSuggestionTitle.setVisibility(View.VISIBLE);
        for (String s : suggestions) {
            Chip chip = createChip(s);
            chip.setOnClickListener(v -> {
                binding.etSearch.setText(s);
                binding.etSearch.setSelection(s.length());
                viewModel.performSearch();
            });
            binding.chipGroupSuggestions.addView(chip);
        }
    }

    private Chip createChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setChipBackgroundColorResource(R.color.slate_50);
        chip.setChipStrokeWidth(0f);
        chip.setTextColor(Color.parseColor("#64748B"));
        chip.setTextSize(13f);
        chip.setPadding(12, 8, 12, 8);
        return chip;
    }

    private void showFilterBottomSheet() {
        SearchFilterBottomSheet bottomSheet = SearchFilterBottomSheet.newInstance(viewModel.getCurrentFilter());
        bottomSheet.setOnFilterConfirmListener(viewModel::updateFilter);
        bottomSheet.show(getSupportFragmentManager(), "filter");
    }

    private void openBillDetail(long localId, String objectId) {
        Intent intent = new Intent(this, BillDetailActivity.class);
        if (objectId != null && !objectId.isEmpty()) {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, objectId);
        } else {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, localId);
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
