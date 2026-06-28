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
                // 🔥 修复点：调用新写的转换方法，将 List<Bill> 转为带日期的 List<Object>
                List<Object> uiModels = convertBillsToUiItems(bills);
                billAdapter.submitList(uiModels);
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

    // ==================== 核心转换逻辑 (修复报错点) ====================

    /**
     * 将原始 Bill 列表转化为 BillAdapter 需要的 [DateHeader, BillUiModel] 混合列表
     * （与首页逻辑一致，确保搜索结果也能漂亮地显示日期和时间轴）
     */
    private List<Object> convertBillsToUiItems(List<Bill> bills) {
        List<Object> items = new ArrayList<>();
        if (bills == null || bills.isEmpty()) return items;

        SimpleDateFormat dateKeyFmt  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat dateDispFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
        SimpleDateFormat timeFmt     = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat    amtFmt      = new DecimalFormat("#,##0.00");
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        String  prevDateKey    = null;
        double  dayExpense     = 0;
        double  dayIncome      = 0;
        int     headerIndex    = -1;
        int     firstBillIndex = -1;

        for (int i = 0; i < bills.size(); i++) {
            Bill bill = bills.get(i);
            if (bill.getBillTime() == null) continue; // 防空保护

            String dateKey = dateKeyFmt.format(bill.getBillTime());
            boolean isDayChange = !dateKey.equals(prevDateKey);

            // 日期切换：插入 Header
            if (isDayChange) {
                // 补写上一组 Header 的汇总金额
                if (headerIndex >= 0) {
                    BillAdapter.DateHeader oldHeader = (BillAdapter.DateHeader) items.get(headerIndex);
                    items.set(headerIndex, new BillAdapter.DateHeader(
                            oldHeader.dateKey, oldHeader.dateText,
                            String.format(Locale.getDefault(), "支出 ¥%.2f", dayExpense),
                            String.format(Locale.getDefault(), "收入 ¥%.2f", dayIncome)
                    ));
                    markLastBillOfDay(items, firstBillIndex);
                }

                dayExpense = 0;
                dayIncome  = 0;

                Calendar cal = Calendar.getInstance();
                cal.setTime(bill.getBillTime());
                String weekDay  = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
                String dateDisp = dateDispFmt.format(bill.getBillTime()) + "（" + weekDay + "）";

                headerIndex = items.size();
                items.add(new BillAdapter.DateHeader(dateKey, dateDisp, "支出 ¥0.00", "收入 ¥0.00"));

                firstBillIndex = items.size();
                prevDateKey    = dateKey;
            }

            // 统计当天收支
            if (bill.getType() == 0) dayExpense += bill.getAmount();
            else                     dayIncome  += bill.getAmount();

            // 构建 UI 字段
            String amountText;
            int    amountColor;
            if (bill.getType() == 0) {
                amountText  = "-¥" + amtFmt.format(bill.getAmount());
                amountColor = ContextCompat.getColor(this, R.color.red);
            } else if (bill.getType() == 1) {
                amountText  = "+¥" + amtFmt.format(bill.getAmount());
                amountColor = ContextCompat.getColor(this, R.color.green);
            } else {
                amountText  = "¥" + amtFmt.format(bill.getAmount());
                amountColor = ContextCompat.getColor(this, android.R.color.black);
            }

            boolean isFirstOfDay = (items.size() == firstBillIndex);

            // 生成 BillUiModel
            BillUiModel uiModel = BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(timeFmt.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "未知")
                    .categoryIconUrl(bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "")
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .remarkText(bill.getRemark())
                    .locationText(bill.getLocation())
                    .imageUrls(bill.getImageUrls())
                    .isFirstOfDay(isFirstOfDay)
                    .isLastOfDay(false)
                    .build();

            items.add(uiModel);
        }

        // 补写最后一组 Header 汇总及最后一笔时间轴
        if (headerIndex >= 0) {
            BillAdapter.DateHeader lastHeader = (BillAdapter.DateHeader) items.get(headerIndex);
            items.set(headerIndex, new BillAdapter.DateHeader(
                    lastHeader.dateKey, lastHeader.dateText,
                    String.format(Locale.getDefault(), "支出 ¥%.2f", dayExpense),
                    String.format(Locale.getDefault(), "收入 ¥%.2f", dayIncome)
            ));
            markLastBillOfDay(items, firstBillIndex);
        }

        return items;
    }

    /** 从 startIndex 往后找到最后一条 BillUiModel，设置 isLastOfDay=true，修复时间轴断线 */
    private void markLastBillOfDay(List<Object> items, int startIndex) {
        for (int j = items.size() - 1; j >= startIndex; j--) {
            if (items.get(j) instanceof BillUiModel) {
                BillUiModel last = (BillUiModel) items.get(j);
                last.isLastOfDay = true;
                break;
            }
        }
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