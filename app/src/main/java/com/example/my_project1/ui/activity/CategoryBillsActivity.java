package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityCategoryBillsBinding;
import com.example.my_project1.ui.adapter.bill.BillListAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.GlideImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.bmob.v3.BmobUser;

/**
 * 分类账单明细页
 *
 * 功能：
 *   • 顶部卡片：分类图标 + 名称 + 该分类总笔数
 *   • 账单列表按日期降序分组，每组上方有日期头（"2026.3.5"）及当日笔数
 *   • 账单条目复用 item_category_stat 布局，点击跳转 BillDetailActivity
 *   • 进入 / 退出动画：slide_in_right / slide_out_left
 *
 * 列表由独立的 BillListAdapter 驱动（已从本类剥离）。
 */
public class CategoryBillsActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_NAME   = "category_name";
    public static final String EXTRA_CATEGORY_ICON   = "category_icon";
    public static final String EXTRA_CATEGORY_ID     = "category_id";
    public static final String EXTRA_BILL_COUNT      = "bill_count";
    public static final String EXTRA_PERIOD_START_MS = "period_start_ms";
    public static final String EXTRA_PERIOD_END_MS   = "period_end_ms";
    public static final String EXTRA_BILL_TYPE       = "bill_type";

    private ActivityCategoryBillsBinding binding;
    private BillListAdapter              adapter;
    private BillViewModel                billViewModel;

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBillsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);

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


        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        String categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        String categoryIcon = getIntent().getStringExtra(EXTRA_CATEGORY_ICON);
        String categoryId   = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        int    billCount    = getIntent().getIntExtra(EXTRA_BILL_COUNT, 0);
        long   startMs      = getIntent().getLongExtra(EXTRA_PERIOD_START_MS, 0L);
        long   endMs        = getIntent().getLongExtra(EXTRA_PERIOD_END_MS, Long.MAX_VALUE);

        setupHeader(categoryName, categoryIcon, billCount);
        binding.ivBack.setOnClickListener(v -> onBackPressed());

        adapter = new BillListAdapter(this);
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBills.setAdapter(adapter);
        binding.rvBills.setNestedScrollingEnabled(false);

        // 设置账单点击跳转详情
        adapter.setOnBillClickListener(billUi -> {
            Intent intent = new Intent(this, BillDetailActivity.class);
            intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, billUi.localId);
            intent.putExtra(BillDetailActivity.EXTRA_BILL_ID,       billUi.objectId);
            startActivity(intent);
        });

        if (categoryId != null && !categoryId.isEmpty()) {
            observeBillsByCategory(categoryId, null, startMs, endMs);
        } else {
            observeBillsByCategory(null, categoryName, startMs, endMs);
        }
    }

    private void observeBillsByCategory(String categoryId, String categoryName, long startMs, long endMs) {
        final Date startDate = new Date(startMs);
        final Date endDate   = new Date(endMs);

        // 统一加载逻辑：按时间范围观察账单，在内存中过滤分类。
        billViewModel.getBillsInTimeRange(startDate, endDate).observe(this, allInRange -> {
            if (allTreeCache == null) {
                loadCategoryTreeAndFilter(categoryId, categoryName, allInRange);
            } else {
                filterAndProcess(categoryId, categoryName, allInRange);
            }
        });
    }

    private List<com.example.my_project1.data.model.CategoryWithSubCategories> allTreeCache;
    private final Map<String, String> childToParentMap = new HashMap<>();

    private void loadCategoryTreeAndFilter(String targetId, String targetName, List<Bill> bills) {
        AppExecutors.get().diskIO().execute(() -> {
            BmobUser u = BmobUser.getCurrentUser();
            if (u == null) return;
            
            List<com.example.my_project1.data.model.CategoryWithSubCategories> tree = 
                    AppDatabase.getInstance(this).categoryDao().getCategoriesWithSubsSync(u.getObjectId(), "expense");
            List<com.example.my_project1.data.model.CategoryWithSubCategories> incomeTree = 
                    AppDatabase.getInstance(this).categoryDao().getCategoriesWithSubsSync(u.getObjectId(), "income");
            
            tree.addAll(incomeTree);
            
            allTreeCache = tree;
            childToParentMap.clear();
            for (com.example.my_project1.data.model.CategoryWithSubCategories node : tree) {
                if (node.subCategories != null) {
                    for (com.example.my_project1.data.model.SubCategory sub : node.subCategories) {
                        childToParentMap.put(sub.cloudId, node.category.cloudId);
                    }
                }
            }

            AppExecutors.get().mainThread().execute(() -> filterAndProcess(targetId, targetName, bills));
        });
    }

    private void filterAndProcess(String targetId, String targetName, List<Bill> bills) {
        List<Bill> result = new ArrayList<>();
        for (Bill b : bills) {
            String bid = b.getCategoryId();
            String bName = b.getCategoryName();

            // 1. 优先使用 ID 匹配
            if (targetId != null && bid != null) {
                if (bid.equals(targetId)) {
                    result.add(b);
                    continue;
                }
                String pid = childToParentMap.get(bid);
                if (targetId.equals(pid)) {
                    result.add(b);
                    continue;
                }
            }

            // 2. 兜底使用名称匹配
            if (targetName != null && bName != null) {
                if (targetName.equals(bName)) {
                    result.add(b);
                }
            }
        }
        processBills(result);
    }

    private String lastFingerprint = "";

    private void processBills(List<Bill> bills) {
        // 更新笔数
        binding.tvBillCount.setText(bills.size() + "笔账单");

        // 2. 指纹比对：ID + 数量 + 最后更新时间 (简单版本)
        String fingerprint = buildFingerprint(bills);
        if (fingerprint.equals(lastFingerprint)) {
            hideLoading();
            return;
        }
        lastFingerprint = fingerprint;

        // 3. 按时间降序
        Collections.sort(bills, (a, b) -> {
            if (a.getBillTime() == null) return 1;
            if (b.getBillTime() == null) return -1;
            return b.getBillTime().compareTo(a.getBillTime());
        });

        List<BillListAdapter.ListItem> listItems = buildGroupedList(bills);
        boolean empty = listItems.isEmpty();
        binding.rvBills.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.submitList(listItems, this::hideLoading);
    }

    private String buildFingerprint(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) return "empty";
        long totalTs = 0;
        for (Bill b : bills) {
            totalTs += b.getId() + (b.getUpdatedAt() != null ? b.getUpdatedAt().getTime() : 0);
        }
        return bills.size() + "_" + totalTs;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ================================================================
    //  顶部卡片
    // ================================================================

    private void setupHeader(String name, String iconUrl, int count) {
        binding.tvCategoryName.setText(name != null ? name : "");
        binding.tvBillCount.setText(count + "笔账单");
        if (iconUrl != null && !iconUrl.isEmpty()) {
            GlideImageLoader.load(this, iconUrl, binding.ivCategoryIcon,
                    android.R.color.transparent, android.R.color.transparent);
        }
    }

    private void hideLoading() {
        if (binding == null || binding.loadingLayout.getVisibility() == View.GONE) return;

        binding.loadingLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    if (binding != null) {
                        binding.loadingLayout.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    // ================================================================
    //  日期分组
    // ================================================================

    /**
     * 将账单列表转换为带日期头的混合列表。
     * 日期头格式：yyyy.M.d（如 2026.3.5），同一天的账单归为一组。
     */
    private List<BillListAdapter.ListItem> buildGroupedList(List<Bill> bills) {
        SimpleDateFormat keyFmt     = new SimpleDateFormat("yyyyMMdd", Locale.CHINESE);
        SimpleDateFormat displayFmt = new SimpleDateFormat("yyyy.M.d", Locale.CHINESE);

        Map<String, List<Bill>> grouped    = new LinkedHashMap<>();
        Map<String, String>     keyDisplay = new LinkedHashMap<>();

        for (Bill b : bills) {
            if (b.getBillTime() == null) continue;
            String key     = keyFmt.format(b.getBillTime());
            String display = displayFmt.format(b.getBillTime());
            keyDisplay.put(key, display);
            
            List<Bill> group = grouped.get(key);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(key, group);
            }
            group.add(b);
        }

        List<BillListAdapter.ListItem> items = new ArrayList<>();
        for (Map.Entry<String, List<Bill>> entry : grouped.entrySet()) {
            List<Bill> dayBills = entry.getValue();
            items.add(new BillListAdapter.ListItem(keyDisplay.get(entry.getKey()), dayBills.size()));
            
            // Map dayBills to UiModels
            List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(dayBills);
            for (BillUiModel uiModel : uiModels) {
                items.add(new BillListAdapter.ListItem(uiModel));
            }
        }
        return items;
    }
}
