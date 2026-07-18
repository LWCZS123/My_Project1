package com.example.my_project1.ui.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityBillDetailBinding;
import com.example.my_project1.ui.adapter.photo.FullImagePagerAdapter;
import com.example.my_project1.ui.adapter.photo.ImagePagerAdapter;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.popup.FilterPopupMenu;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BillDetailActivity - 账单详情页（优化版）
 * -------------------------------------------------------
 * ✅ 轮播图显示账单图片（支持自动滚动）
 * ✅ 点击图片使用Dialog+PhotoView查看原图（支持缩放）
 * ✅ 完整信息展示（统一卡片风格）
 * ✅ 编辑功能：跳转到添加账单页面并传递数据
 * ✅ 优化删除对话框：自定义样式，符合设计规范
 * ✅ 性能优化：减少不必要的observe，优化内存使用
 * ✅ 使用SnackbarUtils显示通知
 * ✅ 支持离线账单查看（使用本地ID和objectId双重查找）
 * ✅ 新增：轮播图自动滚动效果（每3秒切换）
 * ✅ 新增：右上角菜单（编辑/删除）
 */
public class BillDetailActivity extends AppCompatActivity {

    private static final String TAG = "BillDetailActivity";
    private static final long AUTO_SCROLL_DELAY = 3000; // 自动滚动延迟时间（毫秒）

    public static final String EXTRA_BILL_ID = "bill_id";           // objectId（云端ID）
    public static final String EXTRA_BILL_LOCAL_ID = "bill_local_id"; // 本地数据库ID
    public static final int REQUEST_EDIT_BILL = 2001; // 编辑账单请求码

    private ActivityBillDetailBinding binding;
    private BillViewModel billViewModel;
    private AccountViewModel accountViewModel;
    private Bill currentBill;
    private ImagePagerAdapter imagePagerAdapter;

    // 自动滚动相关
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private boolean isAutoScrolling = false;

    // 操作菜单
    private FilterPopupMenu actionMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityBillDetailBinding.inflate(getLayoutInflater());
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

        // 初始化ViewModel
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        // 初始化Toolbar
        setupToolbar();

        // 支持三种传递方式：objectId, 本地ID, 或 直接传递对象(用于虚拟账单)
        String billId = getIntent().getStringExtra(EXTRA_BILL_ID);
        long billLocalId = getIntent().getLongExtra(EXTRA_BILL_LOCAL_ID, -1);
        Bill billObject = (Bill) getIntent().getSerializableExtra("bill_object");

        if (billObject != null) {
            // 虚拟账单直接显示
            currentBill = billObject;
            displayBillDetail(billObject);
            // 虚拟账单不允许编辑删除
            binding.ivMenu.setVisibility(View.GONE);
        } else if (billId != null || billLocalId != -1) {
            // 从数据库加载
            loadBillDetail(billId, billLocalId);
        } else {
            SnackbarUtils.showError(binding.getRoot(), "账单数据错误");
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return;
        }

        // 设置按钮点击事件
        setupButtons();

        // 初始化自动滚动Handler
        autoScrollHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 设置Toolbar
     */
    private void setupToolbar() {
        binding.ivBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
            );
        });

        // 右上角菜单按钮
        binding.ivMenu.setOnClickListener(v -> showActionMenu(v));
    }

    /**
     * 显示操作菜单（编辑/删除）
     */
    private void showActionMenu(View anchor) {
        if (actionMenu == null) {
            actionMenu = new FilterPopupMenu(this, type -> {
                switch (type) {
                    case ALL:
                        // 编辑功能
                        performEdit();
                        break;
                    case EXPENSE:
                        // 删除功能
                        showModernDeleteDialog();
                        break;
                    case INCOME:
                        break;
                }
            });

            // 配置菜单项
            actionMenu.setMenuText(FilterPopupMenu.FilterType.ALL, "编辑");
            actionMenu.setMenuText(FilterPopupMenu.FilterType.EXPENSE, "删除");

            // 设置图标
            actionMenu.setMenuIcon(FilterPopupMenu.FilterType.ALL, R.drawable.ic_edit_bill);
            actionMenu.setMenuIcon(FilterPopupMenu.FilterType.EXPENSE, R.drawable.ic_delete2);

            // 显示图标
            actionMenu.setIconVisibility(FilterPopupMenu.FilterType.ALL, true);
            actionMenu.setIconVisibility(FilterPopupMenu.FilterType.EXPENSE, true);

            // 隐藏第三项
            actionMenu.hideThirdItem();
        }

        actionMenu.toggle(anchor);
    }

    /**
     * 加载账单详情（支持本地ID和objectId双重查找）
     */
    private void loadBillDetail(String billObjectId, long billLocalId) {
        billViewModel.getAllBills().removeObservers(this);

        // 根据ID查询账单，只observe一次
        billViewModel.getAllBills().observe(this, new androidx.lifecycle.Observer<List<Bill>>() {
            @Override
            public void onChanged(List<Bill> bills) {
                if (bills != null && !bills.isEmpty()) {
                    Bill foundBill = null;

                    // 优先通过 objectId 查找（在线账单）
                    if (billObjectId != null && !billObjectId.isEmpty()) {
                        for (Bill bill : bills) {
                            if (billObjectId.equals(bill.getObjectId())) {
                                foundBill = bill;
                                Log.d(TAG, "✅ 通过objectId找到账单: " + billObjectId);
                                break;
                            }
                        }
                    }

                    // 如果通过 objectId 没找到，尝试通过本地ID查找（离线账单）
                    if (foundBill == null && billLocalId != -1) {
                        for (Bill bill : bills) {
                            if (bill.getId() == billLocalId) {
                                foundBill = bill;
                                Log.d(TAG, "✅ 通过本地ID找到账单: " + billLocalId);
                                break;
                            }
                        }
                    }

                    if (foundBill != null) {
                        currentBill = foundBill;
                        displayBillDetail(foundBill);
                    } else {
                        Log.e(TAG, "❌ 未找到账单 - objectId: " + billObjectId + ", localId: " + billLocalId);
                        SnackbarUtils.showError(binding.getRoot(), "账单不存在");
                        finish();
                    }
                } else {
                    Log.w(TAG, "⚠️ 账单列表为空");
                    SnackbarUtils.showError(binding.getRoot(), "账单数据为空");
                    finish();
                }
            }
        });
    }

    /**
     * 显示账单详情
     */
    private void displayBillDetail(Bill bill) {
        // 1. 图片轮播（支持自动滚动）
        setupImageCarousel(bill.getImageUrls());

        // 2. 分类信息
        binding.tvCategory.setText(bill.getCategoryName());
        ImageLoaderUtils.loadThumbnail(this, bill.getCategoryIconUrl(), binding.ivCategoryIcon);

        // 3. 类型和金额
        if (bill.getType() == 0) {
            // 支出
            binding.tvTypeLabel.setText("支出");
            binding.tvTypeLabel.setBackgroundResource(R.drawable.bg_expense_label);
            binding.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", bill.getAmount()));
            binding.tvAmount.setTextColor(getColor(android.R.color.holo_red_dark));
        } else {
            // 收入
            binding.tvTypeLabel.setText("收入");
            binding.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", bill.getAmount()));
            binding.tvAmount.setTextColor(getColor(R.color.green));
        }

        // 4. 根据accountId或localAccountId获取账户名称
        loadAccountName(bill.getAccountId(), bill.getLocalAccountId());

        // 5. 时间
        if (bill.getBillTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            binding.tvBillTime.setText(sdf.format(bill.getBillTime()));
        }

        // 6. 预算设置
        if (bill.isExcludeBudget()) {
            binding.tvBudget.setText("不计入预算");
            binding.tvBudget.setTextColor(getColor(R.color.orange_500));
        } else {
            binding.tvBudget.setText("计入预算");
            binding.tvBudget.setTextColor(getColor(R.color.green));
        }

        // 7. 备注（始终显示，没有内容则显示"无"）
        binding.tvRemark.setVisibility(View.VISIBLE);
        binding.ivRemarkIcon.setVisibility(View.VISIBLE);
        binding.tvRemarkLabel.setVisibility(View.VISIBLE);

        if (bill.getRemark() != null && !bill.getRemark().isEmpty()) {
            binding.tvRemark.setText(bill.getRemark());
            binding.tvRemark.setTextColor(getColor(R.color.primary_text));
        } else {
            binding.tvRemark.setText("无");
            binding.tvRemark.setTextColor(getColor(R.color.secondary_text));
        }

        // 8. 位置（始终显示，没有内容则显示"无"）
        binding.tvLocation.setVisibility(View.VISIBLE);
        binding.ivLocationIcon.setVisibility(View.VISIBLE);
        binding.tvLocationLabel.setVisibility(View.VISIBLE);

        if (bill.getLocation() != null && !bill.getLocation().isEmpty()) {
            binding.tvLocation.setText(bill.getLocation());
            binding.tvLocation.setTextColor(getColor(R.color.primary_text));
        } else {
            binding.tvLocation.setText("无");
            binding.tvLocation.setTextColor(getColor(R.color.secondary_text));
        }

        // 9. 显示同步状态（调试用）
        if (bill.getObjectId() == null || bill.getObjectId().isEmpty()) {
            Log.d(TAG, "📱 离线账单 - 本地ID: " + bill.getId());
        } else {
            Log.d(TAG, "☁️ 在线账单 - objectId: " + bill.getObjectId());
        }
    }

    /**
     * 根据accountId加载账户名称
     */
    private void loadAccountName(String accountId, long localAccountId) {
        if ((accountId == null || accountId.isEmpty()) && localAccountId <= 0) {
            binding.tvAccount.setText("未设置");
            return;
        }

        binding.tvAccount.setText("加载中...");

        if (accountId != null && !accountId.isEmpty()) {
            accountViewModel.getAccountNameById(accountId, new AccountViewModel.ResultCallback() {
                @Override
                public void onResult(boolean success, String accountName) {
                    updateAccountNameUI(success, accountName, accountId);
                }
            });
        } else {
            accountViewModel.getAccountNameByLocalId(localAccountId, new AccountViewModel.ResultCallback() {
                @Override
                public void onResult(boolean success, String accountName) {
                    updateAccountNameUI(success, accountName, String.valueOf(localAccountId));
                }
            });
        }
    }

    private void updateAccountNameUI(boolean success, String accountName, String id) {
        if (success && accountName != null) {
            binding.tvAccount.setText(accountName);
            Log.d(TAG, "✅ 账户名称加载成功: " + accountName);
        } else {
            binding.tvAccount.setText("未知账户");
            Log.e(TAG, "❌ 账户名称加载失败: id=" + id);
        }
    }

    /**
     * 设置图片轮播（支持自动滚动）
     */
    private void setupImageCarousel(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            // 没有图片时，隐藏轮播卡片
            binding.cardImages.setVisibility(View.GONE);
            return;
        }

        binding.cardImages.setVisibility(View.VISIBLE);

        // 设置轮播适配器
        imagePagerAdapter = new ImagePagerAdapter(this, imageUrls, new ImagePagerAdapter.OnImageClickListener() {
            @Override
            public void onImageClick(int position) {
                // 点击图片时暂停自动滚动并显示原图
                stopAutoScroll();
                showFullImageDialog(imageUrls, position);
            }
        });
        binding.vpImages.setAdapter(imagePagerAdapter);

        // 设置指示器
        setupIndicator(imageUrls.size());

        // 监听页面切换
        binding.vpImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                // 用户手动滑动时重置自动滚动
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopAutoScroll();
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    startAutoScroll();
                }
            }
        });

        // 如果有多张图片，启动自动滚动
        if (imageUrls.size() > 1) {
            startAutoScroll();
        }
    }

    /**
     * 启动自动滚动
     */
    private void startAutoScroll() {
        if (isAutoScrolling || imagePagerAdapter == null) {
            return;
        }

        isAutoScrolling = true;
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding != null && binding.vpImages != null && imagePagerAdapter != null) {
                    int currentItem = binding.vpImages.getCurrentItem();
                    int totalItems = imagePagerAdapter.getItemCount();

                    if (totalItems > 0) {
                        int nextItem = (currentItem + 1) % totalItems;
                        binding.vpImages.setCurrentItem(nextItem, true);

                        // 继续自动滚动
                        if (isAutoScrolling) {
                            autoScrollHandler.postDelayed(this, AUTO_SCROLL_DELAY);
                        }
                    }
                }
            }
        };

        autoScrollHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
        Log.d(TAG, "✅ 启动自动滚动");
    }

    /**
     * 停止自动滚动
     */
    private void stopAutoScroll() {
        if (autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
            isAutoScrolling = false;
            Log.d(TAG, "⏸️ 停止自动滚动");
        }
    }

    /**
     * 显示全屏图片Dialog
     */
    private void showFullImageDialog(List<String> imageUrls, int initialPosition) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_full_image);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }

        ViewPager2 vpFullImage = dialog.findViewById(R.id.vp_full_image);
        TextView tvPageIndicator = dialog.findViewById(R.id.tv_page_indicator);
        ImageView ivClose = dialog.findViewById(R.id.iv_close);
        View toolbarContainer = dialog.findViewById(R.id.toolbar_container);

        FullImagePagerAdapter adapter = new FullImagePagerAdapter(this, imageUrls, new FullImagePagerAdapter.OnImageClickListener() {
            @Override
            public void onImageClick() {
                if (toolbarContainer.getVisibility() == View.VISIBLE) {
                    toolbarContainer.setVisibility(View.GONE);
                } else {
                    toolbarContainer.setVisibility(View.VISIBLE);
                }
            }
        });
        vpFullImage.setAdapter(adapter);
        vpFullImage.setCurrentItem(initialPosition, false);

        updatePageIndicator(tvPageIndicator, initialPosition, imageUrls.size());

        vpFullImage.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(tvPageIndicator, position, imageUrls.size());
            }
        });

        ivClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // Dialog关闭时恢复自动滚动
        dialog.setOnDismissListener(dialogInterface -> {
            if (imagePagerAdapter != null && imagePagerAdapter.getItemCount() > 1) {
                startAutoScroll();
            }
        });

        dialog.show();
    }

    /**
     * 更新Dialog中的页码指示器
     */
    private void updatePageIndicator(TextView tvPageIndicator, int position, int total) {
        tvPageIndicator.setText(String.format(Locale.getDefault(), "%d / %d", position + 1, total));
    }

    /**
     * 设置指示器
     */
    private void setupIndicator(int count) {
        binding.layoutIndicator.removeAllViews();

        if (count <= 1) {
            binding.layoutIndicator.setVisibility(View.GONE);
            return;
        }

        binding.layoutIndicator.setVisibility(View.VISIBLE);

        for (int i = 0; i < count; i++) {
            View indicator = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dpToPx(8), dpToPx(8)
            );
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            indicator.setLayoutParams(params);

            if (i == 0) {
                indicator.setBackgroundResource(R.drawable.indicator_active);
            } else {
                indicator.setBackgroundResource(R.drawable.indicator_inactive);
            }

            binding.layoutIndicator.addView(indicator);
        }
    }

    /**
     * 更新指示器
     */
    private void updateIndicator(int position) {
        int count = binding.layoutIndicator.getChildCount();
        for (int i = 0; i < count; i++) {
            View indicator = binding.layoutIndicator.getChildAt(i);
            if (i == position) {
                indicator.setBackgroundResource(R.drawable.indicator_active);
            } else {
                indicator.setBackgroundResource(R.drawable.indicator_inactive);
            }
        }
    }

    /**
     * 设置按钮事件
     */
    private void setupButtons() {
        // 编辑按钮
        //binding.btnEdit.setOnClickListener(v -> performEdit());

        // 删除按钮
        //binding.btnDelete.setOnClickListener(v -> {
//            if (currentBill != null) {
//                showModernDeleteDialog();
//            }
//        });
   }

    /**
     * 执行编辑操作
     */
    private void performEdit() {
        if (currentBill != null) {
            Intent intent = new Intent(BillDetailActivity.this, AddBillActivity.class);
            intent.putExtra("mode", "edit");

            if (currentBill.getObjectId() != null && !currentBill.getObjectId().isEmpty()) {
                intent.putExtra("bill_id", currentBill.getObjectId());
            } else {
                intent.putExtra("bill_local_id", currentBill.getId());
            }

            intent.putExtra("bill_type", currentBill.getType());
            intent.putExtra("bill_amount", currentBill.getAmount());
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

            if (currentBill.getImageUrls() != null && !currentBill.getImageUrls().isEmpty()) {
                intent.putStringArrayListExtra("image_urls",
                        new ArrayList<>(currentBill.getImageUrls()));
            }

            startActivityForResult(intent, REQUEST_EDIT_BILL);
        }
    }

    /**
     * 显示现代化删除确认对话框
     */
    private void showModernDeleteDialog() {
        ConfirmDialog dialog = new ConfirmDialog(this)
                .setTitle("删除账单")
                .setMessage("确定要删除该账单吗?")
                .setConfirmListener(() -> {
                    deleteBill();
                })
                .setCancelListener(() -> {
                    // 取消操作
                });

        dialog.show();
    }

    /**
     * 删除账单
     */
    private void deleteBill() {
        if (currentBill == null) return;

        billViewModel.deleteBill(currentBill);

        billViewModel.operationState.observeForever(response -> {
            if (response.isSuccess()) {
                billViewModel.operationState.removeObserver(observer -> {});

                SnackbarUtils.showSuccess(binding.getRoot(), "删除成功");
                setResult(RESULT_OK);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    finish();
                }, 500);
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
        // 页面不可见时停止自动滚动
        stopAutoScroll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面可见时恢复自动滚动
        if (imagePagerAdapter != null && imagePagerAdapter.getItemCount() > 1) {
            startAutoScroll();
        }
    }

    @Override
    public void finish() {
        super.finish();
        // 退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清理自动滚动
        stopAutoScroll();
        if (autoScrollHandler != null) {
            autoScrollHandler.removeCallbacksAndMessages(null);
            autoScrollHandler = null;
        }

        // 清理资源
        if (imagePagerAdapter != null) {
            imagePagerAdapter = null;
        }

        binding = null;
    }

    /**
     * dp转px
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}