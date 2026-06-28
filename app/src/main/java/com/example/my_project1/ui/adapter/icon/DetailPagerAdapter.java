package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel.PageState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.annotations.NonNull;

/**
 * DetailPagerAdapter（改造版）
 * ─────────────────────────────────────────────────────────────
 * 在原有分页状态机基础上新增：
 *
 * ① 多选状态传递
 *   - setMultiSelectMode / updateSelectedIds 下发到每个 PageViewHolder
 *     内的 IconGridAdapter，走 payload 精准刷新，不重建 ViewHolder。
 *
 * ② 接口桥接
 *   - OnIconLongClickListener / OnSelectionClickListener 透传到
 *     子 IconGridAdapter，由 IconDetailFragment 统一注册。
 *
 * ③ 性能优化保持不变
 *   - sharedPool 复用
 *   - offscreenPageLimit 由 Fragment 控制（≥3）
 *   - payload notifyItemChanged 防闪烁
 *   - setHasStableIds(true)
 */
public class DetailPagerAdapter extends RecyclerView.Adapter<DetailPagerAdapter.PageViewHolder> {

    public static final int SPAN_COUNT = 5;

    // ──────────────────────────────────────────────────────────
    // 接口（原有 + 新增）
    // ──────────────────────────────────────────────────────────

    public interface OnIconClickListener {
        void onIconClick(IconItem item);
    }

    public interface OnIconLongClickListener {
        boolean onIconLongClick(IconItem item);
    }

    public interface OnSelectionClickListener {
        void onSelectionClick(IconItem item, boolean isSelected);
    }

    // ──────────────────────────────────────────────────────────
    // 数据
    // ──────────────────────────────────────────────────────────

    private android.util.SparseArray<PageState> pageStates = new android.util.SparseArray<>();
    private int totalPages = 0;

    /** 多选状态（由 Fragment observe SelectionManager 后注入） */
    private boolean     isMultiSelectMode = false;
    private Set<String> selectedIds       = Collections.emptySet();

    private OnIconClickListener      iconClickListener;
    private OnIconLongClickListener   longClickListener;
    private OnSelectionClickListener  selectionClickListener;

    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    /** 持有所有活跃 ViewHolder 引用，用于多选状态批量下发 */
    private final android.util.SparseArray<PageViewHolder> activeHolders =
            new android.util.SparseArray<>();

    public DetailPagerAdapter() {
        setHasStableIds(true);
    }

    // ──────────────────────────────────────────────────────────
    // 监听器注册
    // ──────────────────────────────────────────────────────────

    public void setOnIconClickListener(OnIconClickListener listener) {
        this.iconClickListener = listener;
    }

    public void setOnIconLongClickListener(OnIconLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnSelectionClickListener(OnSelectionClickListener listener) {
        this.selectionClickListener = listener;
    }

    // ──────────────────────────────────────────────────────────
    // 分页数据更新（原有逻辑，不改动）
    // ──────────────────────────────────────────────────────────

    public void updatePageStates(android.util.SparseArray<PageState> snapshot, int totalPages) {
        int oldTotal   = this.totalPages;
        this.totalPages = totalPages;
        this.pageStates = snapshot != null ? snapshot : new android.util.SparseArray<>();

        if (oldTotal != totalPages) {
            notifyDataSetChanged();
        } else {
            for (int i = 0; i < totalPages; i++) {
                notifyItemChanged(i, "state_changed");
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // ★ 新增：多选状态下发
    // ──────────────────────────────────────────────────────────

    /**
     * 切换多选模式，下发到所有活跃的 PageViewHolder。
     * 使用 PAYLOAD_MODE_CHANGE，子 adapter 只更新选择框可见性，
     * 不重建 ViewHolder，不重新加载图片。
     */
    public void setMultiSelectMode(boolean multiSelectMode, Set<String> newSelectedIds) {
        this.isMultiSelectMode = multiSelectMode;
        this.selectedIds       = newSelectedIds != null ? newSelectedIds : Collections.emptySet();

        // 批量下发到所有活跃 PageViewHolder
        for (int i = 0; i < activeHolders.size(); i++) {
            PageViewHolder holder = activeHolders.valueAt(i);
            if (holder != null) {
                holder.gridAdapter.setMultiSelectMode(multiSelectMode, selectedIds);
            }
        }
    }

    /**
     * 仅更新选中状态（多选模式内 toggle 时调用）。
     * 使用 PAYLOAD_SELECTION，只刷新选中覆盖层。
     */
    public void updateSelectedIds(Set<String> newSelectedIds) {
        this.selectedIds = newSelectedIds != null ? newSelectedIds : Collections.emptySet();

        for (int i = 0; i < activeHolders.size(); i++) {
            PageViewHolder holder = activeHolders.valueAt(i);
            if (holder != null) {
                holder.gridAdapter.updateSelectedIds(selectedIds);
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // RecyclerView.Adapter 实现
    // ──────────────────────────────────────────────────────────

    @Override
    public int getItemCount() { return totalPages; }

    @Override
    public long getItemId(int position) { return position; }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        PageState state = pageStates.get(position);
        if (state == null) state = PageState.idle();
        holder.bind(state);
        // 注入当前多选状态（ViewPager2 创建新页面时立即同步）
        holder.gridAdapter.setMultiSelectMode(isMultiSelectMode, selectedIds);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position,
                                 @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty()) {
            PageState state = pageStates.get(position);
            if (state == null) state = PageState.idle();
            holder.bind(state);
            // payload 刷新也要同步多选状态
            holder.gridAdapter.setMultiSelectMode(isMultiSelectMode, selectedIds);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull PageViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_ID) {
            activeHolders.put(pos, holder);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull PageViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        int pos = holder.getBindingAdapterPosition();
        activeHolders.remove(pos);
    }

    // ──────────────────────────────────────────────────────────
    // ViewHolder
    // ──────────────────────────────────────────────────────────

    class PageViewHolder extends RecyclerView.ViewHolder {

        private final RecyclerView  recyclerView;
        private final ProgressBar   progressBar;
        private final View          errorView;
        final         IconGridAdapter gridAdapter;  // package-private 供外部访问

        PageViewHolder(View itemView) {
            super(itemView);
            recyclerView = itemView.findViewById(R.id.rv_page_icons);
            progressBar  = itemView.findViewById(R.id.progress_page);
            errorView    = itemView.findViewById(R.id.view_page_error);

            GridLayoutManager lm = new GridLayoutManager(itemView.getContext(), SPAN_COUNT);
            recyclerView.setLayoutManager(lm);
            recyclerView.setRecycledViewPool(sharedPool);
            recyclerView.setHasFixedSize(true);

            // ★ 关闭 RecyclerView 默认 item 动画，防止选中状态变化时闪烁
            recyclerView.setItemAnimator(null);

            gridAdapter = new IconGridAdapter(true);

            // 桥接三种点击事件
            gridAdapter.setOnIconClickListener(item -> {
                if (iconClickListener != null) iconClickListener.onIconClick(item);
            });
            gridAdapter.setOnIconLongClickListener(item -> {
                if (longClickListener != null) return longClickListener.onIconLongClick(item);
                return false;
            });
            gridAdapter.setOnSelectionClickListener((item, isSelected) -> {
                if (selectionClickListener != null)
                    selectionClickListener.onSelectionClick(item, isSelected);
            });

            recyclerView.setAdapter(gridAdapter);
        }

        void bind(PageState state) {
            switch (state.status) {
                case LOADING:
                case IDLE:
                    progressBar.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    if (errorView != null) errorView.setVisibility(View.GONE);
                    gridAdapter.submitList(null);
                    break;

                case LOADED:
                    progressBar.setVisibility(View.GONE);
                    if (errorView != null) errorView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    List<IconItem> items = state.items != null
                            ? state.items : new ArrayList<>();
                    // submitList 内部有 DiffUtil，相同数据不触发刷新
                    gridAdapter.submitList(new ArrayList<>(items));
                    break;

                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    if (errorView != null) errorView.setVisibility(View.VISIBLE);
                    gridAdapter.submitList(null);
                    break;
            }
        }
    }
}