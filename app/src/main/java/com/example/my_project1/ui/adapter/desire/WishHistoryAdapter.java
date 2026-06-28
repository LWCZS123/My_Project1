package com.example.my_project1.ui.adapter.desire;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.databinding.ItemWishHistoryBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

import io.reactivex.annotations.NonNull;

/**
 * WishHistoryAdapter - 愿望存钱历史记录适配器
 * -------------------------------------------------------
 * 对应 SavingsActivity 底部历史记录列表
 * 支持长按删除单条记录
 */
public class WishHistoryAdapter extends ListAdapter<WishRecord, WishHistoryAdapter.ViewHolder> {

    private String wishIconUrl = "";


    public void setWishIconUrl(String url) {
        this.wishIconUrl = url;
    }

    public interface OnRecordLongClickListener {
        void onLongClick(WishRecord record);
    }

    private OnRecordLongClickListener longClickListener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault());

    public WishHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnRecordLongClickListener(OnRecordLongClickListener listener) {
        this.longClickListener = listener;
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

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemWishHistoryBinding binding;

        ViewHolder(ItemWishHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WishRecord record) {
            // 存入金额
            binding.tvRecordAmount.setText(String.format(Locale.getDefault(),
                    "+¥%.0f", record.getAmount()));

            // 日期
            if (record.getRecordDate() != null) {
                binding.tvRecordDate.setText(dateFormat.format(record.getRecordDate()));
            } else {
                binding.tvRecordDate.setText("--");
            }


            if (!TextUtils.isEmpty(wishIconUrl)) {
                Glide.with(binding.ivWishIcon)
                        .load(wishIconUrl)
                        .placeholder(R.drawable.ic_face)
                        .into(binding.ivWishIcon);
            } else {
                binding.ivWishIcon.setImageResource(R.drawable.ic_face);
            }

            // 备注
            if (record.getNote() != null && !record.getNote().isEmpty()) {
                binding.tvRecordNote.setText(record.getNote());
            } else {
                binding.tvRecordNote.setText("无备注");
            }



            // 长按删除
            binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(record);
                }
                return true;
            });
        }


    }

    private static final DiffUtil.ItemCallback<WishRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WishRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull WishRecord a, @NonNull WishRecord b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull WishRecord a, @NonNull WishRecord b) {
                    return a.getId() == b.getId()
                            && a.getAmount() == b.getAmount();
                }
            };
}