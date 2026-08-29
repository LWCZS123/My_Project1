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
import com.example.my_project1.utils.BudgetConfig;
import com.example.my_project1.utils.BudgetPeriodHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetDateSelectorFragment extends BottomSheetDialogFragment {

    public interface OnDateSelectedListener {
        void onDateSelected(String type, long startTime, long endTime);
    }

    private String type;
    private long initialStartTime, initialEndTime;
    private OnDateSelectedListener listener;

    public static BudgetDateSelectorFragment newInstance(
            String type, long startTime, long endTime, OnDateSelectedListener listener) {
        BudgetDateSelectorFragment f = new BudgetDateSelectorFragment();
        f.type = type;
        f.initialStartTime = startTime;
        f.initialEndTime = endTime;
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
        View llWeekHeader = view.findViewById(R.id.ll_week_header);
        view.findViewById(R.id.iv_close).setOnClickListener(v -> dismiss());

        tvTitle.setText("选择时间");
        String actionText = "本月";
        if (Budget.TYPE_YEAR.equals(type)) actionText = "今年";
        else if (Budget.TYPE_WEEK.equals(type)) {
            actionText = "本周";
            llWeekHeader.setVisibility(View.VISIBLE);
        }
        
        tvAction.setText(actionText);
        tvAction.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            int startDay = Budget.TYPE_MONTH.equals(type)
                    ? BudgetConfig.getStartDay(requireContext()) : 1;
            long[] range = BudgetPeriodHelper.getPeriodRange(
                    BudgetPeriodHelper.periodForType(type), startDay, c);
            if (listener != null) listener.onDateSelected(type, range[0], range[1]);
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
                Calendar initial = Calendar.getInstance();
                initial.setTimeInMillis(initialStartTime);
                boolean sel = year == initial.get(Calendar.YEAR);
                tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected_white : R.drawable.bg_capsule_gray);
                tv.setTextColor(sel ? ContextCompat.getColor(getContext(), R.color.calendar_selection) : 0xFF333333);
                holder.itemView.setOnClickListener(v -> {
                    Calendar calendar = Calendar.getInstance();
                    calendar.clear();
                    calendar.set(year, Calendar.JANUARY, 1);
                    long[] range = BudgetPeriodHelper.getPeriodRange(
                            Budget.PERIOD_YEAR, 1, calendar);
                    if (listener != null) listener.onDateSelected(type, range[0], range[1]);
                    dismiss();
                });
            }

            @Override
            public int getItemCount() { return years.size(); }
        });
    }

    private void setupMonthSelector(RecyclerView rv) {
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
                        Calendar calendar = Calendar.getInstance();
                        calendar.clear();
                        calendar.set(year, month - 1, 1);
                        int startDay = BudgetConfig.getStartDay(requireContext());
                        calendar.set(Calendar.DAY_OF_MONTH,
                                Math.min(startDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
                        long[] range = BudgetPeriodHelper.getPeriodRange(
                                Budget.PERIOD_MONTH, startDay, calendar);
                        boolean sel = range[0] == initialStartTime;
                        tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected_white : R.drawable.bg_capsule_gray);
                        tv.setTextColor(sel ? ContextCompat.getColor(getContext(), R.color.calendar_selection) : 0xFF333333);
                        h.itemView.setOnClickListener(v -> {
                            if (listener != null) listener.onDateSelected(
                                    Budget.TYPE_MONTH, range[0], range[1]);
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
        
        List<WeekInfo> weeks = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        Calendar cursor = Calendar.getInstance();
        cursor.clear();
        cursor.set(curYear - 3, Calendar.JANUARY, 1);
        long[] firstRange = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, cursor);
        cursor.setTimeInMillis(firstRange[0]);
        Calendar limit = Calendar.getInstance();
        limit.clear();
        limit.set(curYear + 4, Calendar.JANUARY, 1);
        while (cursor.before(limit)) {
            long[] range = BudgetPeriodHelper.getPeriodRange(Budget.PERIOD_WEEK, 1, cursor);
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            start.setTimeInMillis(range[0]);
            end.setTimeInMillis(range[1]);
            weeks.add(new WeekInfo(range[0], range[1], start, end));
            cursor.add(Calendar.DAY_OF_MONTH, 7);
        }

        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector_week_block, parent, false)) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                WeekInfo info = weeks.get(position);
                Calendar s = info.start;
                Calendar e = info.end;
                
                TextView tvRange = holder.itemView.findViewById(R.id.tv_week_range);
                String rangeStr = String.format(Locale.getDefault(), "%d年%d月%d日 - %d年%d月%d日",
                        s.get(Calendar.YEAR), s.get(Calendar.MONTH)+1, s.get(Calendar.DAY_OF_MONTH),
                        e.get(Calendar.YEAR), e.get(Calendar.MONTH)+1, e.get(Calendar.DAY_OF_MONTH));
                tvRange.setText(rangeStr);

                boolean isSelectedWeek = info.startTime == initialStartTime;
                
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
                        
                        if (isSelectedWeek) {
                            tvDay.setBackgroundResource(R.drawable.bg_tab_selected_white);
                            tvDay.setTextColor(ContextCompat.getColor(getContext(), R.color.calendar_selection));
                        } else {
                            tvDay.setBackgroundResource(R.drawable.bg_capsule_gray);
                            tvDay.setTextColor(0xFF333333);
                        }
                        
                        h.itemView.setOnClickListener(v -> {
                            if (listener != null) listener.onDateSelected(
                                    Budget.TYPE_WEEK, info.startTime, info.endTime);
                            dismiss();
                        });
                    }
                    @Override
                    public int getItemCount() { return 7; }
                });
                
                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onDateSelected(
                            Budget.TYPE_WEEK, info.startTime, info.endTime);
                    dismiss();
                });
            }
            @Override
            public int getItemCount() { return weeks.size(); }
        });
        
        // Scroll to initial week if possible
        for (int i = 0; i < weeks.size(); i++) {
            if (weeks.get(i).startTime == initialStartTime) {
                rv.scrollToPosition(i);
                break;
            }
        }
    }

    private static class WeekInfo {
        long startTime;
        long endTime;
        Calendar start;
        Calendar end;
        WeekInfo(long startTime, long endTime, Calendar s, Calendar e) {
            this.startTime = startTime;
            this.endTime = endTime;
            start = s; end = e;
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.8));
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}
