package com.example.my_project1.ui.adapter.saving;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.databinding.ItemSavingPlanBinding;
import com.example.my_project1.databinding.ItemSavingStepBinding;
import com.example.my_project1.ui.saving.SavingUiFormatter;
import com.example.my_project1.ui.viewmodel.saving.SavingCardUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

/**
 * 存钱步骤适配器
 * 支持 Header (SavingPlan) 和 Card (SavingCardUiModel) 两种 ViewType
 */
public class SavingStepAdapter extends ListAdapter<Object, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CARD = 1;

    private OnStepClickListener listener;

    public interface OnStepClickListener {
        void onStepClick(SavingCardUiModel step);
        void onRecordDelete(long recordId);
    }

    public void setOnStepClickListener(OnStepClickListener listener) {
        this.listener = listener;
    }

    public SavingStepAdapter() {
        super(new DiffUtil.ItemCallback<Object>() {
            @Override
            public boolean areItemsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                if (oldItem instanceof SavingPlan && newItem instanceof SavingPlan) {
                    return ((SavingPlan) oldItem).getId() == ((SavingPlan) newItem).getId();
                }
                if (oldItem instanceof SavingCardUiModel && newItem instanceof SavingCardUiModel) {
                    SavingCardUiModel o = (SavingCardUiModel) oldItem;
                    SavingCardUiModel n = (SavingCardUiModel) newItem;
                    // 如果有 recordId 且相等，则认为是同一项
                    if (o.getRecordId() > 0 && n.getRecordId() > 0) {
                        return o.getRecordId() == n.getRecordId();
                    }
                    // 否则对于预设步骤，使用 stepIndex 标识
                    return o.getStepIndex() == n.getStepIndex() && o.getStepIndex() >= 0;
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(ItemSavingPlanBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new CardViewHolder(ItemSavingStepBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((SavingPlan) getItem(position));
        } else {
            ((CardViewHolder) holder).bind((SavingCardUiModel) getItem(position));
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemSavingPlanBinding binding;

        HeaderViewHolder(ItemSavingPlanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // Header 通常不处理外部点击进入详情，因为已经在详情页了
        }

        void bind(SavingPlan plan) {
            // 禁用详情页 Header 的侧滑菜单
            if (binding.getRoot() instanceof com.example.my_project1.utils.ui.SwipeMenuLayout) {
                ((com.example.my_project1.utils.ui.SwipeMenuLayout) binding.getRoot()).setSwipeEnable(false);
            }

            // 只有当数据变化时才更新文本，减少 UI 闪烁
            binding.tvName.setText(plan.getName());
            binding.tvMethod.setText(SavingUiFormatter.getTypeName(plan.getType()));
            binding.tvDateRange.setText(SavingUiFormatter.dateRange(plan));
            binding.tvTargetAmount.setText(SavingUiFormatter.money(plan.getTargetAmount()));
            binding.tvCurrentAmount.setText(SavingUiFormatter.money(plan.getCurrentAmount()));
            binding.tvRemainingAmount.setText(SavingUiFormatter.money(
                    Math.max(0, plan.getTargetAmount() - plan.getCurrentAmount())));
            binding.pbProgress.setProgress(SavingUiFormatter.progress(plan));
            binding.tvProgressPercent.setText(SavingUiFormatter.percent(plan));

            // 图片加载优化
            String iconUrl = plan.getIconUrl();
            if (iconUrl != null && !iconUrl.isEmpty()) {
                ImageLoaderUtils.load(binding.ivIcon.getContext(), iconUrl, binding.ivIcon,
                        R.drawable.ic_piggy_bank, R.drawable.ic_piggy_bank);
            } else {
                binding.ivIcon.setImageResource(R.drawable.ic_piggy_bank);
            }

            if (plan.getIconColor() != null && !plan.getIconColor().isEmpty()) {
                try {
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    bg.setColor(Color.parseColor(plan.getIconColor()));
                    binding.flIcon.setBackground(bg);
                } catch (Exception ignored) {
                    binding.flIcon.setBackgroundResource(R.drawable.bg_wish_icon);
                }
            } else {
                binding.flIcon.setBackgroundResource(R.drawable.bg_wish_icon);
            }
        }
    }

    class CardViewHolder extends RecyclerView.ViewHolder {
        private final ItemSavingStepBinding binding;

        CardViewHolder(ItemSavingStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    Object item = getItem(pos);
                    if (item instanceof SavingCardUiModel) {
                        listener.onStepClick((SavingCardUiModel) item);
                    }
                }
            });
        }

        void bind(SavingCardUiModel step) {
            binding.tvAmount.setText(SavingUiFormatter.money(step.getAmount()));
            binding.tvDate.setText(SavingUiFormatter.dateSimple(step.getDueDate()));
            
            // 显示转账图标 (如果已记录且有关联账单)
            binding.ivTransferIcon.setVisibility(step.isTransfer() ? View.VISIBLE : View.GONE);
            
            if (step.isCompleted()) {
                binding.ivCheck.setImageResource(R.drawable.ic_check_circle);
                binding.ivCheck.setColorFilter(Color.parseColor("#315CF5"));
                binding.tvAmount.setTextColor(Color.parseColor("#315CF5"));
            } else {
                binding.ivCheck.setImageResource(R.drawable.bg_dot_circle);
                binding.ivCheck.setColorFilter(Color.parseColor("#DDDDDD"));
                binding.tvAmount.setTextColor(Color.parseColor("#17151A"));
            }
        }
    }
}
