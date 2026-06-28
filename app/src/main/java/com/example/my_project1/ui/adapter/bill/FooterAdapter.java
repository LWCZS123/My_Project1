package com.example.my_project1.ui.adapter.bill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.ui.viewmodel.billvm.PagingState;

import io.reactivex.annotations.NonNull;

/**
 * FooterAdapter - 分页加载状态 Footer
 * -------------------------------------------------------
 * 根据 PagingState 展示三种状态：
 *   LOADING  → ProgressBar 旋转动画
 *   NO_MORE  → "— 没有更多了 —" 文字
 *   ERROR    → "加载失败，点击重试" 按钮
 *   IDLE     → 隐藏（itemCount=0）
 */
public class FooterAdapter extends RecyclerView.Adapter<FooterAdapter.FooterVH> {

    private PagingState state = PagingState.IDLE;
    private OnRetryClickListener retryListener;

    public interface OnRetryClickListener {
        void onRetryClick();
    }

    public void setRetryClickListener(OnRetryClickListener l) {
        this.retryListener = l;
    }

    /** 更新分页状态，触发 Footer 刷新 */
    public void setState(PagingState newState) {
        PagingState old = this.state;
        this.state = newState;

        boolean wasVisible = old != PagingState.IDLE;
        boolean nowVisible = newState != PagingState.IDLE;

        if (!wasVisible && nowVisible) {
            notifyItemInserted(0);
        } else if (wasVisible && !nowVisible) {
            notifyItemRemoved(0);
        } else if (wasVisible) {
            notifyItemChanged(0);
        }
    }

    public PagingState getState() { return state; }

    @Override
    public int getItemCount() {
        return state == PagingState.IDLE ? 0 : 1;
    }

    @NonNull
    @Override
    public FooterVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bill_footer, parent, false);
        return new FooterVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FooterVH holder, int position) {
        holder.bind(state);
    }

    class FooterVH extends RecyclerView.ViewHolder {
        private final ProgressBar progressBar;
        private final TextView    tvMessage;
        private final Button      btnRetry;

        FooterVH(View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.footerProgressBar);
            tvMessage   = itemView.findViewById(R.id.footerTvMessage);
            btnRetry    = itemView.findViewById(R.id.footerBtnRetry);

            btnRetry.setOnClickListener(v -> {
                if (retryListener != null) retryListener.onRetryClick();
            });
        }

        void bind(PagingState s) {
            progressBar.setVisibility(s == PagingState.LOADING ? View.VISIBLE : View.GONE);
            tvMessage.setVisibility(s == PagingState.NO_MORE  ? View.VISIBLE : View.GONE);
            btnRetry.setVisibility(s == PagingState.ERROR     ? View.VISIBLE : View.GONE);

            if (s == PagingState.NO_MORE) {
                tvMessage.setText("— 没有更多了 —");
            }
        }
    }
}