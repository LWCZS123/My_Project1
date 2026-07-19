package com.example.my_project1.ui.adapter.account;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAccountGroupManageBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccountGroupManageAdapter extends RecyclerView.Adapter<AccountGroupManageAdapter.ViewHolder> {

    private final List<AccountGroup> groups = new ArrayList<>();
    private final Map<String, List<Account>> groupAccounts = new HashMap<>();
    private final Set<String> expandedGroupIds = new HashSet<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEdit(AccountGroup group);
        void onDelete(AccountGroup group);
        void onAddAccount(AccountGroup group);
        void onAccountClick(Account account);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<AccountGroup> newGroups) {
        groups.clear();
        if (newGroups != null) groups.addAll(newGroups);
        notifyDataSetChanged();
    }

    public void setGroupAccounts(String groupId, List<Account> accounts) {
        groupAccounts.put(groupId, accounts);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getObjectId().equals(groupId)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemAccountGroupManageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(groups.get(position));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountGroupManageBinding binding;
        private final AccountSubAdapter subAdapter;

        ViewHolder(ItemAccountGroupManageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.subAdapter = new AccountSubAdapter();
            subAdapter.setSwipeEnabled(false);
            binding.rvSubAccounts.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
            binding.rvSubAccounts.setAdapter(subAdapter);
            
            subAdapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
                @Override public void onAccountClick(Account account) {
                    if (listener != null) listener.onAccountClick(account);
                }
                @Override public void onAccountDelete(Account account) {}
                @Override public void onAccountHide(Account account) {}
                @Override public void onAccountArchive(Account account) {}
                @Override public void onAccountEdit(Account account) {}
            });
        }

        void bind(AccountGroup group) {
            String groupId = group.getObjectId();
            boolean isDefault = isDefaultGroup(group.getName());
            String displayName = group.getName() + (isDefault ? " (默认)" : " (自定义)");
            binding.tvGroupName.setText(displayName);
            
            boolean isExpanded = expandedGroupIds.contains(groupId);
            binding.cardAccounts.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            binding.ivExpand.setRotation(isExpanded ? 180f : 0f);

            if (isExpanded) {
                List<Account> accounts = groupAccounts.get(groupId);
                if (accounts == null || accounts.isEmpty()) {
                    binding.tvEmptyHint.setVisibility(View.VISIBLE);
                    binding.rvSubAccounts.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyHint.setVisibility(View.GONE);
                    binding.rvSubAccounts.setVisibility(View.VISIBLE);
                    subAdapter.setAccounts(accounts);
                }
            }

            binding.layoutHeader.setOnClickListener(v -> {
                if (isExpanded) {
                    expandedGroupIds.remove(groupId);
                } else {
                    expandedGroupIds.add(groupId);
                }
                notifyItemChanged(getAdapterPosition());
            });

            binding.btnMore.setOnClickListener(v -> showMoreMenu(v, group, isDefault));
            
            // 🔑 默认账户组不允许直接添加账户
            binding.btnAddAccount.setVisibility(isDefault ? View.GONE : View.VISIBLE);
            binding.btnAddAccount.setOnClickListener(v -> {
                if (listener != null) listener.onAddAccount(group);
            });
        }

        private boolean isDefaultGroup(String name) {
            return "资金账户".equals(name) || "信用账户".equals(name) || "充值账户".equals(name);
        }

        private void showMoreMenu(View v, AccountGroup group, boolean isDefault) {
            if (listener != null) {
                listener.onEdit(group); // We change 'onEdit' to show the actions bottom sheet
            }
        }
    }
}
