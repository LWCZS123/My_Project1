package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.FragmentSaveCategoryBottomSheetBinding;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

public class SaveCategoryBottomSheet extends BottomSheetDialogFragment {

    private FragmentSaveCategoryBottomSheetBinding binding;
    private IconMarketViewModel viewModel;
    private List<IconItem> selectedItems = new ArrayList<>();

    private String currentType = "expense";
    private boolean isFirstLevel = true;
    private long selectedParentId = -1L;
    private ParentCategoryAdapter parentAdapter;

    public static SaveCategoryBottomSheet newInstance(List<IconItem> items) {
        SaveCategoryBottomSheet f = new SaveCategoryBottomSheet();
        f.selectedItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSaveCategoryBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(IconMarketViewModel.class);

        setupTypeTabsCapsule();
        setupLevelTabsCapsule();
        setupSelectedIconsPreview();
        setupParentCategoryGrid();
        setupConfirmButton();
        observeSaveResult();

        showPanel(true);
        activateTypeTab(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ======================== 收支切换 ========================
    private void setupTypeTabsCapsule() {
        binding.tabExpense.setOnClickListener(v -> {
            currentType = "expense";
            activateTypeTab(true);
            reloadParentCategories();
        });
        binding.tabIncome.setOnClickListener(v -> {
            currentType = "income";
            activateTypeTab(false);
            reloadParentCategories();
        });
    }

    private void activateTypeTab(boolean expenseActive) {
        if (expenseActive) {
            binding.tabExpense.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected1));
            binding.tabExpense.setTextColor(0xFF333333);
            binding.tabIncome.setBackground(null);
            binding.tabIncome.setTextColor(0xFF888888);
        } else {
            binding.tabIncome.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected1));
            binding.tabIncome.setTextColor(0xFF333333);
            binding.tabExpense.setBackground(null);
            binding.tabExpense.setTextColor(0xFF888888);
        }
    }

    // ======================== 一级/二级切换 ========================
    private void setupLevelTabsCapsule() {
        binding.btnTabFirst.setOnClickListener(v -> {
            isFirstLevel = true;
            activateLevelTab(true);
            showPanel(true);
        });
        binding.btnTabSecond.setOnClickListener(v -> {
            isFirstLevel = false;
            activateLevelTab(false);
            showPanel(false);
            reloadParentCategories();
        });
    }

    private void activateLevelTab(boolean firstActive) {
        if (firstActive) {
            binding.btnTabFirst.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected1));
            binding.btnTabFirst.setTextColor(0xFF333333);
            binding.btnTabSecond.setBackground(null);
            binding.btnTabSecond.setTextColor(0xFF888888);
        } else {
            binding.btnTabSecond.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected1));
            binding.btnTabSecond.setTextColor(0xFF333333);
            binding.btnTabFirst.setBackground(null);
            binding.btnTabFirst.setTextColor(0xFF888888);
        }
    }

    private void showPanel(boolean showFirst) {
        binding.panelFirst.setVisibility(showFirst ? View.VISIBLE : View.GONE);
        binding.panelSecond.setVisibility(showFirst ? View.GONE : View.VISIBLE);
        selectedParentId = -1L;
        if (parentAdapter != null) parentAdapter.clearSelection();
    }

    // ======================== 图标预览 ========================
    private void setupSelectedIconsPreview() {
        binding.tvSelectedHint.setText("已选图标（" + selectedItems.size() + " 个）");
        LinearLayout container = binding.llSelectedIcons;
        container.removeAllViews();

        int sizePx = (int) (52 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);

        for (IconItem item : selectedItems) {
            View thumb = LayoutInflater.from(requireContext()).inflate(R.layout.item_icon_grid, container, false);
            ImageView iv = thumb.findViewById(R.id.iv_icon);
            thumb.findViewById(R.id.iv_checkbox).setVisibility(View.GONE);
            Glide.with(this).load(item.getUrl()).into(iv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx + marginPx * 2, ViewGroup.LayoutParams.WRAP_CONTENT);
            thumb.setLayoutParams(lp);
            container.addView(thumb);
        }
    }

    // ======================== 父分类列表 ========================
    private void setupParentCategoryGrid() {
        parentAdapter = new ParentCategoryAdapter(parentId -> selectedParentId = parentId);
        GridLayoutManager lm = new GridLayoutManager(requireContext(), 3);
        binding.rvParentCategories.setLayoutManager(lm);
        binding.rvParentCategories.setAdapter(parentAdapter);
    }

    private void reloadParentCategories() {
        viewModel.getCategoriesByType(currentType).observe(getViewLifecycleOwner(), list -> {
            if (list == null || list.isEmpty()) {
                binding.tvSecondHint.setVisibility(View.VISIBLE);
                parentAdapter.submitList(new ArrayList<>());
            } else {
                binding.tvSecondHint.setVisibility(View.GONE);
                parentAdapter.submitList(list);
            }
        });
    }

    // ======================== ✅ 确认按钮（参照你给的正确写法） ========================
    private void setupConfirmButton() {
        binding.btnConfirmSave.setOnClickListener(v -> {
            // 1. 校验
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择图标", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 二级分类必须选父级
            if (!isFirstLevel && selectedParentId == -1L) {
                Toast.makeText(requireContext(), "请选择目标一级分类", Toast.LENGTH_SHORT).show();
                return;
            }

            // 3. 执行保存
            if (isFirstLevel) {
                viewModel.saveAsFirstLevelCategory(selectedItems, currentType);
            } else {
                viewModel.saveAsSecondLevelCategory(selectedItems, selectedParentId);
            }

            // 4. ✅ 关键：点击后 直接关闭！和你给的账户选择 fragment 逻辑完全一样
            dismiss();

            // 5. 给个即时提示
            Toast.makeText(requireContext(), "已保存 " + selectedItems.size() + " 个分类", Toast.LENGTH_SHORT).show();        });
    }

    // ======================== 监听结果（只用来提示，不控制关闭） ========================
    private void observeSaveResult() {
        viewModel.saveResult.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            // 结果只用来显示最终成功/失败，不控制弹窗
            if (result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
            }

            viewModel.clearSaveResult();
        });

        viewModel.saving.observe(getViewLifecycleOwner(), saving -> {
            if (binding != null) {
                binding.progressSaving.setVisibility(View.GONE);
            }
        });
    }

    // ======================== BottomSheet ========================
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    // ======================== 适配器 ========================
    private static class ParentCategoryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ParentCategoryAdapter.VH> {
        public interface OnParentSelectListener {
            void onSelect(long parentId);
        }
        private final OnParentSelectListener listener;
        private List<CategoryWithSubCategories> data = new ArrayList<>();
        private int selectedPosition = -1;

        public ParentCategoryAdapter(OnParentSelectListener listener) {
            this.listener = listener;
        }

        public void submitList(List<CategoryWithSubCategories> list) {
            data = list != null ? list : new ArrayList<>();
            selectedPosition = -1;
            notifyDataSetChanged();
        }

        public void clearSelection() {
            selectedPosition = -1;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parent_category_select, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CategoryWithSubCategories item = data.get(position);
            holder.bind(item, position == selectedPosition);

            holder.itemView.setOnClickListener(v -> {
                int old = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (old != -1) notifyItemChanged(old);
                notifyItemChanged(selectedPosition);
                listener.onSelect(item.category.getId());
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            private final MaterialCardView card;
            private final ImageView ivIcon;
            private final TextView tvName;
            private final ImageView ivCheck;

            public VH(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.card_parent_category);
                ivIcon = itemView.findViewById(R.id.iv_parent_icon);
                tvName = itemView.findViewById(R.id.tv_category_name);
                ivCheck = itemView.findViewById(R.id.iv_check);
            }

            void bind(CategoryWithSubCategories item, boolean selected) {
                tvName.setText(item.category.getName());
                if (item.category.getIconUri() != null && !item.category.getIconUri().isEmpty()) {
                    Glide.with(itemView.getContext()).load(item.category.getIconUri()).into(ivIcon);
                }

                if (selected) {
                    card.setStrokeColor(0xFF304FFE);
                    card.setCardBackgroundColor(0xFFEEF1FF);
                    tvName.setTextColor(0xFF304FFE);
                    ivCheck.setVisibility(View.VISIBLE);
                } else {
                    card.setStrokeColor(android.graphics.Color.TRANSPARENT);
                    card.setCardBackgroundColor(0xFFFFFFFF);
                    tvName.setTextColor(0xFF333333);
                    ivCheck.setVisibility(View.GONE);
                }
            }
        }
    }
}