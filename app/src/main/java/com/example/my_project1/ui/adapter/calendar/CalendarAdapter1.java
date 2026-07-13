package com.example.my_project1.ui.adapter.calendar;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.calendar.CalendarDay;
import com.example.my_project1.databinding.ItemCalendarDay1Binding;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 日历格适配器 - 修复版
 *
 * 本次改动：
 * ① 节假日标签颜色改为蓝色调
 *    "休"：浅蓝背景 #E3F2FD + 深蓝文字 #1565C0
 *    "班"：浅灰蓝背景 #ECEFF1 + 中灰蓝文字 #546E7A
 *
 * ② 移除反射调用 getHolidayType()
 *    CalendarDay 已有此字段，直接调用，零反射开销
 *
 * ③ applyTodayBg() density 改为 ViewHolder 构造时缓存，
 *    避免每次 bind 时读取 DisplayMetrics
 *
 * ④ setDays() 增加耗时日志（Tag: CalendarAdapterPerf）
 */
public class CalendarAdapter1
        extends ListAdapter<CalendarDay, CalendarAdapter1.DayViewHolder> {

    private static final String PERF_TAG = "CalendarAdapterPerf";

    public interface OnDayClickListener {
        void onDayClick(CalendarDay day);
    }

    public static final int HOLIDAY_NONE    = 0;
    public static final int HOLIDAY_REST    = 1;
    public static final int HOLIDAY_WORKDAY = 2;

    private static final int HEATMAP_LEVELS = 5;

    private static final String[] EXPENSE_COLORS = {
            "#FFEBEE", "#FFCDD2", "#EF9A9A", "#E57373", "#EF5350"
    };
    private static final String[] INCOME_COLORS = {
            "#E8F5E9", "#C8E6C9", "#A5D6A7", "#81C784", "#66BB6A"
    };
    private static final String[] MIXED_COLORS = {
            "#E3F2FD", "#BBDEFB", "#90CAF9", "#64B5F6", "#42A5F5"
    };

    private static final int[] EXPENSE_COLOR_CACHE = new int[HEATMAP_LEVELS];
    private static final int[] INCOME_COLOR_CACHE  = new int[HEATMAP_LEVELS];
    private static final int[] MIXED_COLOR_CACHE   = new int[HEATMAP_LEVELS];

    private static final int COLOR_NON_CURRENT_MONTH = Color.parseColor("#CCCCCC");
    private static final int COLOR_TEXT_DEFAULT      = Color.parseColor("#1A1A1A");
    private static final int COLOR_TEXT_LUNAR_GREY   = Color.parseColor("#AAAAAA");
    private static final int COLOR_GREEN_SELECTED    = Color.parseColor("#4CAF50");
    private static final int COLOR_TEAL_SELECTED     = Color.parseColor("#56A596");
    private static final int COLOR_BLUE              = Color.parseColor("#2196F3");
    private static final int COLOR_WHITE             = Color.WHITE;

    // 节假日项目背景 - 极浅蓝 (修改点)
    private static final int COLOR_ITEM_REST_BG  = Color.parseColor("#F1F8FF"); 

    // 节假日标签 - 蓝色调（修改点）
    private static final int COLOR_TAG_REST_BG   = Color.parseColor("#E3F2FD"); // 浅蓝背景 - 休
    private static final int COLOR_TAG_REST_TEXT = Color.parseColor("#1565C0"); // 深蓝文字 - 休
    private static final int COLOR_TAG_WORK_BG   = Color.parseColor("#ECEFF1"); // 浅灰蓝背景 - 班
    private static final int COLOR_TAG_WORK_TEXT = Color.parseColor("#546E7A"); // 中灰蓝文字 - 班

    static {
        for (int i = 0; i < HEATMAP_LEVELS; i++) {
            EXPENSE_COLOR_CACHE[i] = Color.parseColor(EXPENSE_COLORS[i]);
            INCOME_COLOR_CACHE[i]  = Color.parseColor(INCOME_COLORS[i]);
            MIXED_COLOR_CACHE[i]   = Color.parseColor(MIXED_COLORS[i]);
        }
    }

    private static final DiffUtil.ItemCallback<CalendarDay> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CalendarDay>() {
                @Override
                public boolean areItemsTheSame(@NonNull CalendarDay a, @NonNull CalendarDay b) {
                    return a.getYear() == b.getYear()
                            && a.getMonth() == b.getMonth()
                            && a.getDay() == b.getDay();
                }

                @Override
                public boolean areContentsTheSame(@NonNull CalendarDay a, @NonNull CalendarDay b) {
                    return a.isSelected() == b.isSelected()
                            && a.isToday() == b.isToday()
                            && a.isCurrentMonth() == b.isCurrentMonth()
                            && a.getTotalExpense() == b.getTotalExpense()
                            && a.getTotalIncome() == b.getTotalIncome()
                            && safeEquals(a.getLunarText(), b.getLunarText())
                            && a.getHolidayType() == b.getHolidayType();
                }

                private boolean safeEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    private OnDayClickListener listener;
    private int displayType = 0;
    private double maxAmount = 0;
    private final DecimalFormat amountFormat = new DecimalFormat("#");

    public CalendarAdapter1() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void setDays(List<CalendarDay> days) {
        long t0 = SystemClock.elapsedRealtime();
        List<CalendarDay> safeList = (days != null) ? days : new ArrayList<>();
        recalcMaxAmount(safeList);
        submitList(safeList);
        Log.d(PERF_TAG, "setDays: size=" + safeList.size()
                + " took=" + (SystemClock.elapsedRealtime() - t0) + "ms");
    }

    public void setDisplayType(int type) {
        if (this.displayType == type) return;
        this.displayType = type;
        List<CalendarDay> current = getCurrentList();
        if (!current.isEmpty()) {
            recalcMaxAmount(current);
            notifyItemRangeChanged(0, current.size());
        }
    }

    @Override
    public long getItemId(int position) {
        CalendarDay day = getItem(position);
        return day.getYear() * 10000L + day.getMonth() * 100L + day.getDay();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarDay1Binding binding = ItemCalendarDay1Binding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    private void recalcMaxAmount(List<CalendarDay> days) {
        maxAmount = 0;
        for (int i = 0, n = days.size(); i < n; i++) {
            CalendarDay day = days.get(i);
            if (!day.isCurrentMonth()) continue;
            double amt = amountForDay(day);
            if (amt > maxAmount) maxAmount = amt;
        }
    }

    private double amountForDay(CalendarDay day) {
        if (displayType == 1) return day.getTotalExpense();
        if (displayType == 2) return day.getTotalIncome();
        return day.getTotalExpense() + day.getTotalIncome();
    }

    private int heatmapLevel(double amount) {
        if (maxAmount == 0 || amount == 0) return 0;
        int level = (int) (amount / maxAmount * (HEATMAP_LEVELS - 1));
        return Math.min(level, HEATMAP_LEVELS - 1);
    }

    class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemCalendarDay1Binding binding;

        private final GradientDrawable heatmapDrawable;
        private final GradientDrawable selectedDrawable;
        private final GradientDrawable todayDrawable;
        private final GradientDrawable holidayTagDrawable;
        private final GradientDrawable itemRestBgDrawable;

        private final int   colorRed;
        private final int   colorAccent;
        private final int   todayStrokeWidth; // 构造时计算一次，避免 bind 内重复读 density

        DayViewHolder(@NonNull ItemCalendarDay1Binding binding) {
            super(binding.getRoot());
            this.binding = binding;

            float density = binding.getRoot().getResources().getDisplayMetrics().density;
            todayStrokeWidth = (int)(2 * density);

            heatmapDrawable = new GradientDrawable();
            heatmapDrawable.setShape(GradientDrawable.RECTANGLE);
            heatmapDrawable.setCornerRadius(6f * density);

            selectedDrawable = new GradientDrawable();
            selectedDrawable.setShape(GradientDrawable.OVAL);

            todayDrawable = new GradientDrawable();
            todayDrawable.setShape(GradientDrawable.OVAL);
            todayDrawable.setColor(Color.TRANSPARENT);

            holidayTagDrawable = new GradientDrawable();
            holidayTagDrawable.setShape(GradientDrawable.RECTANGLE);
            holidayTagDrawable.setCornerRadius(4f * density);

            itemRestBgDrawable = new GradientDrawable();
            itemRestBgDrawable.setShape(GradientDrawable.RECTANGLE);
            itemRestBgDrawable.setCornerRadius(8f * density);
            itemRestBgDrawable.setColor(COLOR_ITEM_REST_BG);

            colorRed    = ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_color);
            colorAccent = ContextCompat.getColor(binding.getRoot().getContext(), R.color.accent_color);

            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    CalendarDay day = getItem(pos);
                    if (day.isCurrentMonth()) {
                        listener.onDayClick(day);
                    }
                }
            });
        }

        void bind(CalendarDay day) {
            binding.tvDay.setText(String.valueOf(day.getDay()));
            binding.flDayContainer.setBackground(null);
            binding.vSelectedBg.setBackground(null);
            binding.tvDay.setTextColor(COLOR_TEXT_DEFAULT);
            binding.getRoot().setBackground(null);

            if (!day.isCurrentMonth()) {
                binding.tvDay.setTextColor(COLOR_NON_CURRENT_MONTH);
                applyLunarText(day); // 优化：非本月日期显示农历，但保持灰色
                binding.tvLunar.setTextColor(COLOR_TEXT_LUNAR_GREY);
                binding.tvIncome.setVisibility(View.GONE);
                binding.tvExpense.setVisibility(View.GONE);
                binding.tvHolidayTag.setVisibility(View.GONE);
                binding.getRoot().setClickable(false);
                return;
            }

            binding.getRoot().setClickable(true);
            applyHolidayTag(day);

            // 节假日特殊背景 (修改点)
            if (day.getHolidayType() == HOLIDAY_REST && !day.isSelected()) {
                binding.getRoot().setBackground(itemRestBgDrawable);
            }

            boolean hasBill   = shouldShowBill(day);
            double displayAmt = amountForDay(day);

            if (day.isSelected()) {
                applySelectedBg(day);
            } else if (day.isToday()) {
                applyTodayBg();
            } else if (hasBill && displayAmt > 0) {
                applyHeatmapBg(day, displayAmt);
            }

            if (hasBill) {
                binding.tvLunar.setVisibility(View.GONE);
                applyIncomeExpenseDisplay(day);
            } else {
                applyLunarText(day);
                binding.tvIncome.setVisibility(View.GONE);
                binding.tvExpense.setVisibility(View.GONE);
            }
        }

        private void applyHolidayTag(CalendarDay day) {
            int holidayType = day.getHolidayType(); // 直接调用，无反射
            Log.d("HolidayCheck", day.getYear() + "-" + day.getMonth() + "-" + day.getDay()
                    + " type=" + day.getHolidayType());
            if (holidayType == HOLIDAY_NONE) {
                binding.tvHolidayTag.setVisibility(View.GONE);
                return;
            }
            if (holidayType == HOLIDAY_REST) {
                binding.tvHolidayTag.setText("休");
                holidayTagDrawable.setColor(COLOR_TAG_REST_BG);
                binding.tvHolidayTag.setTextColor(COLOR_TAG_REST_TEXT);
            } else {
                binding.tvHolidayTag.setText("班");
                holidayTagDrawable.setColor(COLOR_TAG_WORK_BG);
                binding.tvHolidayTag.setTextColor(COLOR_TAG_WORK_TEXT);
            }
            binding.tvHolidayTag.setBackground(holidayTagDrawable);
            binding.tvHolidayTag.setVisibility(View.VISIBLE);
        }

        private void applyHeatmapBg(CalendarDay day, double amount) {
            int level = heatmapLevel(amount);
            int color;
            if (displayType == 2) {
                color = INCOME_COLOR_CACHE[level];
            } else if (displayType == 1) {
                color = EXPENSE_COLOR_CACHE[level];
            } else {
                if (day.hasExpense() && day.hasIncome())  color = MIXED_COLOR_CACHE[level];
                else if (day.hasIncome())                 color = INCOME_COLOR_CACHE[level];
                else                                      color = EXPENSE_COLOR_CACHE[level];
            }
            heatmapDrawable.setColor(color);
            binding.flDayContainer.setBackground(heatmapDrawable);
        }

        private void applySelectedBg(CalendarDay day) {
            int bgColor;
            if (displayType == -1)     bgColor = COLOR_TEAL_SELECTED;
            else if (displayType == 2) bgColor = COLOR_GREEN_SELECTED;
            else if (displayType == 1) bgColor = colorRed;
            else {
                if (day.hasExpense() && day.hasIncome()) bgColor = COLOR_BLUE;
                else if (day.hasIncome())                bgColor = COLOR_GREEN_SELECTED;
                else                                     bgColor = colorRed;
            }
            selectedDrawable.setColor(bgColor);
            binding.vSelectedBg.setBackground(selectedDrawable);
            binding.tvDay.setTextColor(COLOR_WHITE);
        }

        private void applyTodayBg() {
            // 使用构造时缓存的 todayStrokeWidth，避免每次读 DisplayMetrics
            todayDrawable.setStroke(todayStrokeWidth, colorAccent);
            binding.vSelectedBg.setBackground(todayDrawable);
            binding.tvDay.setTextColor(colorRed);
        }

        private void applyLunarText(CalendarDay day) {
            String text = day.getLunarText();
            if (text != null && !text.isEmpty()) {
                binding.tvLunar.setText(text);
                binding.tvLunar.setVisibility(View.VISIBLE);
                if (day.isLunarFestival() || day.isSolarTerm()) {
                    binding.tvLunar.setTextColor(colorRed);
                } else {
                    binding.tvLunar.setTextColor(COLOR_TEXT_LUNAR_GREY);
                }
            } else {
                binding.tvLunar.setVisibility(View.INVISIBLE);
            }
        }

        private void applyIncomeExpenseDisplay(CalendarDay day) {
            boolean showIncome = (displayType == 0 || displayType == 2)
                    && day.hasIncome() && day.getTotalIncome() > 0;
            if (showIncome) {
                binding.tvIncome.setText("收 ¥" + amountFormat.format(day.getTotalIncome()));
                binding.tvIncome.setVisibility(View.VISIBLE);
            } else {
                binding.tvIncome.setVisibility(View.GONE);
            }

            boolean showExpense = (displayType == 0 || displayType == 1)
                    && day.hasExpense() && day.getTotalExpense() > 0;
            if (showExpense) {
                binding.tvExpense.setText("支 ¥" + amountFormat.format(day.getTotalExpense()));
                binding.tvExpense.setVisibility(View.VISIBLE);
            } else {
                binding.tvExpense.setVisibility(View.GONE);
            }
        }

        private boolean shouldShowBill(CalendarDay day) {
            if (displayType == 1) return day.hasExpense();
            if (displayType == 2) return day.hasIncome();
            return day.hasBills();
        }
    }
}