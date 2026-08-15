package com.example.my_project1.ui.adapter.bill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;

/**
 * FooterAdapter - 分页加载状态 Footer
 * -------------------------------------------------------
 * Renders Paging 3 append state:
 *   LOADING  → ProgressBar 旋转动画
 *   NO_MORE  → "— 没有更多了 —" 文字
 *   ERROR    → "加载失败，点击重试" 按钮
 *   IDLE     → 隐藏（itemCount=0）
 */
public class FooterAdapter extends LoadStateAdapter<FooterAdapter.FooterVH> {

    private OnRetryClickListener retryListener;

    public interface OnRetryClickListener {
        void onRetryClick();
    }

    public void setRetryClickListener(OnRetryClickListener l) {
        this.retryListener = l;
    }

    @Override
    public FooterVH onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bill_footer, parent, false);
        return new FooterVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FooterVH holder, @NonNull LoadState loadState) {
        holder.bind(loadState);
    }

    @Override
    public boolean displayLoadStateAsItem(@NonNull LoadState loadState) {
        if (loadState instanceof LoadState.Loading || loadState instanceof LoadState.Error) {
            return true;
        }
        return loadState instanceof LoadState.NotLoading
                && ((LoadState.NotLoading) loadState).getEndOfPaginationReached();
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

        void bind(LoadState state) {
            boolean isLoading = state instanceof LoadState.Loading;
            boolean isError = state instanceof LoadState.Error;
            boolean isComplete = state instanceof LoadState.NotLoading
                    && ((LoadState.NotLoading) state).getEndOfPaginationReached();

            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            tvMessage.setVisibility(isComplete ? View.VISIBLE : View.GONE);
            btnRetry.setVisibility(isError ? View.VISIBLE : View.GONE);

            if (isComplete) {
                tvMessage.setText("— 没有更多了 —");
            }
        }
    }
}
