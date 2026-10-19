package com.example.my_project1.ui.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityBillDetailBinding;
import com.example.my_project1.ui.adapter.photo.FullImagePagerAdapter;
import com.example.my_project1.ui.adapter.photo.ImagePagerAdapter;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.fragment.ReimburseBottomSheetFragment;
import com.example.my_project1.ui.popup.FilterPopupMenu;
import com.example.my_project1.ui.view.FlowLayout;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.repository.tag.TagRepository;
import com.example.my_project1.ui.fragment.tag.SelectTagBottomSheetFragment;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;
import com.example.my_project1.utils.TagViewUtils;
import com.example.my_project1.utils.ui.ElasticOverScrollHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BillDetailActivity - 账单详情页（RecyclerView + ConcatAdapter 架构重构版）
 * -------------------------------------------------------
 * ✅ 整体架构采用 RecyclerView + ConcatAdapter
 * ✅ 列表项统一采用 ShapeableImageView + ConstraintLayout/MaterialCardView
 * ✅ 图片轮播固定高度 (180dp) + 精准图片解码 (Glide override) + NestedScrollableHost 冲突处理
 * ✅ 底部间距 WindowInsets 动态适配 (兼容各种全面屏与导航栏)
 * ✅ 报销新增/取消云端同步修复
 */
public class BillDetailActivity extends AppCompatActivity {

    private static final String TAG = "BillDetailActivity";
    private static final long AUTO_SCROLL_DELAY = 3000;

    public static final String EXTRA_BILL_ID = "bill_id";
    public static final String EXTRA_BILL_LOCAL_ID = "bill_local_id";
    public static final int REQUEST_EDIT_BILL = 2001;

    private ActivityBillDetailBinding binding;
    private BillViewModel billViewModel;
    private AccountViewModel accountViewModel;
    private Bill currentBill;

    // ConcatAdapter 与各卡片 Adapter
    private ConcatAdapter concatAdapter;
    private HeaderSectionAdapter headerSectionAdapter;
    private SummarySectionAdapter summarySectionAdapter;
    private ReimburseSectionAdapter reimburseSectionAdapter;
    private AttributesSectionAdapter attributesSectionAdapter;
    private SettingsSectionAdapter settingsSectionAdapter;
    private RemarkSectionAdapter remarkSectionAdapter;

    // 操作菜单
    private FilterPopupMenu actionMenu;

    // 账户监听
    private String lastAccountId = null;
    private long lastLocalAccountId = -1;
    private LiveData<Account> currentAccountLiveData;
    private Observer<Account> currentAccountObserver;
    private String currentAccountName = "加载中...";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBillDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 沉浸式与 WindowInsets 动态适配
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            if (binding.layoutTopBar != null) {
                binding.layoutTopBar.setPadding(0, top, 0, 0);
            }

            if (binding.layoutBottomBar != null) {
                int padLeft = dpToPx(16);
                int padTop = dpToPx(12);
                int padRight = dpToPx(16);
                int padBottom = dpToPx(12) + bottom;
                binding.layoutBottomBar.setPadding(padLeft, padTop, padRight, padBottom);
            }

            if (binding.rvBillDetail != null) {
                int topBarHeight = top + dpToPx(56);
                int bottomBarHeight = dpToPx(48 + 12 + 12) + bottom;
                binding.rvBillDetail.setPadding(0, topBarHeight, 0, bottomBarHeight);
            }

            return insets;
        });

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        // ViewModel 初始化
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        // 初始化 ConcatAdapter & RecyclerView
        setupRecyclerView();

        // 初始化 TopBar
        setupToolbar();

        // 按钮监听
        setupButtons();

        // 加载账单数据
        String billId = getIntent().getStringExtra(EXTRA_BILL_ID);
        long billLocalId = getIntent().getLongExtra(EXTRA_BILL_LOCAL_ID, -1);
        Bill billObject = (Bill) getIntent().getSerializableExtra("bill_object");

        if (billObject != null) {
            currentBill = billObject;
            displayBillDetail(billObject);
            binding.ivMenu.setVisibility(View.GONE);
            if (binding.cardMenu != null) binding.cardMenu.setVisibility(View.GONE);
        } else if (billId != null || billLocalId != -1) {
            loadBillDetail(billId, billLocalId);
        } else {
            SnackbarUtils.showError(binding.getRoot(), "账单数据错误");
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    /**
     * 初始化 RecyclerView 与 ConcatAdapter
     */
    private void setupRecyclerView() {
        headerSectionAdapter = new HeaderSectionAdapter();
        summarySectionAdapter = new SummarySectionAdapter();
        reimburseSectionAdapter = new ReimburseSectionAdapter();
        attributesSectionAdapter = new AttributesSectionAdapter();
        settingsSectionAdapter = new SettingsSectionAdapter();
        remarkSectionAdapter = new RemarkSectionAdapter();

        concatAdapter = new ConcatAdapter(
                headerSectionAdapter,
                summarySectionAdapter,
                reimburseSectionAdapter,
                attributesSectionAdapter,
                settingsSectionAdapter,
                remarkSectionAdapter
        );

        binding.rvBillDetail.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBillDetail.setAdapter(concatAdapter);
        ElasticOverScrollHelper.attach(binding.rvBillDetail);
    }

    private void setupToolbar() {
        binding.ivBack.setOnClickListener(v -> finishWithAnim());
        if (binding.cardBack != null) {
            binding.cardBack.setOnClickListener(v -> finishWithAnim());
        }

        binding.ivMenu.setOnClickListener(this::showActionMenu);
        if (binding.cardMenu != null) {
            binding.cardMenu.setOnClickListener(this::showActionMenu);
        }
    }

    private void showActionMenu(View anchor) {
        if (actionMenu == null) {
            actionMenu = new FilterPopupMenu(this, type -> {
                switch (type) {
                    case ALL:
                        performEdit();
                        break;
                    case EXPENSE:
                        showModernDeleteDialog();
                        break;
                    case INCOME:
                        break;
                }
            });

            actionMenu.setMenuText(FilterPopupMenu.FilterType.ALL, "编辑");
            actionMenu.setMenuText(FilterPopupMenu.FilterType.EXPENSE, "删除");
            actionMenu.setMenuIcon(FilterPopupMenu.FilterType.ALL, R.drawable.ic_edit_bill);
            actionMenu.setMenuIcon(FilterPopupMenu.FilterType.EXPENSE, R.drawable.ic_delete2);
            actionMenu.setIconVisibility(FilterPopupMenu.FilterType.ALL, true);
            actionMenu.setIconVisibility(FilterPopupMenu.FilterType.EXPENSE, true);
            actionMenu.hideThirdItem();
        }

        actionMenu.toggle(anchor);
    }

    private void loadBillDetail(String billObjectId, long billLocalId) {
        billViewModel.getBillLive(billObjectId, billLocalId).observe(this, bill -> {
            if (bill != null) {
                currentBill = bill;
                displayBillDetail(bill);
            } else {
                Log.e(TAG, "未找到账单 - objectId: " + billObjectId + ", localId: " + billLocalId);
                SnackbarUtils.showError(binding.getRoot(), "账单不存在");
                finish();
            }
        });
    }

    /**
     * 刷新展示账单详情
     */
    private void displayBillDetail(Bill bill) {
        // 顶栏日期
        if (bill.getBillTime() != null) {
            SimpleDateFormat sdfTop = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
            binding.tvSubtitle.setText(sdfTop.format(bill.getBillTime()));
        }

        // 付款账户加载
        loadAccountName(bill.getAccountId(), bill.getLocalAccountId());

        // 各 Section Adapter 设值刷新
        headerSectionAdapter.setBill(bill);
        summarySectionAdapter.setBill(bill);
        reimburseSectionAdapter.setBill(bill);
        attributesSectionAdapter.setBill(bill, currentAccountName);
        settingsSectionAdapter.setBill(bill);
        remarkSectionAdapter.setBill(bill);

        // 限制编辑逻辑
        String catName = bill.getCategoryName() != null ? bill.getCategoryName() : "";
        if (catName.contains("报销入账")) {
            if (binding.cardMenu != null) binding.cardMenu.setVisibility(View.GONE);
            binding.ivMenu.setVisibility(View.GONE);
            if (binding.btnEditBottom != null) {
                binding.btnEditBottom.setAlpha(0.5f);
                binding.btnEditBottom.setOnClickListener(v -> SnackbarUtils.showInfo(binding.getRoot(), "报销入账账单不能编辑"));
            }
        } else {
            if (binding.cardMenu != null) binding.cardMenu.setVisibility(View.VISIBLE);
            binding.ivMenu.setVisibility(View.VISIBLE);
            if (binding.btnEditBottom != null) {
                binding.btnEditBottom.setAlpha(1.0f);
                binding.btnEditBottom.setOnClickListener(v -> performEdit());
            }
        }
    }

    private void loadAccountName(String accountId, long localAccountId) {
        String normalizedAccountId = (accountId != null && !accountId.trim().isEmpty()) ? accountId : null;

        if (normalizedAccountId == null && localAccountId <= 0) {
            if (currentAccountLiveData != null && currentAccountObserver != null) {
                currentAccountLiveData.removeObserver(currentAccountObserver);
                currentAccountLiveData = null;
                currentAccountObserver = null;
            }
            lastAccountId = null;
            lastLocalAccountId = -1;
            currentAccountName = "未设置";
            attributesSectionAdapter.updateAccountName(currentAccountName);
            return;
        }

        boolean sameAccount = (normalizedAccountId != null ? normalizedAccountId.equals(lastAccountId) : lastAccountId == null)
                && localAccountId == lastLocalAccountId;

        if (sameAccount && !"加载中...".equals(currentAccountName)) {
            return;
        }

        if (currentAccountLiveData != null && currentAccountObserver != null) {
            currentAccountLiveData.removeObserver(currentAccountObserver);
        }

        lastAccountId = normalizedAccountId;
        lastLocalAccountId = localAccountId;
        currentAccountName = "加载中...";
        attributesSectionAdapter.updateAccountName(currentAccountName);

        currentAccountLiveData = accountViewModel.getAccountLive(normalizedAccountId, localAccountId);
        currentAccountObserver = account -> {
            if (account != null && account.getName() != null && !account.getName().isEmpty()) {
                currentAccountName = account.getName();
            } else {
                currentAccountName = "未知账户";
            }
            attributesSectionAdapter.updateAccountName(currentAccountName);
        };

        currentAccountLiveData.observe(this, currentAccountObserver);
    }

    private void setupButtons() {
        if (binding.btnEditBottom != null) {
            binding.btnEditBottom.setOnClickListener(v -> performEdit());
        }

        if (binding.btnDeleteBottom != null) {
            binding.btnDeleteBottom.setOnClickListener(v -> {
                if (currentBill != null) {
                    showModernDeleteDialog();
                }
            });
        }
    }

    private void performEdit() {
        if (currentBill != null) {
            if (currentBill.getCategoryName() != null && currentBill.getCategoryName().contains("报销入账")) {
                SnackbarUtils.showInfo(binding.getRoot(), "报销入账账单不能编辑");
                return;
            }

            Intent intent = new Intent(BillDetailActivity.this, AddBillActivity.class);
            intent.putExtra("mode", "edit");

            if (currentBill.getObjectId() != null && !currentBill.getObjectId().isEmpty()) {
                intent.putExtra("bill_id", currentBill.getObjectId());
            } else {
                intent.putExtra("bill_local_id", currentBill.getId());
            }

            intent.putExtra("bill_type", currentBill.getType());
            intent.putExtra("bill_amount", currentBill.getAmount());
            intent.putExtra("bill_discount", currentBill.getDiscount());
            intent.putExtra("category_id", currentBill.getCategoryId());
            intent.putExtra("category_name", currentBill.getCategoryName());
            intent.putExtra("category_icon", currentBill.getCategoryIconUrl());
            intent.putExtra("account_id", currentBill.getAccountId());

            if (currentBill.getBillTime() != null) {
                intent.putExtra("bill_time", currentBill.getBillTime().getTime());
            }

            intent.putExtra("remark", currentBill.getRemark());
            intent.putExtra("location", currentBill.getLocation());
            intent.putExtra("exclude_budget", currentBill.isExcludeBudget());
            intent.putExtra("exclude_stat", currentBill.isExcludeStat());
            intent.putExtra("is_reimbursable", currentBill.isReimbursable());

            if (currentBill.getImageUrls() != null && !currentBill.getImageUrls().isEmpty()) {
                intent.putStringArrayListExtra("image_urls", new ArrayList<>(currentBill.getImageUrls()));
            }

            startActivityForResult(intent, REQUEST_EDIT_BILL);
        }
    }

    private void showModernDeleteDialog() {
        ConfirmDialog dialog = new ConfirmDialog(this)
                .setTitle("删除账单")
                .setMessage("确定要删除该账单吗?")
                .setConfirmListener(this::deleteBill)
                .setCancelListener(() -> {});
        dialog.show();
    }

    private void showCancelReimburseDialog() {
        if (currentBill == null) return;
        ConfirmDialog dialog = new ConfirmDialog(this)
                .setTitle("取消报销")
                .setMessage("确定要取消此账单的报销状态吗？相应的报销入账账单将被自动移除。")
                .setConfirmListener(() -> {
                    billViewModel.cancelReimburse(currentBill);
                    currentBill.setReimbursed(false);
                    currentBill.setReimburseAmount(0.0);
                    currentBill.setReimburseAccountId(null);
                    currentBill.setReimburseAccountName(null);
                    currentBill.setReimburseTime(null);
                    currentBill.setReimburseRemark(null);
                    displayBillDetail(currentBill);
                    SnackbarUtils.showSuccess(binding.getRoot(), "已取消报销");
                })
                .setCancelListener(() -> {});
        dialog.show();
    }

    private void deleteBill() {
        if (currentBill == null) return;

        billViewModel.deleteBill(currentBill);

        billViewModel.operationState.observeForever(response -> {
            if (response.isSuccess()) {
                billViewModel.operationState.removeObserver(observer -> {});
                setResult(RESULT_OK);
                new Handler(Looper.getMainLooper()).postDelayed(this::finishWithAnim, 100);
            } else if (response.isError()) {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + response.message);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_BILL && resultCode == RESULT_OK) {
            String billId = getIntent().getStringExtra(EXTRA_BILL_ID);
            long billLocalId = getIntent().getLongExtra(EXTRA_BILL_LOCAL_ID, -1);
            loadBillDetail(billId, billLocalId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (headerSectionAdapter != null) {
            headerSectionAdapter.stopAutoScroll();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (headerSectionAdapter != null) {
            headerSectionAdapter.startAutoScroll();
        }
    }

    private void finishWithAnim() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (headerSectionAdapter != null) {
            headerSectionAdapter.stopAutoScroll();
        }
        binding = null;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // =========================================================================
    // 1. Header Section Adapter (图片轮播卡片)
    // =========================================================================
    private class HeaderSectionAdapter extends RecyclerView.Adapter<HeaderSectionAdapter.ViewHolder> {

        private Bill bill;
        private List<String> imageUrls;
        private ImagePagerAdapter imagePagerAdapter;
        private Handler autoScrollHandler = new Handler(Looper.getMainLooper());
        private Runnable autoScrollRunnable;
        private boolean isAutoScrolling = false;
        private ViewPager2 vpImages;
        private LinearLayout layoutIndicator;

        public void setBill(Bill bill) {
            this.bill = bill;
            this.imageUrls = (bill != null && bill.getImageUrls() != null && !bill.getImageUrls().isEmpty())
                    ? bill.getImageUrls() : null;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_header, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (imageUrls == null || imageUrls.isEmpty()) return;

            vpImages = holder.itemView.findViewById(R.id.vp_images);
            layoutIndicator = holder.itemView.findViewById(R.id.layout_indicator);

            imagePagerAdapter = new ImagePagerAdapter(BillDetailActivity.this, imageUrls, pos -> {
                stopAutoScroll();
                showFullImageDialog(imageUrls, pos);
            });
            vpImages.setAdapter(imagePagerAdapter);

            setupIndicator(imageUrls.size());

            vpImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int pos) {
                    updateIndicator(pos);
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        stopAutoScroll();
                    } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        startAutoScroll();
                    }
                }
            });

            if (imageUrls.size() > 1) {
                startAutoScroll();
            }
        }

        @Override
        public int getItemCount() {
            return (imageUrls != null && !imageUrls.isEmpty()) ? 1 : 0;
        }

        private void setupIndicator(int count) {
            if (layoutIndicator == null) return;
            layoutIndicator.removeAllViews();
            if (count <= 1) {
                layoutIndicator.setVisibility(View.GONE);
                return;
            }
            layoutIndicator.setVisibility(View.VISIBLE);
            for (int i = 0; i < count; i++) {
                View indicator = new View(BillDetailActivity.this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
                params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                indicator.setLayoutParams(params);
                indicator.setBackgroundResource(i == 0 ? R.drawable.indicator_active : R.drawable.indicator_inactive);
                layoutIndicator.addView(indicator);
            }
        }

        private void updateIndicator(int pos) {
            if (layoutIndicator == null) return;
            int count = layoutIndicator.getChildCount();
            for (int i = 0; i < count; i++) {
                View indicator = layoutIndicator.getChildAt(i);
                indicator.setBackgroundResource(i == pos ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            }
        }

        public void startAutoScroll() {
            if (isAutoScrolling || imagePagerAdapter == null || imageUrls == null || imageUrls.size() <= 1) return;
            isAutoScrolling = true;
            autoScrollRunnable = new Runnable() {
                @Override
                public void run() {
                    if (vpImages != null && imagePagerAdapter != null) {
                        int current = vpImages.getCurrentItem();
                        int total = imagePagerAdapter.getItemCount();
                        if (total > 0) {
                            vpImages.setCurrentItem((current + 1) % total, true);
                            if (isAutoScrolling) {
                                autoScrollHandler.postDelayed(this, AUTO_SCROLL_DELAY);
                            }
                        }
                    }
                }
            };
            autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
        }

        public void stopAutoScroll() {
            if (autoScrollRunnable != null) {
                autoScrollHandler.removeCallbacks(autoScrollRunnable);
                isAutoScrolling = false;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    // =========================================================================
    // 2. Summary Section Adapter (核心账单摘要卡片)
    // =========================================================================
    private class SummarySectionAdapter extends RecyclerView.Adapter<SummarySectionAdapter.ViewHolder> {

        private Bill bill;

        public void setBill(Bill bill) {
            this.bill = bill;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_summary, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (bill == null) return;

            String categoryName = bill.getCategoryName();
            holder.tvCategory.setText(categoryName != null ? categoryName : "未分类");
            ImageLoaderUtils.loadHomeBillCategoryIcon(BillDetailActivity.this, bill.getCategoryIconUrl(), holder.ivCategoryIcon);
            holder.ivCategoryIcon.setBackground(null);

            if (categoryName != null && !categoryName.isEmpty()) {
                holder.tvStatLeftLabel.setText("本月" + categoryName);
            } else {
                holder.tvStatLeftLabel.setText("本月支出");
            }

            if (bill.getCategoryIconBackgroundColor() != null && !bill.getCategoryIconBackgroundColor().trim().isEmpty()) {
                try {
                    int color = Color.parseColor(bill.getCategoryIconBackgroundColor().trim());
                    holder.cardIconContainer.setCardBackgroundColor(color);
                } catch (Exception e) {
                    holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#2563EB"));
                }
            } else {
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#2563EB"));
            }

            String catName = categoryName != null ? categoryName : "";
            boolean isReimburse = bill.isReimbursable() || bill.isReimbursed() || catName.contains("报销");
            boolean isAccountCreation = catName.contains("账户创建") || catName.contains("创建账户") || catName.contains("余额调整");
            boolean isTransfer = bill.getType() == 2 || bill.getType() == 3 || catName.contains("转账") || catName.contains("还款");

            if (isReimburse) {
                holder.tvCategorySub.setText(bill.isReimbursed() ? "已报销" : "未报销");
                holder.tvCategory.setTextColor(Color.parseColor("#FF9800"));
                holder.tvCategory.setBackgroundResource(R.drawable.bg_pill_category);
                holder.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", bill.getAmount()));
                holder.tvAmount.setTextColor(Color.parseColor("#FF9800"));
            } else if (isAccountCreation) {
                holder.tvCategorySub.setText("余额调整");
                holder.tvCategory.setTextColor(Color.parseColor("#FF9800"));
                holder.tvCategory.setBackgroundResource(R.drawable.bg_pill_category);
                holder.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", bill.getAmount()));
                holder.tvAmount.setTextColor(Color.parseColor("#FF9800"));
            } else if (isTransfer) {
                holder.tvCategorySub.setText(bill.getType() == 3 ? "还款" : "转账");
                holder.tvCategory.setTextColor(Color.parseColor("#FF9800"));
                holder.tvCategory.setBackgroundResource(R.drawable.bg_pill_category);
                holder.tvAmount.setText(String.format(Locale.getDefault(), "-%.2f", bill.getAmount()));
                holder.tvAmount.setTextColor(Color.parseColor("#FF9800"));
            } else if (bill.getType() == 0) {
                holder.tvCategorySub.setText("支出");
                holder.tvCategory.setTextColor(Color.parseColor("#2563EB"));
                holder.tvCategory.setBackgroundResource(R.drawable.bg_pill_category);
                holder.tvAmount.setText(String.format(Locale.getDefault(), "-%.2f", bill.getAmount()));
                holder.tvAmount.setTextColor(Color.parseColor("#2563EB"));
            } else {
                holder.tvCategorySub.setText("收入");
                holder.tvCategory.setTextColor(Color.parseColor("#2ECC71"));
                holder.tvCategory.setBackgroundResource(R.drawable.bg_income_label);
                holder.tvAmount.setText(String.format(Locale.getDefault(), "+%.2f", bill.getAmount()));
                holder.tvAmount.setTextColor(Color.parseColor("#2ECC71"));
            }

            // 报销按钮状态：仅当账单勾选了需要报销或已报销时才展示报销按钮
            if (bill.isReimbursable() || bill.isReimbursed()) {
                holder.btnReimburse.setVisibility(View.VISIBLE);
                holder.tvReimburseText.setText(bill.isReimbursed() ? "取消报销" : "报销");
            } else {
                holder.btnReimburse.setVisibility(View.GONE);
            }

            // 按钮事件
            holder.btnRefund.setOnClickListener(v -> SnackbarUtils.showInfo(binding.getRoot(), "已发起退款处理"));
            holder.btnTemplate.setOnClickListener(v -> SnackbarUtils.showSuccess(binding.getRoot(), "已保存为记账模板"));
            holder.btnReimburse.setOnClickListener(v -> {
                if (bill.isReimbursed()) {
                    showCancelReimburseDialog();
                } else {
                    ReimburseBottomSheetFragment fragment = ReimburseBottomSheetFragment.newInstance(bill);
                    fragment.setOnReimburseCompletedListener(updated -> {
                        currentBill = updated;
                        displayBillDetail(updated);
                    });
                    fragment.show(getSupportFragmentManager(), "reimburse_fragment");
                }
            });
        }

        @Override
        public int getItemCount() {
            return bill != null ? 1 : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView cardIconContainer;
            ImageView ivCategoryIcon;
            TextView tvCategory, tvCategorySub, tvAmount, tvStatLeftLabel;
            LinearLayout btnRefund, btnReimburse, btnTemplate;
            TextView tvReimburseText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardIconContainer = itemView.findViewById(R.id.card_icon_container);
                ivCategoryIcon = itemView.findViewById(R.id.iv_category_icon);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvCategorySub = itemView.findViewById(R.id.tv_category_sub);
                tvAmount = itemView.findViewById(R.id.tv_amount);
                tvStatLeftLabel = itemView.findViewById(R.id.tv_stat_left_label);
                btnRefund = itemView.findViewById(R.id.btn_refund);
                btnReimburse = itemView.findViewById(R.id.btn_reimburse);
                btnTemplate = itemView.findViewById(R.id.btn_template);
                tvReimburseText = itemView.findViewById(R.id.tv_reimburse_text);
            }
        }
    }

    // =========================================================================
    // 3. Reimburse Section Adapter (报销详情卡片，仅已报销时展示)
    // =========================================================================
    private class ReimburseSectionAdapter extends RecyclerView.Adapter<ReimburseSectionAdapter.ViewHolder> {

        private Bill bill;

        public void setBill(Bill bill) {
            this.bill = bill;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_reimburse, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (bill == null || !bill.isReimbursed()) return;

            holder.tvAmount.setText(String.format(Locale.getDefault(), "¥%,.2f", bill.getReimburseAmount()));

            String accName = bill.getReimburseAccountName();
            if (accName == null || accName.trim().isEmpty()) {
                accName = "未选择账户";
            }
            holder.tvAccount.setText(accName);

            if (bill.getReimburseTime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault());
                holder.tvTime.setText(sdf.format(bill.getReimburseTime()));
            } else {
                holder.tvTime.setText("--");
            }
        }

        @Override
        public int getItemCount() {
            return (bill != null && bill.isReimbursed()) ? 1 : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAmount, tvAccount, tvTime;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAmount = itemView.findViewById(R.id.tv_reimburse_detail_amount);
                tvAccount = itemView.findViewById(R.id.tv_reimburse_detail_account);
                tvTime = itemView.findViewById(R.id.tv_reimburse_detail_time);
            }
        }
    }

    // =========================================================================
    // 4. Attributes Section Adapter (属性列表卡片)
    // =========================================================================
    private class AttributesSectionAdapter extends RecyclerView.Adapter<AttributesSectionAdapter.ViewHolder> {

        private Bill bill;
        private String accountName = "加载中...";

        public void setBill(Bill bill, String accountName) {
            this.bill = bill;
            this.accountName = accountName;
            notifyDataSetChanged();
        }

        public void updateAccountName(String accountName) {
            this.accountName = accountName;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_attributes, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (bill == null) return;

            holder.tvAccount.setText(accountName);

            if (bill.getBillTime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                holder.tvBillTime.setText(sdf.format(bill.getBillTime()));
            }

            if (bill.getLocation() != null && !bill.getLocation().trim().isEmpty()) {
                holder.tvLocation.setText(bill.getLocation());
                holder.tvLocation.setTextColor(Color.parseColor("#64748B"));
            } else {
                holder.tvLocation.setText("未定位");
                holder.tvLocation.setTextColor(Color.parseColor("#94A3B8"));
            }

            if (bill.getDiscount() > 0) {
                holder.tvDiscount.setText(String.format(Locale.getDefault(), "¥%.2f", bill.getDiscount()));
                holder.tvDiscount.setTextColor(Color.parseColor("#2563EB"));
            } else {
                holder.tvDiscount.setText("¥0.00");
                holder.tvDiscount.setTextColor(Color.parseColor("#64748B"));
            }

            // 关联标签加载
            TagRepository tagRepository = new TagRepository(holder.itemView.getContext());
            AppExecutors.get().diskIO().execute(() -> {
                List<TagEntity> tags = tagRepository.getTagsForBillSync(bill.getId());
                AppExecutors.get().mainThread().execute(() -> {
                    if (tags != null && !tags.isEmpty()) {
                        holder.tvTag.setVisibility(View.GONE);
                        if (holder.flTags != null) {
                            holder.flTags.setVisibility(View.VISIBLE);
                            TagViewUtils.renderTags(holder.flTags, tags, Integer.MAX_VALUE);
                        }
                    } else {
                        if (holder.flTags != null) {
                            holder.flTags.setVisibility(View.GONE);
                        }
                        holder.tvTag.setVisibility(View.VISIBLE);
                        holder.tvTag.setText("无");
                        holder.tvTag.setTextColor(Color.parseColor("#94A3B8"));
                    }

                    if (holder.layoutRowTag != null) {
                        holder.layoutRowTag.setOnClickListener(v -> {
                            SelectTagBottomSheetFragment fragment = SelectTagBottomSheetFragment.newInstance(tags);
                            fragment.setOnTagsSelectedListener(newTags -> {
                                tagRepository.saveBillTags(bill.getId(), bill.getUserId(), newTags, (success, message) -> {
                                    notifyDataSetChanged();
                                });
                            });
                            fragment.show(getSupportFragmentManager(), "SelectTagBottomSheetFragment");
                        });
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return bill != null ? 1 : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAccount, tvBillTime, tvLocation, tvDiscount, tvTag;
            FlowLayout flTags;
            View layoutRowTag;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAccount = itemView.findViewById(R.id.tv_account);
                tvBillTime = itemView.findViewById(R.id.tv_bill_time);
                tvLocation = itemView.findViewById(R.id.tv_location);
                tvDiscount = itemView.findViewById(R.id.tv_discount);
                tvTag = itemView.findViewById(R.id.tv_tag);
                flTags = itemView.findViewById(R.id.fl_tags);
                layoutRowTag = itemView.findViewById(R.id.layout_row_tag);
            }
        }
    }

    // =========================================================================
    // 5. Settings Section Adapter (预算与收支卡片)
    // =========================================================================
    private class SettingsSectionAdapter extends RecyclerView.Adapter<SettingsSectionAdapter.ViewHolder> {

        private Bill bill;

        public void setBill(Bill bill) {
            this.bill = bill;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_settings, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (bill == null) return;

            if (bill.isExcludeBudget()) {
                holder.tvBudget.setText("不计入预算");
                holder.tvBudget.setTextColor(Color.parseColor("#FF9800"));
            } else {
                holder.tvBudget.setText("计入月度预算");
                holder.tvBudget.setTextColor(Color.parseColor("#64748B"));
            }

            if (bill.isExcludeStat()) {
                holder.tvStat.setText("不计入收支");
                holder.tvStat.setTextColor(Color.parseColor("#FF9800"));
            } else {
                holder.tvStat.setText(bill.getType() == 0 ? "计入支出" : "计入收入");
                holder.tvStat.setTextColor(Color.parseColor("#64748B"));
            }
        }

        @Override
        public int getItemCount() {
            return bill != null ? 1 : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBudget, tvStat;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBudget = itemView.findViewById(R.id.tv_budget);
                tvStat = itemView.findViewById(R.id.tv_stat);
            }
        }
    }

    // =========================================================================
    // 6. Remark Section Adapter (备注卡片)
    // =========================================================================
    private class RemarkSectionAdapter extends RecyclerView.Adapter<RemarkSectionAdapter.ViewHolder> {

        private Bill bill;

        public void setBill(Bill bill) {
            this.bill = bill;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_detail_remark, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (bill == null) return;

            if (bill.getRemark() != null && !bill.getRemark().trim().isEmpty()) {
                holder.tvRemark.setText(bill.getRemark());
                holder.tvRemark.setTextColor(Color.parseColor("#334155"));
            } else {
                holder.tvRemark.setText("无");
                holder.tvRemark.setTextColor(Color.parseColor("#94A3B8"));
            }
        }

        @Override
        public int getItemCount() {
            return bill != null ? 1 : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvRemark;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRemark = itemView.findViewById(R.id.tv_remark);
            }
        }
    }

    // FullImageDialog 支持
    private void showFullImageDialog(List<String> imageUrls, int initialPosition) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_full_image);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        ViewPager2 vpFullImage = dialog.findViewById(R.id.vp_full_image);
        TextView tvPageIndicator = dialog.findViewById(R.id.tv_page_indicator);
        ImageView ivClose = dialog.findViewById(R.id.iv_close);
        View toolbarContainer = dialog.findViewById(R.id.toolbar_container);

        FullImagePagerAdapter adapter = new FullImagePagerAdapter(this, imageUrls, () -> {
            if (toolbarContainer.getVisibility() == View.VISIBLE) {
                toolbarContainer.setVisibility(View.GONE);
            } else {
                toolbarContainer.setVisibility(View.VISIBLE);
            }
        });
        vpFullImage.setAdapter(adapter);
        vpFullImage.setCurrentItem(initialPosition, false);

        tvPageIndicator.setText(String.format(Locale.getDefault(), "%d / %d", initialPosition + 1, imageUrls.size()));

        vpFullImage.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                tvPageIndicator.setText(String.format(Locale.getDefault(), "%d / %d", pos + 1, imageUrls.size()));
            }
        });

        ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (headerSectionAdapter != null) {
                headerSectionAdapter.startAutoScroll();
            }
        });

        dialog.show();
    }
}
