package com.example.my_project1.ui.adapter.budget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.ItemCategoryBudgetBinding;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CategoryBudgetAdapter
 *
 * 修复点：
 *  1. 新增 updateCategoryInfo() 方法，通过创建新对象 + submitList 触发 DiffUtil 刷新。
 *  2. 将 safeEquals 从 DIFF 匿名类中提取为静态方法，供外部复用。
 *  3. 增加 pendingRequestIds 防抖集合，避免滚动复用时重复触发分类信息请求。
 *  4. 补全缺失的 import 和类型声明。
 */
public class CategoryBudgetAdapter
        extends ListAdapter<CategoryBudgetAdapter.CategoryBudgetItem,
        CategoryBudgetAdapter.VH> {

    // ════════════════════════════════════════════════════════
    //  数据 VO
    // ════════════════════════════════════════════════════════

    public static class CategoryBudgetItem {
        public final Budget budget;
        public final Category category;
        public final double spentAmount;

        public CategoryBudgetItem(Budget budget, Category category, double spentAmount) {
            this.budget = budget;
            this.category = category;
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
        void onEdit(CategoryBudgetItem item);
        void onDelete(int budgetId);
        void onItemClick(CategoryBudgetItem item);
        void onRequestCategoryInfo(String targetId, int adapterPosition);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    // ✅ 防抖集合：记录已发起过请求的 targetId，避免滚动时重复触发
    private final Set<String> pendingRequestIds = new HashSet<>();

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
                            && o.isCategoryInfoComplete() == n.isCategoryInfoComplete()
                            // ✅ 现在调用的是外部静态方法
                            && safeEquals(getCatName(o), getCatName(n))
                            && safeEquals(getCatIcon(o), getCatIcon(n));
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
    //  ✅ 按需更新分类信息（ListAdapter 安全写法）
    // ════════════════════════════════════════════════════════

    /**
     * 按需更新指定位置的分类名称和图标。
     * ListAdapter 的列表不可变，必须创建新对象并重新提交才能触发 DiffUtil 刷新。
     */
    public void updateCategoryInfo(int position, String name, String iconUri) {
        if (position < 0 || position >= getCurrentList().size()) return;

        CategoryBudgetItem oldItem = getCurrentList().get(position);

        // 如果信息已经完整且一致，跳过避免无效刷新
        if (oldItem.isCategoryInfoComplete()
                && safeEquals(oldItem.category.getName(), name)
                && safeEquals(oldItem.category.getIconUri(), iconUri)) {
            return;
        }

        // 创建新的 Category 对象
        Category newCategory = new Category();
        newCategory.setCloudId(oldItem.category != null ? oldItem.category.getCloudId() : null);
        newCategory.setName(name);
        newCategory.setIconUri(iconUri);

        // 创建新的 Item 对象以触发 areContentsTheSame 返回 false
        CategoryBudgetItem newItem = new CategoryBudgetItem(
                oldItem.budget, newCategory, oldItem.spentAmount);

        // 复制当前列表 → 替换指定位置 → 重新提交
        List<CategoryBudgetItem> newList = new ArrayList<>(getCurrentList());
        newList.set(position, newItem);
        submitList(newList);

        // 信息已补全，移除防抖标记以便后续可再次更新
        if (oldItem.budget.getTargetId() != null) {
            pendingRequestIds.remove(oldItem.budget.getTargetId());
        }
    }

    // ✅ 提取为静态方法，供 DIFF 和 updateCategoryInfo 共同复用
    private static boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
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
        CategoryBudgetItem item = getItem(pos);
        Budget budget = item.budget;
        Category category = item.category;
        Context ctx = h.b.getRoot().getContext();

        // ── 分类名称 & 图标 ─────────────────────────────────
        if (category != null && category.getName() != null && !category.getName().isEmpty()) {
            h.b.tvCategoryName.setText(category.getName());
            loadIcon(ctx, category.getIconUri(), h.b.ivCategoryIcon);
            // ✅ 信息已完整，移除待请求标记
            if (budget.getTargetId() != null) {
                pendingRequestIds.remove(budget.getTargetId());
            }
        } else {
            h.b.tvCategoryName.setText("加载中…");
            h.b.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);
            // ✅ 防抖：同一个 targetId 只请求一次，避免滚动复用时重复触发
            String targetId = budget.getTargetId();
            if (listener != null && targetId != null && !pendingRequestIds.contains(targetId)) {
                pendingRequestIds.add(targetId);
                h.b.getRoot().post(() -> {
                    int adapterPos = h.getAdapterPosition();
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        listener.onRequestCategoryInfo(targetId, adapterPos);
                    }
                });
            }
        }

        // ── 金额计算 ─────────────────────────────────────────
        double budgetAmt = budget.getAmount();
        double spent = item.spentAmount;
        int progress = (budgetAmt > 0) ? (int) Math.min(spent / budgetAmt * 100, 100) : 0;
        boolean overBudget = spent > budgetAmt;

        h.b.tvSpent.setText(String.format(Locale.getDefault(), "%.0f", spent));
        h.b.tvBudgetHint.setText(String.format(Locale.getDefault(), "%d%% | %.2f",
                (int) (spent / budgetAmt * 100), budgetAmt));

        // ── 背景进度条样式 ────────────────────────────────────
        h.b.pbBgProgress.setProgress(progress);
        if (overBudget) {
            h.b.pbBgProgress.setProgressDrawable(ctx.getDrawable(R.drawable.bg_progress_fill_light));
            h.b.tvSpent.setTextColor(0xFFEB5757);
        } else {
            h.b.pbBgProgress.setProgressDrawable(ctx.getDrawable(R.drawable.bg_progress_fill_blue_light));
            h.b.tvSpent.setTextColor(0xFF333333);
        }

        // ── 分割线逻辑 ──────────────────────────────────────
        h.b.itemDivider.setVisibility(pos == getItemCount() - 1
                ? android.view.View.GONE : android.view.View.VISIBLE);

        // ── 点击事件 ─────────────────────────────────────────
        if (listener != null) {
            h.b.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            h.b.getRoot().setOnLongClickListener(v -> {
                listener.onEdit(item);
                return true;
            });
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
    private void loadIcon(Context ctx, String iconUri, ImageView iv) {
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