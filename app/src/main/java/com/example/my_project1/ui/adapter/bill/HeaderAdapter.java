package com.example.my_project1.ui.adapter.bill;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemHomeHeaderBinding;
import com.example.my_project1.ui.viewmodel.billvm.HeaderUiModel;

import io.reactivex.annotations.NonNull;

/**
 * HeaderAdapter - 首页顶部概览卡片
 * -------------------------------------------------------
 * 永远只有 0 或 1 个 item；数据通过 setHeader() 刷新。
 * 与 BillAdapter、FooterAdapter 通过 ConcatAdapter 组合使用。
 */
public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderVH> {

    private OnRefreshClickListener refreshClickListener;

    private HeaderUiModel data = new HeaderUiModel(
            "本月支出",
            "¥0.00",
            "¥0.00",
            "¥0.00"
    );


    public interface OnRefreshClickListener {
        void onRefreshClick();
    }

    public void setRefreshClickListener(OnRefreshClickListener l) {
        this.refreshClickListener = l;
    }

    /** 更新数据并局部刷新 */
    public void setHeader(HeaderUiModel model) {
        this.data = model;
        notifyItemChanged(0);
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

    class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemHomeHeaderBinding b;

        HeaderVH(ItemHomeHeaderBinding binding) {
            super(binding.getRoot());
            this.b = binding;
            // 刷新按钮点击
            b.ivRefresh.setOnClickListener(v -> {
                if (refreshClickListener != null) refreshClickListener.onRefreshClick();
            });
        }

        void bind(HeaderUiModel model) {
            b.tvAssetType.setText(model.assetTypeText);
            b.tvTotalAmount.setText(model.totalExpenseText);
            b.tvTotalCount.setText(model.totalIncomeText);
            b.tvDailyAverage.setText(model.balanceText);
        }
    }
}