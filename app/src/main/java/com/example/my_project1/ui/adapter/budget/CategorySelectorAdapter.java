package com.example.my_project1.ui.adapter.budget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ItemCategoryBudgetSelectorBinding;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * CategorySelectorAdapter
 * ─────────────────────────────────────────────────────────────
 * 分类宫格选择适配器（供 AddCategoryBudgetFragment 使用）。
 * 已改用 ViewBinding（ItemCategoryBudgetSelectorBinding）。
 *
 * 布局：item_category_budget_selector.xml
 * 网格：配合外部 GridLayoutManager(context, 4) 每行 4 列
 *
 * 交互：单选，再次点击同一项取消选中。
 */
public class CategorySelectorAdapter
        extends RecyclerView.Adapter<CategorySelectorAdapter.VH> {

    // ── 数据模型 ──────────────────────────────────────────────

    public static class Item {
        public final String cloudId;
        public final String name;
        public final String iconUri;
        /** 已设预算描述，例如 "¥100/月"，null 表示未设置 */
        public final String budgetTag;

        public Item(String cloudId, String name, String iconUri, String budgetTag) {
            this.cloudId   = cloudId;
            this.name      = name;
            this.iconUri   = iconUri;
            this.budgetTag = budgetTag;
        }
    }

    // ── 回调 ──────────────────────────────────────────────────

    public interface OnItemClickListener {
        void onItemClick(Item item);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    // ── 状态 ──────────────────────────────────────────────────

    private final List<Item> data       = new ArrayList<>();
    private       String     selectedId = null;

    // ── 数据更新 ──────────────────────────────────────────────

    public void submitList(List<Item> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    /** 编辑模式回填选中 */
    public void setSelected(String cloudId) {
        this.selectedId = cloudId;
        notifyDataSetChanged();
    }

    public String getSelectedId() {
        return selectedId;
    }

    // ── RecyclerView ──────────────────────────────────────────

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryBudgetSelectorBinding b = ItemCategoryBudgetSelectorBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Item    item    = data.get(pos);
        Context ctx     = h.b.getRoot().getContext();
        boolean checked = item.cloudId != null && item.cloudId.equals(selectedId);

        // ── 容器背景（选中/默认）──────────────────────────────
        h.b.llItemRoot.setBackgroundResource(checked
                ? R.drawable.bg_category_item_selected
                : R.drawable.bg_category_item_normal);

        // ── 图标 ──────────────────────────────────────────────
        if (item.iconUri != null && !item.iconUri.isEmpty()) {
            try {
                int resId = Integer.parseInt(item.iconUri);
                GlideImageLoader.load1(ctx, h.b.ivCatIcon, resId);
            } catch (NumberFormatException e) {
                GlideImageLoader.load(ctx, item.iconUri, h.b.ivCatIcon);
            }
        } else {
            h.b.ivCatIcon.setImageResource(R.drawable.ic_category_default);
        }

        // 图标背景：选中蓝色，否则默认灰
        h.b.flIconBg.setBackgroundResource(checked
                ? R.drawable.bg_circle_icon_selected
                : R.drawable.bg_circle_icon);

        // ── 名称 ──────────────────────────────────────────────
        h.b.tvCatName.setText(item.name);
        h.b.tvCatName.setTextColor(checked ? 0xFF4285F4 : 0xFF333333);

        // ── 预算标签 ──────────────────────────────────────────
        if (item.budgetTag != null && !item.budgetTag.isEmpty()) {
            h.b.tvCatBudgetTag.setText(item.budgetTag);
            h.b.tvCatBudgetTag.setTextColor(checked ? 0xFF4285F4 : 0xFF5B8DEF);
        } else {
            h.b.tvCatBudgetTag.setText("未设置");
            h.b.tvCatBudgetTag.setTextColor(0xFFAAAAAA);
        }

        // ── 选中指示器 ────────────────────────────────────────
        h.b.ivCatCheck.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);

        // ── 点击 ──────────────────────────────────────────────
        h.b.getRoot().setOnClickListener(v -> {
            selectedId = (item.cloudId != null && item.cloudId.equals(selectedId))
                    ? null          // 再次点击取消
                    : item.cloudId;
            notifyDataSetChanged();
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    // ── ViewHolder ────────────────────────────────────────────

    static class VH extends RecyclerView.ViewHolder {
        final ItemCategoryBudgetSelectorBinding b;

        VH(ItemCategoryBudgetSelectorBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}