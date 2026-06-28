package com.example.my_project1.ui.adapter.budget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.ItemCategoryBudgetBinding;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.Locale;

import io.reactivex.annotations.NonNull;

/**
 * CategoryBudgetAdapter
 *
 * 修复：
 *  1. 分类图标 / 名称缺失问题：
 *     - 使用 {@link OnItemClickListener#onRequestCategoryInfo} 回调按需查询，
 *       当 item 绑定时若 category 信息不完整则通知 Activity 补充后重绑。
 *     - 图标加载前添加 null / empty 防护，默认图标兜底。
 *  2. item 整体点击跳转分类预算详情页（通过 onItemClick 回调）。
 *  3. 进度条样式与 BudgetActivity 保持完全一致。
 */
public class CategoryBudgetAdapter
        extends ListAdapter<CategoryBudgetAdapter.CategoryBudgetItem,
        CategoryBudgetAdapter.VH> {

    // ════════════════════════════════════════════════════════
    //  数据 VO
    // ════════════════════════════════════════════════════════

    public static class CategoryBudgetItem {
        public Budget   budget;
        public Category category;
        public double   spentAmount;

        public CategoryBudgetItem(Budget budget, Category category, double spentAmount) {
            this.budget      = budget;
            this.category    = category;
            this.spentAmount = spentAmount;
        }

        /** 判断分类信息是否完整（名称与图标均有效） */
        public boolean isCategoryInfoComplete() {
            return category != null
                    && category.getName() != null
                    && !category.getName().isEmpty()
                    && !"未知分类".equals(category.getName());
        }
    }

    // ════════════════════════════════════════════════════════
    //  回调接口
    // ════════════════════════════════════════════════════════

    public interface OnItemClickListener {
        /** 编辑按钮点击 */
        void onEdit(CategoryBudgetItem item);
        /** 删除按钮点击 */
        void onDelete(int budgetId);
        /** item 整体点击（跳转详情页） */
        void onItemClick(CategoryBudgetItem item);
        /**
         * 当某个 item 的分类信息不完整时，Adapter 通过此回调请求宿主（Activity）
         * 根据 targetId 补充分类名称 & 图标，宿主完成后调用 notifyItemChanged。
         */
        void onRequestCategoryInfo(String targetId, int adapterPosition);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    // ════════════════════════════════════════════════════════
    //  DiffUtil
    // ════════════════════════════════════════════════════════

    private static final DiffUtil.ItemCallback<CategoryBudgetItem> DIFF =
            new DiffUtil.ItemCallback<CategoryBudgetItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryBudgetItem o,
                                               @NonNull CategoryBudgetItem n) {
                    return o.budget.getId() == n.budget.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryBudgetItem o,
                                                  @NonNull CategoryBudgetItem n) {
                    return Double.compare(o.budget.getAmount(), n.budget.getAmount()) == 0
                            && o.budget.getPeriod() == n.budget.getPeriod()
                            && Double.compare(o.spentAmount, n.spentAmount) == 0
                            // 分类信息也纳入比对，保证补全后能触发刷新
                            && o.isCategoryInfoComplete() == n.isCategoryInfoComplete()
                            && safeEquals(getCatName(o), getCatName(n))
                            && safeEquals(getCatIcon(o), getCatIcon(n));
                }

                private boolean safeEquals(String a, String b) {
                    return (a == null && b == null) || (a != null && a.equals(b));
                }

                private String getCatName(CategoryBudgetItem item) {
                    return item.category != null ? item.category.getName() : null;
                }

                private String getCatIcon(CategoryBudgetItem item) {
                    return item.category != null ? item.category.getIconUri() : null;
                }
            };

    public CategoryBudgetAdapter() {
        super(DIFF);
    }

    // ════════════════════════════════════════════════════════
    //  ViewHolder 创建 & 绑定
    // ════════════════════════════════════════════════════════

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryBudgetBinding b = ItemCategoryBudgetBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CategoryBudgetItem item     = getItem(pos);
        Budget             budget   = item.budget;
        Category           category = item.category;
        Context            ctx      = h.b.getRoot().getContext();

        // ── 分类名称 & 图标（带完整性检测与兜底）─────────────
        if (category != null
                && category.getName() != null
                && !category.getName().isEmpty()
                && !"未知分类".equals(category.getName())) {
            // 分类信息完整
            h.b.tvCategoryName.setText(category.getName());
            loadIcon(ctx, category.getIconUri(), h.b.ivCategoryIcon);
        } else {
            // 信息不完整：先用占位显示，并请求宿主补充
            h.b.tvCategoryName.setText("加载中…");
            h.b.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);

            String targetId = budget.getTargetId();
            if (listener != null && targetId != null && !targetId.isEmpty()) {
                // 通知宿主按 targetId 查找分类信息，完成后宿主调用 notifyItemChanged(pos)
                h.b.getRoot().post(() -> listener.onRequestCategoryInfo(targetId, h.getAdapterPosition()));
            }
        }

        // ── 周期标签 ────────────────────────────────────────
        h.b.tvPeriod.setText("/" + Budget.getPeriodLabel(budget.getPeriod()));

        // ── 金额计算 ─────────────────────────────────────────
        double  budgetAmt  = budget.getAmount();
        double  spent      = item.spentAmount;
        double  available  = budgetAmt - spent;
        boolean overBudget = available < 0;

        h.b.tvBudgetAmount.setText(
                String.format(Locale.getDefault(), "预算 ¥%.2f", budgetAmt));
        h.b.tvSpent.setText(
                String.format(Locale.getDefault(), "已用 ¥%.2f", spent));

        if (!overBudget) {
            h.b.tvAvailable.setText(
                    String.format(Locale.getDefault(), "可用 ¥%.2f", available));
            h.b.tvAvailable.setTextColor(0xFF4CAF50);
        } else {
            h.b.tvAvailable.setText(
                    String.format(Locale.getDefault(), "超支 ¥%.2f", Math.abs(available)));
            h.b.tvAvailable.setTextColor(0xFFFF5252);
        }

        // ── 进度条（与 BudgetActivity 完全一致）──────────────
        int progress = (budgetAmt > 0) ? (int) Math.min(spent / budgetAmt * 100, 100) : 0;
        ProgressBar progressBar = h.b.progressBar;
        progressBar.setProgress(progress);

        if (progress < 100 && !overBudget) {
            progressBar.setProgressDrawable(
                    ctx.getResources().getDrawable(R.drawable.bg_progress_budget_blue));
        } else {
            progressBar.setProgressDrawable(
                    ctx.getResources().getDrawable(R.drawable.progress_budget_red));
        }

        // ── 点击事件 ─────────────────────────────────────────
        if (listener != null) {
            // 整体 item 点击 → 跳转详情页
            h.b.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            // 编辑按钮
            h.b.btnEdit.setOnClickListener(v   -> listener.onEdit(item));
            // 删除按钮
            h.b.btnDelete.setOnClickListener(v -> listener.onDelete(budget.getId()));
        }
    }

    // ════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════

    /**
     * 安全加载图标：
     *  - iconUri 为空 → 显示默认图标
     *  - iconUri 为纯数字 → 当作资源 id 加载
     *  - 否则当作 URL / 文件路径加载
     */
    private void loadIcon(Context ctx, String iconUri, android.widget.ImageView iv) {
        if (iconUri == null || iconUri.isEmpty()) {
            iv.setImageResource(R.drawable.ic_category_default);
            return;
        }
        try {
            int resId = Integer.parseInt(iconUri);
            if (resId <= 0) {
                iv.setImageResource(R.drawable.ic_category_default);
                return;
            }
            GlideImageLoader.load1(ctx, iv, resId);
        } catch (NumberFormatException e) {
            GlideImageLoader.load(ctx, iconUri, iv);
        }
    }

    // ════════════════════════════════════════════════════════
    //  ViewHolder
    // ════════════════════════════════════════════════════════

    static class VH extends RecyclerView.ViewHolder {
        final ItemCategoryBudgetBinding b;

        VH(ItemCategoryBudgetBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}