package com.example.my_project1.ui.adapter.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.databinding.ItemSearchHistoryBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * SearchHistoryAdapter - 搜索历史适配器 (优化版)
 * -------------------------------------------------------
 * 显示搜索历史记录列表
 *
 * ✅ 优化点:
 * 1. 支持动态宽度 (根据文字长度自适应)
 * 2. 最大显示8个字符 (超出显示...)
 * 3. 适配瀑布流布局
 */
public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private static final String TAG = "SearchHistoryAdapter";
    private static final int MAX_KEYWORD_LENGTH = 8; // 最大显示字符数

    private final Context context;
    private final List<SearchHistory> historyList = new ArrayList<>();
    private OnHistoryClickListener listener;

    public interface OnHistoryClickListener {
        void onHistoryClick(SearchHistory history);
        void onDeleteClick(SearchHistory history);
    }

    public SearchHistoryAdapter(Context context, OnHistoryClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchHistoryBinding binding = ItemSearchHistoryBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(historyList.get(position));
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    /**
     * 设置搜索历史数据
     */
    public void setHistoryList(List<SearchHistory> list) {
        historyList.clear();
        if (list != null && !list.isEmpty()) {
            historyList.addAll(list);
        }
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchHistoryBinding binding;

        ViewHolder(ItemSearchHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SearchHistory history) {
            String keyword = history.getKeyword();

            // ✅ 优化: 处理文字长度
            if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
                // 超过最大长度,截断并添加省略号
                String displayText = keyword.substring(0, MAX_KEYWORD_LENGTH) + "...";
                binding.tvKeyword.setText(displayText);
            } else {
                // 正常显示
                binding.tvKeyword.setText(keyword);
            }

            // 点击item - 执行搜索 (使用完整关键词)
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHistoryClick(history);
                }
            });

            // 点击删除按钮
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(history);
                }
            });
        }
    }
}