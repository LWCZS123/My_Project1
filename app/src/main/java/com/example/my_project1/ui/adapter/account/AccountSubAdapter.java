package com.example.my_project1.ui.adapter.account;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ItemSubAccountBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class AccountSubAdapter extends RecyclerView.Adapter<AccountSubAdapter.AccountViewHolder> {

    private static final String TAG = "AccountSubAdapter";

    private boolean isAmountHidden = false;

    private final List<Account> accountList = new ArrayList<>();
    private OnAccountClickListener listener;

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
    }

    public void setOnAccountClickListener(OnAccountClickListener listener) {
        this.listener = listener;
    }
    public void setAmountHidden(boolean hidden) {
        this.isAmountHidden = hidden;
        notifyDataSetChanged();
    }

    /**
     * 【关键修复】设置账户列表并刷新UI
     */
    public void setAccounts(List<Account> accounts) {
        Log.d(TAG, "📥 setAccounts 被调用，新数据数量: " + (accounts != null ? accounts.size() : "null"));
        Log.d(TAG, "   - 当前列表数量: " + accountList.size());

        accountList.clear();
        if (accounts != null) {
            accountList.addAll(accounts);
            Log.d(TAG, "   - 更新后列表数量: " + accountList.size());
            for (int i = 0; i < accountList.size(); i++) {
                Log.d(TAG, "     [" + i + "] " + accountList.get(i).getName());
            }
        }

        // 【关键】必须调用 notifyDataSetChanged 来刷新整个列表
        notifyDataSetChanged();
        Log.d(TAG, "✅ notifyDataSetChanged 已调用");
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "🏗️ onCreateViewHolder 被调用");
        ItemSubAccountBinding binding = ItemSubAccountBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AccountViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        Log.d(TAG, "🎨 onBindViewHolder 被调用，position: " + position);
        if (position < accountList.size()) {
            holder.bind(accountList.get(position));
        } else {
            Log.e(TAG, "❌ position 越界: " + position + " >= " + accountList.size());
        }
    }

    @Override
    public int getItemCount() {
        int count = accountList.size();
        return count;
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubAccountBinding binding;

        public AccountViewHolder(ItemSubAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account account) {
            Log.d(TAG, "🎨 bind() 账户: " + account.getName());

            binding.tvName.setText(account.getName());
            binding.tvSubtitle.setText(account.getRemark());

            double balance = account.getBalance();
            double creditLimit = account.getCreditLimit();

            if (isAmountHidden) {
                binding.tvAmount.setText("****");
            } else {
                binding.tvAmount.setText(String.format("%.2f", balance));
            }

            int amountColor = balance >= 0
                    ? binding.getRoot().getContext().getColor(R.color.green)
                    : binding.getRoot().getContext().getColor(R.color.red);

            binding.tvAmount.setTextColor(amountColor);
            if (account.isCredit()) {

                // 可用额度 = 信用额度 + balance（balance 是负数）
                double available = creditLimit + balance;

                binding.tvAvailable.setVisibility(View.VISIBLE);
                if (isAmountHidden) {
                    binding.tvAvailable.setText("可用 ****");
                } else {
                    binding.tvAvailable.setText("可用 " + String.format("%.2f", available));
                }

            } else {
                // 隐藏可用额度
                binding.tvAvailable.setVisibility(View.GONE);
            }

            // 加载图标
            Glide.with(binding.ivIcon.getContext())
                    .load(account.getIconUrl())
                    .placeholder(R.drawable.ic_cross)
                    .into(binding.ivIcon);

            // 点击事件
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onAccountClick(account);
            });
        }
    }
}