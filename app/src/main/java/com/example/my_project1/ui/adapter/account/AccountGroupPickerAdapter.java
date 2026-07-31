package com.example.my_project1.ui.adapter.account;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAccountGroupPickerBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.List;
import java.util.Objects;

import io.reactivex.annotations.NonNull;

public class AccountGroupPickerAdapter extends ListAdapter<AccountGroup, AccountGroupPickerAdapter.ViewHolder> {

    private final Context context;
    private String selectedGroupId;
    private OnGroupClickListener listener;

    public interface OnGroupClickListener {
        void onGroupClick(AccountGroup group);
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }

    public AccountGroupPickerAdapter(Context context, String selectedGroupId) {
        super(new DiffUtil.ItemCallback<AccountGroup>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountGroup oldItem, @NonNull AccountGroup newItem) {
                return Objects.equals(oldItem.getObjectId(), newItem.getObjectId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountGroup oldItem, @NonNull AccountGroup newItem) {
                return Objects.equals(oldItem.getName(), newItem.getName()) &&
                        oldItem.getAccountCount() == newItem.getAccountCount();
            }
        });
        this.context = context;
        this.selectedGroupId = selectedGroupId;
    }

    public void setSelectedGroupId(String selectedGroupId) {
        this.selectedGroupId = selectedGroupId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountGroupPickerBinding binding = ItemAccountGroupPickerBinding.inflate(
                LayoutInflater.from(context), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountGroupPickerBinding binding;

        ViewHolder(@NonNull ItemAccountGroupPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountGroup group) {

            // 账户组名称
            binding.tvGroupName.setText(group.getName());

            // 账户数量
            binding.tvAccountCount.setText(group.getAccountCount() + " 个账户");

            // 图标加载
            if (group.getIconUrl() != null && !group.getIconUrl().isEmpty()) {
                ImageLoaderUtils.load(context, group.getIconUrl(), binding.ivGroupIcon);
            } else {
                binding.ivGroupIcon.setVisibility(View.GONE);
            }

            // 是否选中
            boolean isSelected = group.getObjectId().equals(selectedGroupId);
            binding.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // 点击事件
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onGroupClick(group);
            });
        }
    }
}
