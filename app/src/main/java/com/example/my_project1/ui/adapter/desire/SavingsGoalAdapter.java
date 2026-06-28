package com.example.my_project1.ui.adapter.desire;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.ItemSavingsGoalBinding;

import java.util.Locale;

import io.reactivex.annotations.NonNull;

/**
 * SavingsGoalAdapter - 愿望列表 RecyclerView 适配器
 * -------------------------------------------------------
 * - 使用 ListAdapter + DiffUtil 高效刷新
 * - ViewBinding 绑定 item_savings_goal.xml
 * - 支持点击回调
 */
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
            Context ctx = binding.getRoot().getContext();

            // 愿望名称
            binding.tvTitle.setText(wish.getWishName());

            // 目标金额
            binding.tvTarget.setText(String.format(Locale.getDefault(),
                    "目标 ¥ %,.0f", wish.getTargetAmount()));

            // 当前金额
            binding.tvCurrentAmount.setText(String.format(Locale.getDefault(),
                    "¥ %,.0f", wish.getCurrentAmount()));

            // 进度条
            int percent = wish.getProgressPercent();
            binding.pbSaving.setProgress(percent);

            // 百分比文字
            binding.tvPercent.setText(String.format(Locale.getDefault(), "%.1f%%",
                    wish.getTargetAmount() > 0
                            ? wish.getCurrentAmount() / wish.getTargetAmount() * 100f
                            : 0f));

            // 状态标签
            String statusText;
            switch (wish.getStatus()) {
                case 1:
                    statusText = "🎉 已完成";
                    break;
                case 2:
                    statusText = "❄ 已暂停";
                    break;
                default:
                    statusText = "🚀 进行中";
                    break;
            }
            binding.tvStatus.setText(statusText);

            // 加载图标
            if (!TextUtils.isEmpty(wish.getIconUrl())) {
                Glide.with(ctx)
                        .load(wish.getIconUrl())
                        .placeholder(R.drawable.ic_piggy_bank)
                        .error(R.drawable.ic_piggy_bank)
                        .into(binding.ivPiggy);
            } else {
                binding.ivPiggy.setImageResource(R.drawable.ic_piggy_bank);
            }

            // 点击事件
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) clickListener.onWishClick(wish);
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (clickListener != null) clickListener.onWishLongClick(wish);
                return true;
            });
        }
    }

    // ==================== DiffUtil ====================

    private static final DiffUtil.ItemCallback<Wish> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Wish>() {
                @Override
                public boolean areItemsTheSame(@NonNull Wish a, @NonNull Wish b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Wish a, @NonNull Wish b) {
                    return a.getId() == b.getId()
                            && a.getWishName().equals(b.getWishName())
                            && a.getCurrentAmount() == b.getCurrentAmount()
                            && a.getTargetAmount() == b.getTargetAmount()
                            && a.getStatus() == b.getStatus();
                }
            };
}