package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.databinding.ActivitySearchBinding;
import com.example.my_project1.ui.adapter.bill.BillAdapter;
import com.example.my_project1.ui.adapter.search.SearchHistoryAdapter;
import com.example.my_project1.ui.fragment.SearchFilterBottomSheet;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.SearchViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * SearchActivity - 搜索功能界面 (完整增强版)
 * -------------------------------------------------------
 * ✅ 功能:
 * 1. 关键词搜索
 * 2. 筛选搜索 (日期/金额/账户/备注)
 * 3. 搜索历史管理
 * 4. 实时搜索提示
 * 5. 修复了 UI 数据模型转换 (List<Bill> -> List<Object>)
 */
public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    private static final long SEARCH_DELAY_MS = 500;

    private ActivitySearchBinding binding;
    private SearchViewModel viewModel;

    private SearchHistoryAdapter historyAdapter;
    private BillAdapter billAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // 当前筛选条件
    private SearchFilter currentFilter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
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


        initViewModel();
        initViews();
        setupListeners();
        observeData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
        binding = null;
    }

    // ==================== 初始化 ====================

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        Log.d(TAG, "✅ ViewModel初始化完成");
    }

    private void initViews() {
        // 初始化搜索历史RecyclerView
        historyAdapter = new SearchHistoryAdapter(this, new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onHistoryClick(SearchHistory history) {
                binding.etSearch.setText(history.getKeyword());
                binding.etSearch.setSelection(history.getKeyword().length());
            }

            @Override
            public void onDeleteClick(SearchHistory history) {
                viewModel.deleteSearchHistory(history);
            }
        });

        StaggeredGridLayoutManager staggeredLayoutManager = new StaggeredGridLayoutManager(
                4, StaggeredGridLayoutManager.HORIZONTAL);
        staggeredLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        binding.rvHistory.setLayoutManager(staggeredLayoutManager);
        binding.rvHistory.setAdapter(historyAdapter);

        // 初始化搜索结果RecyclerView
        billAdapter = new BillAdapter(this, new BillAdapter.OnBillClickListener() {
            @Override
            public void onBillClick(long localId, String objectId, View itemView) {
                // 直接使用 adapter 传回的 ID 进行跳转
                openBillDetailById(localId, objectId);
            }

            @Override
            public void onPhotoClick(String imageUrl, int position) {
                Toast.makeText(SearchActivity.this, "查看图片", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBillDelete(BillUiModel bill) {

            }

            @Override
            public void onBillEdit(BillUiModel bill) {

            }

            @Override
            public void onBillRefund(BillUiModel bill) {

            }
        });

        binding.rvResult.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResult.setAdapter(billAdapter);

        binding.tvResultTitle.setVisibility(View.GONE);
        binding.rvResult.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
    }

    private void openBillDetailById(long localId, String objectId) {
        try {
            Intent intent = new Intent(SearchActivity.this, BillDetailActivity.class);

            // 根据是否有远程 ID 决定传参
            if (objectId != null && !objectId.isEmpty()) {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, objectId);
            } else {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, localId);
            }

            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            Log.d(TAG, "✅ 跳转到详情页: " + (objectId != null ? objectId : localId));
        } catch (Exception e) {
            Log.e(TAG, "❌ 打开详情页失败", e);
            showSnackbar("打开详情页失败");
        }
    }

    // ==================== 监听器设置 ====================

    private void setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener(v -> finish());

        // 搜索框输入监听
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);

                searchRunnable = () -> {
                    String keyword = s.toString().trim();
                    performSearch(keyword);
                };

                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 清空历史按钮
        binding.btnClearHistory.setOnClickListener(v -> {
            viewModel.clearAllHistory();
        });

        // 🔥 筛选按钮点击
        binding.tvFilter.setOnClickListener(v -> showFilterDialog());
    }

    // ==================== 数据观察 ====================

    private void observeData() {
        // 观察搜索历史
        viewModel.getSearchHistory().observe(this, historyList -> {
            if (historyList != null && !historyList.isEmpty()) {
                historyAdapter.setHistoryList(historyList);
                binding.rvHistory.setVisibility(View.VISIBLE);
                binding.tvRecentTitle.setVisibility(View.VISIBLE);
                binding.btnClearHistory.setVisibility(View.VISIBLE);
            } else {
                binding.rvHistory.setVisibility(View.GONE);
                binding.tvRecentTitle.setVisibility(View.GONE);
                binding.btnClearHistory.setVisibility(View.GONE);
            }
        });

        // 观察搜索结果
        viewModel.searchResults.observe(this, bills -> {
            if (bills != null && !bills.isEmpty()) {
                List<BillAdapter.BillGroup> uiGroups = viewModel.mapBillsToUiGroups(bills);
                billAdapter.submitList(uiGroups);
                showSearchResults();
            }
        });

        // 观察搜索状态
        viewModel.searchState.observe(this, state -> {
            if (state.isLoading()) {
                showLoading();
            } else if (state.isSuccess()) {
                binding.tvResultTitle.setText(state.data);
                showSearchResults();
            } else if (state.isEmpty()) {
                showEmptyResult();
            } else if (state.isError()) {
                Toast.makeText(SearchActivity.this, state.message, Toast.LENGTH_SHORT).show();
                showSearchResults();
            } else {
                hideSearchResults();
            }
        });

        // 观察Toast消息
        viewModel.toastMessage.observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    // ==================== 筛选功能 ====================

    private void showFilterDialog() {
        SearchFilterBottomSheet bottomSheet = SearchFilterBottomSheet.newInstance(currentFilter);
        if (currentFilter != null) {
            bottomSheet.setCurrentFilter(currentFilter);
        }
        bottomSheet.setOnFilterConfirmListener(filter -> {
            currentFilter = filter;
            applyFilter(filter);
        });
        bottomSheet.show(getSupportFragmentManager(), "filter");
    }

    private void applyFilter(SearchFilter filter) {
        String keyword = binding.etSearch.getText().toString().trim();
        performSearchWithFilter(keyword, filter);
        Toast.makeText(this, "已应用筛选条件", Toast.LENGTH_SHORT).show();
    }

    private void performSearchWithFilter(String keyword, SearchFilter filter) {
        viewModel.searchBillsWithFilter(keyword, filter);
    }

    // ==================== 搜索功能 ====================

    private void performSearch(String keyword) {
        if (keyword.isEmpty()) {
            if (currentFilter != null && currentFilter.hasAnyFilter()) {
                performSearchWithFilter(keyword, currentFilter);
            } else {
                viewModel.resetSearchState();
                hideSearchResults();
            }
        } else {
            if (currentFilter != null && currentFilter.hasAnyFilter()) {
                performSearchWithFilter(keyword, currentFilter);
            } else {
                viewModel.searchBills(keyword);
            }
        }
    }

    // ==================== UI状态控制 ====================

    private void showSnackbar(String message) {
        if (binding != null && binding.getRoot() != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showLoading() {
        binding.tvResultTitle.setVisibility(View.VISIBLE);
        binding.tvResultTitle.setText("搜索中...");
        binding.rvResult.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
    }

    private void showSearchResults() {
        binding.tvResultTitle.setVisibility(View.VISIBLE);
        binding.rvResult.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);

        binding.rvHistory.setVisibility(View.GONE);
        binding.tvRecentTitle.setVisibility(View.GONE);
        binding.btnClearHistory.setVisibility(View.GONE);
    }

    private void showEmptyResult() {
        binding.tvResultTitle.setVisibility(View.VISIBLE);
        binding.tvResultTitle.setText("未找到相关结果");
        binding.rvResult.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);

        binding.rvHistory.setVisibility(View.GONE);
        binding.tvRecentTitle.setVisibility(View.GONE);
        binding.btnClearHistory.setVisibility(View.GONE);

        binding.lottieEmpty.playAnimation();
    }

    private void hideSearchResults() {
        binding.tvResultTitle.setVisibility(View.GONE);
        binding.rvResult.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);

        List<SearchHistory> historyList = viewModel.getSearchHistory().getValue();
        if (historyList != null && !historyList.isEmpty()) {
            binding.rvHistory.setVisibility(View.VISIBLE);
            binding.tvRecentTitle.setVisibility(View.VISIBLE);
            binding.btnClearHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}