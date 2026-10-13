package com.example.my_project1.ui.activity;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategorySelectItem;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.databinding.ActivityTestBillGeneratorBinding;
import com.example.my_project1.databinding.ItemTestGeneratorAccountBinding;
import com.example.my_project1.databinding.ItemTestGeneratorCategoryBinding;
import com.example.my_project1.utils.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import cn.bmob.v3.BmobUser;

/**
 * 测试账单生成器页面
 */
public class TestBillGeneratorActivity extends AppCompatActivity {

    private ActivityTestBillGeneratorBinding binding;

    private BillRepository billRepository;
    private CategoryDao categoryDao;
    private AccountDao accountDao;

    private String currentUserId;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
    private final Random random = new Random();

    // 配置参数
    private boolean isAllRandom = true;
    private int quantity = 20;
    private double maxAmount = 100.0;
    private int selectedType = 0; // 0-支出, 1-收入
    private Date selectedStartDate;
    private Date selectedEndDate;
    private String selectedLocation = "上海市";

    private CategorySelectItem selectedCategoryItem = null; // null 表示随机
    private Account selectedAccount = null;   // null 表示随机

    private int reimburseMode = 0; // 0-随机, 1-无需报销, 2-报销
    private int discountMode = 0;  // 0-随机优惠, 1-无优惠, 2-优惠
    private double maxDiscountAmount = 5.0;

    private final List<CategorySelectItem> expenseCategoryItems = new ArrayList<>();
    private final List<CategorySelectItem> incomeCategoryItems = new ArrayList<>();
    private final List<CategorySelectItem> categoryList = new ArrayList<>();
    private final List<Account> accountList = new ArrayList<>();

    private CategoryAdapter categoryAdapter;
    private AccountAdapter accountAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTestBillGeneratorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化日期区间 (默认近30天)
        Calendar cal = Calendar.getInstance();
        selectedEndDate = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        selectedStartDate = cal.getTime();

        // 初始化数据服务
        billRepository = new BillRepository(this);
        AppDatabase db = AppDatabase.getInstance(this);
        categoryDao = db.categoryDao();
        accountDao = db.accountDao();

        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
        }

        initViews();
        loadCategoriesAndAccounts();
        updateSummaryText();
    }

    private void initViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener(v -> finish());

        // 全部字段随机开关
        binding.switchAllRandom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllRandom = isChecked;
            updateAllRandomState(isChecked);
        });

        // 账单数量 加减 & SeekBar
        binding.sbQuantity.setMax(500);
        binding.sbQuantity.setProgress(quantity);
        binding.tvQuantityNum.setText(String.valueOf(quantity));

        binding.sbQuantity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                quantity = Math.max(1, progress);
                binding.tvQuantityNum.setText(String.valueOf(quantity));
                updateSummaryText();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        binding.btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                binding.sbQuantity.setProgress(quantity);
                binding.tvQuantityNum.setText(String.valueOf(quantity));
                updateSummaryText();
            }
        });

        binding.btnPlus.setOnClickListener(v -> {
            if (quantity < 500) {
                quantity++;
                binding.sbQuantity.setProgress(quantity);
                binding.tvQuantityNum.setText(String.valueOf(quantity));
                updateSummaryText();
            }
        });

        // 账单金额点击设置上限
        binding.tvAmountValue.setOnClickListener(v -> showEditAmountDialog());

        // 收支类型切换
        binding.btnTypeExpense.setOnClickListener(v -> setTypeSelection(0));
        binding.btnTypeIncome.setOnClickListener(v -> setTypeSelection(1));

        // 记录时间段选择
        binding.tvStartDateValue.setText(dateFormat.format(selectedStartDate));
        binding.tvEndDateValue.setText(dateFormat.format(selectedEndDate));
        binding.layoutStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.layoutEndDate.setOnClickListener(v -> showDatePicker(false));

        // 账单地点选择
        binding.tvLocationValue.setText(String.format("%s  ›", selectedLocation));
        binding.layoutRecordLocation.setOnClickListener(v -> showEditLocationDialog());

        // 报销状态切换
        binding.btnReimburseRandom.setOnClickListener(v -> setReimburseMode(0));
        binding.btnReimburseNo.setOnClickListener(v -> setReimburseMode(1));
        binding.btnReimburseYes.setOnClickListener(v -> setReimburseMode(2));

        // 优惠方式切换
        binding.btnDiscountRandom.setOnClickListener(v -> setDiscountMode(0));
        binding.btnDiscountNo.setOnClickListener(v -> setDiscountMode(1));
        binding.btnDiscountYes.setOnClickListener(v -> setDiscountMode(2));

        // 优惠金额点击
        binding.tvDiscountAmountValue.setOnClickListener(v -> showEditDiscountDialog());

        // 底部生成按钮
        binding.btnRandomGenerate.setOnClickListener(v -> generateBills(true));
        binding.btnConfigGenerate.setOnClickListener(v -> generateBills(false));

        // 设置 RecyclerView 适配器
        categoryAdapter = new CategoryAdapter();
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        binding.rvCategories.setAdapter(categoryAdapter);

        accountAdapter = new AccountAdapter();
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        binding.rvAccounts.setAdapter(accountAdapter);
    }

    private void updateAllRandomState(boolean isRandom) {
        int visibility = isRandom ? View.VISIBLE : View.INVISIBLE;
        binding.tvTagQuantity.setVisibility(visibility);
        binding.tvTagAmount.setVisibility(visibility);
        binding.tvTagType.setVisibility(visibility);
        binding.tvTagCategory.setVisibility(visibility);
        binding.tvTagAccount.setVisibility(visibility);
        binding.tvTagStartDate.setVisibility(visibility);
        binding.tvTagEndDate.setVisibility(visibility);
        binding.tvTagLocation.setVisibility(visibility);
        binding.tvTagReimburse.setVisibility(visibility);
        binding.tvTagDiscount.setVisibility(visibility);
        binding.tvTagDiscountAmount.setVisibility(visibility);
        binding.tvTagStat.setVisibility(visibility);
        binding.tvTagBudget.setVisibility(visibility);
        binding.tvTagStatus.setVisibility(visibility);
    }

    private void setTypeSelection(int type) {
        selectedType = type;
        if (type == 0) {
            binding.btnTypeExpense.setBackgroundResource(R.drawable.bg_test_generator_segmented_selected);
            binding.btnTypeExpense.setTextColor(Color.parseColor("#6366F1"));
            binding.btnTypeExpense.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.btnTypeIncome.setBackground(null);
            binding.btnTypeIncome.setTextColor(Color.parseColor("#72778A"));
            binding.btnTypeIncome.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            binding.btnTypeIncome.setBackgroundResource(R.drawable.bg_test_generator_segmented_selected);
            binding.btnTypeIncome.setTextColor(Color.parseColor("#6366F1"));
            binding.btnTypeIncome.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.btnTypeExpense.setBackground(null);
            binding.btnTypeExpense.setTextColor(Color.parseColor("#72778A"));
            binding.btnTypeExpense.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        selectedCategoryItem = null;
        updateDisplayedCategoryList();
        updateSummaryText();
    }

    private void updateDisplayedCategoryList() {
        categoryList.clear();
        if (selectedType == 0) {
            categoryList.addAll(expenseCategoryItems);
        } else {
            categoryList.addAll(incomeCategoryItems);
        }
        categoryAdapter.notifyDataSetChanged();
    }

    private void setReimburseMode(int mode) {
        reimburseMode = mode;
        updateSegmentedStyle(mode, binding.btnReimburseRandom, binding.btnReimburseNo, binding.btnReimburseYes);
    }

    private void setDiscountMode(int mode) {
        discountMode = mode;
        updateSegmentedStyle(mode, binding.btnDiscountRandom, binding.btnDiscountNo, binding.btnDiscountYes);
    }

    private void updateSegmentedStyle(int selectedIndex, TextView... views) {
        for (int i = 0; i < views.length; i++) {
            if (i == selectedIndex) {
                views[i].setBackgroundResource(R.drawable.bg_test_generator_chip_selected);
                views[i].setTextColor(Color.WHITE);
                views[i].setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                views[i].setBackground(null);
                views[i].setTextColor(Color.parseColor("#72778A"));
                views[i].setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void showEditAmountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置账单金额上限");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.getDefault(), "%.2f", maxAmount));
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                try {
                    maxAmount = Double.parseDouble(val);
                    binding.tvAmountValue.setText(String.format(Locale.getDefault(), "¥ %.2f", maxAmount));
                    updateSummaryText();
                } catch (Exception ignored) {}
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showEditDiscountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置最大优惠金额");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.getDefault(), "%.2f", maxDiscountAmount));
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                try {
                    maxDiscountAmount = Double.parseDouble(val);
                    binding.tvDiscountAmountValue.setText(String.format(Locale.getDefault(), "¥ %.2f", maxDiscountAmount));
                } catch (Exception ignored) {}
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(isStart ? selectedStartDate : selectedEndDate);
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    cal.set(year, month, dayOfMonth);
                    if (isStart) {
                        selectedStartDate = cal.getTime();
                        if (selectedStartDate.after(selectedEndDate)) {
                            selectedEndDate = selectedStartDate;
                        }
                    } else {
                        selectedEndDate = cal.getTime();
                        if (selectedEndDate.before(selectedStartDate)) {
                            selectedStartDate = selectedEndDate;
                        }
                    }
                    binding.tvStartDateValue.setText(dateFormat.format(selectedStartDate));
                    binding.tvEndDateValue.setText(dateFormat.format(selectedEndDate));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showEditLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置账单地点");

        final EditText input = new EditText(this);
        input.setText(selectedLocation);
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                selectedLocation = val;
                binding.tvLocationValue.setText(String.format("%s  ›", selectedLocation));
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void loadCategoriesAndAccounts() {
        AppExecutors.get().diskIO().execute(() -> {
            List<CategoryWithSubCategories> expList = categoryDao.getCategoriesWithSubsSync(currentUserId, "expense");
            List<CategorySelectItem> expItems = extractCategoryItems(expList, "expense");

            List<CategoryWithSubCategories> incList = categoryDao.getCategoriesWithSubsSync(currentUserId, "income");
            List<CategorySelectItem> incItems = extractCategoryItems(incList, "income");

            List<Account> accounts = accountDao.getAllAccountsSyncExcludeDeleted();
            if (accounts == null || accounts.isEmpty()) {
                accounts = createDefaultAccounts();
            }

            List<CategorySelectItem> finalExpItems = expItems;
            List<CategorySelectItem> finalIncItems = incItems;
            List<Account> finalAccounts = accounts;

            AppExecutors.get().mainThread().execute(() -> {
                expenseCategoryItems.clear();
                expenseCategoryItems.addAll(finalExpItems);

                incomeCategoryItems.clear();
                incomeCategoryItems.addAll(finalIncItems);

                accountList.clear();
                accountList.addAll(finalAccounts);
                accountAdapter.notifyDataSetChanged();

                updateDisplayedCategoryList();
            });
        });
    }

    private List<CategorySelectItem> extractCategoryItems(List<CategoryWithSubCategories> list, String type) {
        List<CategorySelectItem> items = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (CategoryWithSubCategories cws : list) {
                if (cws.category != null) {
                    Category cat = cws.category;
                    String catId = cat.cloudId != null ? cat.cloudId : String.valueOf(cat.id);
                    items.add(new CategorySelectItem(catId, cat.name, cat.iconUri, 1, cat.iconBackgroundColor, cat));

                    if (cws.subCategories != null) {
                        for (SubCategory sub : cws.subCategories) {
                            String subId = sub.cloudId != null ? sub.cloudId : String.valueOf(sub.id);
                            items.add(new CategorySelectItem(subId, cat.name + "·" + sub.name, sub.iconUri, 2, sub.iconBackgroundColor, sub));
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            items = createDefaultCategoryItems(type);
        }
        return items;
    }

    private List<CategorySelectItem> createDefaultCategoryItems(String type) {
        List<CategorySelectItem> items = new ArrayList<>();
        if ("expense".equals(type)) {
            items.add(new CategorySelectItem("exp_1", "餐饮", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_food).toString(), 1, null, null));
            items.add(new CategorySelectItem("exp_1_1", "餐饮·早餐", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_food).toString(), 2, null, null));
            items.add(new CategorySelectItem("exp_1_2", "餐饮·外卖", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_food).toString(), 2, null, null));
            items.add(new CategorySelectItem("exp_2", "交通", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_expense).toString(), 1, null, null));
            items.add(new CategorySelectItem("exp_2_1", "交通·打车", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_expense).toString(), 2, null, null));
            items.add(new CategorySelectItem("exp_3", "购物", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_expense_shopping).toString(), 1, null, null));
            items.add(new CategorySelectItem("exp_3_1", "购物·服饰", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_expense_shopping).toString(), 2, null, null));
            items.add(new CategorySelectItem("exp_4", "娱乐", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_healthy_food).toString(), 1, null, null));
            items.add(new CategorySelectItem("exp_5", "居住", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_bill).toString(), 1, null, null));
            items.add(new CategorySelectItem("exp_6", "医疗", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_category_default).toString(), 1, null, null));
        } else {
            items.add(new CategorySelectItem("inc_1", "工资", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_income).toString(), 1, null, null));
            items.add(new CategorySelectItem("inc_1_1", "工资·基本工资", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_income).toString(), 2, null, null));
            items.add(new CategorySelectItem("inc_1_2", "工资·奖金", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_income).toString(), 2, null, null));
            items.add(new CategorySelectItem("inc_2", "兼职", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_make_money).toString(), 1, null, null));
            items.add(new CategorySelectItem("inc_3", "理财", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_money).toString(), 1, null, null));
            items.add(new CategorySelectItem("inc_3_1", "理财·基金", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_money).toString(), 2, null, null));
            items.add(new CategorySelectItem("inc_4", "礼金", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_gift).toString(), 1, null, null));
            items.add(new CategorySelectItem("inc_5", "其它", com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_income_lottery).toString(), 1, null, null));
        }
        return items;
    }

    private List<Account> createDefaultAccounts() {
        List<Account> list = new ArrayList<>();
        String[] names = new String[]{"微信", "支付宝", "银行卡", "现金"};
        for (String name : names) {
            Account acc = new Account();
            acc.setName(name);
            list.add(acc);
        }
        return list;
    }

    private void updateSummaryText() {
        String typeName = selectedType == 0 ? "支出" : "收入";
        String catName = selectedCategoryItem != null ? selectedCategoryItem.getName() : "随机分类";
        String summary = String.format(Locale.getDefault(), "%d 笔 · ¥%.2f · %s · %s",
                quantity, maxAmount, typeName, catName);
        binding.tvSummaryInfo.setText(summary);
    }

    /**
     * 生成测试账单
     */
    @SuppressWarnings("deprecation")
    private void generateBills(boolean forcePureRandom) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在批量生成测试账单...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 获取最新当前登录用户 ID
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
        }

        AppExecutors.get().diskIO().execute(() -> {
            List<Bill> bills = new ArrayList<>();

            String[] cities = new String[]{"上海市", "北京市", "深圳市", "广州市", "杭州市", "成都市"};

            for (int i = 0; i < quantity; i++) {
                Bill bill = new Bill();
                bill.setUserId(currentUserId);
                bill.setSyncState(com.example.my_project1.data.model.SyncState.TO_CREATE);

                // 收支类型
                int type = (forcePureRandom || isAllRandom) ? random.nextInt(2) : selectedType;
                bill.setType(type);

                // 分类
                CategorySelectItem catItem = null;
                List<CategorySelectItem> targetCategoryItems = (type == 0) ? expenseCategoryItems : incomeCategoryItems;

                if (!forcePureRandom && selectedCategoryItem != null) {
                    catItem = selectedCategoryItem;
                } else if (targetCategoryItems != null && !targetCategoryItems.isEmpty()) {
                    catItem = targetCategoryItems.get(random.nextInt(targetCategoryItems.size()));
                }

                if (catItem != null) {
                    bill.setCategoryId(catItem.getId());
                    bill.setCategoryName(catItem.getName());
                    bill.setCategoryIconUrl(catItem.getIcon());
                    bill.setCategoryIconBackgroundColor(catItem.getIconBgColor());
                } else {
                    if (type == 0) {
                        bill.setCategoryName("餐饮");
                        bill.setCategoryIconUrl(com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_food).toString());
                    } else {
                        bill.setCategoryName("工资");
                        bill.setCategoryIconUrl(com.example.my_project1.utils.ImageUriUtils.getResourceUri(this, R.drawable.ic_income).toString());
                    }
                }

                // 账户
                Account acc = null;
                if (!forcePureRandom && selectedAccount != null) {
                    acc = selectedAccount;
                } else if (!accountList.isEmpty()) {
                    acc = accountList.get(random.nextInt(accountList.size()));
                }
                if (acc != null) {
                    bill.setAccountId(acc.getObjectId() != null ? acc.getObjectId() : String.valueOf(acc.getId()));
                    bill.setLocalAccountId(acc.getId());
                }

                // 金额 (1.00 ~ maxAmount)
                double amt = 1.0 + random.nextDouble() * Math.max(1.0, maxAmount - 1.0);
                amt = Math.round(amt * 100.0) / 100.0;
                bill.setAmount(amt);

                // 优惠
                double discount = 0.0;
                int currentDiscountMode = forcePureRandom ? 0 : discountMode;
                if (currentDiscountMode == 2 || (currentDiscountMode == 0 && random.nextBoolean())) {
                    discount = Math.round((0.5 + random.nextDouble() * Math.max(0.5, maxDiscountAmount - 0.5)) * 100.0) / 100.0;
                }
                bill.setDiscount(discount);

                // 日期 (在指定的时间段区间内随机生成)
                long startMillis = Math.min(selectedStartDate.getTime(), selectedEndDate.getTime());
                long endMillis = Math.max(selectedStartDate.getTime(), selectedEndDate.getTime());
                long randomMillis;
                if (endMillis == startMillis) {
                    randomMillis = startMillis;
                } else {
                    randomMillis = startMillis + (long) (random.nextDouble() * (endMillis - startMillis));
                }
                bill.setBillTime(new Date(randomMillis));

                // 地点
                if (forcePureRandom || isAllRandom) {
                    bill.setLocation(cities[random.nextInt(cities.length)]);
                } else {
                    bill.setLocation(selectedLocation);
                }

                // 报销状态
                int currentReimburseMode = forcePureRandom ? 0 : reimburseMode;
                if (currentReimburseMode == 2 || (currentReimburseMode == 0 && random.nextBoolean())) {
                    bill.setReimbursable(true);
                    bill.setReimbursed(random.nextBoolean());
                    if (bill.isReimbursed()) {
                        bill.setReimburseAmount(amt);
                        bill.setReimburseTime(new Date());
                    }
                } else {
                    bill.setReimbursable(false);
                }

                // 计入收支 & 预算
                bill.setExcludeStat(!binding.switchIncludeStat.isChecked());
                bill.setExcludeBudget(!binding.switchIncludeBudget.isChecked());

                bill.setRemark("测试生成账单 #" + (i + 1));

                bills.add(bill);
            }

            // 保存到本地 SQLite 数据库，并触发 Bmob 云端同步
            billRepository.insertBills(bills, response -> {
                progressDialog.dismiss();
                if (response.isSuccess()) {
                    Toast.makeText(TestBillGeneratorActivity.this,
                            "✅ 成功生成 " + response.getData() + " 条测试账单，并已触发 Bmob 云端同步！", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(TestBillGeneratorActivity.this,
                            "❌ 生成失败: " + response.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }


    // ==================== Category Adapter ====================

    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTestGeneratorCategoryBinding b = ItemTestGeneratorCategoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.binding.ivCategoryIcon.clearColorFilter();
            if (position == 0) {
                // "随机" 选项
                holder.binding.tvCategoryName.setText("随机");
                boolean isSelected = (selectedCategoryItem == null);
                holder.binding.iconBorder.setBackgroundResource(isSelected ?
                        R.drawable.bg_test_generator_circle_purple : R.drawable.bg_test_generator_circle_btn);
                holder.binding.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);

                holder.itemView.setOnClickListener(v -> {
                    selectedCategoryItem = null;
                    notifyDataSetChanged();
                    updateSummaryText();
                });
            } else {
                CategorySelectItem item = categoryList.get(position - 1);
                holder.binding.tvCategoryName.setText(item.getName());
                boolean isSelected = (selectedCategoryItem != null && TextUtils.equals(selectedCategoryItem.getId(), item.getId()));
                holder.binding.iconBorder.setBackgroundResource(isSelected ?
                        R.drawable.bg_test_generator_circle_purple : R.drawable.bg_test_generator_circle_btn);
                if (item.getIcon() != null && !item.getIcon().isEmpty()) {
                    com.example.my_project1.utils.ImageLoaderUtils.loadCategoryIcon(
                            holder.itemView.getContext(), item.getIcon(), holder.binding.ivCategoryIcon);
                } else {
                    holder.binding.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);
                }

                holder.itemView.setOnClickListener(v -> {
                    selectedCategoryItem = item;
                    notifyDataSetChanged();
                    updateSummaryText();
                });
            }
        }

        @Override
        public int getItemCount() {
            return categoryList.size() + 1;
        }

        class VH extends RecyclerView.ViewHolder {
            ItemTestGeneratorCategoryBinding binding;
            VH(ItemTestGeneratorCategoryBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }


    // ==================== Account Adapter ====================

    private class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTestGeneratorAccountBinding b = ItemTestGeneratorAccountBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (position == 0) {
                // "随机" 选项
                holder.binding.tvAccountName.setText("随机");
                boolean isSelected = (selectedAccount == null);
                setChipStyle(holder.binding.tvAccountName, isSelected);

                holder.itemView.setOnClickListener(v -> {
                    selectedAccount = null;
                    notifyDataSetChanged();
                });
            } else {
                Account acc = accountList.get(position - 1);
                holder.binding.tvAccountName.setText(acc.getName());
                boolean isSelected = (selectedAccount != null && selectedAccount.getId() == acc.getId());
                setChipStyle(holder.binding.tvAccountName, isSelected);

                holder.itemView.setOnClickListener(v -> {
                    selectedAccount = acc;
                    notifyDataSetChanged();
                });
            }
        }

        private void setChipStyle(TextView textView, boolean isSelected) {
            if (isSelected) {
                textView.setBackgroundResource(R.drawable.bg_test_generator_chip_selected);
                textView.setTextColor(Color.WHITE);
            } else {
                textView.setBackgroundResource(R.drawable.bg_test_generator_chip_unselected);
                textView.setTextColor(Color.parseColor("#525866"));
            }
        }

        @Override
        public int getItemCount() {
            return accountList.size() + 1;
        }

        class VH extends RecyclerView.ViewHolder {
            ItemTestGeneratorAccountBinding binding;
            VH(ItemTestGeneratorAccountBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }
}
