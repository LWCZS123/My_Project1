package com.example.my_project1.ui.adapter.desire;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ItemSavingsGoalBinding;
import com.example.my_project1.ui.wish.WishUiFormatter;

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
        clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSavingsGoalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
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
            binding.tvTitle.setText(wish.getWishName());
            binding.tvTarget.setText("目标 " + WishUiFormatter.money(wish.getTargetAmount()));
            
            binding.tvStatus.setText(WishUiFormatter.status(wish));
            int statusIcon;
            switch (wish.getStatus()) {
                case Wish.STATUS_COMPLETED:
                    statusIcon = R.drawable.ic_check_circle;
                    break;
                case Wish.STATUS_ABANDONED:
                    statusIcon = R.drawable.ic_cancel;
                    break;
                default:
                    statusIcon = R.drawable.ic_clock;
                    break;
            }
            
            android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(binding.getRoot().getContext(), statusIcon);
            if (drawable != null) {
                // 设置图标大小为 14dp
                int size = (int) (14 * binding.getRoot().getContext().getResources().getDisplayMetrics().density);
                drawable.setBounds(0, 0, size, size);
                binding.tvStatus.setCompoundDrawables(drawable, null, null, null);
            }
            
            binding.tvStatus.setCompoundDrawablePadding(8);

            binding.pbSaving.setProgress(WishUiFormatter.progress(wish));
            binding.tvCurrentAmount.setText(WishUiFormatter.money(wish.getCurrentAmount()));
            binding.tvPercent.setText(WishUiFormatter.percent(wish));
            if (TextUtils.isEmpty(wish.getIconUrl())) {
                binding.ivPiggy.setImageResource(R.drawable.ic_piggy_bank);
            } else {
                Glide.with(binding.ivPiggy)
                        .load(wish.getIconUrl())
                        .placeholder(R.drawable.ic_piggy_bank)
                        .error(R.drawable.ic_piggy_bank)
                        .into(binding.ivPiggy);
            }
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
                    return TextUtils.equals(oldItem.getWishName(), newItem.getWishName())
                            && TextUtils.equals(oldItem.getIconUrl(), newItem.getIconUrl())
                            && oldItem.getTargetAmount() == newItem.getTargetAmount()
                            && oldItem.getCurrentAmount() == newItem.getCurrentAmount()
                            && oldItem.getStatus() == newItem.getStatus()
                            && oldItem.getSyncState().equals(newItem.getSyncState());
                }
            };
}
