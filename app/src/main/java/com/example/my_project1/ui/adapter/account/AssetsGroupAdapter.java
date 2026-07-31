package com.example.my_project1.ui.adapter.account;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final ItemAssetGroupBinding binding;
        private final AccountSubAdapter subAdapter;

        GroupViewHolder(ItemAssetGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.subAdapter = new AccountSubAdapter();

            binding.rvSubAccounts.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvSubAccounts.setAdapter(subAdapter);
            binding.rvSubAccounts.setNestedScrollingEnabled(false);

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
                rebuildUiModels();
            });
        }

        void bind(AccountGroupUiModel uiModel) {
            binding.tvGroupName.setText(uiModel.name);
            binding.tvCount.setText(uiModel.countText);

            boolean expanded = uiModel.isExpanded;
            binding.layoutAccountsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
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
    }
}
