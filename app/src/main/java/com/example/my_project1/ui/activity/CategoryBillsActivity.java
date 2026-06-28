package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityCategoryBillsBinding;
import com.example.my_project1.ui.adapter.bill.BillListAdapter;
import com.example.my_project1.ui.adapter.bill.BillListAdapter.ListItem;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.GlideImageLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    public static final String EXTRA_BILL_COUNT      = "bill_count";
    public static final String EXTRA_PERIOD_START_MS = "period_start_ms";
    public static final String EXTRA_PERIOD_END_MS   = "period_end_ms";
    public static final String EXTRA_BILL_TYPE       = "bill_type";

    private ActivityCategoryBillsBinding binding;
    private BillListAdapter              adapter;

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBillsBinding.inflate(getLayoutInflater());
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


        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        String categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        String categoryIcon = getIntent().getStringExtra(EXTRA_CATEGORY_ICON);
        int    billCount    = getIntent().getIntExtra(EXTRA_BILL_COUNT, 0);
        long   startMs      = getIntent().getLongExtra(EXTRA_PERIOD_START_MS, 0L);
        long   endMs        = getIntent().getLongExtra(EXTRA_PERIOD_END_MS, Long.MAX_VALUE);
        int    billType     = getIntent().getIntExtra(EXTRA_BILL_TYPE, 0);

        setupHeader(categoryName, categoryIcon, billCount);
        binding.ivBack.setOnClickListener(v -> onBackPressed());

        adapter = new BillListAdapter();
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBills.setAdapter(adapter);
        binding.rvBills.setNestedScrollingEnabled(false);

        loadBills(categoryName, billType, startMs, endMs);
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

    // ================================================================
    //  数据加载
    // ================================================================

    private void loadBills(String categoryName, int type, long startMs, long endMs) {
        BmobUser u      = BmobUser.getCurrentUser();
        String   userId = (u != null) ? u.getObjectId() : null;
        if (userId == null) return;

        Date startDate = new Date(startMs);
        Date endDate   = new Date(endMs);

        AppExecutors.get().diskIO().execute(() -> {
            BillDao    dao    = AppDatabase.getInstance(this).billDao();
            List<Bill> all    = dao.getAllBillsSync();
            List<Bill> result = new ArrayList<>();

            for (Bill b : all) {
                if (!userId.equals(b.getUserId()))                       continue;
                if (b.getType() != type)                                 continue;
                Date bt = b.getBillTime();
                if (bt == null)                                          continue;
                if (bt.before(startDate) || bt.after(endDate))          continue;
                String cn = b.getCategoryName() != null ? b.getCategoryName() : "其他";
                if (!cn.equals(categoryName))                            continue;
                result.add(b);
            }

            // 按时间降序（最新在上）
            Collections.sort(result, (a, b) -> {
                if (a.getBillTime() == null) return  1;
                if (b.getBillTime() == null) return -1;
                return b.getBillTime().compareTo(a.getBillTime());
            });

            List<ListItem> listItems = buildGroupedList(result);

            AppExecutors.get().mainThread().execute(() -> {
                boolean empty = listItems.isEmpty();
                binding.rvBills.setVisibility(empty ? View.GONE  : View.VISIBLE);
                binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                adapter.setData(listItems);
            });
        });
    }

    // ================================================================
    //  日期分组
    // ================================================================

    /**
     * 将账单列表转换为带日期头的混合列表。
     * 日期头格式：yyyy.M.d（如 2026.3.5），同一天的账单归为一组。
     */
    private List<ListItem> buildGroupedList(List<Bill> bills) {
        SimpleDateFormat keyFmt     = new SimpleDateFormat("yyyyMMdd", Locale.CHINESE);
        SimpleDateFormat displayFmt = new SimpleDateFormat("yyyy.M.d", Locale.CHINESE);

        Map<String, List<Bill>> grouped    = new LinkedHashMap<>();
        Map<String, String>     keyDisplay = new LinkedHashMap<>();

        for (Bill b : bills) {
            String key     = keyFmt.format(b.getBillTime());
            String display = displayFmt.format(b.getBillTime());
            keyDisplay.put(key, display);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }

        List<ListItem> items = new ArrayList<>();
        for (Map.Entry<String, List<Bill>> entry : grouped.entrySet()) {
            List<Bill> dayBills = entry.getValue();
            items.add(new ListItem(keyDisplay.get(entry.getKey()), dayBills.size()));
            for (Bill b : dayBills) {
                items.add(new ListItem(b));
            }
        }
        return items;
    }
}