package com.example.my_project1.ui.adapter.account;

import android.animation.ValueAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAccountGroupBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountGroupUiModel;
import com.example.my_project1.ui.viewmodel.accountvm.AccountUiModel;

import java.text.DecimalFormat;
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
public class AccountGroupAdapter extends ListAdapter<AccountGroupUiModel, AccountGroupAdapter.GroupViewHolder> {

    private static final String TAG = "AccountGroupAdapter";

    private OnGroupActionClickListener groupActionClickListener;
    
    // ── 临时存储状态，用于兼容旧的 Activity 调用 ──────────────────
    private List<AccountGroup> rawGroups = new ArrayList<>();
    private final Map<String, List<Account>> groupAccountsMap = new HashMap<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private String currentExpandedGroupId = null;
    // ────────────────────────────────────────────────────────────

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

    public AccountGroupAdapter() {
        super(new DiffUtil.ItemCallback<AccountGroupUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountGroupUiModel oldItem, @NonNull AccountGroupUiModel newItem) {
                return oldItem.id == newItem.id || (oldItem.objectId != null && oldItem.objectId.equals(newItem.objectId));
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountGroupUiModel oldItem, @NonNull AccountGroupUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    public void setOnGroupActionClickListener(OnGroupActionClickListener listener) {
        this.groupActionClickListener = listener;
    }

    public void setGroups(List<AccountGroup> groups) {
        this.rawGroups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
        rebuildUiModels();
    }

    /**
     * 【关键修复】更新指定账户组的账户列表
     */
    public void updateAccountsForExpandedGroup(String groupId, List<Account> accounts) {
        groupAccountsMap.put(groupId, accounts != null ? new ArrayList<>(accounts) : new ArrayList<>());
        rebuildUiModels();
    }

    private void rebuildUiModels() {
        List<AccountGroupUiModel> uiModels = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        for (AccountGroup group : rawGroups) {
            String groupId = group.getObjectId();
            List<Account> accounts = groupAccountsMap.get(groupId);
            
            List<AccountUiModel> subUiModels = new ArrayList<>();
            if (accounts != null) {
                for (Account acc : accounts) {
                    subUiModels.add(new AccountUiModel(
                            acc.getId(),
                            acc.getObjectId(),
                            acc.getName(),
                            acc.getAccountType() != null ? acc.getAccountType() : acc.getRemark(),
                            "¥" + df.format(acc.getBalance()),
                            acc.getBalance() < 0 ? 0xFFFF6B6B : 0xFF333333,
                            "可用额度 ¥" + df.format(acc.getCreditLimit() + acc.getBalance()),
                            acc.isCredit(),
                            acc.getIconUrl(),
                            false,
                            true,
                            acc
                    ));
                }
            }

            uiModels.add(new AccountGroupUiModel(
                    group.getId(),
                    groupId,
                    group.getName(),
                    String.valueOf(group.getAccountCount()),
                    "", "", false, // Not used in this item layout but part of model
                    expandedGroupIds.contains(groupId),
                    subUiModels,
                    false,
                    group
            ));
        }
        submitList(uiModels);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountGroupBinding binding = ItemAccountGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public int getItemCount() {
        return getCurrentList().size();
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
                public void onAccountArchive(Account account) {

                }

                @Override
                public void onAccountEdit(Account account) {
                    if (groupActionClickListener != null)
                        groupActionClickListener.onAccountEdit(account);
                }
            });

            binding.layoutGroupActions.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && groupActionClickListener != null) {
                    groupActionClickListener.onEditGroup(getItem(pos).originalGroup);
                }
            });

            binding.layoutHeader.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                AccountGroupUiModel uiModel = getItem(pos);
                String groupId = uiModel.objectId;
                boolean currentlyExpanded = expandedGroupIds.contains(groupId);

                if (currentlyExpanded) {
                    expandedGroupIds.remove(groupId);
                    collapseSection(binding.layoutAccountsContainer);
                    binding.ivArrow.animate().rotation(0f).setDuration(200).start();
                    currentExpandedGroupId = null;
                } else {
                    expandedGroupIds.add(groupId);
                    currentExpandedGroupId = groupId;

                    if (groupActionClickListener != null)
                        groupActionClickListener.onGroupExpand(groupId); // 通知 Activity 加载

                    // 【关键修复】在展开之前先设置数据，这样可以正确计算高度
                    List<Account> cachedAccounts = groupAccountsMap.get(groupId);
                    if (cachedAccounts != null) {
                        subAdapter.setAccounts(cachedAccounts);
                    } else {
                        subAdapter.setAccounts(new ArrayList<>());
                    }

                    expandSection(binding.layoutAccountsContainer);
                    binding.ivArrow.animate().rotation(90f).setDuration(200).start();
                }
                rebuildUiModels();
            });
        }

        void bind(AccountGroupUiModel uiModel) {
            binding.tvGroupName.setText(uiModel.name);
            binding.tvCount.setText(uiModel.countText);

            if (uiModel.originalGroup.getIconUrl() != null && !uiModel.originalGroup.getIconUrl().isEmpty()) {
                binding.ivGroup.setVisibility(View.VISIBLE);
                Glide.with(binding.ivGroup.getContext())
                        .load(uiModel.originalGroup.getIconUrl())
                        .placeholder(R.drawable.ic_cross)
                        .into(binding.ivGroup);
            } else {
                binding.ivGroup.setVisibility(View.GONE);
            }

            boolean isExpanded = uiModel.isExpanded;
            binding.layoutAccountsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            binding.ivArrow.setRotation(isExpanded ? 90f : 0f);

            // 【关键修复】如果是展开状态，更新子账户列表
            if (isExpanded) {
                subAdapter.submitList(uiModel.accounts);

                // 【关键修复】确保容器有正确的高度
                binding.layoutAccountsContainer.post(() -> {
                    if (binding.layoutAccountsContainer.getHeight() == 0 ||
                            binding.layoutAccountsContainer.getLayoutParams().height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        binding.layoutAccountsContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        binding.layoutAccountsContainer.requestLayout();
                    }
                });
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