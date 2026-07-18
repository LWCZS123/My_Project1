package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.FragmentAddAccountBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

public class AddAccountFragment extends BottomSheetDialogFragment {

    private static final String TAG = "AddAccountFragment";

    private FragmentAddAccountBinding binding;
    private AccountViewModel accountViewModel;

    // ---------- 参数 ----------
    private String accountId;
    private String accountName;
    private String accountGroupId;
    private String accountIconUrl;
    private double balance,creditLimit;
    private boolean isCredit;
    private String remark;
    private String cardNumber;

    private boolean isEdit = false;

    // 选择用
    private String selectedGroupId;
    private String selectedIconUrl;

    // ---------- 新增 ----------
    public static AddAccountFragment newInstance() {
        return new AddAccountFragment();
    }

    // ---------- 编辑 ----------
    public static AddAccountFragment newInstance(Account account) {
        AddAccountFragment fragment = new AddAccountFragment();
        Bundle args = new Bundle();

        args.putString("accountId", account.getObjectId());
        args.putString("accountName", account.getName());
        args.putString("accountGroupId", account.getGroupId());
        args.putString("accountIconUrl", account.getIconUrl());

        args.putDouble("balance", account.getBalance());
        args.putBoolean("isCredit", account.isCredit());
        args.putString("remark", account.getRemark());
        args.putString("cardNumber", account.getCardNumber());
        args.putDouble("creditLimit", account.getCreditLimit());

        fragment.setArguments(args);
        return fragment;
    }

    // ---------- onCreate：解析参数 ----------
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null && args.containsKey("accountId")) {

            isEdit = true;

            accountId = args.getString("accountId");
            accountName = args.getString("accountName");
            accountGroupId = args.getString("accountGroupId");
            accountIconUrl = args.getString("accountIconUrl");

            balance = args.getDouble("balance");
            isCredit = args.getBoolean("isCredit");
            remark = args.getString("remark");
            cardNumber = args.getString("cardNumber");
            creditLimit = args.getDouble("creditLimit");

            // 让 UI 选择时用
            selectedGroupId = accountGroupId;
            selectedIconUrl = accountIconUrl;

            Log.d(TAG, "编辑模式: " + accountId);
        }
    }

    // ---------- onCreateView ----------
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAddAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    // ---------- onViewCreated ----------
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        accountViewModel = new ViewModelProvider(requireActivity())
                .get(AccountViewModel.class);

        initUi();
        initClick();

        if (isEdit) fillEditData();
    }

    // ---------- 回显数据 ----------
    private void fillEditData() {

        binding.etAccountName.setText(accountName);
        binding.etDebtAmount.setText(String.valueOf(balance));
        binding.etRemark.setText(remark);
        binding.etCardNumber.setText(cardNumber);
        binding.switchCreditAccount.setChecked(isCredit);
        binding.etCreditLimit.setText(String.valueOf(creditLimit));
        accountViewModel.getAccountByGroupName(accountGroupId, (success, groupName) -> {
            if (success && groupName != null) {
                selectedGroupId = accountGroupId; // 确保选中的组ID也同步
                binding.tvGroupName.setText(groupName);
            } else {
                Log.e("AddAccountFragment", "获取账户组名称失败: " + accountGroupId);
            }
        });


        // 显示图标
        ImageLoaderUtils.load(requireActivity(), accountIconUrl, binding.imgSelectedIcon);

        // 设置标题
        binding.btnCreate.setText("更新账户");
        binding.tvTitle.setText("编辑账户");

        // 信用额度（如果是信用账户）
        if (isCredit) {
            binding.cardCreditLimit.setVisibility(View.VISIBLE);
        }
    }

    // ---------- 初始化 UI ----------
    private void initUi() {
        binding.switchCreditAccount.setTrackDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ios_switch_track));
        binding.switchCreditAccount.setThumbDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ios_switch_thumb));
    }

    // ---------- 点击事件 ----------
    private void initClick() {

        // --- 选择图标 ---
        binding.imgSelectedIcon.setOnClickListener(v -> {
            IconAccountBottomSheet sheet = new IconAccountBottomSheet();
            sheet.setOnIconSelectedListener(icon -> {
                selectedIconUrl = icon.getUrl();
                ImageLoaderUtils.load(requireActivity(), icon.getUrl(), binding.imgSelectedIcon);
                binding.etAccountName.setText(icon.getName());
            });
            sheet.show(getParentFragmentManager(), "icon_account_selector");
        });

        // --- 选择账户组 ---
        binding.tvGroupName.setOnClickListener(v -> {
            BmobUser user = BmobUser.getCurrentUser();
            if (user != null) {
                accountViewModel.loadAccountGroups(user.getObjectId());
                observeGroupList();
            } else {
                Toast.makeText(requireActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 信用卡开关 ---
        binding.switchCreditAccount.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                binding.tvDebtAmount.setText("当前欠款");
                binding.cardCreditLimit.setVisibility(View.VISIBLE);
            } else {
                binding.tvDebtAmount.setText("余额");
                binding.cardCreditLimit.setVisibility(View.GONE);
                binding.etCreditLimit.setText("0.00");
            }
        });

        // --- 提交 ---
        binding.btnCreate.setOnClickListener(v -> submit());
    }

    // ---------- 提交 ----------
    private void submit() {

        String name = binding.etAccountName.getText().toString().trim();
        String remarkStr = binding.etRemark.getText().toString().trim();
        String cardNum = binding.etCardNumber.getText().toString().trim();
        String balanceStr = binding.etDebtAmount.getText().toString().trim();
        String creditLimitStr = binding.etCreditLimit.getText().toString().trim();

        boolean credit = binding.switchCreditAccount.isChecked();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getActivity(), "请输入账户名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedGroupId == null) {
            Toast.makeText(getActivity(), "请选择账户组", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedIconUrl == null) {
            Toast.makeText(getActivity(), "请选择账户图标", Toast.LENGTH_SHORT).show();
            return;
        }

        // balance
        double balance = 0;
        try {
            balance = Double.parseDouble(balanceStr);
        } catch (Exception ignored) {}

        // 信用额度
        double creditLimit = 0;
        if (credit) {
            if (TextUtils.isEmpty(creditLimitStr)) {
                binding.etCreditLimit.setError("请输入信用额度");
                return;
            }
            try {
                creditLimit = Double.parseDouble(creditLimitStr);
            } catch (Exception e) {
                binding.etCreditLimit.setError("格式错误");
                return;
            }
        }

        if (credit && balance > 0) {
            balance = -balance;
        }

        BmobUser user = BmobUser.getCurrentUser();
        if (user == null) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // ----- 封装 Account -----
        Account account = new Account();
        account.setUserId(user.getObjectId());
        account.setName(name);
        account.setRemark(remarkStr);
        account.setCardNumber(cardNum);
        account.setGroupId(selectedGroupId);
        account.setIconUrl(selectedIconUrl);
        account.setBalance(balance);
        account.setCreditLimit(creditLimit);
        account.setCredit(credit);

        if (isEdit) {
            account.setObjectId(accountId);
            updateAccount(account);
        } else {
            addAccount(account);
        }
    }

    // ---------- 新增 ----------
    private void addAccount(Account account) {

        accountViewModel.insertAccount(account);
        Toast.makeText(getActivity(), "添加成功", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    // ---------- 更新 ----------
    private void updateAccount(Account account) {

        accountViewModel.updateAccount(account);
        Toast.makeText(getActivity(), "更新成功", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    // ---------- 监听账户组 ----------
    private void observeGroupList() {
        accountViewModel.getAccountGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups == null || groups.isEmpty()) {
                Toast.makeText(requireContext(), "暂无账户组，请创建", Toast.LENGTH_SHORT).show();
                return;
            }

            SelectGroupBottomSheetFragment sheet =
                    SelectGroupBottomSheetFragment.newInstance(groups, selectedGroupId);

            sheet.setOnGroupSelectedListener(group -> {
                selectedGroupId = group.getObjectId();
                binding.tvGroupName.setText(group.getName());
            });

            sheet.show(getParentFragmentManager(), "SelectGroup");
        });
    }

    // ---------- BottomSheet 圆角 + 高度处理 ----------
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.85);
                bottomSheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return dialog;
    }

    private int getScreenHeight() {
        return requireContext().getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
