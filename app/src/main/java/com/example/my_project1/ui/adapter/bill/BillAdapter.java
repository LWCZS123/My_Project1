package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemBillBinding;
import com.example.my_project1.databinding.ItemBillDateHeaderBinding;
import com.example.my_project1.ui.adapter.photo.PhotoAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.annotations.NonNull;

/**
 * BillAdapter (重构版) - ConcatAdapter 架构中的账单列表部分
 * -------------------------------------------------------
 * ✅ 使用 AsyncListDiffer + DiffUtil 实现丝滑局部刷新
 * ✅ 数据类型为 Object (DateHeader | BillUiModel)，支持日期分组
 * ✅ onBindViewHolder 只做简单 setText/setVisibility，零计算
 * ✅ 时间轴连线根据 BillUiModel.isFirstOfDay / isLastOfDay 动态控制
 */
public class BillAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_BILL        = 1;

    private final Context context;
    private OnBillClickListener listener;

    // 共享 RecycledViewPool（图片列表内嵌 RecyclerView 复用）
    private static final RecyclerView.RecycledViewPool sharedPhotoPool =
            new RecyclerView.RecycledViewPool();
    static { sharedPhotoPool.setMaxRecycledViews(0, 20); }

    // ──────────── DiffUtil 回调 ────────────────────────
    private static final DiffUtil.ItemCallback<Object> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Object>() {

                @Override
                public boolean areItemsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                    if (oldItem instanceof DateHeader && newItem instanceof DateHeader) {
                        return ((DateHeader) oldItem).dateKey.equals(((DateHeader) newItem).dateKey);
                    }
                    if (oldItem instanceof BillUiModel && newItem instanceof BillUiModel) {
                        return ((BillUiModel) oldItem).diffKey.equals(((BillUiModel) newItem).diffKey);
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                    if (oldItem instanceof DateHeader && newItem instanceof DateHeader) {
                        DateHeader o = (DateHeader) oldItem;
                        DateHeader n = (DateHeader) newItem;
                        return o.dateText.equals(n.dateText)
                                && o.expenseText.equals(n.expenseText)
                                && o.incomeText.equals(n.incomeText);
                    }
                    if (oldItem instanceof BillUiModel && newItem instanceof BillUiModel) {
                        BillUiModel o = (BillUiModel) oldItem;
                        BillUiModel n = (BillUiModel) newItem;
                        return o.timeText.equals(n.timeText)
                                && o.amountText.equals(n.amountText)
                                && o.categoryName.equals(n.categoryName)
                                && o.remarkText.equals(n.remarkText)
                                && o.locationText.equals(n.locationText)
                                && o.isFirstOfDay == n.isFirstOfDay
                                && o.isLastOfDay  == n.isLastOfDay
                                && Objects.equals(o.imageUrls, n.imageUrls);
                    }
                    return false;
                }
            };

    private final AsyncListDiffer<Object> differ =
            new AsyncListDiffer<>(this, DIFF_CALLBACK);

    // ──────────── 日期分组 Header 数据类 ──────────────
    public static class DateHeader {
        /** "2024-04-21" 用于 DiffUtil 判同 */
        public final String dateKey;
        /** "4月21日（周日）" 显示文字 */
        public final String dateText;
        public final String expenseText; // "支出 ¥21.00"
        public final String incomeText;  // "收入 ¥500.00"

        public DateHeader(String dateKey, String dateText,
                          String expenseText, String incomeText) {
            this.dateKey     = dateKey;
            this.dateText    = dateText;
            this.expenseText = expenseText;
            this.incomeText  = incomeText;
        }
    }

    // ──────────── 接口 ─────────────────────────────────
    public interface OnBillClickListener {
        void onBillClick(long localId, String objectId, View itemView);
        void onPhotoClick(String imageUrl, int position);
    }

    public BillAdapter(Context context, OnBillClickListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    // ──────────── 数据更新（外部调用）─────────────────
    /**
     * 提交新数据列表，AsyncListDiffer 在后台线程计算 diff 后主线程局部刷新
     * @param items DateHeader | BillUiModel 混合列表（已由 ViewModel 构建好）
     */
    public void submitList(List<Object> items) {
        differ.submitList(items);
    }

    // ──────────── RecyclerView 基础方法 ───────────────
    @Override public int getItemCount()            { return differ.getCurrentList().size(); }
    @Override public int getItemViewType(int pos)  {
        return differ.getCurrentList().get(pos) instanceof DateHeader
                ? TYPE_DATE_HEADER : TYPE_BILL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE_HEADER) {
            return new DateHeaderVH(ItemBillDateHeaderBinding.inflate(
                    LayoutInflater.from(context), parent, false));
        } else {
            return new BillVH(ItemBillBinding.inflate(
                    LayoutInflater.from(context), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = differ.getCurrentList().get(position);
        if (holder instanceof DateHeaderVH) {
            ((DateHeaderVH) holder).bind((DateHeader) item);
        } else {
            ((BillVH) holder).bind((BillUiModel) item);
        }
    }

    // ──────────── DateHeader ViewHolder ───────────────
    static class DateHeaderVH extends RecyclerView.ViewHolder {
        private final ItemBillDateHeaderBinding b;

        DateHeaderVH(ItemBillDateHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(DateHeader h) {
            b.tvDate.setText(h.dateText);
            b.tvExpense.setText(h.expenseText);
            b.tvIncome.setText(h.incomeText);
        }
    }

    // ──────────── Bill ViewHolder ──────────────────────
    class BillVH extends RecyclerView.ViewHolder {
        private final ItemBillBinding b;
        private PhotoAdapter photoAdapter;

        BillVH(ItemBillBinding b) {
            super(b.getRoot());
            this.b = b;

            // 图片内嵌 RecyclerView 一次性配置
            b.ivBillImage.setLayoutManager(
                    new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            b.ivBillImage.setHasFixedSize(true);
            b.ivBillImage.setRecycledViewPool(sharedPhotoPool);
        }

        void bind(BillUiModel model) {
            // ── 基础字段（纯赋值）────────────────────
            b.tvTime.setText(model.timeText);
            b.tvCategory.setText(model.categoryName);
            b.tvAmount.setText(model.amountText);
            b.tvAmount.setTextColor(model.amountColor);

            // 分类图标
            ImageLoaderUtils.loadThumbnail(context, model.categoryIconUrl, b.ivCategoryIcon);

            // 备注
            if (!model.remarkText.isEmpty()) {
                b.tvRemark.setVisibility(View.VISIBLE);
                b.tvRemark.setText("备注：" + model.remarkText);
            } else {
                b.tvRemark.setVisibility(View.GONE);
            }

            // 位置
            if (!model.locationText.isEmpty()) {
                b.layoutLocation.setVisibility(View.VISIBLE);
                b.tvLocation.setText(model.locationText);
            } else {
                b.layoutLocation.setVisibility(View.GONE);
            }

            // ── 图片列表 ──────────────────────────────
            if (!model.imageUrls.isEmpty()) {
                b.ivBillImage.setVisibility(View.VISIBLE);
                if (photoAdapter == null) {
                    photoAdapter = new PhotoAdapter(context, new ArrayList<>(), false,
                            new PhotoAdapter.OnPhotoClickListener() {
                                @Override
                                public void onPhotoClick(String url, int pos) {
                                    if (listener != null) listener.onPhotoClick(url, pos);
                                }
                                @Override public void onDeleteClick(int pos) { }
                            });
                    b.ivBillImage.setAdapter(photoAdapter);
                }
                photoAdapter.setPhotos(model.imageUrls);
            } else {
                b.ivBillImage.setVisibility(View.GONE);
            }

            // ── 时间轴连线控制 ────────────────────────
            // 上连线：该条账单不是当天第一笔时才显示
            b.timelineLineTop.setVisibility(model.isFirstOfDay ? View.INVISIBLE : View.VISIBLE);
            // 下连线：该条账单不是当天最后一笔时才显示
            b.timelineLine.setVisibility(model.isLastOfDay ? View.INVISIBLE : View.VISIBLE);

            // ── 点击事件 ─────────────────────────────
            b.cardView.setTransitionName("bill_card_" + model.diffKey);
            b.cardView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onBillClick(model.localId, model.objectId, b.cardView);
            });
        }
    }
}