package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 图表图例适配器 - 支持滚动显示
 */
public class ChartLegendAdapter extends RecyclerView.Adapter<ChartLegendAdapter.LegendViewHolder> {

    private Context context;
    private List<LegendItem> legendItems = new ArrayList<>();

    public ChartLegendAdapter(Context context) {
        this.context = context;
    }

    public void setLegendItems(List<LegendItem> items) {
        this.legendItems = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LegendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chart_legend, parent, false);
        return new LegendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LegendViewHolder holder, int position) {
        LegendItem item = legendItems.get(position);

        // 设置颜色指示器
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(6f);
        drawable.setColor(item.color);
        holder.colorIndicator.setBackground(drawable);

        // 设置文本
        holder.tvLabel.setText(item.label);
        holder.tvPercentage.setText(item.percentage);
        holder.tvAmount.setText(item.amount);
    }

    @Override
    public int getItemCount() {
        return legendItems.size();
    }

    static class LegendViewHolder extends RecyclerView.ViewHolder {
        View colorIndicator;
        TextView tvLabel;
        TextView tvPercentage;
        TextView tvAmount;

        public LegendViewHolder(@NonNull View itemView) {
            super(itemView);
            colorIndicator = itemView.findViewById(R.id.color_indicator);
            tvLabel = itemView.findViewById(R.id.tv_legend_label);
            tvPercentage = itemView.findViewById(R.id.tv_legend_percentage);
            tvAmount = itemView.findViewById(R.id.tv_legend_amount);
        }
    }

    /**
     * 图例数据项
     */
    public static class LegendItem {
        public String label;       // 分类名称
        public String percentage;  // 百分比
        public String amount;      // 金额
        public int color;          // 颜色

        public LegendItem(String label, String percentage, String amount, int color) {
            this.label = label;
            this.percentage = percentage;
            this.amount = amount;
            this.color = color;
        }
    }
}