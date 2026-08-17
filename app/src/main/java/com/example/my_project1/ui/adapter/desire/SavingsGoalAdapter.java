package com.example.my_project1.ui.adapter.desire;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ItemSavingsGoalBinding;

public class SavingsGoalAdapter extends ListAdapter<Wish, SavingsGoalAdapter.ViewHolder> {

    public interface OnWishClickListener {
        void onWishClick(Wish wish);
        void onWishLongClick(Wish wish);
    }

    private OnWishClickListener clickListener;

    public SavingsGoalAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnWishClickListener(OnWishClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSavingsGoalBinding binding = ItemSavingsGoalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSavingsGoalBinding binding;

        ViewHolder(ItemSavingsGoalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Wish wish) {
            // 绑定数据到 UI
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) clickListener.onWishClick(wish);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (clickListener != null) clickListener.onWishLongClick(wish);
                return true;
            });
        }
    }

    private static final DiffUtil.ItemCallback<Wish> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Wish>() {
                @Override
                public boolean areItemsTheSame(@NonNull Wish oldItem, @NonNull Wish newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Wish oldItem, @NonNull Wish newItem) {
                    return oldItem.getId() == newItem.getId();
                }
            };
}
