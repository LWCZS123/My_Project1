package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.annotations.NonNull;

/**
 * IconGridAdapter（改造版）
 * ─────────────────────────────────────────────────────────────
 * 复用于：详情页（ViewPager2 内 RecyclerView）/ 搜索结果页。
 *
 * 新增能力：
 * ① 多选模式支持
 *   - isMultiSelectMode 标志控制选择框显示
 *   - selectedIds 由外部 SelectionManager 注入（Set<String> 快照）
 *   - 长按 → onLongClick 回调
 *   - 单击 → 多选模式下 onItemClick 回调，非多选模式走原有逻辑
 *
 * ② 精准 payload 刷新（彻底消除闪烁）
 *   PAYLOAD_SELECTION  → 只刷新选中状态覆盖层，不重绘整个 item
 *   PAYLOAD_MODE_CHANGE → 多选模式切换，批量更新选择框可见性
 *
 * ③ 性能优化
 *   - 关闭 Glide 淡入动画：GlideImageLoader.loadThumbnailNoAnim()
 *   - setHasStableIds(true) + DiffUtil 配合，彻底消除无关刷新
 *   - 图标加载失败兜底占位图
 *
 * ④ 接口分离
 *   OnIconClickListener     → 普通点击（原有）
 *   OnIconLongClickListener → 长按（新增）
 *   OnSelectionClickListener → 多选模式单击（新增）
 */
public class IconGridAdapter extends ListAdapter<IconItem, IconGridAdapter.ViewHolder> {

    // ──────────────────────────────────────────────────────────
    // Payload 常量（精准刷新用）
    // ──────────────────────────────────────────────────────────

    /** 只刷新选中状态（选中覆盖层），不重绘整个 item */
    public static final String PAYLOAD_SELECTION    = "payload_selection";
    /** 多选模式切换，批量刷新选择框可见性 */
    public static final String PAYLOAD_MODE_CHANGE  = "payload_mode_change";

    // ──────────────────────────────────────────────────────────
    // 接口
    // ──────────────────────────────────────────────────────────

    public interface OnIconClickListener {
        void onIconClick(IconItem item);
    }

    public interface OnIconLongClickListener {
        /** 长按回调，返回 true 表示已消费 */
        boolean onIconLongClick(IconItem item);
    }

    /** 多选模式下单击选中/取消选中 */
    public interface OnSelectionClickListener {
        void onSelectionClick(IconItem item, boolean isSelected);
    }

    // ──────────────────────────────────────────────────────────
    // 状态
    // ──────────────────────────────────────────────────────────

    private final boolean showCircleBg;

    /** 是否处于多选模式（由 ViewModel observe 后注入，不直接依赖 VM）*/
    private boolean isMultiSelectMode = false;

    /** 当前选中 ID 集合快照（只读引用，由外部注入）*/
    private Set<String> selectedIds = Collections.emptySet();

    private OnIconClickListener      clickListener;
    private OnIconLongClickListener   longClickListener;
    private OnSelectionClickListener  selectionClickListener;

    // ──────────────────────────────────────────────────────────
    // 构造
    // ──────────────────────────────────────────────────────────

    /**
     * @param showCircleBg true=显示圆形背景（详情页），false=不显示（搜索页）
     */
    public IconGridAdapter(boolean showCircleBg) {
        super(DIFF_CALLBACK);
        this.showCircleBg = showCircleBg;
        setHasStableIds(true);
    }

    // ──────────────────────────────────────────────────────────
    // 外部注入接口
    // ──────────────────────────────────────────────────────────

    public void setOnIconClickListener(OnIconClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnIconLongClickListener(OnIconLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnSelectionClickListener(OnSelectionClickListener listener) {
        this.selectionClickListener = listener;
    }

    // ──────────────────────────────────────────────────────────
    // 多选状态更新（由 Fragment/Activity observe SelectionManager 后调用）
    // ──────────────────────────────────────────────────────────

    /**
     * 切换多选模式（同时重置选中状态）。
     * 使用 PAYLOAD_MODE_CHANGE 精准刷新所有 item，只更新选择框可见性，
     * 不重绘图标图片，彻底消除切换模式时的图片闪烁。
     */
    public void setMultiSelectMode(boolean multiSelectMode, Set<String> newSelectedIds) {
        this.isMultiSelectMode = multiSelectMode;
        this.selectedIds       = newSelectedIds != null ? newSelectedIds : Collections.emptySet();
        // payload 刷新：只更新模式相关 UI，不整体 rebind
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_MODE_CHANGE);
    }

    /**
     * 仅更新选中状态集合（多选模式内 toggle 时调用）。
     * 使用 PAYLOAD_SELECTION 精准刷新，只更新选中覆盖层。
     */
    public void updateSelectedIds(Set<String> newSelectedIds) {
        Set<String> oldIds = this.selectedIds;
        this.selectedIds   = newSelectedIds != null ? newSelectedIds : Collections.emptySet();

        // 只刷新状态有变化的 item（新增选中 or 取消选中）
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            IconItem item = getItem(i);
            if (item == null) continue;
            boolean wasSelected = oldIds.contains(item.getId());
            boolean isSelected  = this.selectedIds.contains(item.getId());
            if (wasSelected != isSelected) {
                notifyItemChanged(i, PAYLOAD_SELECTION);
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // RecyclerView.Adapter 实现
    // ──────────────────────────────────────────────────────────

    @Override
    public long getItemId(int position) {
        IconItem item = getItem(position);
        // 使用 hash 确保 stableId 稳定（避免 notifyDataSetChanged 时 ViewHolder 复用错乱）
        return item != null ? item.getId().hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_icon_grid, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // 全量 bind（首次创建或结构性刷新）
        holder.bindFull(getItem(position), isMultiSelectMode, selectedIds);
    }

    /**
     * payload 刷新：只更新变化的部分，不重绘图标图片。
     * 这是消除闪烁的核心手段。
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // 无 payload，走全量 bind
            onBindViewHolder(holder, position);
            return;
        }

        IconItem item = getItem(position);
        if (item == null) return;

        for (Object payload : payloads) {
            if (PAYLOAD_SELECTION.equals(payload)) {
                // 只更新选中覆盖层，图标图片不动
                holder.bindSelectionState(selectedIds.contains(item.getId()));

            } else if (PAYLOAD_MODE_CHANGE.equals(payload)) {
                // 只更新多选模式 UI（选择框可见性 + 选中状态）
                holder.bindModeChange(isMultiSelectMode, selectedIds.contains(item.getId()));
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // DiffUtil
    // ──────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<IconItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<IconItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull IconItem a, @NonNull IconItem b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull IconItem a, @NonNull IconItem b) {
                    return a.getId().equals(b.getId())
                            && a.getName().equals(b.getName());
                }
            };

    // ──────────────────────────────────────────────────────────
    // ViewHolder
    // ──────────────────────────────────────────────────────────

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView   ivIcon;
        private final TextView    tvName;
        private final FrameLayout iconContainer;

        /** 右上角选择框（CheckBox 样式）*/
        private final ImageView   ivCheckbox;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon            = itemView.findViewById(R.id.iv_icon);
            tvName            = itemView.findViewById(R.id.tv_icon_name);
            iconContainer     = itemView.findViewById(R.id.icon_container);
            ivCheckbox        = itemView.findViewById(R.id.iv_checkbox);
        }

        /** 全量 bind（首次绑定或列表数据变化） */
        void bindFull(IconItem item, boolean multiSelectMode, Set<String> selectedIds) {
            if (item == null) return;

            // 1. 加载图标（关闭 Glide 淡入动画，防止重绑时闪烁）
            GlideImageLoader.loadThumbnailNoAnim(itemView.getContext(), item.getThumbUrl(), ivIcon);

            // 2. 圆形背景
            if (showCircleBg) {
                iconContainer.setBackgroundResource(R.drawable.bg_icon_circle);
            } else {
                iconContainer.setBackground(null);
            }

            // 3. 名称
            tvName.setText(item.getName());

            // 4. 多选状态 UI
            boolean isSelected = selectedIds.contains(item.getId());
            bindModeChange(multiSelectMode, isSelected);

            // 5. 点击事件
            setupClickListeners(item);
        }

        /** 只更新选中状态覆盖层（PAYLOAD_SELECTION） */
        void bindSelectionState(boolean isSelected) {

            if (ivCheckbox != null) {
                ivCheckbox.setImageResource(isSelected
                        ? R.drawable.ic_checkbox_checked
                        : R.drawable.ic_checkbox_unchecked);
            }
        }

        /** 多选模式切换时更新（PAYLOAD_MODE_CHANGE） */
        void bindModeChange(boolean multiSelectMode, boolean isSelected) {
            if (ivCheckbox != null) {
                ivCheckbox.setVisibility(multiSelectMode ? View.VISIBLE : View.GONE);
                if (multiSelectMode) {
                    ivCheckbox.setImageResource(isSelected
                            ? R.drawable.ic_checkbox_checked
                            : R.drawable.ic_checkbox_unchecked);
                }
            }

        }

        /** 设置点击/长按事件 */
        private void setupClickListeners(IconItem item) {
            itemView.setOnClickListener(v -> {
                if (isMultiSelectMode) {
                    // 多选模式：切换选中
                    boolean nowSelected = !selectedIds.contains(item.getId());
                    if (selectionClickListener != null) {
                        selectionClickListener.onSelectionClick(item, nowSelected);
                    }
                } else {
                    // 普通模式：原有点击逻辑
                    if (clickListener != null) {
                        clickListener.onIconClick(item);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    return longClickListener.onIconLongClick(item);
                }
                return false;
            });
        }
    }
}