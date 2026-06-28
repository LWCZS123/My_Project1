package com.example.my_project1.ui.adapter.account;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemAccountGroupPickerBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.List;

import io.reactivex.annotations.NonNull;

public class AccountGroupPickerAdapter extends RecyclerView.Adapter<AccountGroupPickerAdapter.ViewHolder> {

    private final Context context;
    private List<AccountGroup> groups;
    private String selectedGroupId;
    private OnGroupClickListener listener;

    public interface OnGroupClickListener {
        void onGroupClick(AccountGroup group);
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }

    public AccountGroupPickerAdapter(Context context, List<AccountGroup> groups, String selectedGroupId) {
        this.context = context;
        this.groups = groups;
        this.selectedGroupId = selectedGroupId;
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
        AccountGroup group = groups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groups != null ? groups.size() : 0;
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
