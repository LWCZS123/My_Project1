package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.FragmentIconDetailBinding;
import com.example.my_project1.ui.adapter.icon.DetailPagerAdapter;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel.PageState;
import com.example.my_project1.ui.viewmodel.icon.SelectionManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.Nullable;

/**
 * IconDetailFragment（改造版）
 * ─────────────────────────────────────────────────────────────
 * 新增能力：
 *
 * ① 多选模式 Toolbar
 *   - 进入多选后显示：全选/取消全选、确认、取消
 *   - 标题实时显示已选数量
 *   - 退出（返回键 / 取消按钮）清空选中
 *
 * ② observe SelectionManager 状态
 *   - multiSelectMode → 切换 Toolbar / 正常 UI
 *   - selectedIds     → 下发到 DetailPagerAdapter（精准 payload 刷新）
 *   - selectedCount   → 更新 Toolbar 标题
 *
 * ③ 保存操作
 *   - 确认按钮 → 弹出 SaveCategoryBottomSheet 选择保存类型
 *
 * ④ 其余逻辑（ViewPager2 分页、预加载、进度条）保持不变
 */
public class IconDetailFragment extends BottomSheetDialogFragment {

    private static final String TAG = "IconDetailFragment";
    private static final int PRELOAD_AHEAD = 2;

    private FragmentIconDetailBinding binding;
    private IconMarketViewModel       viewModel;
    private DetailPagerAdapter        pagerAdapter;
    private IconCategory              currentCategory;

    /** 返回键拦截（多选模式下退出多选而非关闭 Fragment） */
    private OnBackPressedCallback backPressedCallback;

    // ──────────────────────────────────────────────────────────
    // 生命周期
    // ──────────────────────────────────────────────────────────


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentIconDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(IconMarketViewModel.class);

        initViewPager();
        initMultiSelectToolbar();
        observeViewModel();
        registerBackPressHandler();
    }

    @Override
    public void onStart() {
        super.onStart();
        View sheet = getDialog().findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 退出时清空多选，避免再次打开时残留状态
        viewModel.exitMultiSelectMode();
        if (backPressedCallback != null) {
            backPressedCallback.remove();
        }
        binding = null;
    }

    // ──────────────────────────────────────────────────────────
    // 初始化 ViewPager
    // ──────────────────────────────────────────────────────────

    private void initViewPager() {
        pagerAdapter = new DetailPagerAdapter();

        // 普通模式点击：Toast 显示图标名（保持原有行为）
        pagerAdapter.setOnIconClickListener(item -> {
            // 单击 = 直接进入保存 BottomSheet
            List<IconItem> singleList = new ArrayList<>();
            singleList.add(item);
            showSaveBottomSheet(singleList);
        });

        // ★ 长按 → 进入多选
        pagerAdapter.setOnIconLongClickListener(item -> {
            viewModel.onIconLongClick(item.getId());
            return true; // 消费事件，不触发 click
        });

        // ★ 多选模式单击 → 切换选中
        pagerAdapter.setOnSelectionClickListener((item, isSelected) ->
                viewModel.onIconClick(item.getId()));

        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOffscreenPageLimit(3);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(position);
                if (currentCategory == null) return;
                for (int i = position; i <= position + PRELOAD_AHEAD; i++) {
                    viewModel.loadDetailPage(currentCategory, i);
                }
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    // 初始化多选 Toolbar
    // ──────────────────────────────────────────────────────────

    private void initMultiSelectToolbar() {
        // 初始隐藏多选工具栏
        binding.toolbarMultiSelect.setVisibility(View.GONE);

        // 全选 / 取消全选
        binding.btnSelectAll.setOnClickListener(v -> {
            List<String> allIds = getAllCurrentPageIds();
            viewModel.toggleSelectAll(allIds);
        });

        // 取消按钮 → 退出多选模式
        binding.btnCancelSelect.setOnClickListener(v ->
                viewModel.exitMultiSelectMode());

        // 确认按钮 → 弹出保存选项
        binding.btnConfirmSelect.setOnClickListener(v -> {
            int count = viewModel.selectionManager.getCount();
            if (count == 0) {
                Toast.makeText(requireContext(), "请先选择图标", Toast.LENGTH_SHORT).show();
                return;
            }
            showSaveBottomSheet(collectSelectedItems());
        });
    }

    // ──────────────────────────────────────────────────────────
    // 数据观察
    // ──────────────────────────────────────────────────────────

    private void observeViewModel() {

        // 选中分类
        viewModel.selectedCategory.observe(getViewLifecycleOwner(), category -> {
            if (category != null) {
                currentCategory = category;
            }
        });

        // 分页状态
        viewModel.detailPageStates.observe(getViewLifecycleOwner(), snapshot -> {
            if (binding == null) return;
            Integer total = viewModel.detailTotalPages.getValue();
            int pages = total != null ? total : 0;
            pagerAdapter.updatePageStates(snapshot, pages);
            updatePageIndicator(binding.viewPager.getCurrentItem());
        });

        viewModel.detailTotalPages.observe(getViewLifecycleOwner(), totalPages -> {
            if (binding == null || totalPages == null) return;
            SparseArray<PageState> snapshot = viewModel.detailPageStates.getValue();
            pagerAdapter.updatePageStates(snapshot, totalPages);
            updatePageIndicator(binding.viewPager.getCurrentItem());

            if (currentCategory != null) {
                for (int i = 0; i <= PRELOAD_AHEAD; i++) {
                    viewModel.loadDetailPage(currentCategory, i);
                }
            }
        });

        // 进度条
        viewModel.detailLoading.observe(getViewLifecycleOwner(), loading ->
                binding.progressDetail.setVisibility(
                        Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        // 错误 Toast
        viewModel.detailError.observe(getViewLifecycleOwner(), error -> {
            if (error != null && binding != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                viewModel.clearDetailError();
            }
        });

        // ★ 多选模式切换
        SelectionManager sm = viewModel.selectionManager;

        sm.multiSelectMode.observe(getViewLifecycleOwner(), isMultiSelect -> {
            if (binding == null) return;
            java.util.Set<String> ids = sm.selectedIds.getValue();
            if (ids == null) ids = java.util.Collections.emptySet();

            // 切换 Toolbar 显示
            binding.toolbarMultiSelect.setVisibility(
                    Boolean.TRUE.equals(isMultiSelect) ? View.VISIBLE : View.GONE);
            binding.tvPageIndicator.setVisibility(
                    Boolean.TRUE.equals(isMultiSelect) ? View.GONE : View.VISIBLE);

            // 下发到 Adapter（payload 精准刷新，不重建 ViewHolder）
            pagerAdapter.setMultiSelectMode(Boolean.TRUE.equals(isMultiSelect), ids);

            if (!Boolean.TRUE.equals(isMultiSelect)) {
                binding.tvSelectCount.setText("选择图标");
            }
        });

        // ★ 选中 ID 变化 → 下发到 Adapter（只刷新变化的 item）
        sm.selectedIds.observe(getViewLifecycleOwner(), ids -> {
            if (binding == null) return;
            if (ids == null) ids = java.util.Collections.emptySet();
            pagerAdapter.updateSelectedIds(ids);
        });

        // ★ 选中数量 → 更新 Toolbar 标题
        sm.selectedCount.observe(getViewLifecycleOwner(), count -> {
            if (binding == null) return;
            binding.tvSelectCount.setText(
                    count != null && count > 0 ? "已选 " + count + " 个" : "选择图标");
            // 更新全选按钮文案
            boolean isAll = sm.isAllSelected(getAllCurrentPageIds());
            binding.btnSelectAll.setText(isAll ? "取消全选" : "全选");
            // 确认按钮是否可用
            binding.btnConfirmSelect.setEnabled(count != null && count > 0);
            binding.btnConfirmSelect.setAlpha(
                    (count != null && count > 0) ? 1.0f : 0.4f);
        });

        // ★ 保存结果
        viewModel.saveResult.observe(getViewLifecycleOwner(), result -> {
            if (result == null || binding == null) return;
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
            viewModel.clearSaveResult();
        });
    }

    // ──────────────────────────────────────────────────────────
    // 返回键处理
    // ──────────────────────────────────────────────────────────

    private void registerBackPressHandler() {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(
                        viewModel.selectionManager.multiSelectMode.getValue())) {
                    // 多选模式下：退出多选，不关闭 Fragment
                    viewModel.exitMultiSelectMode();
                } else {
                    // 非多选：正常关闭 BottomSheet
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    // ──────────────────────────────────────────────────────────
    // 保存 BottomSheet
    // ──────────────────────────────────────────────────────────

    private void showSaveBottomSheet(List<IconItem> selectedItems) {
        // 收集已选中的完整 IconItem 列表
        if (selectedItems == null || selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择图标", Toast.LENGTH_SHORT).show();
            return;
        }

        SaveCategoryBottomSheet sheet =
                SaveCategoryBottomSheet.newInstance(selectedItems);
        sheet.show(getChildFragmentManager(), "SaveCategory");
    }

    /**
     * 从当前所有已加载的分页数据中，筛选出选中的 IconItem。
     */
    private List<IconItem> collectSelectedItems() {
        java.util.Set<String> ids = viewModel.selectionManager.selectedIds.getValue();
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<IconItem> result = new ArrayList<>();
        Integer total = viewModel.detailTotalPages.getValue();
        if (total == null) return result;

        SparseArray<PageState> states = viewModel.detailPageStates.getValue();
        if (states == null) return result;

        for (int page = 0; page < total; page++) {
            PageState state = states.get(page);
            if (state == null || state.items == null) continue;
            for (IconItem item : state.items) {
                if (ids.contains(item.getId())) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /**
     * 获取当前所有已加载页面的图标 ID（供全选使用）。
     */
    private List<String> getAllCurrentPageIds() {
        List<String> ids = new ArrayList<>();
        Integer total = viewModel.detailTotalPages.getValue();
        if (total == null) return ids;

        SparseArray<PageState> states = viewModel.detailPageStates.getValue();
        if (states == null) return ids;

        for (int page = 0; page < total; page++) {
            PageState state = states.get(page);
            if (state == null || state.items == null) continue;
            for (IconItem item : state.items) {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    // ──────────────────────────────────────────────────────────
    // UI 工具
    // ──────────────────────────────────────────────────────────

    private void updatePageIndicator(int currentPage) {
        if (binding == null) return;
        int total = pagerAdapter.getItemCount();
        binding.tvPageIndicator.setText((currentPage + 1) + " / " + total);
    }
}