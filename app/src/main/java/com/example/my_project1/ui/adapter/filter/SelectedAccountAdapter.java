package com.example.my_project1.ui.adapter.filter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ItemSelectedAccountBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * SelectedAccountAdapter - 已选中账户适配器
 * -------------------------------------------------------
 * 用于在筛选页面显示已选中的账户(瀑布流)
 */
public class SelectedAccountAdapter extends RecyclerView.Adapter<SelectedAccountAdapter.ViewHolder> {

    private final Context context;
    private final List<Account> selectedAccounts = new ArrayList<>();
    private OnAccountRemoveListener listener;

    public interface OnAccountRemoveListener {
        void onAccountRemove(Account account, int position);
    }

    public SelectedAccountAdapter(Context context, OnAccountRemoveListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSelectedAccountBinding binding = ItemSelectedAccountBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(selectedAccounts.get(position), position);
    }

    @Override
    public int getItemCount() {
        return selectedAccounts.size();
    }

    /**
     * 设置已选中账户列表
     */
    public void setSelectedAccounts(List<Account> accounts) {
        selectedAccounts.clear();
        if (accounts != null && !accounts.isEmpty()) {
            selectedAccounts.addAll(accounts);
        }
        notifyDataSetChanged();
    }

    /**
     * 添加账户
     */
    public void addAccount(Account account) {
        if (!selectedAccounts.contains(account)) {
            selectedAccounts.add(account);
            notifyItemInserted(selectedAccounts.size() - 1);
        }
    }

    /**
     * 移除账户
     */
    public void removeAccount(int position) {
        if (position >= 0 && position < selectedAccounts.size()) {
            selectedAccounts.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * 移除指定账户
     */
    public void removeAccount(Account account) {
        int index = selectedAccounts.indexOf(account);
        if (index >= 0) {
            selectedAccounts.remove(index);
            notifyItemRemoved(index);
        }
    }

    /**
     * 获取已选中账户列表
     */
    public List<Account> getSelectedAccounts() {
        return new ArrayList<>(selectedAccounts);
    }

    /**
     * 清空所有选中
     */
    public void clearAll() {
        int size = selectedAccounts.size();
        selectedAccounts.clear();
        notifyItemRangeRemoved(0, size);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSelectedAccountBinding binding;

        ViewHolder(ItemSelectedAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account account, int position) {
            // 设置账户名称
            binding.tvAccountName.setText(account.getName());

            // 设置账户图标
            if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
                Glide.with(context)
                        .load(account.getIconUrl())
                        .placeholder(R.drawable.ic_wallet)
                        .error(R.drawable.ic_wallet)
                        .into(binding.imgAccountIcon);
            } else {
                binding.imgAccountIcon.setImageResource(R.drawable.ic_wallet);
            }

            // 删除按钮点击
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccountRemove(account, position);
                }
            });
        }
    }
}