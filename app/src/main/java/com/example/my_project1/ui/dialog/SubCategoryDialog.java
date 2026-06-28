package com.example.my_project1.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.databinding.DialogSubcategoryBinding;
import com.example.my_project1.databinding.ItemSubcategoryGridBinding;
import com.example.my_project1.databinding.ItemSubcategoryPageBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class SubCategoryDialog extends Dialog {

    private final CategoryWithSubCategories categoryData;
    private OnSubCategorySelectedListener listener;
    private DialogSubcategoryBinding binding;
    private final View anchorView;

    // 配置：4行5列，一页20个
    private static final int SPAN_COUNT = 5;
    private static final int PAGE_ROWS = 4;
    private static final int ITEMS_PER_PAGE = SPAN_COUNT * PAGE_ROWS;

    // 边距
    private static final int SCREEN_MARGIN = 15;
    private static final int ARROW_PADDING = 10;

    public interface OnSubCategorySelectedListener {
        void onSubCategorySelected(SubCategory subCategory);
    }

    public SubCategoryDialog(Context context, CategoryWithSubCategories categoryData, View anchorView) {
        super(context);
        this.categoryData = categoryData;
        this.anchorView = anchorView;
    }

    public void setOnSubCategorySelectedListener(OnSubCategorySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        binding = DialogSubcategoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.TOP | Gravity.START);
        }

        initViewPager2();
    }

    @Override
    public void show() {
        super.show();
        positionPopupImmediate();
    }

    private void initViewPager2() {
        if (categoryData.subCategories == null || categoryData.subCategories.isEmpty()) {
            return;
        }

        // 分页
        List<List<SubCategory>> pages = splitListByPage(categoryData.subCategories);

        SubCategoryPagerAdapter adapter = new SubCategoryPagerAdapter(pages);
        binding.viewPager2.setAdapter(adapter);

        // 解决 ViewPager2  wrap_content 高度自适应
        binding.viewPager2.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    // 数据分页
    private List<List<SubCategory>> splitListByPage(List<SubCategory> list) {
        List<List<SubCategory>> pages = new ArrayList<>();
        int size = list.size();
        for (int i = 0; i < size; i += ITEMS_PER_PAGE) {
            int end = Math.min(i + ITEMS_PER_PAGE, size);
            pages.add(new ArrayList<>(list.subList(i, end)));
        }
        return pages;
    }

    // ==================== ViewPager2 适配器 ====================
    private class SubCategoryPagerAdapter extends RecyclerView.Adapter<PageVH> {
        private final List<List<SubCategory>> pages;

        public SubCategoryPagerAdapter(List<List<SubCategory>> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSubcategoryPageBinding binding = ItemSubcategoryPageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new PageVH(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH holder, int position) {
            holder.setData(pages.get(position));
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    // ==================== 每页 ViewHolder ====================
    private class PageVH extends RecyclerView.ViewHolder {
        private final ItemSubcategoryPageBinding binding;
        private final GridAdapter adapter;

        public PageVH(ItemSubcategoryPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            GridLayoutManager manager = new GridLayoutManager(itemView.getContext(), SPAN_COUNT);
            binding.rvSubcategory.setLayoutManager(manager);

            adapter = new GridAdapter();
            binding.rvSubcategory.setAdapter(adapter);
        }

        public void setData(List<SubCategory> data) {
            adapter.setNewData(data);
        }
    }

    // ==================== 网格适配器 ====================
    private class GridAdapter extends RecyclerView.Adapter<GridVH> {
        private List<SubCategory> list = new ArrayList<>();

        public void setNewData(List<SubCategory> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public GridVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSubcategoryGridBinding binding = ItemSubcategoryGridBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new GridVH(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull GridVH holder, int position) {
            holder.bind(list.get(position));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    // ==================== 子项 ====================
    private class GridVH extends RecyclerView.ViewHolder {
        private final ItemSubcategoryGridBinding binding;

        public GridVH(ItemSubcategoryGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(SubCategory sub) {
            binding.tvSubcategoryName.setText(formatSubCategoryName(sub.getName()));
            loadIcon(sub.getIconUri(), binding.ivSubcategoryIcon);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSubCategorySelected(sub);
                }
                dismiss();
            });
        }
    }

    // ==================== 定位弹窗（不变） ====================
    private void positionPopupImmediate() {
        if (anchorView == null) return;
        Window window = getWindow();
        if (window == null) return;

        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        float density = getContext().getResources().getDisplayMetrics().density;
        int screenMarginPx = (int) (SCREEN_MARGIN * density);
        int arrowPaddingPx = (int) (ARROW_PADDING * density);

        int[] anchorLoc = new int[2];
        anchorView.getLocationOnScreen(anchorLoc);
        int anchorCenterX = anchorLoc[0] + anchorView.getWidth() / 2;

        int maxPopupWidth = screenWidth - 2 * screenMarginPx;

        binding.ivArrow.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int arrowWidth = binding.ivArrow.getMeasuredWidth();

        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        binding.getRoot().measure(widthSpec, heightSpec);

        int measuredW = binding.getRoot().getMeasuredWidth();
        int popupW = Math.min(measuredW, maxPopupWidth);

        int idealX = anchorCenterX - popupW / 2;
        int minX = screenMarginPx;
        int maxX = screenWidth - popupW - screenMarginPx;
        int realX = Math.max(minX, Math.min(idealX, maxX));

        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = realX;
        params.y = anchorLoc[1] + anchorView.getHeight() + 10;
        window.setAttributes(params);

        positionArrowImmediate(anchorCenterX, realX, popupW, arrowWidth, arrowPaddingPx);
    }

    private void positionArrowImmediate(int anchorCenterX, int popupX, int popupW,
                                        int arrowW, int arrowPadding) {
        float arrowX = anchorCenterX - popupX - arrowW / 2f;
        float min = arrowPadding;
        float max = popupW - arrowW - arrowPadding;
        arrowX = Math.max(min, Math.min(arrowX, max));
        binding.ivArrow.setTranslationX(arrowX);
    }

    // ==================== 工具方法 ====================
    private void loadIcon(String uri, android.widget.ImageView iv) {
        Object source = ImageLoaderUtils.getGlideSource(getContext(), uri);
        if (source instanceof String) {
            try {
                uri = new URI((String) source).toASCIIString();
            } catch (Exception ignored) {}
            ImageLoaderUtils.load(getContext(), uri, iv, R.drawable.ic_default_category, R.drawable.ic_default_category);
        } else if (source instanceof Uri) {

        } else if (source instanceof Integer) {
            iv.setImageResource((Integer) source);
        }
    }

    private String formatSubCategoryName(String name) {
        if (name == null) return "";
        name = name.trim();
        return name.length() > 4 ? name.substring(0, 4) : name;
    }
}