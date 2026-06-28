package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.FragmentAddCategoryBudgetBinding;
import com.example.my_project1.ui.adapter.budget.CategorySelectorAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.GlideImageLoader;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

public class AddCategoryBudgetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AddCategoryBudgetFrag";

    private static final String ARG_AMOUNT = "arg_amount";
    private static final String ARG_PERIOD = "arg_period";
    private static final String ARG_TARGET_ID = "arg_target_id";
    private static final String ARG_BUDGET_ID = "arg_budget_id";
    private static final String ARG_IS_EDIT = "arg_is_edit";

    private FragmentAddCategoryBudgetBinding binding;
    private BudgetViewModel budgetVm;
    private CategoryViewModel categoryVm;

    private int selectedPeriod = Budget.PERIOD_MONTH;
    private int maxPeriod = Budget.PERIOD_YEAR;

    private boolean isEditMode = false;
    private int editBudgetId = -1;

    private boolean showMainCat = true;
    private List<CategoryWithSubCategories> allMainCats = new ArrayList<>();
    private final CategorySelectorAdapter catAdapter = new CategorySelectorAdapter();

    private String selectedCatCloudId = null;
    private String selectedCatName = null;
    private String selectedCatIconUri = null;

    public static AddCategoryBudgetFragment newInstance(@Nullable Budget existingBudget) {
        AddCategoryBudgetFragment f = new AddCategoryBudgetFragment();
        Bundle args = new Bundle();
        if (existingBudget != null) {
            args.putBoolean(ARG_IS_EDIT, true);
            args.putInt(ARG_BUDGET_ID, existingBudget.getId());
            args.putDouble(ARG_AMOUNT, existingBudget.getAmount());
            args.putInt(ARG_PERIOD, existingBudget.getPeriod());
            if (existingBudget.getTargetId() != null) {
                args.putString(ARG_TARGET_ID, existingBudget.getTargetId());
            }
        } else {
            args.putBoolean(ARG_IS_EDIT, false);
        }
        f.setArguments(args);
        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                ViewGroup.LayoutParams lp = sheet.getLayoutParams();
                lp.height = (int) (requireContext().getResources().getDisplayMetrics().heightPixels * 0.92);
                sheet.setLayoutParams(lp);
                BottomSheetBehavior<View> beh = BottomSheetBehavior.from(sheet);
                beh.setSkipCollapsed(true);
                beh.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddCategoryBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        budgetVm = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);
        categoryVm = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        restoreArgs();
        initCategoryGrid();
        initPeriodTabs();
        initLevelTabs();
        initAmountInput();
        initConfirmBtn();
        observeCategories();
        observeRemainingAllocation();
        updateSelectedHeader();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void restoreArgs() {
        Bundle args = getArguments();
        if (args == null) return;
        isEditMode = args.getBoolean(ARG_IS_EDIT, false);
        editBudgetId = args.getInt(ARG_BUDGET_ID, -1);
        if (isEditMode) {
            double amount = args.getDouble(ARG_AMOUNT, 0);
            selectedPeriod = args.getInt(ARG_PERIOD, Budget.PERIOD_MONTH);
            selectedCatCloudId = args.getString(ARG_TARGET_ID);
            if (amount > 0 && binding != null) {
                binding.etAmount.setText(String.format(Locale.getDefault(), "%.2f", amount));
            }
        }
        Budget total = budgetVm.getTotalBudget().getValue();
        if (total != null) maxPeriod = total.getPeriod();
    }

    private void initCategoryGrid() {
        binding.rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.rvCategories.setNestedScrollingEnabled(false);
        binding.rvCategories.setAdapter(catAdapter);

        catAdapter.setOnItemClickListener(item -> {
            selectedCatCloudId = item.cloudId;
            selectedCatName = item.name;
            selectedCatIconUri = item.iconUri;
            updateSelectedHeader();
            updateRemainingHintFromInput();
        });

        if (selectedCatCloudId != null) {
            catAdapter.setSelected(selectedCatCloudId);
        }
    }

    private void initPeriodTabs() {
        refreshPeriodTabStyle();
        binding.tabPeriodDay.setOnClickListener(v -> onPeriodClick(Budget.PERIOD_DAY));
        binding.tabPeriodWeek.setOnClickListener(v -> onPeriodClick(Budget.PERIOD_WEEK));
        binding.tabPeriodMonth.setOnClickListener(v -> onPeriodClick(Budget.PERIOD_MONTH));
        binding.tabPeriodYear.setOnClickListener(v -> onPeriodClick(Budget.PERIOD_YEAR));
    }

    private void onPeriodClick(int period) {
        if (period > maxPeriod) {
            showPeriodConstraintHint();
            return;
        }
        binding.tvPeriodConstraint.setVisibility(View.GONE);
        selectedPeriod = period;
        refreshPeriodTabStyle();
        updateRemainingHintFromInput();
    }

    private void showPeriodConstraintHint() {
        binding.tvPeriodConstraint.setVisibility(View.VISIBLE);
        binding.tvPeriodConstraint.setText("* 分类预算周期不能超过总预算周期（" + Budget.getPeriodLabel(maxPeriod) + "）");
    }

    private void refreshPeriodTabStyle() {
        TextView[] tabs = {binding.tabPeriodDay, binding.tabPeriodWeek, binding.tabPeriodMonth, binding.tabPeriodYear};
        int[] periods = {Budget.PERIOD_DAY, Budget.PERIOD_WEEK, Budget.PERIOD_MONTH, Budget.PERIOD_YEAR};
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = periods[i] == selectedPeriod;
            boolean dis = periods[i] > maxPeriod;
            if (sel) {
                tabs[i].setBackgroundResource(R.drawable.bg_tab_selected1);
                tabs[i].setTextColor(0xFF333333);
                tabs[i].setAlpha(1f);
            } else if (dis) {
                tabs[i].setBackground(null);
                tabs[i].setTextColor(0xFFCCCCCC);
                tabs[i].setAlpha(0.45f);
            } else {
                tabs[i].setBackground(null);
                tabs[i].setTextColor(0xFF888888);
                tabs[i].setAlpha(1f);
            }
        }
    }

    private void initLevelTabs() {
        refreshLevelTabStyle();
        binding.tabLevelMain.setOnClickListener(v -> {
            if (showMainCat) return;
            showMainCat = true;
            refreshLevelTabStyle();
            scheduleRefreshCategoryList();
        });
        binding.tabLevelSub.setOnClickListener(v -> {
            if (!showMainCat) return;
            showMainCat = false;
            refreshLevelTabStyle();
            scheduleRefreshCategoryList();
        });
    }

    private void refreshLevelTabStyle() {
        if (showMainCat) {
            binding.tabLevelMain.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabLevelMain.setTextColor(0xFFFFFFFF);
            binding.tabLevelSub.setBackground(null);
            binding.tabLevelSub.setTextColor(0xFF888888);
        } else {
            binding.tabLevelSub.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabLevelSub.setTextColor(0xFFFFFFFF);
            binding.tabLevelMain.setBackground(null);
            binding.tabLevelMain.setTextColor(0xFF888888);
        }
    }

    private void initAmountInput() {
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateRemainingHintFromInput(); }
        });
    }

    private void initConfirmBtn() {
        binding.btnConfirm.setOnClickListener(v -> onConfirm());
    }

    private void observeRemainingAllocation() {
        budgetVm.getRemainingAllocation().observe(getViewLifecycleOwner(), remaining -> {
            if (binding == null) return;
            if (remaining == null) {
                binding.tvRemainingHint.setText("剩余可分配：¥—");
                binding.tvRemainingHint.setTextColor(0xFF4CAF50);
                binding.llOverWarning.setVisibility(View.GONE);
                return;
            }
            if (remaining >= 0) {
                binding.tvRemainingHint.setText(String.format(Locale.getDefault(), "剩余可分配：¥%.0f", remaining));
                binding.tvRemainingHint.setTextColor(0xFF5B8DEF);
            } else {
                binding.tvRemainingHint.setText(String.format(Locale.getDefault(), "已超分配：¥%.0f", Math.abs(remaining)));
                binding.tvRemainingHint.setTextColor(0xFFFF5252);
            }
            updateOverWarning(remaining);
        });
    }

    private void updateRemainingHintFromInput() {
        Double remaining = budgetVm.getRemainingAllocation().getValue();
        if (remaining != null) updateOverWarning(remaining);
    }

    private void updateOverWarning(double remaining) {
        String amtStr = binding.etAmount.getText().toString().trim();
        if (amtStr.isEmpty()) {
            binding.llOverWarning.setVisibility(View.GONE);
            return;
        }
        double input;
        try {
            input = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            binding.llOverWarning.setVisibility(View.GONE);
            return;
        }
        boolean over = input > Math.max(remaining, 0);
        binding.llOverWarning.setVisibility(over ? View.VISIBLE : View.GONE);
        if (over) {
            binding.tvOverWarning.setText(String.format(Locale.getDefault(), "输入金额超过剩余可分配预算（¥%.0f）", Math.max(remaining, 0)));
        }
    }

    private void observeCategories() {
        categoryVm.getExpenseCategories(budgetVm.getUserId())
                .observe(getViewLifecycleOwner(), list -> {
                    if (list == null) return;
                    allMainCats = list;
                    scheduleRefreshCategoryList();
                    if (isEditMode && selectedCatCloudId != null) resolveSelectedCatMeta();
                });
    }

    private void scheduleRefreshCategoryList() {
        String budgetType = budgetVm.getBudgetType();
        int year = budgetVm.getCurrentYear();
        int month = budgetVm.getCurrentMonth();
        boolean isYear = Budget.TYPE_YEAR.equals(budgetType);
        int qMonth = isYear ? 0 : month;
        List<CategoryWithSubCategories> snapshot = new ArrayList<>(allMainCats);
        boolean showMain = showMainCat;

        AppExecutors.get().diskIO().execute(() -> {
            List<CategorySelectorAdapter.Item> items = new ArrayList<>();
            if (showMain) {
                for (CategoryWithSubCategories cws : snapshot) {
                    boolean hasChildren = cws.subCategories != null && !cws.subCategories.isEmpty();
                    if (hasChildren) continue;
                    String cloudId = safeCloudId(cws);
                    if (cloudId == null) continue;
                    String tag = buildBudgetTag(cloudId, budgetType, year, qMonth);
                    items.add(new CategorySelectorAdapter.Item(cloudId, safeName(cws), safeIconUri(cws), tag));
                }
            } else {
                for (CategoryWithSubCategories cws : snapshot) {
                    if (cws.subCategories == null || cws.subCategories.isEmpty()) continue;
                    for (SubCategory sub : cws.subCategories) {
                        String subId = sub.getCloudId();
                        if (subId == null || subId.isEmpty()) continue;
                        String tag = buildBudgetTag(subId, budgetType, year, qMonth);
                        items.add(new CategorySelectorAdapter.Item(subId, sub.getName(), sub.getIconUri(), tag));
                    }
                }
            }
            AppExecutors.get().mainThread().execute(() -> {
                if (binding == null) return;
                catAdapter.submitList(items);
                if (selectedCatCloudId != null) catAdapter.setSelected(selectedCatCloudId);
            });
        });
    }

    private String buildBudgetTag(String catCloudId, String budgetType, int year, int qMonth) {
        Budget b = budgetVm.getCategoryBudgetSync(catCloudId, budgetType, year, qMonth);
        if (b == null) return null;
        String periodLabel = Budget.getPeriodLabel(b.getPeriod());
        String budgetPart = String.format(Locale.getDefault(), "¥%.0f/%s", b.getAmount(), periodLabel);
        double spent = budgetVm.getSpentByCategorySync(catCloudId);
        if (spent <= 0) return budgetPart;
        double remain = b.getAmount() - spent;
        if (remain < 0) {
            return budgetPart + " 超支¥" + String.format(Locale.getDefault(), "%.0f", Math.abs(remain));
        }
        return budgetPart + " 已花¥" + String.format(Locale.getDefault(), "%.0f", spent);
    }

    @Nullable
    private String safeCloudId(CategoryWithSubCategories cws) {
        if (cws == null || cws.category == null) return null;
        String id = cws.category.getCloudId();
        return (id != null && !id.isEmpty()) ? id : null;
    }

    private String safeName(CategoryWithSubCategories cws) {
        if (cws == null || cws.category == null) return "未知分类";
        String n = cws.category.getName();
        return (n != null && !n.isEmpty()) ? n : "未知分类";
    }

    private String safeIconUri(CategoryWithSubCategories cws) {
        if (cws == null || cws.category == null) return null;
        return cws.category.getIconUri();
    }

    private void updateSelectedHeader() {
        if (selectedCatName == null) {
            binding.tvSelectedName.setText("请选择分类");
            binding.tvSelectedHint.setText("从下方宫格中点击选择");
            binding.ivSelectedCheck.setVisibility(View.GONE);
            binding.ivSelectedIcon.setImageResource(R.drawable.ic_category_default);
            return;
        }
        binding.tvSelectedName.setText(selectedCatName);
        binding.tvSelectedHint.setText("已选择，可继续修改");
        binding.ivSelectedCheck.setVisibility(View.VISIBLE);
        loadIcon(selectedCatIconUri, binding.ivSelectedIcon);
    }

    private void resolveSelectedCatMeta() {
        for (CategoryWithSubCategories cws : allMainCats) {
            boolean hasChildren = cws.subCategories != null && !cws.subCategories.isEmpty();
            if (!hasChildren) {
                String id = safeCloudId(cws);
                if (selectedCatCloudId.equals(id)) {
                    selectedCatName = safeName(cws);
                    selectedCatIconUri = safeIconUri(cws);
                    updateSelectedHeader();
                    return;
                }
            }
            if (cws.subCategories != null) {
                for (SubCategory sub : cws.subCategories) {
                    if (selectedCatCloudId.equals(sub.getCloudId())) {
                        selectedCatName = sub.getName();
                        selectedCatIconUri = sub.getIconUri();
                        updateSelectedHeader();
                        return;
                    }
                }
            }
        }
    }

    private void loadIcon(String iconUri, android.widget.ImageView iv) {
        if (iconUri == null || iconUri.isEmpty()) {
            iv.setImageResource(R.drawable.ic_category_default);
            return;
        }
        try {
            int resId = Integer.parseInt(iconUri);
            GlideImageLoader.load1(requireContext(), iv, resId);
        } catch (NumberFormatException e) {
            GlideImageLoader.load(requireContext(), iconUri, iv);
        }
    }

    private void pushCacheToActivity() {
        if (selectedCatCloudId == null) return;
        if (getActivity() instanceof com.example.my_project1.ui.activity.BudgetActivity) {
            ((com.example.my_project1.ui.activity.BudgetActivity) getActivity())
                    .updateCategoryCache(selectedCatCloudId, selectedCatName, selectedCatIconUri);
        }
    }

    private void onConfirm() {
        if (selectedCatCloudId == null) {
            Toast.makeText(requireContext(), "请先选择分类", Toast.LENGTH_SHORT).show();
            return;
        }
        String amtStr = binding.etAmount.getText().toString().trim();
        if (amtStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入预算金额", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "金额格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(requireContext(), "预算金额须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedPeriod > maxPeriod) {
            showPeriodConstraintHint();
            return;
        }

        if (isEditMode) {
            AppExecutors.get().diskIO().execute(() -> {
                List<Budget> all = budgetVm.getCategoryBudgetsSyncForCurrentType();
                for (Budget b : all) {
                    if (b.getId() == editBudgetId) {
                        budgetVm.updateCategoryBudget(b, amount, selectedPeriod);
                        break;
                    }
                }
                if (isAdded()) {
                    AppExecutors.get().mainThread().execute(() -> {
                        pushCacheToActivity();
                        Toast.makeText(requireContext(), "分类预算已更新", Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                }
            });
        } else {
            boolean ok = budgetVm.addCategoryBudget(selectedCatCloudId,
                    amount,
                    selectedPeriod,
                    selectedCatName,
                    selectedCatIconUri);
            if (ok) {
                pushCacheToActivity();
                Toast.makeText(requireContext(), "分类预算已保存", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        }
    }
}