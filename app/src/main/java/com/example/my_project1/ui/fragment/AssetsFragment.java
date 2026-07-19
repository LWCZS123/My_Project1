package com.example.my_project1.ui.fragment;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentAssetsBinding;
import com.example.my_project1.ui.activity.AccountDetailActivity;
import com.example.my_project1.ui.activity.AccountGroupActivity;
import com.example.my_project1.ui.activity.ArchivedAccountsActivity;
import com.example.my_project1.ui.activity.HiddenAccountsActivity;
import com.example.my_project1.ui.adapter.account.AssetsGroupAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.user.UserProfileViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

public class AssetsFragment extends Fragment {

    private static final String TAG = "AssetsFragment";

    private boolean isAmountHidden = false;

    private FragmentAssetsBinding binding;
    private AccountViewModel viewModel;
    private UserProfileViewModel userViewModel;
    private AssetsGroupAdapter adapter;
    private double totalPositiveAssets;
    private double totalNegativeAssets;
    private double netAsset;


    private Map<String, Observer<List<Account>>> accountObservers = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAssetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        userViewModel = new ViewModelProvider(requireActivity()).get(UserProfileViewModel.class);


        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        setupRecyclerView();

        //先清理可能存在的旧观察者
        clearAllObservers();

        // 设置观察者，然后加载数据
        observeData();
        loadAccountGroups();
        loadUserAvatar();

        binding.btnAddAccountTop.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.example.my_project1.ui.activity.ChooseAccountTypeActivity.class);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
        
        // 如果有 fabAdd 按钮也统一逻辑
        try {
            View fabAdd = binding.getRoot().findViewById(R.id.fab_add);
            if (fabAdd != null) {
                fabAdd.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), com.example.my_project1.ui.activity.ChooseAccountTypeActivity.class);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    }
                });
            }
        } catch (Exception e) {}

        binding.btnMore.setOnClickListener(v -> showMoreMenu());

        binding.ivEves.setOnClickListener(v -> {
            isAmountHidden = !isAmountHidden;

            binding.ivEves.setImageResource(
                    isAmountHidden ? R.drawable.ic_eye_close : R.drawable.ic_eye_open
            );

            if (isAmountHidden) {
                binding.tvNetAsset.setText("****");
                binding.tvTotalAsset.setText("****");
                binding.tvTotalDebt.setText("****");
                binding.tvTotalBorrowed.setText("****");
                binding.tvTotalLent.setText("****");
            } else {
                binding.tvTotalAsset.setText(String.format("¥%,.2f", totalPositiveAssets));
                binding.tvTotalDebt.setText(String.format("¥%,.2f", totalNegativeAssets));
                binding.tvTotalBorrowed.setText(String.format("¥%,.2f", totalNegativeAssets));
                binding.tvTotalLent.setText("¥0.00");
                animateNumber(binding.tvNetAsset, netAsset);
            }

            adapter.setAmountHidden(isAmountHidden);
        });
    }

    private void showMoreMenu() {
        com.example.my_project1.ui.popup.AssetsMorePopup popup = new com.example.my_project1.ui.popup.AssetsMorePopup(requireContext(), new com.example.my_project1.ui.popup.AssetsMorePopup.OnOptionClickListener() {
            @Override
            public void onAssetGrouping() {
                Intent intent = new Intent(requireContext(), AccountGroupActivity.class);
                startActivity(intent);
            }

            @Override
            public void onArchivedAccounts() {
                Intent intent = new Intent(requireContext(), ArchivedAccountsActivity.class);
                startActivity(intent);
            }

            @Override
            public void onHiddenAccounts() {
                Intent intent = new Intent(requireContext(), HiddenAccountsActivity.class);
                startActivity(intent);
            }
        });

        // 获取按钮在屏幕上的位置
        int[] location = new int[2];
        binding.btnMore.getLocationOnScreen(location);

        // 计算弹出位置，使其出现在按钮下方并右对齐
        popup.showAsDropDown(binding.btnMore, -100, 0);
    }

    private void setupRecyclerView() {
        adapter = new AssetsGroupAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setInitialPrefetchItemCount(5);

        binding.rvAccounts.setLayoutManager(layoutManager);
        binding.rvAccounts.setAdapter(adapter);
        binding.rvAccounts.setHasFixedSize(false);
        binding.rvAccounts.setItemViewCacheSize(10);

        adapter.setOnGroupActionClickListener(new AssetsGroupAdapter.OnGroupActionClickListener() {
            @Override
            public void onEditGroup(AccountGroup group) {
                // TODO: 实现编辑账户组功能
            }

            @Override
            public void onAccount(Account account) {
                Intent intent = new Intent(requireContext(), AccountDetailActivity.class);
                intent.putExtra(AccountDetailActivity.EXTRA_ACCOUNT_ID, account.getObjectId());
                intent.putExtra(AccountDetailActivity.EXTRA_ACCOUNT_LOCAL_ID, account.getId());
                startActivity(intent);
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onAccountDelete(Account account) {
                if (account == null) return;
                
                // 弹出确认对话框
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("删除账户")
                    .setMessage("确定要删除账户「" + account.getName() + "」吗？这将标记账户为待删除状态并同步到云端。")
                    .setPositiveButton("删除", (dialog, which) -> {
                        viewModel.deleteAccount(account, (success, message) -> {
                            if (success) {
                                SnackbarUtils.showSuccess(binding.getRoot(), "已标记删除账户");
                            } else {
                                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
                            }
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }

            @Override
            public void onAccountHide(Account account) {
                account.setIncludeInTotal(false);
                viewModel.updateAccount(account);
                // 🔕 移除提示，按用户要求静默隐藏
            }

            @Override
            public void onAccountArchive(Account account) {
                account.setCanBeSelected(false);
                viewModel.updateAccount(account);
                SnackbarUtils.showInfo(binding.getRoot(), "已归档账户: " + account.getName());
            }

            @Override
            public void onAccountEdit(Account account) {
                Intent intent = new Intent(requireContext(), com.example.my_project1.ui.activity.AddAccountActivity.class);
                intent.putExtra("editAccount", account);
                startActivity(intent);
            }

            @Override
            public void onGroupExpand(String groupId) {
                Log.d(TAG, "🔄 展开账户组: " + groupId);
            }

        });
    }

    /**
     * 🔴 新增：清理所有账户观察者
     */
    private void clearAllObservers() {
        if (!accountObservers.isEmpty()) {
            // 注意：现在由于使用了 MediatorLiveData 和 getAllAccounts，
            // 这里的 accountObservers 逻辑可能需要调整，或者直接移除。
            // 目前先保持空，因为新的 observeData 不再填充这个 Map。
            accountObservers.clear();
        }
    }

    private void observeData() {
        // 使用 MediatorLiveData 合并账户组和所有账户的观察
        androidx.lifecycle.MediatorLiveData<CombinedData> combinedLiveData = new androidx.lifecycle.MediatorLiveData<>();
        
        combinedLiveData.addSource(viewModel.getAccountGroups(), groups -> {
            CombinedData current = combinedLiveData.getValue();
            if (current == null) current = new CombinedData();
            current.groups = groups;
            combinedLiveData.setValue(current);
        });
        
        combinedLiveData.addSource(viewModel.getAllAccounts(), accounts -> {
            CombinedData current = combinedLiveData.getValue();
            if (current == null) current = new CombinedData();
            current.accounts = accounts;
            combinedLiveData.setValue(current);
        });

        combinedLiveData.observe(getViewLifecycleOwner(), data -> {
            if (data.groups != null && data.accounts != null) {
                processCombinedData(data.groups, data.accounts);
            }
        });
    }

    private static class CombinedData {
        List<AccountGroup> groups;
        List<Account> accounts;
    }

    private void processCombinedData(List<AccountGroup> groups, List<Account> allAccounts) {
        List<AccountGroup> displayGroups = new java.util.ArrayList<>();
        Map<String, List<Account>> groupToAccountsMap = new HashMap<>();
        
        // 0. 过滤掉不计入总资产（已隐藏）的账户
        List<Account> visibleAccounts = new ArrayList<>();
        for (Account acc : allAccounts) {
            if (acc.isIncludeInTotal()) {
                visibleAccounts.add(acc);
            }
        }
        allAccounts = visibleAccounts;

        // 1. 先将所有账户按 groupId 分组 (O(A))
        Map<String, List<Account>> tempMap = new HashMap<>();
        Map<String, List<Account>> categoryMap = new HashMap<>();
        
        for (Account acc : allAccounts) {
            String gid = acc.getGroupId();
            if (gid != null && !gid.isEmpty()) {
                if (!tempMap.containsKey(gid)) tempMap.put(gid, new ArrayList<>());
                tempMap.get(gid).add(acc);
            } else {
                String cat = acc.getCategory();
                if (cat == null) cat = "其他";
                if (!categoryMap.containsKey(cat)) categoryMap.put(cat, new ArrayList<>());
                categoryMap.get(cat).add(acc);
            }
        }

        // 2. 处理真实的账户组 (O(G))
        for (AccountGroup group : groups) {
            List<Account> accountsInGroup = tempMap.get(group.getObjectId());
            if (accountsInGroup != null && !accountsInGroup.isEmpty()) {
                // 🔑 修复点：确保显示的账户组数量仅包含可见账户（实时同步）
                group.setAccountCount(accountsInGroup.size());
                displayGroups.add(group);
                groupToAccountsMap.put(group.getObjectId(), accountsInGroup);
            }
        }

        // 3. 处理没有分组的账户，按大类归类为“虚拟分组”
        for (Map.Entry<String, List<Account>> entry : categoryMap.entrySet()) {
            AccountGroup virtualGroup = new AccountGroup();
            String catKey = "CATEGORY_" + entry.getKey();
            virtualGroup.setObjectId(catKey);
            virtualGroup.setName(entry.getKey());
            virtualGroup.setAccountCount(entry.getValue().size());
            
            displayGroups.add(virtualGroup);
            groupToAccountsMap.put(catKey, entry.getValue());
        }

        Log.d(TAG, "✅ 数据合并完成: 总显示分组 " + displayGroups.size());
        
        adapter.setGroups(displayGroups);
        for (Map.Entry<String, List<Account>> entry : groupToAccountsMap.entrySet()) {
            adapter.updateAccountsForExpandedGroup(entry.getKey(), entry.getValue());
        }
        updateTotalAssets();
    }

    /**
     * 🔴 改进:正确计算总资产、总负债和净资产
     */
    private void updateTotalAssets() {
        totalPositiveAssets = adapter.getTotalPositiveAssets();
        totalNegativeAssets = adapter.getTotalNegativeAssets();
        netAsset = totalPositiveAssets - totalNegativeAssets;

        Log.d(TAG, "💰 更新财务数据:");
        Log.d(TAG, "   - 总资产: ¥" + String.format("%.2f", totalPositiveAssets));
        Log.d(TAG, "   - 总负债: ¥" + String.format("%.2f", totalNegativeAssets));
        Log.d(TAG, "   - 净资产: ¥" + String.format("%.2f", netAsset));

        if (!isAmountHidden) {
            binding.tvTotalAsset.setText(String.format("¥%,.2f", totalPositiveAssets));
            binding.tvTotalDebt.setText(String.format("¥%,.2f", totalNegativeAssets));
            binding.tvNetAsset.setText(String.format("¥%,.2f", netAsset));
            binding.tvTotalBorrowed.setText(String.format("¥%,.2f", totalNegativeAssets));
            binding.tvTotalLent.setText("¥0.00"); // 暂无专门的借出统计，可根据业务扩展
        }
    }

    private void loadUserAvatar() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            userViewModel.getUserProfile(currentUser.getObjectId()).observe(getViewLifecycleOwner(), profile -> {
                if (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                    String avatarUrl = profile.getAvatarUrl();
                    String fullUrl = avatarUrl.startsWith("http") ? avatarUrl : "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/" + avatarUrl;
                    ImageLoaderUtils.loadThumbnail(requireContext(), fullUrl, binding.ivUserAvatar);
                }
            });
        }
    }

    /**
     * 🔴 改进：加载账户组数据，并强制刷新
     */
    private void loadAccountGroups() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getObjectId();
            Log.d(TAG, "🚀 开始加载用户账户组: " + userId);

            // 🔴 关键：先触发云同步，确保数据是最新的
            viewModel.syncFromCloud();

            // 然后加载账户组
            viewModel.loadAccountGroups(userId);
        } else {
            Log.d(TAG, "❌ 用户未登录");
        }
    }

    /**
     * 数字动画
     */
    private void animateNumber(TextView textView, double targetValue) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) targetValue);
        animator.setDuration(800);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            textView.setText(String.format("¥%.2f", value));
        });
        animator.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 🔴 onResume 时重新加载数据
        viewModel.syncFromCloud();

        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            viewModel.loadAccountGroups(currentUser.getObjectId());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 🔴 清理所有观察者
        clearAllObservers();

        binding = null;
    }
}
