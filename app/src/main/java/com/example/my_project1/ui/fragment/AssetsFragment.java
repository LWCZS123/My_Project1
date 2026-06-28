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
import com.example.my_project1.ui.activity.AccountChartActivity;
import com.example.my_project1.ui.activity.AccountDetailActivity;
import com.example.my_project1.ui.activity.AddAccountActivity;
import com.example.my_project1.ui.adapter.account.AssetsGroupAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

public class AssetsFragment extends Fragment {

    private static final String TAG = "AssetsFragment";
    private static final String TIME_TAG = "APP_LOAD_TIME";
    private long fragmentStartTime;
    private long dataLoadStartTime;

    private boolean isAmountHidden = false;

    private FragmentAssetsBinding binding;
    private AccountViewModel viewModel;
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
        fragmentStartTime = System.currentTimeMillis();
        Log.d(TIME_TAG, "==================== 【AssetsFragment】启动 ====================");
        Log.d(TIME_TAG, "AssetsFragment → onCreateView 开始");


        binding = FragmentAssetsBinding.inflate(inflater, container, false);
        return binding.getRoot();


    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TIME_TAG, "AssetsFragment → onViewCreated 开始");
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);


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

        binding.btnChart.setOnClickListener(v->{
            Intent intent = new Intent(requireContext(), AccountChartActivity.class);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.btnAddAccount.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddAccountActivity.class);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.ivEves.setOnClickListener(v -> {
            isAmountHidden = !isAmountHidden;

            binding.ivEves.setImageResource(
                    isAmountHidden ? R.drawable.ic_eye_close : R.drawable.ic_eye_open
            );

            if (isAmountHidden) {
                binding.tvNetAsset.setText("****");
                binding.tvTotalAsset.setText("****");
                binding.tvTotalDebt.setText("****");
            } else {
                binding.tvTotalAsset.setText(String.format("%.2f", totalPositiveAssets));
                binding.tvTotalDebt.setText(String.format("%.2f", totalNegativeAssets));
                animateNumber(binding.tvNetAsset, netAsset);
            }

            adapter.setAmountHidden(isAmountHidden);
        });

        long cost = System.currentTimeMillis() - fragmentStartTime;
        Log.d(TIME_TAG, "AssetsFragment → onViewCreated 完成，耗时：" + cost + "ms");
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
                Log.d(TAG, "编辑账户组: " + group.getName());
                // TODO: 实现编辑账户组功能
            }

            @Override
            public void onAccount(Account account) {
                Log.d(TAG, "查看账户: " + account.getName());
                Intent intent = new Intent(requireContext(), AccountDetailActivity.class);
                intent.putExtra(AccountDetailActivity.EXTRA_ACCOUNT_ID, account.getObjectId());
                startActivity(intent);
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onGroupExpand(String groupId) {
                Log.d(TAG, "🔄 展开账户组: " + groupId);
            }

        });
    }

    /**
     * 🔴 关键改进：清理旧的观察者并重新订阅
     */
    private void observeAllGroupAccounts(List<AccountGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            Log.d(TAG, "⚠️ 账户组列表为空");
            return;
        }

        Log.d(TAG, "🔄 开始为所有账户组设置观察者");

        for (AccountGroup group : groups) {
            if (group.getAccountCount() > 0) {
                String groupId = group.getObjectId();

                // 🔴 如果已经有观察者，先移除
                if (accountObservers.containsKey(groupId)) {
                    Observer<List<Account>> oldObserver = accountObservers.get(groupId);
                    viewModel.getAccountsByGroupId(groupId).removeObserver(oldObserver);
                    Log.d(TAG, "🗑️ 移除组 " + groupId + " 的旧观察者");
                }

                // 🔴 创建新的观察者
                Observer<List<Account>> observer = accounts -> {
                    int accountSize = (accounts != null ? accounts.size() : 0);
                    Log.d(TAG, "📢 组 " + group.getName() + " 的账户更新: " + accountSize + " 个账户");

                    // 立即更新适配器
                    if (accounts != null && !accounts.isEmpty()) {
                        adapter.updateAccountsForExpandedGroup(groupId, accounts);
                    } else {
                        adapter.updateAccountsForExpandedGroup(groupId, new java.util.ArrayList<>());
                    }

                    // 更新总金额
                    updateTotalAssets();
                };

                // 🔴 添加观察者并保存引用
                viewModel.getAccountsByGroupId(groupId).observe(getViewLifecycleOwner(), observer);
                accountObservers.put(groupId, observer);

                Log.d(TAG, "✅ 为组 " + group.getName() + " (" + groupId + ") 添加观察者");
            }
        }

        Log.d(TAG, "✅ 共为 " + accountObservers.size() + " 个账户组设置了观察者");
    }

    /**
     * 🔴 新增：清理所有账户观察者
     */
    private void clearAllObservers() {
        if (!accountObservers.isEmpty()) {
            Log.d(TAG, "🧹 清理 " + accountObservers.size() + " 个旧观察者");
            for (Map.Entry<String, Observer<List<Account>>> entry : accountObservers.entrySet()) {
                viewModel.getAccountsByGroupId(entry.getKey()).removeObserver(entry.getValue());
            }
            accountObservers.clear();
        }
    }

    private void observeData() {
        // 🔥 观察账户组列表
        viewModel.getAccountGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups != null) {
                int validGroupCount = 0;
                for (AccountGroup group : groups) {
                    if (group.getAccountCount() > 0) {
                        validGroupCount++;
                    }
                }

                Log.d(TAG, "✅ 账户组更新: 总共 " + groups.size() + " 个, 有效 " + validGroupCount + " 个");

                // 更新适配器
                adapter.setGroups(groups);
                clearAllObservers();
                observeAllGroupAccounts(groups);

                long totalCost = System.currentTimeMillis() - fragmentStartTime;
                Log.d(TIME_TAG, "==================================================");
                Log.d(TIME_TAG, "【AssetsFragment】数据完全加载完成 → 总耗时：" + totalCost + "ms");
                Log.d(TIME_TAG, "==================================================");
            } else {
                Log.d(TAG, "⚠️ 账户组数据为 null");
            }
        });

        // 🔥 观察账户组更新通知(用于实时刷新)
        viewModel.groupAccountsUpdate.observe(getViewLifecycleOwner(), updateMap -> {
            if (updateMap != null && !updateMap.isEmpty()) {
                for (Map.Entry<String, List<Account>> entry : updateMap.entrySet()) {
                    String groupId = entry.getKey();
                    List<Account> accounts = entry.getValue();

                    Log.d(TAG, "📢 收到组 " + groupId + " 的更新通知");
                    Log.d(TAG, "   - 账户数量: " + (accounts != null ? accounts.size() : 0));

                    if (accounts != null) {
                        adapter.updateAccountsForExpandedGroup(groupId, accounts);
                    }

                    updateTotalAssets();
                }
            }
        });
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
            binding.tvTotalAsset.setText(String.format("%.2f", totalPositiveAssets));
            binding.tvTotalDebt.setText(String.format("%.2f", totalNegativeAssets));
            binding.tvNetAsset.setText("¥" + String.format("%.2f", netAsset));
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
        Log.d(TAG, "📱 onResume - 触发云同步");
        Log.d(TIME_TAG, "AssetsFragment → onResume");
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

        Log.d(TIME_TAG, "AssetsFragment → onDestroyView");
    }
}