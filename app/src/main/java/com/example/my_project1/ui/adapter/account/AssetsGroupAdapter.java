package com.example.my_project1.ui.adapter.account;

import android.animation.ValueAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAssetGroupBinding;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.annotations.NonNull;

public class AssetsGroupAdapter extends RecyclerView.Adapter<AssetsGroupAdapter.GroupViewHolder> {

    private static final String TAG = "AssetsGroupAdapter";

    private boolean isAmountHidden = false;

    private final List<AccountGroup> groupList = new ArrayList<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private OnGroupActionClickListener groupActionClickListener;

    // 🔴 存储所有账户组的账户数据（无论是否展开）
    private final Map<String, List<Account>> groupAccounts = new HashMap<>();

    // 🔴 存储每个账户组的总金额缓存
    private final Map<String, Double> groupTotalAmounts = new HashMap<>();

    // 🔴 新增：存储动画状态，避免重复触发
    private final Set<String> animatingGroups = new HashSet<>();

    public interface OnGroupActionClickListener {
        void onEditGroup(AccountGroup group);
        void onAccount(Account account);
        void onGroupExpand(String groupId);
    }

    public void setOnGroupActionClickListener(OnGroupActionClickListener listener) {
        this.groupActionClickListener = listener;
    }

    /**
     * 🔴 改进：过滤掉账户数量为0的账户组
     */
    public void setGroups(List<AccountGroup> groups) {
        groupList.clear();
        if (groups != null) {
            for (AccountGroup group : groups) {
                // 只添加账户数量大于0的组
                if (group.getAccountCount() > 0) {
                    groupList.add(group);
                }
            }
        }
        Log.d(TAG, "📋 过滤后的账户组数量: " + groupList.size() + " (原始: " + (groups != null ? groups.size() : 0) + ")");
        notifyDataSetChanged();
    }

    public void setAmountHidden(boolean hidden) {
        this.isAmountHidden = hidden;
        notifyDataSetChanged();
    }

    /**
     * 🔴 更新账户数据并自动计算总金额
     */
    public void updateAccountsForExpandedGroup(String groupId, List<Account> accounts) {
        List<Account> copy = accounts != null ? new ArrayList<>(accounts) : new ArrayList<>();
        groupAccounts.put(groupId, copy);

        // 🔴 关键：立即计算并缓存总金额
        double totalAmount = calculateTotalAmount(copy);
        groupTotalAmounts.put(groupId, totalAmount);

        Log.d(TAG, "✅ 更新组 " + groupId + " 的账户数据: " +
                copy.size() + " 个账户, 总金额: ¥" + totalAmount);

        // 🔴 关键：查找并更新对应的 ViewHolder
        for (int i = 0; i < groupList.size(); i++) {
            if (groupList.get(i).getObjectId().equals(groupId)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    /**
     * 🔴 计算账户列表的总金额
     */
    private double calculateTotalAmount(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (Account account : accounts) {
            total += account.getBalance();
        }
        return total;
    }

    /**
     * 🔴 获取账户组的总金额（优先使用缓存）
     */
    public double getGroupTotalAmount(String groupId) {
        // 优先返回缓存的总金额
        if (groupTotalAmounts.containsKey(groupId)) {
            return groupTotalAmounts.get(groupId);
        }

        // 如果没有缓存，从账户数据计算
        List<Account> accounts = groupAccounts.get(groupId);
        if (accounts != null) {
            double total = calculateTotalAmount(accounts);
            groupTotalAmounts.put(groupId, total);
            return total;
        }

        return 0.0;
    }

    /**
     * 🔴 新增：获取所有正数账户余额总和（总资产）
     */
    public double getTotalPositiveAssets() {
        double total = 0.0;
        for (List<Account> accounts : groupAccounts.values()) {
            if (accounts != null) {
                for (Account account : accounts) {
                    if (account.getBalance() > 0) {
                        total += account.getBalance();
                    }
                }
            }
        }
        return total;
    }

    /**
     * 🔴 新增：获取所有负数账户余额总和的绝对值（总负债）
     */
    public double getTotalNegativeAssets() {
        double total = 0.0;
        for (List<Account> accounts : groupAccounts.values()) {
            if (accounts != null) {
                for (Account account : accounts) {
                    if (account.getBalance() < 0) {
                        total += Math.abs(account.getBalance());
                    }
                }
            }
        }
        return total;
    }

    /**
     * 🔴 新增：获取净资产（总资产 - 总负债）
     */
    public double getNetAssets() {
        double positiveAssets = getTotalPositiveAssets();
        double negativeAssets = getTotalNegativeAssets();
        return positiveAssets - negativeAssets;
    }

    /**
     * 🔴 原有方法：获取所有账户组的总金额（包含正负）
     */
    public double getTotalAssets() {
        double total = 0.0;
        for (AccountGroup group : groupList) {
            total += getGroupTotalAmount(group.getObjectId());
        }
        return total;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAssetGroupBinding binding =
                ItemAssetGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(groupList.get(position));
    }

    @Override
    public int getItemCount() {
        Log.d(TAG, "getItemCount: "+groupList.size());
        return groupList.size();
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final ItemAssetGroupBinding binding;
        private final AccountSubAdapter subAdapter;
        private ValueAnimator currentAnimator; // 🔴 保存当前动画引用

        GroupViewHolder(ItemAssetGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.subAdapter = new AccountSubAdapter();

            // 🔴 性能优化：使用 LinearLayoutManager 的性能优化
            LinearLayoutManager layoutManager = new LinearLayoutManager(binding.getRoot().getContext());
            layoutManager.setInitialPrefetchItemCount(4); // 预加载4个item
            binding.rvSubAccounts.setLayoutManager(layoutManager);
            binding.rvSubAccounts.setAdapter(subAdapter);

            //增加缓存池大小
            binding.rvSubAccounts.setItemViewCacheSize(10);
            binding.rvSubAccounts.setHasFixedSize(true);

            //
            binding.rvSubAccounts.setNestedScrollingEnabled(false);

            subAdapter.setOnAccountClickListener(account -> {
                if (groupActionClickListener != null)
                    groupActionClickListener.onAccount(account);
            });

            // 编辑组
            binding.layoutGroupActions.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && groupActionClickListener != null) {
                    groupActionClickListener.onEditGroup(groupList.get(pos));
                }
            });

            // 展开/折叠
            binding.layoutHeader.setOnClickListener(v -> toggleExpand());
        }

        void toggleExpand() {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            AccountGroup group = groupList.get(pos);
            String groupId = group.getObjectId();

            //防止动画期间重复点击
            if (animatingGroups.contains(groupId)) {
                Log.d(TAG, "⚠️ 动画进行中，忽略点击");
                return;
            }

            boolean expanded = expandedGroupIds.contains(groupId);

            if (expanded) {
                // 折叠
                expandedGroupIds.remove(groupId);
                collapseSection(binding.layoutAccountsContainer, groupId);
                binding.ivArrow.animate().rotation(0f).setDuration(200).start();
            } else {
                // 展开
                expandedGroupIds.add(groupId);

                // 🔴 关键：通知Fragment加载该组的账户数据
                if (groupActionClickListener != null) {
                    groupActionClickListener.onGroupExpand(groupId);
                }

                // 🔴 改进：立即显示已缓存的账户数据
                List<Account> cached = groupAccounts.get(groupId);
                subAdapter.setAccounts(cached != null ? cached : new ArrayList<>());

                expandSection(binding.layoutAccountsContainer, groupId);
                binding.ivArrow.animate().rotation(90f).setDuration(200).start();
            }
        }

        void bind(AccountGroup group) {
            String groupId = group.getObjectId();

            binding.tvGroupName.setText(group.getName());
            binding.tvCount.setText(String.valueOf(group.getAccountCount()));

            // 图标
            if (group.getIconUrl() != null && !group.getIconUrl().isEmpty()) {
                binding.ivGroup.setVisibility(View.VISIBLE);
                Glide.with(binding.ivGroup.getContext())
                        .load(group.getIconUrl())
                        .placeholder(R.drawable.ic_cross)
                        .into(binding.ivGroup);
            } else {
                binding.ivGroup.setVisibility(View.GONE);
            }

            // 展开/折叠状态
            boolean expanded = expandedGroupIds.contains(groupId);
            binding.layoutAccountsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.ivArrow.setRotation(expanded ? 90f : 0f);

            // 🔴 关键改进：总金额实时更新
            double totalBalance = getGroupTotalAmount(groupId);

            if (isAmountHidden) {
                binding.tvTotalAmount.setText("****");
            } else {
                binding.tvTotalAmount.setText(formatMoney(totalBalance));
            }

            subAdapter.setAmountHidden(isAmountHidden);

            // 如果已展开，更新子账户列表
            if (expanded) {
                List<Account> accounts = groupAccounts.get(groupId);
                subAdapter.setAccounts(accounts != null ? accounts : new ArrayList<>());
            }
        }

        private String formatMoney(double amount) {
            DecimalFormat formatter = new DecimalFormat("#,##0.00");
            return "¥" + formatter.format(amount);
        }

        /**
         * 🔴 优化版展开动画
         */
        private void expandSection(View section, String groupId) {
            // 🔴 取消之前的动画
            if (currentAnimator != null && currentAnimator.isRunning()) {
                currentAnimator.cancel();
            }

            animatingGroups.add(groupId);

            // 🔴 优化：先设置为可见再测量
            section.setVisibility(View.VISIBLE);
            section.getLayoutParams().height = 0;

            // 🔴 强制立即布局，获取准确高度
            section.post(() -> {
                section.measure(
                        View.MeasureSpec.makeMeasureSpec(section.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );

                int targetHeight = section.getMeasuredHeight();
                if (targetHeight == 0) targetHeight = 300; // 默认高度

                currentAnimator = ValueAnimator.ofInt(0, targetHeight);
                currentAnimator.setDuration(250); // 缩短动画时间
                currentAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator()); // 使用更流畅的插值器

                currentAnimator.addUpdateListener(animation -> {
                    section.getLayoutParams().height = (int) animation.getAnimatedValue();
                    section.requestLayout();
                });

                currentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        section.requestLayout();
                        animatingGroups.remove(groupId);
                        currentAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        animatingGroups.remove(groupId);
                        currentAnimator = null;
                    }
                });

                currentAnimator.start();
            });
        }

        /**
         * 🔴 优化版折叠动画
         */
        private void collapseSection(View section, String groupId) {
            // 🔴 取消之前的动画
            if (currentAnimator != null && currentAnimator.isRunning()) {
                currentAnimator.cancel();
            }

            animatingGroups.add(groupId);

            int startHeight = section.getHeight();
            if (startHeight == 0) {
                section.setVisibility(View.GONE);
                animatingGroups.remove(groupId);
                return;
            }

            currentAnimator = ValueAnimator.ofInt(startHeight, 0);
            currentAnimator.setDuration(250);
            currentAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator()); // 使用加速插值器

            currentAnimator.addUpdateListener(animation -> {
                section.getLayoutParams().height = (int) animation.getAnimatedValue();
                section.requestLayout();
            });

            currentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    section.setVisibility(View.GONE);
                    section.getLayoutParams().height = 0;
                    animatingGroups.remove(groupId);
                    currentAnimator = null;
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    animatingGroups.remove(groupId);
                    currentAnimator = null;
                }
            });

            currentAnimator.start();
        }
    }

    /**
     * 🔴 清理缓存数据
     */
    public void clearCache() {
        groupAccounts.clear();
        groupTotalAmounts.clear();
        expandedGroupIds.clear();
        animatingGroups.clear();
    }
}