package com.example.my_project1.ui.adapter.account;

import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAssetGroupBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountGroupUiModel;
import com.example.my_project1.ui.viewmodel.accountvm.AccountUiModel;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AssetsGroupAdapter extends ListAdapter<AccountGroupUiModel, AssetsGroupAdapter.GroupViewHolder> {

    private static final Object PAYLOAD_EXPANSION = new Object();
    private static final long EXPANSION_DURATION_MS = 220L;

    private OnGroupActionClickListener groupActionClickListener;

    // ── 内部状态存储，维持与旧 Fragment 的兼容性 ──────────────────
    private boolean isAmountHidden = false;
    private List<AccountGroup> rawGroups = new ArrayList<>();
    private final Map<String, List<Account>> groupAccountsMap = new HashMap<>();
    private final Set<String> expandedGroupIds = new HashSet<>();

    public AssetsGroupAdapter() {
        super(new DiffUtil.ItemCallback<AccountGroupUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountGroupUiModel oldItem, @NonNull AccountGroupUiModel newItem) {
                return Objects.equals(oldItem.objectId, newItem.objectId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountGroupUiModel oldItem, @NonNull AccountGroupUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    // ==================== 维持兼容性方法 ====================

    public void setGroups(List<AccountGroup> groups) {
        this.rawGroups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
        pruneExpandedGroups();
        rebuildUiModels();
    }

    /**
     * Replaces the complete asset snapshot with a single RecyclerView update.
     * The assets screen receives groups and accounts together, so rebuilding once
     * avoids a full DiffUtil pass for every group.
     */
    public void setGroupsAndAccounts(List<AccountGroup> groups, Map<String, List<Account>> accountsByGroup) {
        this.rawGroups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
        this.groupAccountsMap.clear();
        if (accountsByGroup != null) {
            for (Map.Entry<String, List<Account>> entry : accountsByGroup.entrySet()) {
                this.groupAccountsMap.put(entry.getKey(), entry.getValue() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(entry.getValue()));
            }
        }
        pruneExpandedGroups();
        rebuildUiModels();
    }

    public void setAmountHidden(boolean hidden) {
        this.isAmountHidden = hidden;
        rebuildUiModels();
    }

    public void updateAccountsForExpandedGroup(String groupId, List<Account> accounts) {
        groupAccountsMap.put(groupId, accounts != null ? new ArrayList<>(accounts) : new ArrayList<>());
        rebuildUiModels();
    }

    private void pruneExpandedGroups() {
        Set<String> visibleGroupIds = new HashSet<>();
        for (AccountGroup group : rawGroups) {
            visibleGroupIds.add(group.getObjectId());
        }
        expandedGroupIds.retainAll(visibleGroupIds);
    }

    public double getTotalPositiveAssets() {
        double total = 0;
        for (List<Account> accounts : groupAccountsMap.values()) {
            for (Account acc : accounts) {
                if (acc.isIncludeInTotal() && acc.getBalance() > 0) {
                    total += acc.getBalance();
                }
            }
        }
        return total;
    }

    public double getTotalNegativeAssets() {
        double total = 0;
        for (List<Account> accounts : groupAccountsMap.values()) {
            for (Account acc : accounts) {
                if (acc.isIncludeInTotal() && acc.getBalance() < 0) {
                    total += Math.abs(acc.getBalance());
                }
            }
        }
        return total;
    }

    private void rebuildUiModels() {
        List<AccountGroupUiModel> uiModels = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        for (AccountGroup group : rawGroups) {
            String groupId = group.getObjectId();
            List<Account> accounts = groupAccountsMap.get(groupId);
            
            double pos = 0, neg = 0;
            List<AccountUiModel> subUiModels = new ArrayList<>();
            
            if (accounts != null) {
                for (Account acc : accounts) {
                    if (acc.isIncludeInTotal()) {
                        if (acc.getBalance() > 0) pos += acc.getBalance();
                        else neg += Math.abs(acc.getBalance());
                        
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
                                isAmountHidden,
                                true,
                                acc
                        ));
                    }
                }
            }

            uiModels.add(new AccountGroupUiModel(
                    group.getId(),
                    groupId,
                    group.getName(),
                    "(" + subUiModels.size() + ")",
                    "¥" + df.format(pos),
                    "¥" + df.format(neg),
                    neg > 0,
                    expandedGroupIds.contains(groupId),
                    subUiModels,
                    isAmountHidden,
                    group
            ));
        }
        submitList(uiModels);
    }

    public interface OnGroupActionClickListener {
        void onEditGroup(AccountGroup group);
        void onAccount(Account account);
        void onAccountDelete(Account account);
        void onAccountHide(Account account);
        void onAccountArchive(Account account);
        void onAccountEdit(Account account);
        void onGroupExpand(String groupId);
    }

    public void setOnGroupActionClickListener(OnGroupActionClickListener listener) {
        this.groupActionClickListener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        // Expansion uses its own height animator. The default animator would
        // animate the same layout change a second time and cause frame drops.
        recyclerView.setItemAnimator(null);
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GroupViewHolder(ItemAssetGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_EXPANSION)) {
            holder.bindExpansion(expandedGroupIds.contains(getItem(position).objectId));
        } else {
            holder.bind(getItem(position));
        }
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final ItemAssetGroupBinding binding;
        private final AccountSubAdapter subAdapter;
        private ValueAnimator expansionAnimator;
        private String boundGroupId;
        private Runnable pendingExpansionRetry;
        private int expansionRetryCount;

        GroupViewHolder(ItemAssetGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.subAdapter = new AccountSubAdapter();

            binding.rvSubAccounts.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvSubAccounts.setAdapter(subAdapter);
            binding.rvSubAccounts.setNestedScrollingEnabled(false);
            binding.rvSubAccounts.setItemAnimator(null);

            subAdapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
                @Override public void onAccountClick(Account acc) { if (groupActionClickListener != null) groupActionClickListener.onAccount(acc); }
                @Override public void onAccountDelete(Account acc) { if (groupActionClickListener != null) groupActionClickListener.onAccountDelete(acc); }
                @Override public void onAccountHide(Account acc) { if (groupActionClickListener != null) groupActionClickListener.onAccountHide(acc); }
                @Override public void onAccountArchive(Account acc) { if (groupActionClickListener != null) groupActionClickListener.onAccountArchive(acc); }
                @Override public void onAccountEdit(Account acc) { if (groupActionClickListener != null) groupActionClickListener.onAccountEdit(acc); }
            });

            binding.layoutGroupActions.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && groupActionClickListener != null) {
                    AccountGroupUiModel uiModel = getItem(pos);
                    if (uiModel.objectId != null && uiModel.objectId.startsWith("CATEGORY_")) return;
                    groupActionClickListener.onEditGroup(uiModel.originalGroup);
                }
            });

            binding.layoutHeader.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                String groupId = getItem(pos).objectId;

                if (expandedGroupIds.contains(groupId)) expandedGroupIds.remove(groupId);
                else expandedGroupIds.add(groupId);

                if (groupActionClickListener != null) groupActionClickListener.onGroupExpand(groupId);
                // Keep the current item and its nested list intact. Only the
                // expansion payload is rebound, which avoids rebuilding every
                // group while the user is scrolling or tapping quickly.
                notifyItemChanged(pos, PAYLOAD_EXPANSION);
            });
        }

        void bind(AccountGroupUiModel uiModel) {
            boundGroupId = uiModel.objectId;
            binding.tvGroupName.setText(uiModel.name);
            binding.tvCount.setText(uiModel.countText);

            boolean expanded = expandedGroupIds.contains(uiModel.objectId);
            stopExpansionAnimation();
            binding.layoutAccountsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.layoutAccountsContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            binding.ivArrow.setRotation(expanded ? 90f : 0f);

            if (uiModel.isAmountHidden) {
                binding.tvGroupBalance.setText("****");
                binding.tvGroupDebt.setText("****");
            } else {
                binding.tvGroupBalance.setText(uiModel.positiveBalanceText);
                binding.tvGroupDebt.setText(uiModel.negativeBalanceText);
            }

            binding.tvDebtLabel.setVisibility(uiModel.isDebtVisible ? View.VISIBLE : View.GONE);
            binding.tvGroupDebt.setVisibility(uiModel.isDebtVisible ? View.VISIBLE : View.GONE);

            // 更新子列表内容
            subAdapter.submitList(uiModel.accounts);
        }

        void bindExpansion(boolean expanded) {
            expansionRetryCount = 0;
            animateSection(expanded);
            binding.ivArrow.animate().cancel();
            binding.ivArrow.animate()
                    .rotation(expanded ? 90f : 0f)
                    .setDuration(EXPANSION_DURATION_MS)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }

        private void animateSection(boolean expand) {
            View section = binding.layoutAccountsContainer;
            stopExpansionAnimation();

            if (expand) {
                section.setVisibility(View.VISIBLE);
                int width = section.getWidth();
                if (width <= 0 && section.getParent() instanceof View) {
                    width = ((View) section.getParent()).getWidth();
                }
                if (width <= 0) {
                    section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    section.requestLayout();
                    return;
                }
                section.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int targetHeight = section.getMeasuredHeight();
                if (targetHeight <= 0) {
                    // ListAdapter can finish its child diff on the next frame.
                    // Retry after layout so a just-loaded group still gets
                    // the same animation as a cached group.
                    if (expansionRetryCount++ == 0) {
                        pendingExpansionRetry = () -> {
                            pendingExpansionRetry = null;
                            if (expandedGroupIds.contains(boundGroupId)) {
                                animateSection(true);
                            }
                        };
                        section.post(pendingExpansionRetry);
                    } else {
                        section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        section.requestLayout();
                    }
                    return;
                }

                int startHeight = section.getHeight();
                if (startHeight >= targetHeight) {
                    section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    section.requestLayout();
                    return;
                }
                animateHeight(section, startHeight, targetHeight, true);
            } else {
                int startHeight = section.getHeight();
                if (startHeight <= 0 || section.getVisibility() != View.VISIBLE) {
                    section.setVisibility(View.GONE);
                    section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    return;
                }
                animateHeight(section, startHeight, 0, false);
            }
        }

        private void animateHeight(View section, int startHeight, int targetHeight, boolean expanding) {
            expansionAnimator = ValueAnimator.ofInt(startHeight, targetHeight);
            final ValueAnimator animator = expansionAnimator;
            final boolean[] cancelled = {false};
            animator.setDuration(EXPANSION_DURATION_MS);
            animator.setInterpolator(new FastOutSlowInInterpolator());
            animator.addUpdateListener(animation -> {
                section.getLayoutParams().height = (int) animation.getAnimatedValue();
                section.requestLayout();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled[0] = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled[0] && expansionAnimator == animation) {
                        expansionAnimator = null;
                        section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        section.setVisibility(expanding ? View.VISIBLE : View.GONE);
                        section.requestLayout();
                    }
                }
            });
            animator.start();
        }

        private void stopExpansionAnimation() {
            if (pendingExpansionRetry != null) {
                binding.layoutAccountsContainer.removeCallbacks(pendingExpansionRetry);
                pendingExpansionRetry = null;
            }
            if (expansionAnimator != null) {
                expansionAnimator.cancel();
                expansionAnimator = null;
            }
            binding.ivArrow.animate().cancel();
        }
    }
}
