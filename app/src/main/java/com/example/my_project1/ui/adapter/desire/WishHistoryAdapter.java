package com.example.my_project1.ui.adapter.desire;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.databinding.ItemWishHistoryBinding;
import com.example.my_project1.ui.wish.WishUiFormatter;

public class WishHistoryAdapter extends ListAdapter<WishRecord, WishHistoryAdapter.ViewHolder> {

    public interface Listener {
        void onEdit(WishRecord record);
        void onDelete(WishRecord record);
    }

    private final Listener listener;

    public WishHistoryAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemWishHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemWishHistoryBinding binding;

        ViewHolder(ItemWishHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WishRecord record) {
            binding.tvRecordDate.setText(WishUiFormatter.date(record.getRecordDate()));
            binding.tvRecordAmount.setText(WishUiFormatter.money(record.getAmount()));
            binding.tvRecordNote.setText(TextUtils.isEmpty(record.getNote()) ? "存钱记录" : record.getNote());
            binding.getRoot().setOnClickListener(v -> listener.onEdit(record));
            binding.btnDeleteRecord.setOnClickListener(v -> listener.onDelete(record));
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
                    return oldItem.getAmount() == newItem.getAmount()
                            && TextUtils.equals(oldItem.getNote(), newItem.getNote())
                            && TextUtils.equals(String.valueOf(oldItem.getRecordDate()), String.valueOf(newItem.getRecordDate()))
                            && oldItem.getSyncState() == newItem.getSyncState();
                }
            };
}
