package com.example.my_project1.ui.adapter.account;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ItemGroupBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.List;

import io.reactivex.annotations.NonNull;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<AccountGroup> groupList;
    private int selectedPosition = -1;

    public interface OnGroupSelectListener {
        void onSelected(AccountGroup group);
    }

    private OnGroupSelectListener listener;

    public void setOnGroupSelectListener(OnGroupSelectListener listener) {
        this.listener = listener;
    }

    public GroupAdapter(List<AccountGroup> groupList) {
        this.groupList = groupList;
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
        AccountGroup group = groupList.get(position);

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

    @Override
    public int getItemCount() {
        return groupList == null ? 0 : groupList.size();
    }

    public void setSelectedGroup(String groupId) {
        for (int i = 0; i < groupList.size(); i++) {
            if (groupList.get(i).getObjectId().equals(groupId)) {
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
