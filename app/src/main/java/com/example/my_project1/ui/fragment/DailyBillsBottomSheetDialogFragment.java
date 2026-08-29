package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.LayoutDailyBillsBottomSheetBinding;
import com.example.my_project1.ui.activity.BillDetailActivity;
import com.example.my_project1.ui.adapter.bill.BillListAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nlf.calendar.Solar;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 每日账单详情底部弹窗
 * 用于在日历模块点击日期后弹出，展示当日的统计数据和账单列表
 */
public class DailyBillsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private LayoutDailyBillsBottomSheetBinding binding;
    private BillViewModel billViewModel;
    private BillListAdapter adapter;
    private int year, month, day;

    public static DailyBillsBottomSheetDialogFragment newInstance(int year, int month, int day) {
        DailyBillsBottomSheetDialogFragment fragment = new DailyBillsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putInt("year", year);
        args.putInt("month", month);
        args.putInt("day", day);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        if (getArguments() != null) {
            year = getArguments().getInt("year");
            month = getArguments().getInt("month");
            day = getArguments().getInt("day");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutDailyBillsBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);

        setupUI();
        List<Bill> cachedBills = billViewModel.getCachedBillsForDate(year, month, day);
        if (cachedBills != null) {
            renderBills(cachedBills);
        }
        observeData();
    }

    private void setupUI() {
        // 设置公历和农历日期标题
        binding.tvDateTitle.setText(String.format(Locale.getDefault(), "%d月%d日", month, day));
        binding.tvLunarInfo.setText("");
        loadLunarText();

        // 初始化账单列表
        adapter = new BillListAdapter(requireContext());
        adapter.setOnBillClickListener(bill -> {
            Intent intent = new Intent(requireContext(), BillDetailActivity.class);
            intent.putExtra("bill_id", bill.objectId);
            intent.putExtra("bill_local_id", bill.localId);
            startActivity(intent);
            dismiss();
        });

        binding.rvDailyBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDailyBills.setAdapter(adapter);
        binding.rvDailyBills.setItemAnimator(null);
    }

    private void loadLunarText() {
        AppExecutors.get().computation().execute(() -> {
            String lunarText = Solar.fromYmd(year, month, day).getLunar().getDayInChinese();
            AppExecutors.get().mainThread().execute(() -> {
                if (binding != null) binding.tvLunarInfo.setText(lunarText);
            });
        });
    }

    private void observeData() {
        billViewModel.getBillsForDate(year, month, day)
                .observe(getViewLifecycleOwner(), this::renderBills);
    }

    private void renderBills(@Nullable List<Bill> bills) {
        if (binding == null) return;
        List<Bill> safeBills = bills == null ? Collections.emptyList() : bills;

        double income = 0;
        double expense = 0;
        for (Bill bill : safeBills) {
            if (bill.getType() == 1) income += bill.getAmount();
            else if (bill.getType() == 0) expense += bill.getAmount();
        }
        updateStats(income, expense);

        if (safeBills.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.rvDailyBills.setVisibility(View.GONE);
            adapter.submitList(Collections.emptyList());
            return;
        }

        binding.layoutEmpty.setVisibility(View.GONE);
        binding.rvDailyBills.setVisibility(View.VISIBLE);

        List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(safeBills);
        List<BillListAdapter.ListItem> items = new ArrayList<>(uiModels.size());
        for (BillUiModel uiModel : uiModels) {
            items.add(new BillListAdapter.ListItem(uiModel));
        }
        adapter.submitList(items);
    }

    /**
     * 更新顶部统计卡片
     */
    private void updateStats(double income, double expense) {
        DecimalFormat df = new DecimalFormat("0.00");
        binding.tvTotalIncome.setText(df.format(income));
        binding.tvTotalExpense.setText(df.format(expense));
        binding.tvBalance.setText(df.format(income - expense));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
