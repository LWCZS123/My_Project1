package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ActivityAddAccountBinding;
import com.example.my_project1.ui.fragment.IconAccountBottomSheet;
import com.example.my_project1.ui.fragment.SelectGroupBottomSheetFragment;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.Calendar;

import cn.bmob.v3.BmobUser;

public class AddAccountActivity extends AppCompatActivity {

    private static final String TAG = "AddAccountActivity";

    private ActivityAddAccountBinding binding;
    private AccountViewModel viewModel;

    private String accountType; // e.g., "支付宝", "信用卡"
    private String accountCategory; // "资金账户", "信用账户", "充值账户"
    private int iconRes;
    private String selectedIconUrl;
    
    private String selectedGroupId;
    private boolean isCreditType = false;
    private int billingDay = 0;
    private int repaymentDay = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        parseIntent();
        initUi();
        setupListeners();
        findDefaultGroup(); // 恢复自动匹配分组逻辑
    }

    private void parseIntent() {
        accountType = getIntent().getStringExtra("accountName");
        iconRes = getIntent().getIntExtra("accountIconRes", R.drawable.ic_wallet);
        accountCategory = getIntent().getStringExtra("accountCategory");

        if (accountCategory != null) {
            isCreditType = accountCategory.equals("信用账户");
        }
    }

    private void initUi() {
        binding.etAccountName.setText(accountType);
        binding.ivAccountIcon.setImageResource(iconRes);
        
        if (isCreditType) {
            binding.tvToolbarTitle.setText("添加信用账户");
            binding.tvBalanceLabel.setText("当前欠款");
            binding.etBalance.setHint("请输入当前欠款金额");
            
            binding.layoutCreditLimit.setVisibility(View.VISIBLE);
            binding.dividerLimit.setVisibility(View.VISIBLE);
            binding.cardDateInfo.setVisibility(View.VISIBLE);
        } else {
            binding.tvToolbarTitle.setText("添加账户");
            binding.tvBalanceLabel.setText("账户余额");
            binding.etBalance.setHint("请输入账户余额");
            binding.cardDateInfo.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnSave.setOnClickListener(v -> saveAccount());
        
        binding.btnMoveGroup.setOnClickListener(v -> showSelectGroupDialog());

        binding.ivAccountIcon.setOnClickListener(v -> {
            if ("储蓄卡".equals(accountType) || "信用卡".equals(accountType)) {
                IconAccountBottomSheet sheet = new IconAccountBottomSheet();
                sheet.setOnlyBanks(true);
                sheet.setOnIconSelectedListener(icon -> {
                    iconRes = -1; // 不再使用本地资源
                    // 这里可能需要一个字段存储网络 URL
                    // ImageLoaderUtils.load(this, icon.getUrl(), binding.ivAccountIcon);
                    // 为了简单，我们先用 icon.getName() 更新名称，并保存 URL
                    binding.etAccountName.setText(icon.getName());
                    // 假设我们有一个变量保存选中的图标 URL
                    selectedIconUrl = icon.getUrl();
                    com.example.my_project1.utils.ImageLoaderUtils.loadThumbnail(this, icon.getUrl(), binding.ivAccountIcon);
                });
                sheet.show(getSupportFragmentManager(), "SelectBankIcon");
            }
        });

        // 日期选择使用 CustomDateTimePickerFragment
        binding.tvBillingDay.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (billingDay > 0) calendar.set(Calendar.DAY_OF_MONTH, billingDay);
            
            com.example.my_project1.ui.fragment.CustomDateTimePickerFragment.show(
                    getSupportFragmentManager(),
                    calendar,
                    selectedCalendar -> {
                        billingDay = selectedCalendar.get(Calendar.DAY_OF_MONTH);
                        binding.tvBillingDay.setText("每月" + billingDay + "日");
                        binding.tvBillingDay.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    }
            );
        });

        binding.tvRepaymentDay.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (repaymentDay > 0) calendar.set(Calendar.DAY_OF_MONTH, repaymentDay);

            com.example.my_project1.ui.fragment.CustomDateTimePickerFragment.show(
                    getSupportFragmentManager(),
                    calendar,
                    selectedCalendar -> {
                        repaymentDay = selectedCalendar.get(Calendar.DAY_OF_MONTH);
                        binding.tvRepaymentDay.setText("每月" + repaymentDay + "日");
                        binding.tvRepaymentDay.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    }
            );
        });
    }

    private void findDefaultGroup() {
        if (TextUtils.isEmpty(accountCategory)) return;

        viewModel.getAccountGroups().observe(this, groups -> {
            if (groups != null && selectedGroupId == null) {
                for (AccountGroup group : groups) {
                    if (group.getName().equals(accountCategory)) {
                        selectedGroupId = group.getObjectId();
                        binding.btnMoveGroup.setText("所属分组：" + group.getName());
                        break;
                    }
                }
            }
        });
    }

    private void showSelectGroupDialog() {
        viewModel.getAccountGroups().observe(this, groups -> {
            if (groups == null || groups.isEmpty()) {
                Toast.makeText(this, "暂无分组，请先创建", Toast.LENGTH_SHORT).show();
                return;
            }
            SelectGroupBottomSheetFragment fragment = SelectGroupBottomSheetFragment.newInstance(groups, selectedGroupId);
            fragment.setOnGroupSelectedListener(group -> {
                selectedGroupId = group.getObjectId();
                binding.btnMoveGroup.setText("所属分组：" + group.getName());
            });
            fragment.show(getSupportFragmentManager(), "SelectGroup");
        });
    }

    private void saveAccount() {
        String name = binding.etAccountName.getText().toString().trim();
        String balanceStr = binding.etBalance.getText().toString().trim();
        
        if (TextUtils.isEmpty(name)) {
            SnackbarUtils.showError(binding.getRoot(), "请输入账户名称");
            return;
        }

        // 🔴 移除强制选择账户组的限制，如果没有选择组，默认属于该大类
        /*
        if (selectedGroupId == null) {
            SnackbarUtils.showError(binding.getRoot(), "请选择所属分组");
            return;
        }
        */

        double balance = 0;
        try {
            balance = Double.parseDouble(balanceStr);
        } catch (Exception ignored) {}

        // 如果是信用账户，余额存为负数表示欠款
        if (isCreditType && balance > 0) {
            balance = -balance;
        }

        Account account = new Account();
        account.setName(name);
        account.setBalance(balance);
        account.setRemark(binding.etAccountRemark.getText().toString().trim());
        account.setCardNumber(binding.etCardNumber.getText().toString().trim());
        account.setGroupId(selectedGroupId);
        account.setAccountType(accountType);
        account.setCategory(accountCategory);
        account.setIconUrl(selectedIconUrl);
        account.setCredit(isCreditType);
        account.setIncludeInTotal(binding.switchIncludeTotal.isChecked());
        account.setCanBeSelected(binding.switchCanBeSelected.isChecked());
        
        // 信用账户特有字段
        if (isCreditType) {
            String limitStr = binding.etCreditLimit.getText().toString().trim();
            double limit = 0;
            try {
                limit = Double.parseDouble(limitStr);
            } catch (Exception ignored) {}
            account.setCreditLimit(limit);
            account.setIncludeBillInCurrentPeriod(binding.switchIncludeBill.isChecked());
            account.setBillingDay(billingDay);
            account.setRepaymentDay(repaymentDay);
        }

        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            account.setUserId(user.getObjectId());
        }

        viewModel.insertAccount(account, (success, message) -> {
            if (success) {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                SnackbarUtils.showError(binding.getRoot(), "保存失败: " + message);
            }
        });
    }
}
