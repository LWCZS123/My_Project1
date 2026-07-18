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
import com.example.my_project1.databinding.ItemAccountGroupBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.annotations.NonNull;

/**
 * AccountGroupAdapter
 * -----------------------------------------------------
 * 展示账户组及其子账户的折叠式列表适配器。
 * 支持点击展开组、加载子账户、编辑组、子项点击。
 */
public class AccountGroupAdapter extends RecyclerView.Adapter<AccountGroupAdapter.GroupViewHolder> {

    private static final String TAG = "AccountGroupAdapter";

    private final List<AccountGroup> groupList = new ArrayList<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private OnGroupActionClickListener groupActionClickListener;
    private final Map<String, List<Account>> groupAccounts = new HashMap<>();
    private String currentExpandedGroupId = null;

    // ---------------- 外部接口 ----------------
    public interface OnGroupActionClickListener {
        void onEditGroup(AccountGroup group);
        void onAccount(Account account);
        void onAccountDelete(Account account);
        void onAccountHide(Account account);
        void onAccountEdit(Account account);
        void onGroupExpand(String groupId); // 通知 Activity 加载账户
    }

    public String getCurrentExpandedGroupId() {
        return currentExpandedGroupId;
    }

    public AccountGroupAdapter() {}

    public void setOnGroupActionClickListener(OnGroupActionClickListener listener) {
        this.groupActionClickListener = listener;
    }

    public void setGroups(List<AccountGroup> groups) {
        groupList.clear();
        if (groups != null) groupList.addAll(groups);
        notifyDataSetChanged();
    }

    /**
     * 【关键修复】更新指定账户组的账户列表
     */
    public void updateAccountsForExpandedGroup(String groupId, List<Account> accounts) {
        Log.d(TAG, "📝 updateAccountsForExpandedGroup 被调用");
        Log.d(TAG, "   - 目标组ID: " + groupId);
        Log.d(TAG, "   - 账户数量: " + (accounts != null ? accounts.size() : "null"));
        Log.d(TAG, "   - 当前展开组ID: " + currentExpandedGroupId);

        // 保存账户数据到缓存（始终保存）
        List<Account> accountsCopy = accounts != null ? new ArrayList<>(accounts) : new ArrayList<>();
        groupAccounts.put(groupId, accountsCopy);

        Log.d(TAG, "✅ 已保存到缓存，准备刷新UI");

        // 查找对应的 position 并刷新（始终刷新找到的那一项）
        for (int i = 0; i < groupList.size(); i++) {
            if (groupList.get(i).getObjectId().equals(groupId)) {
                Log.d(TAG, "✅ 找到对应位置: " + i + "，调用 notifyItemChanged");
                notifyItemChanged(i);
                return;
            }
        }

        Log.w(TAG, "⚠️ 未找到对应的账户组位置");
    }

    // ---------------- Adapter 重写 ----------------
    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountGroupBinding binding = ItemAccountGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(groupList.get(position));
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    // ---------------- ViewHolder ----------------
    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountGroupBinding binding;
        private final AccountSubAdapter subAdapter;

        GroupViewHolder(ItemAccountGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.subAdapter = new AccountSubAdapter();

            // 设置 RecyclerView 的布局和适配器
            binding.rvSubAccounts.setLayoutManager(
                    new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvSubAccounts.setAdapter(subAdapter);

            // 禁用 RecyclerView 的回收机制，确保每次都刷新
            binding.rvSubAccounts.setItemViewCacheSize(0);
            binding.rvSubAccounts.setRecycledViewPool(new RecyclerView.RecycledViewPool());

            subAdapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
                @Override
                public void onAccountClick(Account account) {
                    if (groupActionClickListener != null)
                        groupActionClickListener.onAccount(account);
                }

                @Override
                public void onAccountDelete(Account account) {
                    if (groupActionClickListener != null)
                        groupActionClickListener.onAccountDelete(account);
                }

                @Override
                public void onAccountHide(Account account) {
                    if (groupActionClickListener != null)
                        groupActionClickListener.onAccountHide(account);
                }

                @Override
                public void onAccountEdit(Account account) {
                    if (groupActionClickListener != null)
                        groupActionClickListener.onAccountEdit(account);
                }
            });

            binding.layoutGroupActions.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && groupActionClickListener != null) {
                    groupActionClickListener.onEditGroup(groupList.get(pos));
                }
            });

            binding.layoutHeader.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                AccountGroup group = groupList.get(pos);
                boolean currentlyExpanded = expandedGroupIds.contains(group.getObjectId());

                if (currentlyExpanded) {
                    expandedGroupIds.remove(group.getObjectId());
                    collapseSection(binding.layoutAccountsContainer);
                    binding.ivArrow.animate().rotation(0f).setDuration(200).start();
                    currentExpandedGroupId = null;
                } else {
                    expandedGroupIds.add(group.getObjectId());
                    currentExpandedGroupId = group.getObjectId();
                    Log.d("AccountGroup", group.getObjectId().toString().trim());

                    if (groupActionClickListener != null)
                        groupActionClickListener.onGroupExpand(group.getObjectId()); // 通知 Activity 加载

                    // 【关键修复】在展开之前先设置数据，这样可以正确计算高度
                    List<Account> cachedAccounts = groupAccounts.get(group.getObjectId());
                    if (cachedAccounts != null) {
                        subAdapter.setAccounts(cachedAccounts);
                    } else {
                        subAdapter.setAccounts(new ArrayList<>());
                    }

                    expandSection(binding.layoutAccountsContainer);
                    binding.ivArrow.animate().rotation(90f).setDuration(200).start();
                }
            });
        }

        void bind(AccountGroup group) {
            Log.d(TAG, "🎨 bind() 被调用，组名: " + group.getName() + ", ID: " + group.getObjectId());

            binding.tvGroupName.setText(group.getName());

            binding.tvCount.setText(String.valueOf(group.getAccountCount()));

            if (group.getIconUrl() != null && !group.getIconUrl().isEmpty()) {
                binding.ivGroup.setVisibility(View.VISIBLE);
                Glide.with(binding.ivGroup.getContext())
                        .load(group.getIconUrl())
                        .placeholder(R.drawable.ic_cross)
                        .into(binding.ivGroup);
            } else {
                binding.ivGroup.setVisibility(View.GONE);
            }

            boolean isExpanded = expandedGroupIds.contains(group.getObjectId());
            Log.d(TAG, "   - 是否展开: " + isExpanded);

            binding.layoutAccountsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            binding.ivArrow.setRotation(isExpanded ? 90f : 0f);

            // 【关键修复】如果是展开状态，更新子账户列表
            if (isExpanded) {
                List<Account> accounts = groupAccounts.get(group.getObjectId());
                Log.d(TAG, "   - 从缓存获取账户数据: " + (accounts != null ? accounts.size() : "null"));
                if (accounts != null && !accounts.isEmpty()) {
                    Log.d(TAG, "🔄 设置账户到子列表，数量: " + accounts.size());
                    for (int i = 0; i < accounts.size(); i++) {
                        Log.d(TAG, "     - " + accounts.get(i).getName());
                    }
                    subAdapter.setAccounts(accounts);

                    // 【关键修复】确保容器有正确的高度
                    binding.layoutAccountsContainer.post(() -> {
                        if (binding.layoutAccountsContainer.getHeight() == 0 ||
                                binding.layoutAccountsContainer.getLayoutParams().height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                            binding.layoutAccountsContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            binding.layoutAccountsContainer.requestLayout();
                        }
                    });
                } else {
                    Log.d(TAG, "⚠️ 账户数据为空，显示空列表");
                    subAdapter.setAccounts(new ArrayList<>());
                }
            }
        }

        private void expandSection(View section) {
            // 【关键修复】先设置为 WRAP_CONTENT，让容器根据内容自适应高度
            section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            section.setVisibility(View.VISIBLE);

            // 强制测量以获取实际高度
            section.measure(
                    View.MeasureSpec.makeMeasureSpec(section.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int targetHeight = section.getMeasuredHeight();

            Log.d(TAG, "📏 expandSection - 目标高度: " + targetHeight);

            // 防止高度为0
            if (targetHeight == 0) {
                targetHeight = 200; // 默认高度
                Log.d(TAG, "⚠️ 测量高度为0，使用默认高度: " + targetHeight);
            }

            // 从0开始动画到目标高度
            section.getLayoutParams().height = 0;
            ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
            animator.addUpdateListener(animation -> {
                section.getLayoutParams().height = (int) animation.getAnimatedValue();
                section.requestLayout();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    // 【关键修复】动画结束后设置为 WRAP_CONTENT，确保内容变化时高度能自适应
                    section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    section.requestLayout();
                    Log.d(TAG, "✅ 展开动画完成，高度设置为 WRAP_CONTENT");
                }
            });
            animator.setDuration(250);
            animator.start();
        }

        private void collapseSection(View section) {
            int startHeight = section.getHeight();
            Log.d(TAG, "📏 collapseSection - 起始高度: " + startHeight);

            ValueAnimator animator = ValueAnimator.ofInt(startHeight, 0);
            animator.addUpdateListener(animation -> {
                section.getLayoutParams().height = (int) animation.getAnimatedValue();
                section.requestLayout();
            });
            animator.setDuration(250);
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    section.setVisibility(View.GONE);
                    Log.d(TAG, "✅ 折叠动画完成");
                }
            });
            animator.start();
        }
    }
}