package com.example.my_project1.ui.adapter.account;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ItemSubAccountBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountUiModel;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class AccountSubAdapter extends ListAdapter<AccountUiModel, AccountSubAdapter.AccountViewHolder> {

    private static final String TAG = "AccountSubAdapter";

    private OnAccountClickListener listener;
    private boolean isSwipeEnabledGlobal = true;

    public AccountSubAdapter() {
        super(new DiffUtil.ItemCallback<AccountUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountUiModel oldItem, @NonNull AccountUiModel newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountUiModel oldItem, @NonNull AccountUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    public void setSwipeEnabled(boolean enabled) {
        this.isSwipeEnabledGlobal = enabled;
    }

    /**
     * 兼容旧代码：直接提交 Account 列表，内部转换为 UiModel
     */
    public void setAccounts(List<Account> accounts) {
        if (accounts == null) {
            submitList(null);
            return;
        }
        List<AccountUiModel> uiModels = new ArrayList<>();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        for (Account acc : accounts) {
            uiModels.add(new AccountUiModel(
                    acc.getId(),
                    acc.getObjectId(),
                    acc.getName(),
                    acc.getAccountType() != null ? acc.getAccountType() : acc.getRemark(),
                    "¥" + df.format(acc.getBalance()),
                    acc.getBalance() < 0 ? 0xFFFF6B6B : 0xFF333333,
                    "可用额度 ¥" + df.format(acc.getCreditLimit() + acc.getBalance()),
                    acc.isCredit(),
                    acc.getIconUrl(),
                    false, // isAmountHidden 默认显示
                    isSwipeEnabledGlobal,
                    acc
            ));
        }
        submitList(uiModels);
    }

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
        void onAccountDelete(Account account);
        void onAccountHide(Account account);
        void onAccountArchive(Account account);
        void onAccountEdit(Account account);
    }

    public void setOnAccountClickListener(OnAccountClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubAccountBinding binding = ItemSubAccountBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AccountViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubAccountBinding binding;

        public AccountViewHolder(ItemSubAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountUiModel uiModel) {
            binding.swipeLayout.setSwipeEnable(uiModel.isSwipeEnabled);

            binding.tvName.setText(uiModel.name);
            binding.tvSubtitle.setText(uiModel.subtitle);

            if (uiModel.isAmountHidden) {
                binding.tvAmount.setText("****");
            } else {
                binding.tvAmount.setText(uiModel.amountText);
            }

            binding.tvAmount.setTextColor(uiModel.amountColor);

            if (uiModel.isAvailableVisible) {
                binding.tvAvailable.setVisibility(View.VISIBLE);
                if (uiModel.isAmountHidden) {
                    binding.tvAvailable.setText("可用额度 ****");
                } else {
                    binding.tvAvailable.setText(uiModel.availableText);
                }
            } else {
                binding.tvAvailable.setVisibility(View.GONE);
            }

            // 加载图标
            if (uiModel.iconUrl != null && !uiModel.iconUrl.isEmpty()) {
                Glide.with(binding.ivIcon.getContext())
                        .load(uiModel.iconUrl)
                        .placeholder(R.drawable.ic_wallet)
                        .into(binding.ivIcon);
            } else {
                binding.ivIcon.setImageResource(R.drawable.ic_wallet);
            }

            // 点击事件
            binding.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onAccountClick(uiModel.originalAccount);
            });

            // 菜单点击事件
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onAccountDelete(uiModel.originalAccount);
                binding.swipeLayout.quickClose();
            });
            binding.btnHide.setOnClickListener(v -> {
                if (listener != null) listener.onAccountHide(uiModel.originalAccount);
                binding.swipeLayout.quickClose();
            });
            binding.btnArchive.setOnClickListener(v -> {
                if (listener != null) listener.onAccountArchive(uiModel.originalAccount);
                binding.swipeLayout.quickClose();
            });
            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onAccountEdit(uiModel.originalAccount);
                binding.swipeLayout.quickClose();
            });
        }
    }
}
