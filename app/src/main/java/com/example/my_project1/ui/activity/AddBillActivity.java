package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityAddBillBinding;
import com.example.my_project1.ui.adapter.CategoryIconPagerAdapter;
import com.example.my_project1.ui.dialog.UploadProgressDialog;
import com.example.my_project1.ui.fragment.AlbumPickerBottomFragment;
import com.example.my_project1.ui.fragment.BillChooseAccountFragment;
import com.example.my_project1.ui.fragment.DateTimePickerFragment;
import com.example.my_project1.ui.fragment.RemarkBottomSheetFragment;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.ui.viewmodel.billvm.ImageUploadViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.OssUploadUtil;
import com.example.my_project1.utils.SnackbarUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * AddBillActivity - 符合MVVM架构规范
 * -------------------------------------------------------
 * ✅ ⭐ 编辑模式：支持账单编辑和新增
 * ✅ ⭐ 分类图标选中：编辑模式下自动选中对应分类
 * ✅ ⭐ 数据同步：确保修改后同步到云端和本地数据库
 * ✅ ⭐ 性能优化：优化初始化流程和内存使用
 */
public class AddBillActivity extends AppCompatActivity implements com.example.my_project1.ui.fragment.TransferFragment.OnTransferAccountSelectedListener {

    private static final String TAG = "AddBillActivity";

    private static final String PREF_LAST_ACCOUNT_ID = "last_account_id";

    // View Binding
    private ActivityAddBillBinding binding;

    // ViewModels
    private BillViewModel billViewModel;
    private com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel accountViewModel;
    private ImageUploadViewModel uploadViewModel;

    // Adapter
    private CategoryIconPagerAdapter pagerAdapter;

    // 用户ID
    private String currentUserId;

    // ==================== 账单数据 ====================

    // ⭐ 编辑模式标识
    private boolean isEditMode = false;
    private String  editBillId = null;
    private long    editBillLocalId = -1;
    private Bill originalBill = null; // ⭐ 保存原始账单对象

    // 账单类型：0-支出，1-收入，2-转账，3-还款
    private int billType = 0;

    // 分类相关
    private String selectedCategoryName = null;
    private String selectedCategoryCloudId = null;
    private String selectedCategoryImageUrl = null;
    private String selectedCategoryIconBackgroundColor = null;

    // 金额
    private double currentAmount = 0;

    // 账户
    private Account selectedAccount = null;
    private Account selectedTargetAccount = null; // 🔑 新增：转入账户 (用于转账/还款)

    // 日期时间
    private Date selectedBillTime = new Date();

    // 备注
    private String billRemark = "";

    // 位置
    private String selectedLocationName = null;
    private String selectedLocationAddress = null;
    private double selectedLat = 0;
    private double selectedLng = 0;

    // 预算设置
    private boolean excludeBudget = false;

    // 图片支持远程图片URL和本地Uri
    private List<Uri> selectedImages = new ArrayList<>();
    private List<String> existingImageUrls = new ArrayList<>(); // 编辑模式下已有的图片URL
    private static final int MAX_IMAGE_COUNT = 6;

    // ==================== 计算器状态 ====================

    private StringBuilder currentInput = new StringBuilder();
    private String pendingOperator = null;
    private boolean isCalculating = false;
    private String fullExpression = "";
    private String lastConfirmedOperator = null;

    // ==================== UI状态 ====================

    private boolean isSaving = false;
    private boolean isRepeatMode = false;
    private UploadProgressDialog uploadDialog;

    // ==================== Request Codes ====================

    private static final int REQUEST_LOCATION = 1003;
    private Bill bill;
    private String uriStr;

    // ==================== 生命周期方法 ====================

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentUserId != null) outState.putString("saved_user_id", currentUserId);
        if (selectedLocationName != null) outState.putString("saved_location_name", selectedLocationName);
        if (selectedLocationAddress != null) outState.putString("saved_location_address", selectedLocationAddress);
        outState.putDouble("saved_lat", selectedLat);
        outState.putDouble("saved_lng", selectedLng);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityAddBillBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 🔴 修复：从 savedInstanceState 恢复 userId 防止重建时 Bmob session 未就绪导致 finish()
        if (savedInstanceState != null) {
            String savedUserId = savedInstanceState.getString("saved_user_id");
            if (savedUserId != null) currentUserId = savedUserId;
            selectedLocationName = savedInstanceState.getString("saved_location_name");
            selectedLocationAddress = savedInstanceState.getString("saved_location_address");
            selectedLat = savedInstanceState.getDouble("saved_lat", 0);
            selectedLng = savedInstanceState.getDouble("saved_lng", 0);
        }


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

        initUser();

        // ⭐ 检查是否为编辑模式
        checkEditMode();

        initViewModels();
        setupViewPager();
        setupTabs();
        setupKeyboard();
        setupButtons();

        // 🔴 修复：Activity 重建后（如从地图返回），恢复已选位置的 UI 显示
        if (selectedLocationName != null && !selectedLocationName.isEmpty()) {
            binding.tvRecord.setText(selectedLocationName);
            binding.ivRecord.setVisibility(View.GONE);
        }

        // ⭐ 如果是编辑模式，加载账单数据
        if (isEditMode) {
            loadBillDataForEdit();
        } else {
            // 🔑 检查是否为还款流程
            int type = getIntent().getIntExtra("bill_type", 0);
            if (type == 3 || getIntent().getBooleanExtra("is_repayment", false)) {
                billType = 3;
                String accountId = getIntent().getStringExtra("account_id");
                if (accountId != null) {
                    AppExecutors.get().diskIO().execute(() -> {
                        Account account = accountViewModel.getAccountByIdSync(accountId);
                        if (account != null) {
                            AppExecutors.get().mainThread().execute(() -> {
                                selectedTargetAccount = account;
                                binding.tvTargetAccount.setText(account.getName());
                                if (account.getIconUrl() != null) {
                                    ImageLoaderUtils.load(getApplication(), account.getIconUrl(), binding.ivTargetAccount);
                                }
                                switchToTransferTab(3);
                            });
                        }
                    });
                } else {
                    switchToTransferTab(3);
                }
            }
            updateAmountDisplay();

            // 🔑 优化：初始化账户选中逻辑（ Intent 传参 > 上次使用 ）
            initAccountSelection();
        }

        observeViewModels();
    }

    private void switchToTransferTab(int type) {
        billType = type;
        binding.viewpagerCategory.setVisibility(View.VISIBLE);
        binding.viewpagerCategory.setCurrentItem(2, false); // 🔑 切到转账页 (index 2)
        updateTabSelection(type);
        updateTransferUi();
    }

    private void updateTransferUi() {
        boolean isTransfer = (billType == 2 || billType == 3);

        // 🔑 核心重构：在转账模式下，隐藏底部的横向账户选择卡片，改由 Fragment 处理
        binding.cardAccount.setVisibility(isTransfer ? View.GONE : View.VISIBLE);
        binding.ivTransferArrow.setVisibility(View.GONE);
        binding.cardTargetAccount.setVisibility(View.GONE);

        if (isTransfer) {
            syncTransferUi();
        }
    }

    @Override
    public void onAccountSelected(Account account, boolean isTarget) {
        if (isTarget) {
            this.selectedTargetAccount = account;
        } else {
            this.selectedAccount = account;
            updateBaseAccountUi(account);
        }
    }

    @Override
    public void onSwapRequested() {
        Account temp = selectedAccount;
        selectedAccount = selectedTargetAccount;
        selectedTargetAccount = temp;
        syncTransferUi();
    }

    private void syncTransferUi() {
        com.example.my_project1.ui.fragment.TransferFragment fragment = pagerAdapter.getTransferFragment();
        if (fragment != null) {
            updateTransferFragmentUi(fragment, selectedAccount, false);
            updateTransferFragmentUi(fragment, selectedTargetAccount, true);
        }
        updateBaseAccountUi(selectedAccount);
    }

    private void updateTransferFragmentUi(com.example.my_project1.ui.fragment.TransferFragment fragment, Account account, boolean isTarget) {
        if (account != null) {
            if (isTarget) fragment.updateTargetUi(account, account.getIconUrl(), account.getName());
            else fragment.updateSourceUi(account, account.getIconUrl(), account.getName());
        } else {
            if (isTarget) fragment.updateTargetUi(null, null, "请选择转入账户");
            else fragment.updateSourceUi(null, null, "请选择转出账户");
        }
    }

    private void updateBaseAccountUi(Account account) {
        if (account != null) {
            binding.tvAccount.setText(account.getName());
            if (account.getIconUrl() != null) {
                ImageLoaderUtils.load(getApplication(), account.getIconUrl(), binding.ivAccount);
            }
        } else {
            binding.tvAccount.setText("无账户");
            binding.ivAccount.setImageResource(R.drawable.ic_unselect_account);
        }
    }

    /**
     * 初始化用户信息
     */
    private void initUser() {
        // 🔴 修复：优先使用 BmobUser，如果因 session 未就绪返回 null，
        //    则使用 onSaveInstanceState 中保存的 userId（Activity 重建场景）
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        if (user != null) {
            currentUserId = user.getObjectId();
        } else if (currentUserId == null) {
            // 仅在没有任何缓存 userId 时才退出
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
        }
        // 若 currentUserId 已由 savedInstanceState 恢复，则跳过 finish()，继续显示界面
    }

    /**
     * ⭐ 检查是否为编辑模式
     */
    private void checkEditMode() {
        Intent intent = getIntent();
        String mode = intent.getStringExtra("mode");

        if ("edit".equals(mode)) {
            isEditMode = true;
            editBillId = intent.getStringExtra("bill_id");
            editBillLocalId = intent.getLongExtra("bill_local_id", -1);

            // 修改标题
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("编辑账单");
            }
        }
    }

    /**
     * ⭐ 加载账单数据用于编辑
     */
    private void loadBillDataForEdit() {
        Intent intent = getIntent();

        // 1. 账单类型
        billType = intent.getIntExtra("bill_type", 0);

        // 2. 金额
        currentAmount = intent.getDoubleExtra("bill_amount", 0);
        if (currentAmount > 0) {
            currentInput = new StringBuilder(formatAmount(currentAmount));
        }

        // 3.分类（在ViewPager设置后才能选中）
        selectedCategoryCloudId = intent.getStringExtra("category_id");
        selectedCategoryName = intent.getStringExtra("category_name");
        selectedCategoryImageUrl = intent.getStringExtra("category_icon");
        selectedCategoryIconBackgroundColor = intent.getStringExtra("category_icon_bg_color");

        // 4. 账户
        String accountId = intent.getStringExtra("account_id");
        String toAccountId = intent.getStringExtra("to_account_id"); // 🔑 新增

        if (accountId != null) {
            AppExecutors.get().diskIO().execute(() -> {
                Account account = accountViewModel.getAccountByIdSync(accountId);
                if (account != null) {
                    AppExecutors.get().mainThread().execute(() -> {
                        selectedAccount = account;
                        binding.tvAccount.setText(account.getName());
                        if (account.getIconUrl() != null) {
                            ImageLoaderUtils.load(getApplication(), account.getIconUrl(), binding.ivAccount);
                        }
                    });
                }
            });
        }

        if (toAccountId != null) {
            AppExecutors.get().diskIO().execute(() -> {
                Account account = accountViewModel.getAccountByIdSync(toAccountId);
                if (account != null) {
                    AppExecutors.get().mainThread().execute(() -> {
                        selectedTargetAccount = account;
                        binding.tvTargetAccount.setText(account.getName());
                        if (account.getIconUrl() != null) {
                            ImageLoaderUtils.load(getApplication(), account.getIconUrl(), binding.ivTargetAccount);
                        }
                    });
                }
            });
        }

        // 5. 时间
        long billTime = intent.getLongExtra("bill_time", System.currentTimeMillis());
        selectedBillTime = new Date(billTime);

        // 6. 备注
        billRemark = intent.getStringExtra("remark");
        if (billRemark == null) billRemark = "";
        if (!billRemark.isEmpty()) {
            String displayText = billRemark;
            if (displayText.length() > 30) {
                displayText = displayText.substring(0, 30) + "...";
            }
            binding.tvRemarkHint.setText(displayText);
        }

        // 7. 位置
        selectedLocationName = intent.getStringExtra("location");
        if (selectedLocationName != null && !selectedLocationName.isEmpty()) {
            binding.tvRecord.setText(selectedLocationName);
            binding.ivRecord.setVisibility(View.GONE);
        }

        // 8. 预算设置
        excludeBudget = intent.getBooleanExtra("exclude_budget", false);

        // 9. 图片URLs
        ArrayList<String> imageUrls = intent.getStringArrayListExtra("image_urls");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            existingImageUrls.addAll(imageUrls);
            // 显示第一张图片
            if (!existingImageUrls.isEmpty()) {
                binding.ivAlbum.setScaleType(ImageView.ScaleType.CENTER_CROP);
                binding.ivAlbum.clearColorFilter();

                // 使用Glide加载远程图片
                ImageLoaderUtils.loadThumbnail(this, existingImageUrls.get(0), binding.ivAlbum);

                if (existingImageUrls.size() > 1) {
                    binding.tvAlbumBadge.setVisibility(View.VISIBLE);
                    binding.tvAlbumBadge.setText(String.valueOf(existingImageUrls.size()));
                }
            }
        }

        // 10. 显示日期
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        binding.tvCalendar.setText(sdf.format(selectedBillTime));
        binding.ivCalendar.setVisibility(View.GONE);

        // 11. ⭐ 设置类型并选中分类
        // 修复闪烁：不再用postDelayed(200)猜测ViewPager初始化时机。
        // pagerAdapter.setSelectedCategory() 内部已经能正确处理两种情况：
        //  - 若目标Fragment尚未创建（常见于此处，因为loadBillDataForEdit()
        //    在setupViewPager()之后立即调用，Fragment可能还未createFragment），
        //    会记录pending信息，createFragment()时随Fragment一起构造出来，
        //    从而在Fragment/Adapter创建的第一时刻就带着正确选中态，无需二次刷新。
        //  - 若Fragment已存在，直接同步调用，立即生效。
        binding.viewpagerCategory.setCurrentItem(billType, false);
        updateTabSelection(billType);

        if (pagerAdapter != null && selectedCategoryCloudId != null) {
            pagerAdapter.setSelectedCategory(billType, selectedCategoryCloudId);
        }

        // 更新金额显示
        updateAmountDisplay();
    }

    private void initViewModels() {
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);
        accountViewModel = new ViewModelProvider(this).get(com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel.class);
        uploadViewModel = new ViewModelProvider(this).get(ImageUploadViewModel.class);
    }

    /**
     * 🔑 核心优化：初始化账户选中逻辑
     * 优先级：Intent 传入账户 > 上次使用账户 > 不选
     * 同时考虑账户是否被删除或归档
     */
    private void initAccountSelection() {
        if (isEditMode) return;

        Intent intent = getIntent();
        String intentAccountId = intent.getStringExtra("account_id");
        // 如果是还款模式，intentAccountId 是作为 TargetAccount (转入账户) 的，所以不应作为 selectedAccount (转出账户)
        boolean isRepayment = (intent.getIntExtra("bill_type", 0) == 3 || intent.getBooleanExtra("is_repayment", false));

        android.content.SharedPreferences prefs = getSharedPreferences("bill_prefs", MODE_PRIVATE);
        String lastAccountId = prefs.getString(PREF_LAST_ACCOUNT_ID, null);

        AppExecutors.get().diskIO().execute(() -> {
            Account accountToSelect = null;

            // 1. 优先尝试从 Intent 获取（仅在非还款流程中作为主账户）
            if (intentAccountId != null && !isRepayment) {
                Account account = accountViewModel.getAccountByIdSync(intentAccountId);
                if (isValidAccount(account)) {
                    accountToSelect = account;
                }
            }

            // 2. 如果没获取到，尝试上次使用的账户
            if (accountToSelect == null && lastAccountId != null) {
                Account account = accountViewModel.getAccountByIdSync(lastAccountId);
                if (isValidAccount(account)) {
                    accountToSelect = account;
                }
            }

            if (accountToSelect != null) {
                Account finalAccount = accountToSelect;
                AppExecutors.get().mainThread().execute(() -> {
                    selectedAccount = finalAccount;
                    updateBaseAccountUi(finalAccount);

                    // 如果已经初始化了 ViewPager 且处于转账模式，同步 UI
                    if (billType == 2 || billType == 3) {
                        syncTransferUi();
                    }
                });
            }
        });
    }

    private boolean isValidAccount(Account account) {
        return account != null
                && account.getSyncState() != com.example.my_project1.data.model.SyncState.TO_DELETE
                && account.isCanBeSelected();
    }

    // ==================== 观察ViewModel ====================

    /**
     * ⭐ 观察ViewModel的数据变化（性能优化）
     */
    private void observeViewModels() {
        // 观察账单操作状态
        billViewModel.operationState.observe(this, response -> {
            if (response == null) return;

            if (response.isLoading()) {
                // 显示加载中
            } else if (response.isSuccess()) {
                handleBillSaveSuccess();
            } else if (response.isError()) {
                handleBillSaveError(response.message);
            }
        });

        // 观察Toast消息
        billViewModel.toastMessage.observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                // 过滤掉“成功”类的提示，保留“失败”、“警告”等
                if (!message.contains("成功")) {
                    showSnackbar(message, SnackbarUtils.Type.INFO);
                }
            }
        });

        // 观察图片上传状态
        uploadViewModel.batchUploadState.observe(this, response -> {
            if (response == null) return;

            if (response.isLoading()) {
                showUploadDialog();
            } else if (response.isSuccess()) {
                handleUploadSuccess(response.data);
            } else if (response.isError()) {
                handleUploadError(response.message);
            } else if (response.isIdle()) {
                dismissUploadDialog();
            }
        });

        // 观察上传进度
        uploadViewModel.uploadProgress.observe(this, progress -> {
            if (uploadDialog != null && uploadDialog.isShowing()) {
                uploadDialog.updateProgress(progress.percentage);
            }
        });
    }

    // ==================== UI初始化 ====================

    /**
     * 设置ViewPager
     */
    private void setupViewPager() {
        pagerAdapter = new CategoryIconPagerAdapter(this, currentUserId);
        binding.viewpagerCategory.setAdapter(pagerAdapter);

        // 🔑 性能优化：预加载所有页面，减少切换时的布局渲染耗时
        binding.viewpagerCategory.setOffscreenPageLimit(3);

        // 🔑 设置转账监听
        pagerAdapter.setOnTransferAccountSelectedListener(this);

        // 分类选择监听
        pagerAdapter.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl, backgroundColor) -> {
            selectedCategoryName = displayName;
            selectedCategoryCloudId = categoryCloudId;
            selectedCategoryImageUrl = categoryImageUrl;
            selectedCategoryIconBackgroundColor = backgroundColor;
        });

        // 页面切换监听
        binding.viewpagerCategory.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                billType = position;
                updateTabSelection(position);
                updateTransferUi(); // 🔑 修复：滑动切换时也更新转账 UI 状态

                // ⭐ 编辑模式下切换类型时保持分类选择
                if (isEditMode && position == getIntent().getIntExtra("bill_type", 0)) {
                    String originalCategoryId = getIntent().getStringExtra("category_id");
                    if (pagerAdapter != null && originalCategoryId != null) {
                        pagerAdapter.setSelectedCategory(position, originalCategoryId);
                    }
                }
            }
        });
    }

    /**
     * 设置Tab
     */
    private void setupTabs() {
        binding.tabExpense.setOnClickListener(v -> handleTabClick(0));
        binding.tabIncome.setOnClickListener(v -> handleTabClick(1));
        binding.tabTransfer.setOnClickListener(v -> handleTabClick(2));

        updateTabSelection(billType);
    }

    private void handleTabClick(int type) {
        if (type == 2) {
            switchToTransferTab(2);
        } else {
            binding.viewpagerCategory.setVisibility(View.VISIBLE);
            binding.viewpagerCategory.setCurrentItem(type, true);
            billType = type;
            updateTransferUi();
        }
    }

    /**
     * 设置按钮
     */
    private void setupButtons() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivSetting.setOnClickListener(v -> openSettings());
        binding.ivAlbum.setOnClickListener(v -> openGallery());

        // 长按相册查看已选图片
        binding.ivAlbum.setOnLongClickListener(v -> {
            if (!selectedImages.isEmpty() || !existingImageUrls.isEmpty()) {
                openGallery();
                return true;
            }
            return false;
        });

        // 区域3的按钮
        binding.cardAccount.setOnClickListener(v -> selectAccount(false));
        binding.cardTargetAccount.setOnClickListener(v -> selectAccount(true)); // 🔑 选择转入账户
        binding.cardCalendar.setOnClickListener(v -> selectCalendar());
        binding.cardLabel.setOnClickListener(v -> selectLabel());
        binding.cardRecord.setOnClickListener(v -> selectLocation());
        binding.tvRemarkHint.setOnClickListener(v -> editRemark());
    }

    /**
     * 设置键盘
     */
    private void setupKeyboard() {
        // 数字键
        binding.btn0.setOnClickListener(v -> onNumberClick("0"));
        binding.btn1.setOnClickListener(v -> onNumberClick("1"));
        binding.btn2.setOnClickListener(v -> onNumberClick("2"));
        binding.btn3.setOnClickListener(v -> onNumberClick("3"));
        binding.btn4.setOnClickListener(v -> onNumberClick("4"));
        binding.btn5.setOnClickListener(v -> onNumberClick("5"));
        binding.btn6.setOnClickListener(v -> onNumberClick("6"));
        binding.btn7.setOnClickListener(v -> onNumberClick("7"));
        binding.btn8.setOnClickListener(v -> onNumberClick("8"));
        binding.btn9.setOnClickListener(v -> onNumberClick("9"));

        // 功能键
        binding.btnDot.setOnClickListener(v -> onDotClick());
        binding.btnDelete.setOnClickListener(v -> onDeleteClick());
        binding.btnAddSubtract.setOnClickListener(v -> onOperatorClick("+", "-"));
        binding.btnMultiplyDivide.setOnClickListener(v -> onOperatorClick("×", "÷"));

        // 完成/等于按钮
        binding.btnComplete.setOnClickListener(v -> {
            if (isCalculating) {
                onEqualsClick();
            } else {
                onCompleteClick();
            }
        });

        // ⭐ 编辑模式下隐藏"再记一笔"按钮
        if (isEditMode) {
            binding.btnRepeat.setVisibility(View.GONE);
        } else {
            binding.btnRepeat.setOnClickListener(v -> onRepeatClick());
        }
    }

    // ==================== 计算器逻辑 ====================

    private void onNumberClick(String number) {
        if (pendingOperator != null) {
            confirmOperator();
        }

        if (currentInput.length() < 12) {
            if (currentInput.length() == 0 || currentInput.toString().equals("0")) {
                currentInput = new StringBuilder(number);
            } else {
                currentInput.append(number);
            }
            updateAmountDisplay();
        }
    }

    private void onDotClick() {
        if (pendingOperator != null) {
            confirmOperator();
        }

        if (currentInput.length() == 0) {
            currentInput.append("0.");
        } else if (!currentInput.toString().contains(".")) {
            currentInput.append(".");
        }
        updateAmountDisplay();
    }

    private void onDeleteClick() {
        if (isCalculating) {
            resetCalculation();
        } else if (currentInput.length() > 0) {
            currentInput.deleteCharAt(currentInput.length() - 1);
            updateAmountDisplay();
        }
    }

    private void onOperatorClick(String op1, String op2) {
        String nextOperator;
        String buttonId = op1 + "/" + op2;

        if (lastConfirmedOperator == null || !lastConfirmedOperator.startsWith(buttonId)) {
            nextOperator = op1;
            lastConfirmedOperator = buttonId + ":" + op1;
        } else {
            String currentDisplay = lastConfirmedOperator.split(":")[1];
            if (currentDisplay.equals(op1)) {
                nextOperator = op2;
                lastConfirmedOperator = buttonId + ":" + op2;
            } else {
                nextOperator = op1;
                lastConfirmedOperator = buttonId + ":" + op1;
            }
        }

        if (buttonId.equals("+/-")) {
            binding.btnAddSubtract.setText(nextOperator);
        } else {
            binding.btnMultiplyDivide.setText(nextOperator);
        }

        pendingOperator = nextOperator;
        isCalculating = true;
        binding.btnComplete.setText("=");
        updateAmountDisplay();
    }

    private void confirmOperator() {
        if (pendingOperator == null) return;

        String inputValue = currentInput.length() > 0 ? currentInput.toString() : "0";

        if (fullExpression.isEmpty()) {
            fullExpression = inputValue;
        } else {
            fullExpression = fullExpression + inputValue;
        }

        fullExpression = fullExpression + pendingOperator;
        currentInput = new StringBuilder();
        pendingOperator = null;
    }

    private void onEqualsClick() {
        if (!isCalculating) return;

        if (pendingOperator != null) {
            confirmOperator();
        }

        String inputValue = currentInput.length() > 0 ? currentInput.toString() : "0";
        String completeExpression = fullExpression.isEmpty() ?
                inputValue : fullExpression + inputValue;

        try {
            double result = calculateExpression(completeExpression);

            if (result < 0) {
                showSnackbar("金额不能为负数", SnackbarUtils.Type.ERROR);
                resetCalculation();
                return;
            }

            currentInput = new StringBuilder(formatAmount(result));
            fullExpression = "";
            pendingOperator = null;
            isCalculating = false;
            lastConfirmedOperator = null;

            updateAmountDisplay();
            binding.btnComplete.setText("完成");
            binding.btnAddSubtract.setText("+/-");
            binding.btnMultiplyDivide.setText("×/÷");

        } catch (Exception e) {
            showSnackbar("计算错误", SnackbarUtils.Type.ERROR);
            resetCalculation();
        }
    }

    private void onCompleteClick() {
        if (!validateAndPrepare(false)) return;
        startSaveBill();
    }

    private void onRepeatClick() {
        if (!validateAndPrepare(true)) return;
        startSaveBill();
    }

    /**
     * 验证并准备保存数据
     */
    private boolean validateAndPrepare(boolean isRepeat) {
        if (isSaving) {
            showSnackbar("正在保存中，请稍候...", SnackbarUtils.Type.WARNING);
            return false;
        }

        String amountStr = currentInput.toString();
        if (amountStr.isEmpty() || amountStr.equals("0") || amountStr.equals("0.")) {
            showSnackbar("请输入金额", SnackbarUtils.Type.WARNING);
            return false;
        }

        try {
            double finalAmount = Double.parseDouble(amountStr);
            if (finalAmount <= 0) {
                showSnackbar("请输入有效金额", SnackbarUtils.Type.WARNING);
                return false;
            }

            // 🔑 验证账户
            if (selectedAccount == null) {
                showSnackbar(billType == 2 || billType == 3 ? "请选择转出账户" : "请选择账户", SnackbarUtils.Type.WARNING);
                return false;
            }

            if ((billType == 2 || billType == 3) && selectedTargetAccount == null) {
                showSnackbar("请选择转入账户", SnackbarUtils.Type.WARNING);
                return false;
            }

            if (selectedAccount != null && selectedTargetAccount != null &&
                    selectedAccount.getObjectId() != null &&
                    selectedAccount.getObjectId().equals(selectedTargetAccount.getObjectId())) {
                showSnackbar("转出账户和转入账户不能相同", SnackbarUtils.Type.WARNING);
                return false;
            }

            currentAmount = finalAmount;
            isRepeatMode = isRepeat;
            return true;

        } catch (NumberFormatException e) {
            showSnackbar("金额格式错误", SnackbarUtils.Type.ERROR);
            return false;
        }
    }

    private double calculateExpression(String expression) throws Exception {
        expression = expression.replace("×", "*").replace("÷", "/");

        // 简单的表达式计算
        String[] tokens = expression.split("(?=[+\\-*/])|(?<=[+\\-*/])");
        double result = Double.parseDouble(tokens[0]);

        for (int i = 1; i < tokens.length; i += 2) {
            String operator = tokens[i];
            double operand = Double.parseDouble(tokens[i + 1]);

            switch (operator) {
                case "+":
                    result += operand;
                    break;
                case "-":
                    result -= operand;
                    break;
                case "*":
                    result *= operand;
                    break;
                case "/":
                    if (operand == 0) throw new ArithmeticException("除数不能为0");
                    result /= operand;
                    break;
            }
        }

        return result;
    }

    private void resetCalculation() {
        currentInput = new StringBuilder();
        fullExpression = "";
        pendingOperator = null;
        isCalculating = false;
        lastConfirmedOperator = null;
        updateAmountDisplay();
        binding.btnComplete.setText("完成");
        binding.btnAddSubtract.setText("+/-");
        binding.btnMultiplyDivide.setText("×/÷");
    }

    /**
     * 更新金额显示
     */
    private void updateAmountDisplay() {
        String display;
        if (isCalculating && pendingOperator != null) {
            display = fullExpression + currentInput.toString() + " " + pendingOperator;
        } else if (isCalculating) {
            display = fullExpression + currentInput.toString();
        } else if (currentInput.length() == 0) {
            display = "0";
        } else {
            display = currentInput.toString();
        }

        binding.tvAmount.setText(display);
    }

    private String formatAmount(double amount) {
        BigDecimal bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        String formatted = bd.stripTrailingZeros().toPlainString();
        return formatted.equals("0") ? "0" : formatted;
    }

    // ==================== 保存逻辑 ====================

    /**
     * ⭐ 开始保存账单（支持编辑模式）
     */
    private void startSaveBill() {
        isSaving = true;

        // 如果有新选择的图片,先上传
        if (!selectedImages.isEmpty()) {
            // ✅ 使用批量上传方法
            uploadViewModel.uploadBatchImages(
                    selectedImages,
                    currentUserId,
                    OssUploadUtil.UploadScene.BILL
            );
        } else {
            // 编辑模式下,如果没有新图片,直接使用已有的图片URL
            if (isEditMode && !existingImageUrls.isEmpty()) {
                saveBillToDatabase(existingImageUrls);
            } else {
                saveBillToDatabase(null);
            }
        }
    }

    /**
     * 处理图片上传成功
     */
    private void handleUploadSuccess(List<String> objectKeys) {
        // ⭐ 合并已有图片和新上传的图片
        List<String> allImageUrls = new ArrayList<>();
        if (isEditMode && !existingImageUrls.isEmpty()) {
            allImageUrls.addAll(existingImageUrls);
        }
        if (objectKeys != null && !objectKeys.isEmpty()) {
            allImageUrls.addAll(objectKeys);
        }

        saveBillToDatabase(allImageUrls.isEmpty() ? null : allImageUrls);
    }

    /**
     * 处理图片上传失败
     */
    private void handleUploadError(String errorMessage) {
        isSaving = false;
        showSnackbar("图片上传失败: " + errorMessage, SnackbarUtils.Type.ERROR);
    }

    /**
     * 保存账单到数据库（使用同步查询）
     */
    private void saveBillToDatabase(List<String> imageObjectKeys) {
        if (isEditMode && (editBillId != null || editBillLocalId != -1)) {
            // 编辑模式：在后台线程同步查询
            AppExecutors.get().diskIO().execute(() -> {
                try {
                    if (editBillId != null) {
                        bill = billViewModel.saveBill(editBillId);
                    } else {
                        bill = billViewModel.saveBillLocal(editBillLocalId);
                    }

                    if (bill == null) {
                        AppExecutors.get().mainThread().execute(() -> {
                            isSaving = false;
                            showSnackbar("找不到要编辑的账单", SnackbarUtils.Type.ERROR);
                        });
                        return;
                    }

                    // 在主线程更新数据并保存
                    final Bill finalBill = bill;
                    AppExecutors.get().mainThread().execute(() -> {
                        // 更新账单数据
                        updateBillData(finalBill, imageObjectKeys);

                        billViewModel.updateBill(finalBill);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 查询或更新Bill对象异常", e);
                    AppExecutors.get().mainThread().execute(() -> {
                        isSaving = false;
                        showSnackbar("保存失败: " + e.getMessage(), SnackbarUtils.Type.ERROR);
                    });
                }
            });

        } else {
            // 新增模式：创建新账单
            Bill bill = new Bill();
            updateBillData(bill, imageObjectKeys);

            Log.d(TAG, "⭐ 调用insertBill: " + bill.getAmount());
            billViewModel.insertBill(bill);
        }
    }

    /**
     * ⭐ 处理账单保存成功（支持编辑模式）
     */
    private void handleBillSaveSuccess() {
        isSaving = false;
        uploadViewModel.resetBatchUploadState();

        // 保存最后使用的账户ID
        if (selectedAccount != null) {
            getSharedPreferences("bill_prefs", MODE_PRIVATE)
                    .edit()
                    .putString(PREF_LAST_ACCOUNT_ID, selectedAccount.getObjectId())
                    .apply();
        }

        if (isEditMode) {
            // 编辑模式：返回详情页
            setResult(RESULT_OK);
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 100);
        } else if (isRepeatMode) {
            // 再记一笔：重置输入
            resetForRepeat();
        } else {
            // 完成：返回上一页
            setResult(RESULT_OK);
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 100);
        }
    }

    /**
     * 处理账单保存失败
     */
    private void handleBillSaveError(String message) {
        isSaving = false;
        uploadViewModel.resetBatchUploadState();
        showSnackbar("保存失败: " + message, SnackbarUtils.Type.ERROR);
    }

    /**
     * 重置输入（再记一笔）
     */
    private void resetForRepeat() {
        resetCalculation();
        selectedImages.clear();
        existingImageUrls.clear();
        updateImageDisplay();
        billRemark = "";
        binding.tvRemarkHint.setText("添加备注");
        selectedLocationName = null;
        binding.tvRecord.setText("记录");
        binding.ivRecord.setVisibility(View.VISIBLE);
    }

    // ==================== 图片相关 ====================

    /**
     * ⭐ 打开相册（支持编辑模式）
     */
    private void openGallery() {
        // ⭐ 计算剩余可选图片数量
        int remainingCount = MAX_IMAGE_COUNT - existingImageUrls.size() - selectedImages.size();

        AlbumPickerBottomFragment fragment = AlbumPickerBottomFragment.newInstance(
                selectedImages, remainingCount);

        fragment.setOnPhotosSelectedListener(photos -> {
            selectedImages.clear();
            selectedImages.addAll(photos);
            updateImageDisplay();
        });

        fragment.show(getSupportFragmentManager(), "album_picker");
    }

    /**
     * ⭐ 更新图片显示（支持编辑模式）
     */
    private void updateImageDisplay() {
        int totalImageCount = existingImageUrls.size() + selectedImages.size();

        if (totalImageCount > 0) {
            binding.ivAlbum.setScaleType(ImageView.ScaleType.CENTER_CROP);
            binding.ivAlbum.clearColorFilter();

            // 优先显示本地新选择的图片
            if (!selectedImages.isEmpty()) {
                Glide.with(this)
                        .load(selectedImages.get(0))
                        .centerCrop()
                        .placeholder(R.drawable.ic_album)
                        .into(binding.ivAlbum);
            } else if (!existingImageUrls.isEmpty()) {
                // 显示已有的远程图片
                ImageLoaderUtils.loadThumbnail(this, existingImageUrls.get(0), binding.ivAlbum);
            }

            if (totalImageCount > 1) {
                binding.tvAlbumBadge.setVisibility(View.VISIBLE);
                binding.tvAlbumBadge.setText(String.valueOf(totalImageCount));
            } else {
                binding.tvAlbumBadge.setVisibility(View.GONE);
            }
        } else {
            binding.ivAlbum.setImageResource(R.drawable.ic_album);
            binding.ivAlbum.setScaleType(ImageView.ScaleType.CENTER);
            binding.tvAlbumBadge.setVisibility(View.GONE);
        }
    }

    // ==================== 区域3功能 ====================

    private void selectAccount(boolean isTarget) {
        BillChooseAccountFragment fragment = new BillChooseAccountFragment();

        fragment.setOnAccountChooseListener((account, iconUrl, accountName) -> {
            if (isTarget) {
                selectedTargetAccount = account;
                binding.tvTargetAccount.setText(accountName);
                if (iconUrl != null) {
                    ImageLoaderUtils.load(getApplication(), iconUrl, binding.ivTargetAccount);
                } else {
                    binding.tvTargetAccount.setText("转入账户");
                    binding.ivTargetAccount.setImageResource(R.drawable.ic_wallet_change);
                }
            } else {
                selectedAccount = account;
                binding.tvAccount.setText(accountName);
                if (iconUrl != null) {
                    ImageLoaderUtils.load(getApplication(), iconUrl, binding.ivAccount);
                } else {
                    binding.tvAccount.setText("无账户");
                    binding.ivAccount.setImageResource(R.drawable.ic_unselect_account);
                }
            }
        });

        fragment.show(getSupportFragmentManager(), "choose_account");
    }

    private void selectCalendar() {
        DateTimePickerFragment fragment = new DateTimePickerFragment();

        fragment.setOnDateTimeSelectedListener((timestamp, formattedDateTime) -> {
            selectedBillTime = new Date(timestamp);
            binding.ivCalendar.setVisibility(View.GONE);
            binding.tvCalendar.setText(formattedDateTime);
            showSnackbar("已选择: " + formattedDateTime, SnackbarUtils.Type.SUCCESS);
        });

        fragment.show(getSupportFragmentManager(), "date_time_picker");
    }

    private void selectLabel() {
        // TODO: 实现标签功能
        showSnackbar("标签功能开发中", SnackbarUtils.Type.INFO);
    }

    private void selectLocation() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        startActivityForResult(intent, REQUEST_LOCATION);
    }

    /**
     * 编辑备注 - 使用底部弹窗
     */
    private void editRemark() {
        RemarkBottomSheetFragment fragment = RemarkBottomSheetFragment.newInstance(billRemark);

        fragment.setOnRemarkConfirmListener(remark -> {
            billRemark = remark;
            if (!billRemark.isEmpty()) {
                // 如果备注很长，只显示前30个字符
                String displayText = billRemark;
                if (displayText.length() > 30) {
                    displayText = displayText.substring(0, 30) + "...";
                }
                binding.tvRemarkHint.setText(displayText);
            } else {
                binding.tvRemarkHint.setText("添加备注");
            }
        });

        fragment.show(getSupportFragmentManager(), "remark_bottom_sheet");
    }

    /**
     * ⭐ 辅助方法：更新Bill对象的数据
     */
    private void updateBillData(Bill bill, List<String> imageObjectKeys) {
        bill.setUserId(currentUserId);
        bill.setAmount(currentAmount);
        bill.setType(billType);
        bill.setCategoryId(selectedCategoryCloudId);
        bill.setCategoryName(selectedCategoryName);
        bill.setCategoryIconUrl(selectedCategoryImageUrl);
        bill.setCategoryIconBackgroundColor(selectedCategoryIconBackgroundColor);
        bill.setBillTime(selectedBillTime);
        bill.setRemark(billRemark);
        bill.setExcludeBudget(excludeBudget);

        // 设置图片URL
        if (imageObjectKeys != null && !imageObjectKeys.isEmpty()) {
            bill.setImageUrls(imageObjectKeys);
        }

        // 账户（可选）
        if (selectedAccount != null) {
            bill.setAccountId(selectedAccount.getObjectId());
            bill.setLocalAccountId(selectedAccount.getId());
        }

        // 🔑 转入账户 (用于转账/还款)
        if ((billType == 2 || billType == 3) && selectedTargetAccount != null) {
            bill.setToAccountId(selectedTargetAccount.getObjectId());
            bill.setToLocalAccountId(selectedTargetAccount.getId());

            // 转账默认名称和图标 (如果没有选分类)
            if (bill.getCategoryName() == null) {
                bill.setCategoryName(billType == 3 ? "还款" : "转账");
                bill.setCategoryId("system_transfer");
                Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.ic_transference);
                uriStr = uri.toString();
                bill.setCategoryIconUrl(uriStr);
            }
        }

        // 地点（可选）
        if (selectedLocationName != null) {
            bill.setLocation(selectedLocationName);
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, CategoryActivity.class);
        startActivity(intent);
    }

    // ==================== Activity Result ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_LOCATION) {
            handleLocationResult(data);
        }
    }

    private void handleLocationResult(Intent data) {
        if (data == null) return;

        selectedLocationName = data.getStringExtra("location_name");
        selectedLocationAddress = data.getStringExtra("location_address");
        selectedLat = data.getDoubleExtra("location_lat", 0);
        selectedLng = data.getDoubleExtra("location_lng", 0);

        // 🔴 修复：binding 可能因 Activity 重建或 onDestroy 后为 null，必须做保护
        if (binding == null) return;

        if (selectedLocationName != null && !selectedLocationName.isEmpty()) {
            binding.tvRecord.setText(selectedLocationName);
            binding.ivRecord.setVisibility(View.GONE);
            showSnackbar("已选择: " + selectedLocationName, SnackbarUtils.Type.SUCCESS);
        }
    }

    // ==================== UI工具方法 ====================

    private void updateTabSelection(int position) {
        // 重置样式
        binding.tabExpense.setBackgroundColor(Color.TRANSPARENT);
        binding.tabExpense.setTextColor(Color.parseColor("#666666"));
        binding.tabIncome.setBackgroundColor(Color.TRANSPARENT);
        binding.tabIncome.setTextColor(Color.parseColor("#666666"));
        binding.tabTransfer.setBackgroundColor(Color.TRANSPARENT);
        binding.tabTransfer.setTextColor(Color.parseColor("#666666"));

        if (position == 0) {
            binding.tabExpense.setBackgroundResource(R.drawable.tab_selected_bg);
            binding.tabExpense.setTextColor(Color.WHITE);
        } else if (position == 1) {
            binding.tabIncome.setBackgroundResource(R.drawable.tab_selected_bg);
            binding.tabIncome.setTextColor(Color.WHITE);
        } else if (position == 2 || position == 3) {
            binding.tabTransfer.setBackgroundResource(R.drawable.tab_selected_bg);
            binding.tabTransfer.setTextColor(Color.WHITE);
        }
    }

    private void showUploadDialog() {
        if (uploadDialog == null) {
            uploadDialog = new UploadProgressDialog(this);
        }

        if (!uploadDialog.isShowing()) {
            uploadDialog.show();
        }
    }

    private void dismissUploadDialog() {
        if (uploadDialog != null && uploadDialog.isShowing()) {
            uploadDialog.dismiss();
        }
    }

    /**
     * 显示美化的Snackbar
     */
    private void showSnackbar(String message, SnackbarUtils.Type type) {
        if (binding != null && binding.getRoot() != null) {
            // 过滤掉明确标为 SUCCESS 且包含“成功”字样的消息
            if (type == SnackbarUtils.Type.SUCCESS && message != null && message.contains("成功")) {
                return;
            }
            SnackbarUtils.show(binding.getRoot(), message, type);
        }
    }
    @Override
    public void finish() {
        super.finish();
        // 退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }


    // ==================== 生命周期清理 ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissUploadDialog();

        // ⭐ 性能优化：清理资源
        if (pagerAdapter != null) {
            pagerAdapter = null;
        }

        binding = null;
    }
}