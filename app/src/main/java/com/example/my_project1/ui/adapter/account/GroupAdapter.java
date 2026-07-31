package com.example.my_project1.ui.adapter.account;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemGroupBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.List;
import java.util.Objects;

import io.reactivex.annotations.NonNull;

public class GroupAdapter extends ListAdapter<AccountGroup, GroupAdapter.GroupViewHolder> {

    private int selectedPosition = -1;

    public interface OnGroupSelectListener {
        void onSelected(AccountGroup group);
    }

    private OnGroupSelectListener listener;

    public void setOnGroupSelectListener(OnGroupSelectListener listener) {
        this.listener = listener;
    }

    public GroupAdapter() {
        super(new DiffUtil.ItemCallback<AccountGroup>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountGroup oldItem, @NonNull AccountGroup newItem) {
                return Objects.equals(oldItem.getObjectId(), newItem.getObjectId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountGroup oldItem, @NonNull AccountGroup newItem) {
                return Objects.equals(oldItem.getName(), newItem.getName());
            }
        });
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGroupBinding binding = ItemGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, @SuppressLint("RecyclerView") int position) {
        AccountGroup group = getItem(position);

        holder.binding.tvGroupName.setText(group.getName());

        // 🔴 逻辑调整：在选择分组界面隐藏图标显示
        holder.binding.ivGroupIcon.setVisibility(View.GONE);

        holder.binding.ivCheck.setVisibility(
                position == selectedPosition ? View.VISIBLE : View.GONE
        );


        holder.itemView.setOnClickListener(v -> {
            int old = selectedPosition;
            selectedPosition = position;

            notifyItemChanged(old);
            notifyItemChanged(position);

            if (listener != null) {
                listener.onSelected(group);
            }
        });
    }

    public void setSelectedGroup(String groupId) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).getObjectId().equals(groupId)) {
                selectedPosition = i;
                break;
            }
        }
        notifyDataSetChanged();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        ItemGroupBinding binding;

        public GroupViewHolder(@NonNull ItemGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
