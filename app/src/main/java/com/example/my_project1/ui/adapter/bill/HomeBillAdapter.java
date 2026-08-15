package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ItemBillBinding;
import com.example.my_project1.databinding.ItemTransationDateHeaderBinding;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.HomeBillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.Objects;

/**
 * HomeBillAdapter - 首页扁平化账单适配器
 * 🚀 彻底消除嵌套 RecyclerView，实现高性能滚动
 */
public class HomeBillAdapter extends PagingDataAdapter<HomeBillUiModel, RecyclerView.ViewHolder> {

    private final Context context;
    private final BillAdapter.OnBillClickListener listener;
    private final boolean isSearchMode;

    public HomeBillAdapter(Context context, BillAdapter.OnBillClickListener listener) {
        this(context, listener, false);
    }

    public HomeBillAdapter(Context context, BillAdapter.OnBillClickListener listener, boolean isSearchMode) {
        super(new DiffUtil.ItemCallback<HomeBillUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull HomeBillUiModel oldItem, @NonNull HomeBillUiModel newItem) {
                if (oldItem.type != newItem.type) return false;
                if (oldItem.type == HomeBillUiModel.TYPE_DATE_HEADER) {
                    return Objects.equals(oldItem.dateKey, newItem.dateKey);
                }
                return oldItem.billItem != null && newItem.billItem != null && 
                       oldItem.billItem.localId == newItem.billItem.localId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull HomeBillUiModel oldItem, @NonNull HomeBillUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.context = context;
        this.listener = listener;
        this.isSearchMode = isSearchMode;
    }

    @Override
    public int getItemViewType(int position) {
        HomeBillUiModel item = getItem(position);
        return item != null ? item.type : HomeBillUiModel.TYPE_BILL_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == HomeBillUiModel.TYPE_DATE_HEADER) {
            return new HeaderVH(ItemTransationDateHeaderBinding.inflate(inflater, parent, false));
        } else {
            // 🚀 优化：使用 FrameLayout 包裹 SwipeMenuLayout 以解决侧滑泄露和边距问题
            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(context);
            wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            
            ItemBillBinding binding = ItemBillBinding.inflate(inflater, wrapper, true);
            return new BillVH(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HomeBillUiModel item = getItem(position);
        if (item == null) {
            return;
        }
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(item);
        } else if (holder instanceof BillVH) {
            ((BillVH) holder).bind(item.billItem, item.isLastInDay);
        }
    }

    private static void setMargins(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            Context context = v.getContext();
            p.setMargins(dp2px(context, l), dp2px(context, t), dp2px(context, r), dp2px(context, b));
            v.requestLayout();
        }
    }
    
    private static int dp2px(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemTransationDateHeaderBinding b;

        HeaderVH(ItemTransationDateHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(HomeBillUiModel model) {
            b.tvDate.setText(model.dateText != null ? model.dateText : "");
            String summary = (model.expenseText != null ? model.expenseText : "") + "  " + 
                            (model.incomeText != null ? model.incomeText : "");
            b.tvSummary.setText(summary);
            
            // 🚀 保持和首页大卡片一致的 16dp 边距
            b.getRoot().setBackgroundResource(R.drawable.bg_item_top);
            setMargins(b.getRoot(), 16, 12, 16, 0);

            b.tvDate.setTextSize(14f);
            b.tvSummary.setTextSize(11f);
            
            b.getRoot().setPadding(dp2px(b.getRoot().getContext(), 16), 
                                 dp2px(b.getRoot().getContext(), 12), 
                                 dp2px(b.getRoot().getContext(), 16), 
                                 dp2px(b.getRoot().getContext(), 4));
        }
    }

    class BillVH extends RecyclerView.ViewHolder {
        private final ItemBillBinding b;
        private final View itemViewWrapper;

        BillVH(ItemBillBinding b) {
            super(b.getRoot().getParent() instanceof View ? (View)b.getRoot().getParent() : b.getRoot());
            this.b = b;
            this.itemViewWrapper = itemView;
        }

        void bind(BillUiModel bill, boolean isLast) {
            if (bill == null) return;

            // 🚀 优化：边距设置在 Wrapper 上，SwipeMenuLayout 内部保持铺满，解决侧滑露底问题
            setMargins(itemViewWrapper, 16, 0, 16, isLast ? 12 : 0);
            
            b.billDivider.setVisibility(View.VISIBLE);
            if (isLast) {
                b.contentView.setBackgroundResource(R.drawable.bg_item_bottom);
                b.billDivider.setBackgroundColor(Color.WHITE); // 最后一项将分割线设为白色（隐形），保持高度一致
            } else {
                b.contentView.setBackgroundColor(Color.WHITE);
                b.billDivider.setBackgroundColor(Color.parseColor("#F0F0F0"));
            }
            
            b.tvCategory.setText(bill.categoryName);
            b.tvAmount.setText(bill.amountText);
            b.tvAmount.setTextColor(bill.amountColor);
            
            String subInfo = bill.timeText;
            if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                subInfo += " | " + bill.remarkText;
            }
            b.tvSubInfo.setText(subInfo);
            b.tvAccount.setText(bill.accountName);

            // 转账逻辑 (使用新布局字段)
            if (bill.billType == 2 || bill.billType == 3) {
                b.ivTransferArrow.setVisibility(View.VISIBLE);
                b.tvToAccount.setVisibility(View.VISIBLE);
                b.tvToAccount.setText(bill.toAccountName);
            } else {
                b.ivTransferArrow.setVisibility(View.GONE);
                b.tvToAccount.setVisibility(View.GONE);
            }

            ImageLoaderUtils.loadHomeBillCategoryIcon(context, bill.categoryIconUrl, b.ivCategoryIcon);

            // 背景色
            if (bill.categoryIconBackgroundColor != null && !bill.categoryIconBackgroundColor.isEmpty()) {
                try {
                    int color = Color.parseColor(bill.categoryIconBackgroundColor);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(color);
                    b.ivCategoryIcon.setBackground(gd);
                } catch (Exception e) {
                    b.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
                }
            } else {
                b.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
            }

            // 图片展示逻辑 (1-3张)
            if (bill.imageUrls != null && !bill.imageUrls.isEmpty()) {
                b.llBillImages.setVisibility(View.VISIBLE);
                int size = bill.imageUrls.size();
                b.ivBillImage1.setVisibility(View.VISIBLE);
                ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(0), b.ivBillImage1);
                b.ivBillImage1.setOnClickListener(v -> { if (listener != null) listener.onPhotoClick(bill.imageUrls.get(0), 0); });

                if (size >= 2) {
                    b.ivBillImage2.setVisibility(View.VISIBLE);
                    ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(1), b.ivBillImage2);
                    b.ivBillImage2.setOnClickListener(v -> { if (listener != null) listener.onPhotoClick(bill.imageUrls.get(1), 1); });
                } else b.ivBillImage2.setVisibility(View.GONE);

                if (size >= 3) {
                    b.ivBillImage3.setVisibility(View.VISIBLE);
                    ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(2), b.ivBillImage3);
                    b.ivBillImage3.setOnClickListener(v -> { if (listener != null) listener.onPhotoClick(bill.imageUrls.get(2), 2); });
                } else b.ivBillImage3.setVisibility(View.GONE);

                if (size > 3) {
                    b.tvBillImageMore.setVisibility(View.VISIBLE);
                    b.tvBillImageMore.setText("+" + (size - 3));
                    b.tvBillImageMore.setOnClickListener(v -> {
                        if (listener != null) listener.onBillClick(bill.localId, bill.objectId, b.getRoot());
                    });
                } else {
                    b.tvBillImageMore.setVisibility(View.GONE);
                }
            } else {
                b.llBillImages.setVisibility(View.GONE);
                b.tvBillImageMore.setVisibility(View.GONE);
            }

            b.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onBillClick(bill.localId, bill.objectId, b.getRoot());
            });

            b.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onBillDelete(bill);
                b.swipeLayout.quickClose();
            });
            b.btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onBillEdit(bill);
                b.swipeLayout.quickClose();
            });
            b.btnRefund.setOnClickListener(v -> {
                if (listener != null) listener.onBillRefund(bill);
                b.swipeLayout.quickClose();
            });
        }
    }
}
