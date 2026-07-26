package com.example.my_project1.ui.adapter.bill;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ItemHomeHeaderBinding;
import com.example.my_project1.ui.viewmodel.billvm.HeaderUiModel;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * HeaderAdapter - 首页顶部概览卡片
 * -------------------------------------------------------
 * 永远只有 0 或 1 个 item；数据通过 setHeader() 刷新。
 * 与 BillAdapter、FooterAdapter 通过 ConcatAdapter 组合使用。
 */
public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderVH> {

    private OnRefreshClickListener refreshClickListener;
    
    // Payload 常量
    private static final String PAYLOAD_MODE = "payload_mode";
    private static final String PAYLOAD_DATA = "payload_data";

    private HeaderUiModel data = new HeaderUiModel(
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00",
            "¥0.00"
    );

    private int currentMode = 0; // 0: 净资产, 1: 总收入, 2: 总支出, 3: 周结余


    public interface OnRefreshClickListener {
        void onRefreshClick();
    }

    public void setRefreshClickListener(OnRefreshClickListener l) {
        this.refreshClickListener = l;
    }

    /** 更新数据并局部刷新 */
    public void setHeader(HeaderUiModel model) {
        this.data = model;
        notifyItemChanged(0, PAYLOAD_DATA);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @NonNull
    @Override
    public HeaderVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeHeaderBinding binding = ItemHomeHeaderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new HeaderVH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderVH holder, int position) {
        holder.bind(data);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderVH holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // 局部刷新，防止闪烁
            for (Object payload : payloads) {
                if (PAYLOAD_MODE.equals(payload)) {
                    holder.updateMode(data);
                } else if (PAYLOAD_DATA.equals(payload)) {
                    holder.updateData(data);
                }
            }
        }
    }

    class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemHomeHeaderBinding b;

        HeaderVH(ItemHomeHeaderBinding binding) {
            super(binding.getRoot());
            this.b = binding;
            
            b.llHeaderTitle.setOnClickListener(v -> {
                currentMode = (currentMode + 1) % 4;
                notifyItemChanged(getBindingAdapterPosition(), PAYLOAD_MODE);
            });
            
            // 初始播放动画
            if (!b.lottieAnimation.isAnimating()) {
                b.lottieAnimation.playAnimation();
            }
        }

        void bind(HeaderUiModel model) {
            updateMode(model);
            updateData(model);
        }

        /** 仅更新切换模式相关的 UI */
        void updateMode(HeaderUiModel model) {
            switch (currentMode) {
                case 0:
                    b.tvTitle.setText("我的净资产");
                    b.tvTotalAmount.setText(model.mainBalance);
                    break;
                case 1:
                    b.tvTitle.setText("总收入");
                    b.tvTotalAmount.setText(model.totalIncome);
                    break;
                case 2:
                    b.tvTitle.setText("总支出");
                    b.tvTotalAmount.setText(model.totalExpense);
                    break;
                case 3:
                    b.tvTitle.setText("周结余");
                    b.tvTotalAmount.setText(model.weeklyBalance);
                    break;
            }
        }

        /** 仅更新数值相关的 UI */
        void updateData(HeaderUiModel model) {
            // 如果模式没变，也要更新主金额（因为 model 可能变了）
            updateMode(model);

            // 设置今日变化颜色：收入>支出为绿色，支出>收入为红色
            String changeText = model.todayChange;
            b.tvTodayChange.setText(changeText);
            
            // 解析数值判断正负（根据前缀 + 或 -）
            if (changeText != null && !changeText.isEmpty()) {
                try {
                    boolean isNegative = changeText.trim().startsWith("-");
                    String numericValue = changeText.replace("¥", "")
                            .replace("$", "")
                            .replace("+", "")
                            .replace("-", "")
                            .replace(",", "")
                            .trim();
                    
                    double value = Double.parseDouble(numericValue);
                    if (isNegative) value = -value;
                    
                    if (value > 0) {
                        b.tvTodayChange.setTextColor(ContextCompat.getColor(
                                b.getRoot().getContext(), R.color.green));
                    } else if (value < 0) {
                        b.tvTodayChange.setTextColor(ContextCompat.getColor(
                                b.getRoot().getContext(), R.color.red));
                    } else {
                        b.tvTodayChange.setTextColor(ContextCompat.getColor(
                                b.getRoot().getContext(), R.color.icon_tint));
                    }
                } catch (NumberFormatException e) {
                    b.tvTodayChange.setTextColor(ContextCompat.getColor(
                            b.getRoot().getContext(), R.color.icon_tint));
                }
            }
            
            b.tvAssetsValue.setText(model.assets);
            b.tvLiabilitiesValue.setText(model.liabilities);
            b.tvMonthlyIncome.setText("月收入: " + model.monthlyIncome);
            b.tvMonthlyExpense.setText("月支出: " + model.monthlyExpense);
            
            // 💡 优化：避免重复触发 Lottie 动画重置
            if (!b.lottieAnimation.isAnimating()) {
                b.lottieAnimation.playAnimation();
            }
        }
    }
}
