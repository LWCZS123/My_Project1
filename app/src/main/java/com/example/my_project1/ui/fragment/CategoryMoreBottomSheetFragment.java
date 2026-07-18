package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.my_project1.R;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


public class CategoryMoreBottomSheetFragment extends BottomSheetDialogFragment {

    private  String title,categoryName,categoryIconUrl,type;
    private long categoryId = -1;
    private long subcategoryId = -1;
    private TextView tv_category,tv_category_name;
    private ImageView iv_category_icon;
    private CardView btnEdit,btnDelete;
    private CategoryViewModel categoryViewModel;
    private SubCategoryViewModel subCategoryViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        categoryViewModel = new ViewModelProvider(requireActivity())
                .get(CategoryViewModel.class);
        subCategoryViewModel = new ViewModelProvider(requireActivity())
                .get(SubCategoryViewModel.class);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_category_more_bottom_sheet,
                container, false);


        tv_category = view.findViewById(R.id.tv_category);
        tv_category_name = view.findViewById(R.id.tv_category_name);
        iv_category_icon = view.findViewById(R.id.tv_category_icon);
        btnEdit = view.findViewById(R.id.btn_edit_category);
        btnDelete = view.findViewById(R.id.btn_delete_category);

        Bundle args = getArguments();
        if (args != null){
            title = args.getString("title","分类标题");
            categoryName = args.getString("categoryName");
            categoryIconUrl = args.getString("categoryIconUrl");
            categoryId = args.getLong("categoryId", -1);
            subcategoryId  = args.getLong("subcategoryId",-1);
            type = args.getString("type","category");
            Log.d("CategoryMoreBottomSheetFragment",
                    categoryIconUrl != null ? categoryIconUrl : "categoryIconUrl is null");


        }
        tv_category.setText(title);
        tv_category_name.setText(categoryName);

        Object source = ImageLoaderUtils.getGlideSource(requireContext(),categoryIconUrl);

        Glide.with(requireContext())
                .load(source)
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本
                .skipMemoryCache(false)                   // 使用内存缓存
                .into(iv_category_icon);

        btnEdit.setOnClickListener(v -> showEditDialog());
        btnDelete.setOnClickListener(v -> showDeleteDialog());



        return view;

    }

    /**删除分类*/
    private void showDeleteDialog() {
        if ("category".equals(type)) {
            // 删除一级分类（根据 ID）
            if (categoryId != -1) {
                categoryViewModel.deleteCategoryById(categoryId);
                Toast.makeText(requireContext(), "分类已删除", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "分类 ID 无效", Toast.LENGTH_SHORT).show();
            }
        } else if ("subcategory".equals(type)) {
            if (subcategoryId != -1){
                subCategoryViewModel.deleteSubCategoryById(subcategoryId);
                Toast.makeText(requireContext(), "子分类已删除", Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(requireContext(), "子分类 ID 无效", Toast.LENGTH_SHORT).show();

            }


        }
        dismiss();
    }

    /** 弹出修改分类的 BottomSheet */
    private void showEditDialog() {
        CategoryAddBottomSheetFragment dialog = new CategoryAddBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("title", "编辑分类");
        args.putString("categoryName", categoryName);
        args.putString("iconUri", categoryIconUrl);
        dialog.setArguments(args);

        dialog.setOnCategoryAddedListener((name, excludeBudget, iconUrl) -> {
            if ("category".equals(type)) {
                // 一级分类
                categoryViewModel.updateCategorySafe(categoryId, name, iconUrl, excludeBudget);
            } else if ("subcategory".equals(type)) {
                // 二级分类
                subCategoryViewModel.updateSubCategorySafe(subcategoryId, name, iconUrl,excludeBudget);
            }
            Toast.makeText(requireContext(), "分类已修改", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        dialog.show(getParentFragmentManager(), "edit_category");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheetDialog != null){

                    bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                            R.drawable.bg_bottom_sheet1));
                    // 默认展开 BottomSheet
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
        });


        return bottomSheetDialog;

    }
}