package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
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
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


public class CategoryMoreBottomSheetFragment extends BottomSheetDialogFragment {

    private String title, categoryName, categoryIconUrl, type;
    private long categoryId = -1;
    private long subcategoryId = -1;
    private View btnModify, btnSort, btnPromote, btnMigrate, btnBelongTo, btnViewBills, btnArchive, btnDelete, btnCancel;
    private View dividerPromote;
    private TextView tvCategoryName;
    private ImageView ivCategoryIcon;
    private CategoryViewModel categoryViewModel;
    private SubCategoryViewModel subCategoryViewModel;
    private String categoryCloudId;

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
        View view = inflater.inflate(R.layout.layout_category_action_sheet,
                container, false);

        tvCategoryName = view.findViewById(R.id.tv_category_name);
        ivCategoryIcon = view.findViewById(R.id.iv_category_icon);
        btnModify = view.findViewById(R.id.btn_modify);
        btnSort = view.findViewById(R.id.btn_sort);
        btnPromote = view.findViewById(R.id.btn_promote);
        dividerPromote = view.findViewById(R.id.divider_promote);
        btnMigrate = view.findViewById(R.id.btn_migrate);
        btnBelongTo = view.findViewById(R.id.btn_belong_to);
        btnViewBills = view.findViewById(R.id.btn_view_bills);
        btnArchive = view.findViewById(R.id.btn_archive);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnCancel = view.findViewById(R.id.btn_cancel);

        Bundle args = getArguments();
        if (args != null) {
            title = args.getString("title", "分类标题");
            categoryName = args.getString("categoryName");
            categoryIconUrl = args.getString("categoryIconUrl");
            categoryId = args.getLong("categoryId", -1);
            subcategoryId = args.getLong("subcategoryId", -1);
            type = args.getString("type", "category");
            categoryCloudId = args.getString("categoryCloudId");
        }

        tvCategoryName.setText(categoryName);
        Object source = ImageLoaderUtils.getGlideSource(requireContext(), categoryIconUrl);
        Glide.with(requireContext())
                .load(source)
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .into(ivCategoryIcon);

        // 仅在子分类时显示 "调整为一级分类"
        if ("subcategory".equals(type)) {
            btnPromote.setVisibility(View.VISIBLE);
            dividerPromote.setVisibility(View.VISIBLE);
        }

        btnModify.setOnClickListener(v -> showEditDialog());
        btnSort.setOnClickListener(v -> Toast.makeText(requireContext(), "排序功能开发中", Toast.LENGTH_SHORT).show());
        btnPromote.setOnClickListener(v -> Toast.makeText(requireContext(), "调整功能开发中", Toast.LENGTH_SHORT).show());
        btnMigrate.setOnClickListener(v -> Toast.makeText(requireContext(), "迁移功能开发中", Toast.LENGTH_SHORT).show());
        btnBelongTo.setOnClickListener(v -> Toast.makeText(requireContext(), "归属功能开发中", Toast.LENGTH_SHORT).show());
        btnViewBills.setOnClickListener(v -> viewBills());
        btnArchive.setOnClickListener(v -> Toast.makeText(requireContext(), "归档功能开发中", Toast.LENGTH_SHORT).show());
        btnDelete.setOnClickListener(v -> showDeleteDialog());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void viewBills() {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.my_project1.ui.activity.CategoryBillsActivity.class);
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_NAME, categoryName);
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_ICON, categoryIconUrl);
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_ID, categoryCloudId);
        // 如果是支出分类，type=0，收入分类 type=1。这里简单判断。
        int billType = title.contains("支出") || "expense".equals(argsType()) ? 0 : 1;
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_BILL_TYPE, billType);
        startActivity(intent);
        dismiss();
    }

    private String argsType() {
        Bundle args = getArguments();
        return args != null ? args.getString("categoryType") : "";
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

    /** 弹出修改分类的 IconSelectionActivity */
    private void showEditDialog() {
        android.content.Intent intent = new android.content.Intent(requireContext(), IconSelectionActivity.class);
        intent.putExtra(IconSelectionActivity.EXTRA_MODE, "modify");
        intent.putExtra(IconSelectionActivity.EXTRA_TYPE, type);
        intent.putExtra(IconSelectionActivity.EXTRA_TITLE, "编辑分类");
        intent.putExtra(IconSelectionActivity.EXTRA_NAME, categoryName);
        intent.putExtra(IconSelectionActivity.EXTRA_ICON_URI, categoryIconUrl);
        intent.putExtra(IconSelectionActivity.EXTRA_ID, "category".equals(type) ? categoryId : subcategoryId);
        // 需要从数据库获取背景色和预算状态，这里如果 fragment 没带，可能需要额外查询或作为参数传进来
        // 简单处理：如果 model 有缓存，可以从那里取，但此处 arguments 似乎只有这几样。
        // 为保持功能完整，理想是在这里根据 id 查询完整的 Category/SubCategory。
        
        startActivity(intent);
        dismiss();
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