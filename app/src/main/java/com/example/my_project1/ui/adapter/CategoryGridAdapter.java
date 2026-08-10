package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.databinding.ItemCategoryGridBinding;
import com.example.my_project1.ui.dialog.SubCategoryDialog;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.reactivex.annotations.NonNull;

/**
 * CategoryGridAdapter - 分类网格适配器（支持编辑模式选中）
 * -------------------------------------------------------
 * ⭐ 修复闪烁：
 * 1. preSelectedCategoryId 通过构造函数传入，第一次 onBindViewHolder 就能
 *    直接绑定出正确的选中态，不再是"先绑定未选中 -> 再notify一次改成选中"。
 * 2. updateData() 去掉 postDelayed(100)，改为在 submitList 的回调
 *    (commitCallback) 里同步执行 findAndSelectCategory，这个回调本身就是
 *    "diff计算完成、已应用到RecyclerView"之后的时机，不需要再猜测延迟时间。
 */
public class CategoryGridAdapter extends ListAdapter<CategoryWithSubCategories, CategoryGridAdapter.ViewHolder> {

    private static final String TAG = "CategoryGridAdapter";

    private Context context;
    private OnCategorySelectedListener listener;

    // 🔹 记录当前选中的 item 位置
    private int selectedPosition = -1;

    // 🔹 记录每个一级分类对应的选中二级分类
    private Map<Long, SubCategory> selectedSubCategoryMap = new HashMap<>();

    // ⭐ 预选中的分类ID（编辑模式）。首次数据到达时会用它直接计算出selectedPosition，
    // 不再有"先渲染未选中，后台再notify改成选中"的中间态。
    private String preSelectedCategoryId;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl, String backgroundColor, boolean excludeBudget);
    }

    public CategoryGridAdapter(Context context) {
        this(context, null);
    }

    /**
     * ⭐ 新增构造函数：创建Adapter时就传入预选中分类ID。
     * Fragment/Activity 在编辑模式下应使用此构造函数，从源头上避免"先创建
     * 未选中的Adapter，再事后调用setSelectedCategory"的两阶段流程。
     */
    public CategoryGridAdapter(Context context, String preSelectedCategoryId) {
        super(new DiffUtil.ItemCallback<CategoryWithSubCategories>() {
            @Override
            public boolean areItemsTheSame(@NonNull CategoryWithSubCategories oldItem, @NonNull CategoryWithSubCategories newItem) {
                return oldItem.category.getId() == newItem.category.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull CategoryWithSubCategories oldItem, @NonNull CategoryWithSubCategories newItem) {
                return Objects.equals(oldItem.category, newItem.category) &&
                        Objects.equals(oldItem.subCategories, newItem.subCategories);
            }
        });
        this.context = context;
        this.preSelectedCategoryId = preSelectedCategoryId;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    /**
     * ⭐ 设置选中的分类（编辑模式使用）。
     * 若此时数据已经加载完成（getItemCount() > 0），立即同步查找并选中；
     * 若数据尚未到达，记录下来，交给 updateData() 的 submitList 回调处理，
     * 不使用任何 postDelayed。
     */
    public void setSelectedCategory(String categoryId) {
        this.preSelectedCategoryId = categoryId;
        if (getItemCount() > 0) {
            findAndSelectCategory(categoryId, getCurrentList(), /*notify=*/true);
        }
        // getItemCount()==0 时，等 updateData() 会处理
    }

    /**
     * @param notify 是否需要用 notifyItemChanged 触发局部刷新。
     *               当这次选中计算发生在“数据首次绑定之前”（例如构造函数传入、
     *               或 submitList 之前），不需要notify，
     *               因为 onBindViewHolder 会直接读取 selectedPosition 完成正确绘制。
     */
    private void findAndSelectCategory(String categoryId, List<CategoryWithSubCategories> data, boolean notify) {
        if (categoryId == null || data == null) return;

        for (int i = 0; i < data.size(); i++) {
            CategoryWithSubCategories item = data.get(i);

            // 1. 一级分类匹配
            if (categoryId.equals(item.category.getCloudId())) {
                applySelection(i, null, notify, data);
                Log.d(TAG, "✅ 选中一级分类: " + item.category.getName() + ", position=" + i);
                return;
            }

            // 2. 二级分类匹配
            if (item.subCategories != null) {
                for (SubCategory subCategory : item.subCategories) {
                    if (categoryId.equals(subCategory.getCloudId())) {
                        applySelection(i, subCategory, notify, data);
                        Log.d(TAG, "✅ 选中二级分类: " + item.category.getName() + "." + subCategory.getName());
                        return;
                    }
                }
            }
        }
        Log.w(TAG, "⚠️ 未找到匹配的分类: " + categoryId);
    }

    private void applySelection(int position, SubCategory subCategory, boolean notify, List<CategoryWithSubCategories> currentData) {
        int oldSelected = selectedPosition;
        selectedPosition = position;

        CategoryWithSubCategories item = currentData.get(position);
        if (subCategory != null) {
            selectedSubCategoryMap.put(item.category.getId(), subCategory);
        } else {
            selectedSubCategoryMap.remove(item.category.getId());
        }

        if (notify) {
            if (oldSelected != -1) notifyItemChanged(oldSelected);
            notifyItemChanged(selectedPosition);
        }
    }

    /**
     * ⭐ 核心修复点：不再依赖 commitCallback 进行二次刷新。
     * 在调用 submitList 之前，先利用新数据 newCategories 预计算出正确的
     * selectedPosition。这样当 ListAdapter 开始绑定第一行数据时，
     * selectedPosition 已经是有意义的值，onBindViewHolder 能一次性
     * 渲染出正确的选中态，从物理上消除了“先显示未选中再闪烁成选中”的问题。
     */
    public void updateData(List<CategoryWithSubCategories> newCategories) {
        if (preSelectedCategoryId != null && newCategories != null) {
            // 在提交列表之前，先同步找出位置
            findAndSelectCategory(preSelectedCategoryId, newCategories, false);
        }
        submitList(newCategories);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryGridBinding binding = ItemCategoryGridBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryWithSubCategories item = getItem(position);
        holder.bind(item, position);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryGridBinding binding;

        ViewHolder(ItemCategoryGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CategoryWithSubCategories categoryData, int position) {
            SubCategory selectedSub = selectedSubCategoryMap.get(categoryData.category.getId());

            if (position == selectedPosition && selectedSub != null) {
                binding.tvCategoryName.setText(categoryData.category.getName() + "." + selectedSub.getName());
                loadIcon(selectedSub.getIconUri(), binding.ivCategoryIcon);
            } else {
                binding.tvCategoryName.setText(categoryData.category.getName());
                loadIcon(categoryData.category.getIconUri(), binding.ivCategoryIcon);
            }

            boolean hasSubCategories = categoryData.subCategories != null && !categoryData.subCategories.isEmpty();
            binding.ivDots.setVisibility(hasSubCategories ? android.view.View.VISIBLE : android.view.View.GONE);

            if (position == selectedPosition) {
                binding.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round_selected);
                binding.tvCategoryName.setTextColor(context.getColor(R.color.accent_color));
            } else {
                // 🎨 不再根据分类数据设置图标背景色，统一使用默认圆形背景
                binding.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round);
                binding.tvCategoryName.setTextColor(context.getColor(R.color.black));
            }

            itemView.setOnClickListener(v -> {
                if (hasSubCategories) {
                    showSubCategoryDialog(categoryData, position);
                } else {
                    int oldSelected = selectedPosition;
                    selectedPosition = position;
                    selectedSubCategoryMap.remove(categoryData.category.getId());

                    if (oldSelected != -1) notifyItemChanged(oldSelected);
                    notifyItemChanged(selectedPosition);

                    binding.tvCategoryName.setText(categoryData.category.getName());
                    loadIcon(categoryData.category.getIconUri(), binding.ivCategoryIcon);

                    if (listener != null) {
                        listener.onCategorySelected(
                                categoryData.category.getName(),
                                categoryData.category.getCloudId(),
                                categoryData.category.getIconUri(),
                                categoryData.category.getIconBackgroundColor(),
                                categoryData.category.isExcludeBudget()
                        );
                    }
                }
            });
        }

        private void showSubCategoryDialog(CategoryWithSubCategories categoryData, int position) {
            SubCategoryDialog dialog = new SubCategoryDialog(context, categoryData, itemView);
            dialog.setOnSubCategorySelectedListener(subCategory -> {
                int oldSelected = selectedPosition;
                selectedPosition = position;
                selectedSubCategoryMap.put(categoryData.category.getId(), subCategory);

                if (oldSelected != -1) notifyItemChanged(oldSelected);
                notifyItemChanged(selectedPosition);

                binding.tvCategoryName.setText(categoryData.category.getName() + "." + subCategory.getName());
                loadIcon(subCategory.getIconUri(), binding.ivCategoryIcon);

                if (listener != null) {
                    listener.onCategorySelected(
                            categoryData.category.getName() + "." + subCategory.getName(),
                            subCategory.getCloudId(),
                            subCategory.getIconUri(),
                            subCategory.getIconBackgroundColor(),
                            subCategory.isExcludeBudget()
                    );
                }
            });
            dialog.show();
        }

        private void loadIcon(String iconUri, android.widget.ImageView imageView) {
            Object iconSource = ImageLoaderUtils.getGlideSource(context, iconUri);

            if (iconSource instanceof String) {
                // ⭐ 使用优化后的 loadCategoryIcon，关闭 dontAnimate，防止图片加载闪烁
                ImageLoaderUtils.loadCategoryIcon(context, (String) iconSource, imageView);

            } else if (iconSource instanceof Uri) {
                Glide.with(context)
                        .load((Uri) iconSource)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .dontAnimate() // 🔑 同样关闭动画
                        .placeholder(R.drawable.ic_default_category)
                        .error(R.drawable.ic_default_category)
                        .into(imageView);

            } else if (iconSource instanceof Integer) {
                imageView.setImageResource((Integer) iconSource);
            }
        }
    }
}