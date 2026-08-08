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

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cn.bmob.v3.BmobUser;


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
        } else {
            // 一级分类不显示“归属到其它分类”
            btnBelongTo.setVisibility(View.GONE);
            view.findViewById(R.id.divider_belong_to).setVisibility(View.GONE);
        }

        btnModify.setOnClickListener(v -> showEditDialog());
        btnSort.setOnClickListener(v -> Toast.makeText(requireContext(), "排序功能开发中", Toast.LENGTH_SHORT).show());
        btnPromote.setOnClickListener(v -> showPromoteConfirmDialog());
        btnMigrate.setOnClickListener(v -> showMigrationDialog());
        btnBelongTo.setOnClickListener(v -> showBelongToDialog());
        btnViewBills.setOnClickListener(v -> viewBills());
        btnArchive.setOnClickListener(v -> showArchiveConfirmDialog());
        btnDelete.setOnClickListener(v -> showDeleteDialog());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void showPromoteConfirmDialog() {
        new ConfirmDialog(requireContext())
                .setTitle("晋升分类")
                .setMessage("确定要将「" + categoryName + "」调整为一级分类吗？此操作将同步迁移所属账单。")
                .setConfirmText("确定")
                .setCancelText("取消")
                .setConfirmListener(() -> {
                    String catType = argsType();
                    categoryViewModel.promoteToMainCategory(subcategoryId, catType);
                    dismiss();
                })
                .show();
    }

    private void showArchiveConfirmDialog() {
        if ("category".equals(type)) {
            boolean hasChildren = getArguments() != null && getArguments().getBoolean("hasChildren", false);
            if (hasChildren) {
                new ConfirmDialog(requireContext())
                        .setTitle("归档分类")
                        .setMessage("该分类包含子分类，是否同时归档？")
                        .setConfirmText("同时归档")
                        .setCancelText("仅归档一级")
                        .setConfirmListener(() -> {
                            categoryViewModel.archiveCategory(categoryId, true);
                            dismiss();
                        })
                        .setCancelListener(() -> {
                            categoryViewModel.archiveCategory(categoryId, false);
                            dismiss();
                        })
                        .show();
            } else {
                new ConfirmDialog(requireContext())
                        .setTitle("归档分类")
                        .setMessage("确定要归档分类「" + categoryName + "」吗？")
                        .setConfirmText("归档")
                        .setCancelText("取消")
                        .setConfirmListener(() -> {
                            categoryViewModel.archiveCategory(categoryId, false);
                            dismiss();
                        })
                        .show();
            }
        } else {
            new ConfirmDialog(requireContext())
                    .setTitle("归档分类")
                    .setMessage("确定要归档子分类「" + categoryName + "」吗？")
                    .setConfirmText("归档")
                    .setCancelText("取消")
                    .setConfirmListener(() -> {
                        categoryViewModel.archiveSubCategory(subcategoryId);
                        Toast.makeText(requireContext(), "已归档", Toast.LENGTH_SHORT).show();
                        dismiss();
                    })
                    .show();
        }
    }

    private void showMigrationDialog() {
        String catType = argsType();
        String sourceIdStr = "category".equals(type) ? String.valueOf(categoryCloudId) : String.valueOf(categoryCloudId);
        // 实际上 migrateBills 在 ViewModel 中需要的是 String sourceId (CloudId)
        CategoryMigrationBottomSheetFragment fragment = CategoryMigrationBottomSheetFragment.newInstance("migrate", sourceIdStr, categoryName, catType);
        fragment.show(getParentFragmentManager(), "category_migration");
        dismiss();
    }

    private void showBelongToDialog() {
        String catType = argsType();
        String sourceIdStr = String.valueOf(subcategoryId); // 归属调整需要 long id 进行本地更新
        long currentParentId = getArguments() != null ? getArguments().getLong("parentCategoryId", -1) : -1;
        CategoryMigrationBottomSheetFragment fragment = CategoryMigrationBottomSheetFragment.newInstance("change_parent", sourceIdStr, categoryName, catType);
        Bundle extra = fragment.getArguments();
        if (extra != null) {
            extra.putLong("currentParentId", currentParentId);
        }
        fragment.show(getParentFragmentManager(), "category_belong_to");
        dismiss();
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
        String userId = BmobUser.getCurrentUser().getObjectId();
        categoryViewModel.checkCategoryBills(userId, categoryCloudId, count -> {
            String message = count > 0 
                    ? "该分类下关联了 " + count + " 条账单，删除后这些账单将变成未分类。建议先「迁移数据」或选择「归档」。确定要删除吗？"
                    : "确定要删除该分类吗？";
            
            new ConfirmDialog(requireContext())
                    .setTitle("删除分类")
                    .setMessage(message)
                    .setConfirmText("确定删除")
                    .setCancelText("取消")
                    .setConfirmListener(() -> executeDelete())
                    .show();
        });
    }

    private void executeDelete() {
        if ("category".equals(type)) {
            if (categoryId != -1) {
                categoryViewModel.deleteCategoryById(categoryId);
                Toast.makeText(requireContext(), "分类已标记删除", Toast.LENGTH_SHORT).show();
            }
        } else if ("subcategory".equals(type)) {
            if (subcategoryId != -1){
                subCategoryViewModel.deleteSubCategoryById(subcategoryId);
                Toast.makeText(requireContext(), "子分类已标记删除", Toast.LENGTH_SHORT).show();
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
        
        if (getArguments() != null) {
            intent.putExtra(IconSelectionActivity.EXTRA_EXCLUDE_BUDGET, 
                    getArguments().getBoolean("excludeBudget", false));
            intent.putExtra(IconSelectionActivity.EXTRA_ICON_BG_COLOR,
                    getArguments().getString("categoryIconBg"));
        }

        startActivity(intent);
        dismiss();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                // 默认展开 BottomSheet
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return bottomSheetDialog;
    }
}