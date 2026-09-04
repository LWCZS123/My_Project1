package com.example.my_project1.ui.adapter.saving;

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
import com.example.my_project1.databinding.ItemSavingPlanEmptyBinding;
import com.example.my_project1.databinding.ItemSavingPlanHeaderBinding;
import com.example.my_project1.databinding.ItemSavingPlanMethodsBinding;
import com.example.my_project1.databinding.ItemSavingPlanSectionBinding;
import com.example.my_project1.ui.saving.SavingUiFormatter;
import com.example.my_project1.utils.ImageLoaderUtils;

/**
 * 存钱计划主列表适配器 (多类型版)
 * -------------------------------------------------------
 * 📌 优化：将 NestedScrollView 逻辑完全迁移至 RecyclerView 多类型实现
 * 📌 性能：支持 ViewType 复用，减少过度绘制，消除闪烁感
 */
public class SavingPlanAdapter extends ListAdapter<Object, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_METHODS = 1;
    private static final int TYPE_SECTION = 2;
    private static final int TYPE_PLAN = 3;
    private static final int TYPE_EMPTY = 4;

    private OnPlanActionListener listener;

    public interface OnPlanActionListener {
        void onPlanClick(SavingPlan plan);
        void onPlanEdit(SavingPlan plan);
        void onPlanDelete(SavingPlan plan);
        void onPlanArchive(SavingPlan plan);
        void onMethodClick(int type);
        void onViewArchived();
    }

    public void setOnPlanActionListener(OnPlanActionListener listener) {
        this.listener = listener;
    }

    public SavingPlanAdapter() {
        super(new DiffUtil.ItemCallback<Object>() {
            @Override
            public boolean areItemsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                if (oldItem instanceof SavingPlan && newItem instanceof SavingPlan) {
                    return ((SavingPlan) oldItem).getId() == ((SavingPlan) newItem).getId();
                }
                return oldItem.getClass().equals(newItem.getClass()) && oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                if (oldItem instanceof SavingPlan && newItem instanceof SavingPlan) {
                    SavingPlan o = (SavingPlan) oldItem;
                    SavingPlan n = (SavingPlan) newItem;
                    return o.getUpdatedAt().equals(n.getUpdatedAt()) &&
                            o.getCurrentAmount() == n.getCurrentAmount() &&
                            o.getStatus() == n.getStatus() &&
                            o.isArchived() == n.isArchived();
                }
                return true;
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        Object item = getItem(position);
        if (item instanceof String) {
            switch ((String) item) {
                case "HEADER": return TYPE_HEADER;
                case "METHODS": return TYPE_METHODS;
                case "SECTION": return TYPE_SECTION;
                case "EMPTY": return TYPE_EMPTY;
            }
        } else if (item instanceof SavingPlan) {
            return TYPE_PLAN;
        }
        return super.getItemViewType(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderViewHolder(ItemSavingPlanHeaderBinding.inflate(inflater, parent, false));
            case TYPE_METHODS:
                return new MethodsViewHolder(ItemSavingPlanMethodsBinding.inflate(inflater, parent, false));
            case TYPE_SECTION:
                return new SectionViewHolder(ItemSavingPlanSectionBinding.inflate(inflater, parent, false));
            case TYPE_EMPTY:
                return new EmptyViewHolder(ItemSavingPlanEmptyBinding.inflate(inflater, parent, false));
            case TYPE_PLAN:
            default:
                return new PlanViewHolder(ItemSavingPlanBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PlanViewHolder) {
            ((PlanViewHolder) holder).bind((SavingPlan) getItem(position));
        }
        // 其他类型通常是静态内容或已在 ViewHolder 构造函数中设置了监听器
    }

    // --- 各类型 ViewHolder 定义 ---

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(ItemSavingPlanHeaderBinding binding) {
            super(binding.getRoot());
        }
    }

    class MethodsViewHolder extends RecyclerView.ViewHolder {
        MethodsViewHolder(ItemSavingPlanMethodsBinding binding) {
            super(binding.getRoot());
            binding.cvFixed.setOnClickListener(v -> { if(listener != null) listener.onMethodClick(SavingPlan.TYPE_FIXED); });
            binding.cvFlexible.setOnClickListener(v -> { if(listener != null) listener.onMethodClick(SavingPlan.TYPE_FLEXIBLE); });
            binding.cv52week.setOnClickListener(v -> { if(listener != null) listener.onMethodClick(SavingPlan.TYPE_52WEEK); });
            binding.cv365day.setOnClickListener(v -> { if(listener != null) listener.onMethodClick(SavingPlan.TYPE_365DAY); });
        }
    }

    class SectionViewHolder extends RecyclerView.ViewHolder {
        SectionViewHolder(ItemSavingPlanSectionBinding binding) {
            super(binding.getRoot());
            binding.tvArchived.setOnClickListener(v -> { if(listener != null) listener.onViewArchived(); });
        }
    }

    class EmptyViewHolder extends RecyclerView.ViewHolder {
        EmptyViewHolder(ItemSavingPlanEmptyBinding binding) {
            super(binding.getRoot());
        }
    }

    class PlanViewHolder extends RecyclerView.ViewHolder {
        private final ItemSavingPlanBinding binding;

        PlanViewHolder(ItemSavingPlanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.cvContent.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlanClick((SavingPlan) getItem(pos));
                }
            });
            binding.llEdit.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlanEdit((SavingPlan) getItem(pos));
                }
            });
            binding.llDelete.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlanDelete((SavingPlan) getItem(pos));
                }
            });
            binding.llArchive.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlanArchive((SavingPlan) getItem(pos));
                }
            });
        }

        void bind(SavingPlan plan) {
            binding.tvName.setText(plan.getName());
            binding.tvMethod.setText(SavingUiFormatter.getTypeName(plan.getType()));
            binding.tvDateRange.setText(SavingUiFormatter.dateRange(plan));
            binding.tvTargetAmount.setText(SavingUiFormatter.money(plan.getTargetAmount()));
            binding.tvCurrentAmount.setText(SavingUiFormatter.money(plan.getCurrentAmount()));
            binding.tvRemainingAmount.setText(SavingUiFormatter.money(
                    Math.max(0, plan.getTargetAmount() - plan.getCurrentAmount())));
            
            int progress = SavingUiFormatter.progress(plan);
            if (binding.pbProgress.getProgress() != progress) {
                binding.pbProgress.setProgress(progress);
            }
            binding.tvProgressPercent.setText(SavingUiFormatter.percent(plan));
            
            binding.tvArchiveLabel.setText(plan.isArchived() ? "恢复" : "归档");
            binding.ivArchiveIcon.setImageResource(plan.isArchived() ? R.drawable.ic_refresh : R.drawable.ic_package);

            if (plan.getIconUrl() != null && !plan.getIconUrl().isEmpty()) {
                ImageLoaderUtils.load(binding.ivIcon.getContext(), plan.getIconUrl(), binding.ivIcon,
                        R.drawable.ic_piggy_bank, R.drawable.ic_piggy_bank);
            } else {
                binding.ivIcon.setImageResource(R.drawable.ic_piggy_bank);
            }

            if (plan.getIconColor() != null && !plan.getIconColor().isEmpty()) {
                try {
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    bg.setColor(android.graphics.Color.parseColor(plan.getIconColor()));
                    binding.flIcon.setBackground(bg);
                } catch (Exception ignored) {
                    binding.flIcon.setBackgroundResource(R.drawable.bg_wish_icon);
                }
            } else {
                binding.flIcon.setBackgroundResource(R.drawable.bg_wish_icon);
            }
        }
    }
}
