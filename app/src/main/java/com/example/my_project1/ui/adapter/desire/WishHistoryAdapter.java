package com.example.my_project1.ui.adapter.desire;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.databinding.ItemWishHistoryBinding;

public class WishHistoryAdapter extends ListAdapter<WishRecord, WishHistoryAdapter.ViewHolder> {

    public WishHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWishHistoryBinding binding = ItemWishHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemWishHistoryBinding binding;

        ViewHolder(ItemWishHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WishRecord record) {
            // 绑定数据到 UI
        }
    }

    private static final DiffUtil.ItemCallback<WishRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WishRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull WishRecord oldItem, @NonNull WishRecord newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull WishRecord oldItem, @NonNull WishRecord newItem) {
                    return oldItem.equals(newItem);
                }
            };
}
