package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.budget.Budget;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetDateSelectorFragment extends BottomSheetDialogFragment {

    public interface OnDateSelectedListener {
        void onDateSelected(String type, int year, int month);
    }

    private String type;
    private int initialYear, initialMonth;
    private OnDateSelectedListener listener;

    public static BudgetDateSelectorFragment newInstance(String type, int year, int month, OnDateSelectedListener listener) {
        BudgetDateSelectorFragment f = new BudgetDateSelectorFragment();
        f.type = type;
        f.initialYear = year;
        f.initialMonth = month;
        f.listener = listener;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget_date_selector, container, false);
        initView(view);
        return view;
    }

    private void initView(View view) {
        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvAction = view.findViewById(R.id.tv_action);
        RecyclerView rv = view.findViewById(R.id.rv_selector);
        view.findViewById(R.id.iv_close).setOnClickListener(v -> dismiss());

        tvTitle.setText("选择时间");
        String actionText = "本月";
        if (Budget.TYPE_YEAR.equals(type)) actionText = "今年";
        else if (Budget.TYPE_WEEK.equals(type)) actionText = "本周";
        tvAction.setText(actionText);
        tvAction.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (listener != null) listener.onDateSelected(type, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
            dismiss();
        });

        if (Budget.TYPE_YEAR.equals(type)) {
            setupYearSelector(rv);
        } else if (Budget.TYPE_MONTH.equals(type)) {
            setupMonthSelector(rv);
        } else {
            setupWeekSelector(rv);
        }
    }

    private void setupYearSelector(RecyclerView rv) {
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Integer> years = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = curYear - 5; i <= curYear + 5; i++) years.add(i);
        
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_year, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TextView tv = holder.itemView.findViewById(R.id.tv_year);
                int year = years.get(position);
                tv.setText(year + "年");
                boolean sel = year == initialYear;
                tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected_white : R.drawable.bg_capsule_gray);
                tv.setTextColor(sel ? ContextCompat.getColor(getContext(), R.color.calendar_selection) : 0xFF333333);
                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onDateSelected(type, year, 1);
                    dismiss();
                });
            }

            @Override
            public int getItemCount() { return years.size(); }
        });
    }

    private void setupMonthSelector(RecyclerView rv) {
        // 使用更简单的结构实现月份选择：按年份分块显示 12 个月
        setupSimpleMonthSelector(rv);
    }
    
    private void setupSimpleMonthSelector(RecyclerView rv) {
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Integer> years = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        for(int i=curYear-2; i<=curYear+2; i++) years.add(i);
        
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_month_block, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                int year = years.get(position);
                TextView tvHead = holder.itemView.findViewById(R.id.tv_year_head);
                tvHead.setText(year + "年");
                RecyclerView rvGrid = holder.itemView.findViewById(R.id.rv_month_grid);
                rvGrid.setLayoutManager(new GridLayoutManager(getContext(), 5));
                rvGrid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_month_cell, parent, false)) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
                        TextView tv = (TextView) h.itemView;
                        int month = p + 1;
                        tv.setText(month + "月");
                        boolean sel = (year == initialYear && month == initialMonth);
                        tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected_white : R.drawable.bg_capsule_gray);
                        tv.setTextColor(sel ? ContextCompat.getColor(getContext(), R.color.calendar_selection) : 0xFF333333);
                        h.itemView.setOnClickListener(v -> {
                            if (listener != null) listener.onDateSelected(Budget.TYPE_MONTH, year, month);
                            dismiss();
                        });
                    }
                    @Override
                    public int getItemCount() { return 12; }
                });
            }
            @Override
            public int getItemCount() { return years.size(); }
        });
    }

    private void setupWeekSelector(RecyclerView rv) {
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        
        List<long[]> weekRanges = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        // Go back a few weeks
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.add(Calendar.WEEK_OF_YEAR, 2); 
        for (int i = 0; i < 8; i++) {
            Calendar start = (Calendar) cal.clone();
            Calendar end = (Calendar) cal.clone();
            end.add(Calendar.DAY_OF_MONTH, 6);
            weekRanges.add(new long[]{start.getTimeInMillis(), end.getTimeInMillis()});
            cal.add(Calendar.WEEK_OF_YEAR, -1);
        }

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_week_block, parent, false)) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                long[] range = weekRanges.get(position);
                Calendar s = Calendar.getInstance(); s.setTimeInMillis(range[0]);
                Calendar e = Calendar.getInstance(); e.setTimeInMillis(range[1]);
                
                TextView tvRange = holder.itemView.findViewById(R.id.tv_week_range);
                String rangeStr = String.format(Locale.getDefault(), "%d年%d月%d日 - %d年%d月%d日",
                        s.get(Calendar.YEAR), s.get(Calendar.MONTH)+1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.YEAR), e.get(Calendar.MONTH)+1, e.get(Calendar.DAY_OF_MONTH));
                tvRange.setText(rangeStr);

                RecyclerView rvDays = holder.itemView.findViewById(R.id.rv_days);
                rvDays.setLayoutManager(new GridLayoutManager(getContext(), 7));
                rvDays.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_day_cell, parent, false)) {};
                    }
                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
                        TextView tvDay = (TextView) h.itemView;
                        Calendar dayCal = (Calendar) s.clone();
                        dayCal.add(Calendar.DAY_OF_MONTH, p);
                        int day = dayCal.get(Calendar.DAY_OF_MONTH);
                        tvDay.setText(String.valueOf(day));
                        
                        // Check if current day is in this week for highlight? 
                        h.itemView.setOnClickListener(v -> {
                            if (listener != null) listener.onDateSelected(Budget.TYPE_WEEK, s.get(Calendar.YEAR), s.get(Calendar.MONTH)+1);
                            dismiss();
                        });
                    }
                    @Override
                    public int getItemCount() { return 7; }
                });
            }
            @Override
            public int getItemCount() { return weekRanges.size(); }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.7));
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}
