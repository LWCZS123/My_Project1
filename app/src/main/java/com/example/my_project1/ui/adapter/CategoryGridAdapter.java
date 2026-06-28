package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.databinding.ItemCategoryGridBinding;
import com.example.my_project1.ui.dialog.SubCategoryDialog;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.annotations.NonNull;

/**
 * CategoryGridAdapter - 分类网格适配器（支持编辑模式选中）
 * -------------------------------------------------------
 * ✅ 原有功能：显示分类、选中状态、子分类弹窗
 * ✅ ⭐ 新增功能：支持通过categoryId设置选中状态（编辑模式使用）
 */
public class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.ViewHolder> {

    private static final String TAG = "CategoryGridAdapter";

    private List<CategoryWithSubCategories> categories;
    private Context context;
    private OnCategorySelectedListener listener;

    // 🔹 记录当前选中的 item 位置
    private int selectedPosition = -1;

    // 🔹 记录每个一级分类对应的选中二级分类
    private Map<Long, SubCategory> selectedSubCategoryMap = new HashMap<>();

    // ⭐ 新增：记录要选中的分类ID（用于编辑模式）
    private String preSelectedCategoryId = null;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl);
    }

    public CategoryGridAdapter(List<CategoryWithSubCategories> categories, Context context) {
        this.categories = categories != null ? categories : new ArrayList<>();
        this.context = context;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    /**
     * ⭐ 新增：设置选中的分类（编辑模式使用）
     * @param categoryId 要选中的分类的cloudId
     */
    public void setSelectedCategory(String categoryId) {
        Log.d(TAG, "⭐ setSelectedCategory: " + categoryId);
        this.preSelectedCategoryId = categoryId;

        // 查找对应的位置并选中
        if (categories != null && !categories.isEmpty()) {
            findAndSelectCategory(categoryId);
        }
    }

    /**
     * ⭐ 新增：根据categoryId查找并选中分类
     */
    private void findAndSelectCategory(String categoryId) {
        if (categoryId == null) {
            return;
        }

        // 遍历所有分类查找匹配的categoryId
        for (int i = 0; i < categories.size(); i++) {
            CategoryWithSubCategories item = categories.get(i);

            // 1. 检查一级分类是否匹配
            if (categoryId.equals(item.category.getCloudId())) {
                int oldSelected = selectedPosition;
                selectedPosition = i;
                selectedSubCategoryMap.remove(item.category.getId());

                if (oldSelected != -1) {
                    notifyItemChanged(oldSelected);
                }
                notifyItemChanged(selectedPosition);

                Log.d(TAG, "✅ 找到并选中一级分类: " + item.category.getName() + ", position=" + i);
                return;
            }

            // 2. 检查二级分类是否匹配
            if (item.subCategories != null && !item.subCategories.isEmpty()) {
                for (SubCategory subCategory : item.subCategories) {
                    if (categoryId.equals(subCategory.getCloudId())) {
                        int oldSelected = selectedPosition;
                        selectedPosition = i;
                        selectedSubCategoryMap.put(item.category.getId(), subCategory);

                        if (oldSelected != -1) {
                            notifyItemChanged(oldSelected);
                        }
                        notifyItemChanged(selectedPosition);

                        Log.d(TAG, "✅ 找到并选中二级分类: " + item.category.getName() + "." +
                                subCategory.getName() + ", position=" + i);
                        return;
                    }
                }
            }
        }

        Log.w(TAG, "⚠️ 未找到匹配的分类: " + categoryId);
    }

    public void updateData(List<CategoryWithSubCategories> newCategories) {
        this.categories = newCategories != null ? newCategories : new ArrayList<>();
        notifyDataSetChanged();

        // ⭐ 数据更新后，如果有预选中的分类ID，重新查找并选中
        if (preSelectedCategoryId != null) {
            Log.d(TAG, "⭐ 数据更新后重新查找选中分类: " + preSelectedCategoryId);
            findAndSelectCategory(preSelectedCategoryId);
        }
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
        CategoryWithSubCategories item = categories.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryGridBinding binding;

        ViewHolder(ItemCategoryGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CategoryWithSubCategories categoryData, int position) {
            // 获取当前一级分类选中的二级分类（如果有）
            SubCategory selectedSub = selectedSubCategoryMap.get(categoryData.category.getId());

            // 显示名称和图标
            if (position == selectedPosition && selectedSub != null) {
                // 显示一级.二级
                binding.tvCategoryName.setText(categoryData.category.getName() + "." + selectedSub.getName());
                loadIcon(selectedSub.getIconUri(), binding.ivCategoryIcon);
            } else {
                // 显示一级分类原始
                binding.tvCategoryName.setText(categoryData.category.getName());
                loadIcon(categoryData.category.getIconUri(), binding.ivCategoryIcon);
            }

            // 是否显示三点指示器
            boolean hasSubCategories = categoryData.subCategories != null && !categoryData.subCategories.isEmpty();
            binding.ivDots.setVisibility(hasSubCategories ? android.view.View.VISIBLE : android.view.View.GONE);

            // ⭐ 设置选中状态UI
            if (position == selectedPosition) {
                binding.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round_selected); // 点亮圆形背景
                binding.tvCategoryName.setTextColor(context.getColor(R.color.accent_color)); // 选中字变亮
            } else {
                binding.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round); // 恢复默认圆形
                binding.tvCategoryName.setTextColor(context.getColor(R.color.black));
            }

            // 点击事件
            itemView.setOnClickListener(v -> {
                if (hasSubCategories) {
                    showSubCategoryDialog(categoryData, position);
                } else {
                    // 点击一级分类，重置之前的选中项
                    int oldSelected = selectedPosition;
                    selectedPosition = position;
                    selectedSubCategoryMap.remove(categoryData.category.getId());

                    if (oldSelected != -1) {
                        notifyItemChanged(oldSelected);
                    }
                    notifyItemChanged(selectedPosition);

                    binding.tvCategoryName.setText(categoryData.category.getName());
                    loadIcon(categoryData.category.getIconUri(), binding.ivCategoryIcon);

                    if (listener != null) {
                        listener.onCategorySelected(
                                categoryData.category.getName(),
                                categoryData.category.getCloudId(),
                                categoryData.category.getIconUri()
                        );
                    }
                }
            });
        }

        private void showSubCategoryDialog(CategoryWithSubCategories categoryData, int position) {
            SubCategoryDialog dialog = new SubCategoryDialog(context, categoryData, itemView);
            dialog.setOnSubCategorySelectedListener(subCategory -> {
                // 设置当前选中状态
                int oldSelected = selectedPosition;
                selectedPosition = position;
                selectedSubCategoryMap.put(categoryData.category.getId(), subCategory);

                // 刷新上一个选中和当前选中
                if (oldSelected != -1) {
                    notifyItemChanged(oldSelected);
                }
                notifyItemChanged(selectedPosition);

                // 更新当前 item 显示
                binding.tvCategoryName.setText(categoryData.category.getName() + "." + subCategory.getName());
                loadIcon(subCategory.getIconUri(), binding.ivCategoryIcon);

                if (listener != null) {
                    listener.onCategorySelected(
                            categoryData.category.getName() + "." + subCategory.getName(),
                            subCategory.getCloudId(),
                            subCategory.getIconUri()
                    );
                }
            });
            dialog.show();
        }

        private void loadIcon(String iconUri, android.widget.ImageView imageView) {
            Object iconSource = ImageLoaderUtils.getGlideSource(context, iconUri);

            if (iconSource instanceof String) {
                String url = (String) iconSource;
                try {
                    url = new URI(url).toASCIIString();
                } catch (Exception ignored) {}
                ImageLoaderUtils.load(context, url, imageView,
                        R.drawable.ic_default_category, R.drawable.ic_default_category);

            } else if (iconSource instanceof Uri) {
                Glide.with(context)
                        .load((Uri) iconSource)
                        .placeholder(R.drawable.ic_default_category)
                        .error(R.drawable.ic_default_category)
                        .into(imageView);

            } else if (iconSource instanceof Integer) {
                imageView.setImageResource((Integer) iconSource);
            }
        }
    }
}