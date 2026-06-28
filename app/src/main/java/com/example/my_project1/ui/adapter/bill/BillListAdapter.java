package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.ui.activity.BillDetailActivity;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 分类账单明细列表 Adapter
 *
 * 两种 ViewType：
 *   TYPE_HEADER — 日期分组头（item_date_header.xml）
 *   TYPE_BILL   — 账单条目（item_category_stat.xml）
 *
 * 进度条：item_category_stat 已改为原生 ProgressBar（id: progress_bar），
 * 本页不需要显示进度，直接 setProgress(0) 即可，不涉及任何 layout 操作。
 *
 * 点击账单条目 → 跳转 BillDetailActivity，附带 slide_in_right 动画。
 */
public class BillListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ================================================================
    //  数据模型
    // ================================================================

    public static class ListItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_BILL   = 1;

        public final int    viewType;
        public final String dateText;   // Header 专用
        public final int    dayCount;   // Header 专用
        public final Bill   bill;       // Bill 专用

        /** 日期分组头 */
        public ListItem(String dateText, int dayCount) {
            this.viewType = TYPE_HEADER;
            this.dateText = dateText;
            this.dayCount = dayCount;
            this.bill     = null;
        }

        /** 账单条目 */
        public ListItem(Bill bill) {
            this.viewType = TYPE_BILL;
            this.dateText = null;
            this.dayCount = 0;
            this.bill     = bill;
        }
    }

    // ================================================================
    //  Adapter 核心
    // ================================================================

    private final List<ListItem> items = new ArrayList<>();

    public void setData(List<ListItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == ListItem.TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_date_header, parent, false));
        } else {
            return new BillVH(inf.inflate(R.layout.item_category_stat, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);
        if (holder instanceof HeaderVH) {
            bindHeader((HeaderVH) holder, item);
        } else {
            bindBill((BillVH) holder, item.bill);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ================================================================
    //  绑定：日期头
    // ================================================================

    private void bindHeader(HeaderVH h, ListItem item) {
        h.tvDate.setText(item.dateText);
        h.tvDayCount.setText(item.dayCount + "笔");
    }

    // ================================================================
    //  绑定：账单条目
    // ================================================================

    private void bindBill(BillVH h, Bill bill) {
        // ── 分类图标 ──
        String iconUrl = bill.getCategoryIconUrl();
        if (iconUrl != null && !iconUrl.isEmpty()) {
            GlideImageLoader.load(h.ivIcon.getContext(), iconUrl, h.ivIcon,
                    android.R.color.transparent, android.R.color.transparent);
        } else {
            h.ivIcon.setImageDrawable(null);
        }

        // ── 主标题：优先备注，无则分类名 ──
        String remark  = bill.getRemark();
        String catName = bill.getCategoryName() != null ? bill.getCategoryName() : "";
        h.tvName.setText((remark != null && !remark.isEmpty()) ? remark : catName);

        // ── 副标题：分类名 ──
        h.tvPercent.setText(catName);

        // ── 金额（支出红 / 收入绿） ──
        boolean isExpense = (bill.getType() == 0);
        h.tvAmount.setText(
                (isExpense ? "-¥" : "+¥") + String.format("%.2f", bill.getAmount()));
        h.tvAmount.setTextColor(isExpense ? 0xFFFF4C5B : 0xFF4CAF50);

        // ── 右下角留空 ──
        h.tvCount.setText("");

        // ── 进度条：本页不需要显示，置 0 即可（无 layout 操作，不闪烁）──
        h.progressBar.setProgress(0);

        // ── 图标圆形背景 ──
        int color = isExpense ? 0xFFFF4C5B : 0xFF4CAF50;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor((color & 0x00FFFFFF) | (38 << 24));
        h.vIconBg.setBackground(bg);

        // ── 点击 → 账单详情 ──
        h.itemView.setOnClickListener(v -> navigateToDetail(v.getContext(), bill));
    }

    private void navigateToDetail(Context context, Bill bill) {
        Intent intent = new Intent(context, BillDetailActivity.class);
        if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.getObjectId());
        } else {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, bill.getId());
        }
        context.startActivity(intent);
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context)
                    .overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    // ================================================================
    //  ViewHolder：日期头
    // ================================================================

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvDayCount;

        HeaderVH(@NonNull View v) {
            super(v);
            tvDate     = v.findViewById(R.id.tv_date);
            tvDayCount = v.findViewById(R.id.tv_day_count);
        }
    }

    // ================================================================
    //  ViewHolder：账单条目（item_category_stat）
    // ================================================================

    static class BillVH extends RecyclerView.ViewHolder {
        final ImageView   ivIcon;
        final View        vIconBg;
        final TextView    tvName;
        final TextView    tvPercent;
        final TextView    tvAmount;
        final TextView    tvCount;
        final ProgressBar progressBar;   // 原生 ProgressBar（替换旧的 fl+v 组合）

        BillVH(@NonNull View v) {
            super(v);
            ivIcon      = v.findViewById(R.id.iv_category_icon);
            vIconBg     = v.findViewById(R.id.v_icon_bg);
            tvName      = v.findViewById(R.id.tv_category_name);
            tvPercent   = v.findViewById(R.id.tv_category_percent);
            tvAmount    = v.findViewById(R.id.tv_category_amount);
            tvCount     = v.findViewById(R.id.tv_bill_count);
            progressBar = v.findViewById(R.id.progress_bar);
        }
    }
}