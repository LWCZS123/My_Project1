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
            int year = c.get(Calendar.YEAR);
            int val;
            if (Budget.TYPE_WEEK.equals(type)) {
                val = c.get(Calendar.WEEK_OF_YEAR);
            } else if (Budget.TYPE_YEAR.equals(type)) {
                val = 1;
            } else {
                val = c.get(Calendar.MONTH) + 1;
            }
            if (listener != null) listener.onDateSelected(type, year, val);
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
        
        List<WeekInfo> weeks = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = curYear - 3; y <= curYear + 3; y++) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, y);
            cal.set(Calendar.MONTH, Calendar.JANUARY);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            // Adjust to the first Sunday of the year (or just before)
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                cal.add(Calendar.DAY_OF_MONTH, -1);
            }
            
            // Loop through the whole year
            while (true) {
                Calendar start = (Calendar) cal.clone();
                Calendar end = (Calendar) cal.clone();
                end.add(Calendar.DAY_OF_MONTH, 6);
                
                // Get the representative week number for this period
                // We use the week number of the midpoint or the Thursday as per ISO, 
                // but here we'll just use what WEEK_OF_YEAR gives us for the start of the week.
                weeks.add(new WeekInfo(y, start.get(Calendar.WEEK_OF_YEAR), start, end));
                
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                
                // Stop when we reach the next year's first week
                if (cal.get(Calendar.YEAR) > y && cal.get(Calendar.WEEK_OF_YEAR) == 1) break;
                // Safety break
                if (cal.get(Calendar.YEAR) > y + 1) break;
            }
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

                boolean isSelectedWeek = (info.year == initialYear && info.weekNum == initialMonth);
                
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
                            if (listener != null) listener.onDateSelected(Budget.TYPE_WEEK, info.year, info.weekNum);
                            dismiss();
                        });
                    }
                    @Override
                    public int getItemCount() { return 7; }
                });
                
                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onDateSelected(Budget.TYPE_WEEK, info.year, info.weekNum);
                    dismiss();
                });
            }
            @Override
            public int getItemCount() { return weeks.size(); }
        });
        
        // Scroll to initial week if possible
        for (int i = 0; i < weeks.size(); i++) {
            if (weeks.get(i).year == initialYear && weeks.get(i).weekNum == initialMonth) {
                rv.scrollToPosition(i);
                break;
            }
        }
    }

    private static class WeekInfo {
        int year;
        int weekNum;
        Calendar start;
        Calendar end;
        WeekInfo(int y, int w, Calendar s, Calendar e) {
            year = y; weekNum = w; start = s; end = e;
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
