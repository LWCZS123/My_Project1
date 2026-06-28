package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Build;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.ui.adapter.bill.CategoryStatAdapter.CategoryStatItem;
import com.example.my_project1.ui.view.BarChartView.BarEntry;
import com.example.my_project1.ui.view.LineChartView.LineEntry;
import com.example.my_project1.ui.view.PieChartView;
import com.example.my_project1.utils.AppExecutors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * BillStatisticsViewModel
 *
 * 修复：
 *   过滤后的账单列表在构建图表数据之前先按 billTime 升序排序，
 *   确保柱状图 / 曲线图的 X 轴从左到右是时间正序（最早在左）。
 */
public class BillStatisticsViewModel extends AndroidViewModel {

    public enum Period { WEEK, MONTH, YEAR, CUSTOM }

    // ── 输出 LiveData ──
    public final MutableLiveData<List<BarEntry>>              barEntries    = new MutableLiveData<>();
    public final MutableLiveData<List<LineEntry>>             lineEntries   = new MutableLiveData<>();
    public final MutableLiveData<List<PieChartView.PieEntry>> pieEntries    = new MutableLiveData<>();
    public final MutableLiveData<List<CategoryStatItem>>      categoryItems = new MutableLiveData<>();
    public final MutableLiveData<Float>  totalExpense = new MutableLiveData<>(0f);
    public final MutableLiveData<Float>  totalIncome  = new MutableLiveData<>(0f);
    public final MutableLiveData<Float>  totalBalance = new MutableLiveData<>(0f);
    public final MutableLiveData<String> periodLabel  = new MutableLiveData<>("");

    public final MutableLiveData<Integer> pieType = new MutableLiveData<>(0);

    private final BillDao      billDao;
    private final AppExecutors executors;
    private final String       currentUserId;

    private Date   windowStart, windowEnd;
    private Period currentPeriod  = Period.MONTH;
    private int    currentPieType = 0;   // 0=支出，1=收入

    private List<Bill> cachedBills;

    public BillStatisticsViewModel(@NonNull Application app) {
        super(app);
        billDao       = AppDatabase.getInstance(app).billDao();
        executors     = AppExecutors.get();
        BmobUser u    = BmobUser.getCurrentUser();
        currentUserId = (u != null) ? u.getObjectId() : null;
        resetWindowToToday(Period.MONTH);
        loadData();
    }

    // ================================================================
    //  公开方法
    // ================================================================

    public void setPeriod(Period period) {
        this.currentPeriod = period;
        resetWindowToToday(period);
        loadData();
    }

    public void navigatePrevious() { shiftWindow(-1); loadData(); }
    public void navigateNext()     { shiftWindow( 1); loadData(); }

    public void setCustomRange(Date start, Date end) {
        currentPeriod = Period.CUSTOM;
        windowStart   = atDayStart(start);
        windowEnd     = atDayEnd(end);
        updatePeriodLabel();
        loadData();
    }

    public void setPieType(int type) {
        currentPieType = type;
        pieType.setValue(type);
        if (cachedBills != null) buildAndPostPieData(cachedBills, type);
    }

    /** 供 Activity 读取当前窗口起点毫秒（跳转分类明细页用） */
    public long getWindowStartMs() {
        return windowStart != null ? windowStart.getTime() : 0L;
    }

    /** 供 Activity 读取当前窗口终点毫秒（跳转分类明细页用） */
    public long getWindowEndMs() {
        return windowEnd != null ? windowEnd.getTime() : Long.MAX_VALUE;
    }

    /** 当前饼图类型：0=支出，1=收入 */
    public int getCurrentPieType() {
        return currentPieType;
    }

    // ================================================================
    //  数据加载
    // ================================================================

    private void loadData() {
        if (currentUserId == null) return;
        final Date start = windowStart;
        final Date end   = windowEnd;

        executors.diskIO().execute(() -> {
            List<Bill> all      = billDao.getAllBillsSync();
            List<Bill> filtered = new ArrayList<>();

            for (Bill b : all) {
                if (!currentUserId.equals(b.getUserId())) continue;
                Date bt = b.getBillTime();
                if (bt == null) continue;
                if (!bt.before(start) && !bt.after(end)) filtered.add(b);
            }

            Collections.sort(filtered, (a, b) -> {
                if (a.getBillTime() == null) return -1;
                if (b.getBillTime() == null) return  1;
                return a.getBillTime().compareTo(b.getBillTime());
            });

            cachedBills = filtered;

            List<BarEntry>  bars  = buildBarData(filtered, currentPeriod);
            List<LineEntry> lines = buildLineData(bars);

            float expense = 0, income = 0;
            for (Bill b : filtered) {
                if (b.getType() == 0) expense += b.getAmount();
                else                  income  += b.getAmount();
            }
            final float fExpense = expense;
            final float fIncome  = income;
            final float fBalance = income - expense;

            buildAndPostPieData(filtered, currentPieType);

            executors.mainThread().execute(() -> {
                barEntries.setValue(bars);
                lineEntries.setValue(lines);
                totalExpense.setValue(fExpense);
                totalIncome.setValue(fIncome);
                totalBalance.setValue(fBalance);
            });
        });
    }

    // ================================================================
    //  图表数据构建
    // ================================================================

    private List<BarEntry> buildBarData(List<Bill> bills, Period period) {
        // bills 已经升序，LinkedHashMap 保留插入顺序，X 轴即为时间正序
        Map<String, float[]> map = new LinkedHashMap<>();
        for (Bill b : bills) {
            String key = groupKey(b.getBillTime(), period);
            float[] v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v = map.computeIfAbsent(key, k -> new float[2]);
            } else {
                v = map.get(key);
                if (v == null) { v = new float[2]; map.put(key, v); }
            }
            if (b.getType() == 0) v[0] += b.getAmount();
            else                  v[1] += b.getAmount();
        }

        List<BarEntry> result = new ArrayList<>();
        for (Map.Entry<String, float[]> e : map.entrySet()) {
            float exp = e.getValue()[0], inc = e.getValue()[1];
            result.add(new BarEntry(e.getKey(), exp, inc, inc - exp));
        }
        return result;
    }

    private List<LineEntry> buildLineData(List<BarEntry> bars) {
        List<LineEntry> r = new ArrayList<>();
        for (BarEntry b : bars)
            r.add(new LineEntry(b.label, b.expense, b.income, b.balance));
        return r;
    }

    private void buildAndPostPieData(List<Bill> bills, int type) {
        Map<String, float[]>  map     = new LinkedHashMap<>();
        Map<String, String>   iconMap = new LinkedHashMap<>();

        for (Bill b : bills) {
            if (b.getType() != type) continue;
            String name = b.getCategoryName() != null ? b.getCategoryName() : "其他";
            if (!iconMap.containsKey(name)) {
                iconMap.put(name, b.getCategoryIconUrl());
            }
            float[] v;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v = map.computeIfAbsent(name, k -> new float[2]);
            } else {
                v = map.get(name);
                if (v == null) { v = new float[2]; map.put(name, v); }
            }
            v[0] += b.getAmount();
            v[1]++;
        }

        float sum = 0;
        for (float[] v : map.values()) sum += v[0];
        if (sum == 0) sum = 1f;
        final float fSum = sum;

        List<PieChartView.PieEntry> pie = new ArrayList<>();
        List<CategoryStatItem>      cat = new ArrayList<>();

        int idx = 0;
        for (Map.Entry<String, float[]> e : map.entrySet()) {
            int   color = PieChartView.getPresetColor(idx++);
            float pct   = e.getValue()[0] / fSum * 100f;

            pie.add(new PieChartView.PieEntry(e.getKey(), e.getValue()[0], color, ""));

            String iconUrl = "其他".equals(e.getKey())
                    ? "android.resource://" + getApplication().getPackageName() + "/drawable/ic_cat"
                    : iconMap.get(e.getKey());

            cat.add(new CategoryStatItem(
                    e.getKey(),
                    iconUrl,
                    e.getValue()[0],
                    pct,
                    color,
                    (int) e.getValue()[1]
            ));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cat.sort((a, b) -> Float.compare(b.amount, a.amount));
        }

        executors.mainThread().execute(() -> {
            pieEntries.setValue(pie);
            categoryItems.setValue(cat);
        });
    }

    // ================================================================
    //  时间窗口辅助
    // ================================================================

    private void resetWindowToToday(Period p) {
        Calendar c = Calendar.getInstance();
        switch (p) {
            case WEEK:
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                windowStart = atDayStart(c.getTime());
                c.add(Calendar.DAY_OF_WEEK, 6);
                windowEnd = atDayEnd(c.getTime());
                break;
            case YEAR:
                c.set(Calendar.DAY_OF_YEAR, 1);
                windowStart = atDayStart(c.getTime());
                c.set(Calendar.MONTH, 11);
                c.set(Calendar.DAY_OF_MONTH, 31);
                windowEnd = atDayEnd(c.getTime());
                break;
            case CUSTOM:
                break;
            default: // MONTH
                c.set(Calendar.DAY_OF_MONTH, 1);
                windowStart = atDayStart(c.getTime());
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                windowEnd = atDayEnd(c.getTime());
                break;
        }
        updatePeriodLabel();
    }

    private void shiftWindow(int dir) {
        Calendar c = Calendar.getInstance();
        switch (currentPeriod) {
            case WEEK:
                c.setTime(windowStart);
                c.add(Calendar.WEEK_OF_YEAR, dir);
                windowStart = atDayStart(c.getTime());
                c.add(Calendar.DAY_OF_WEEK, 6);
                windowEnd = atDayEnd(c.getTime());
                break;
            case YEAR:
                c.setTime(windowStart);
                c.add(Calendar.YEAR, dir);
                c.set(Calendar.DAY_OF_YEAR, 1);
                windowStart = atDayStart(c.getTime());
                c.set(Calendar.MONTH, 11);
                c.set(Calendar.DAY_OF_MONTH, 31);
                windowEnd = atDayEnd(c.getTime());
                break;
            case CUSTOM:
                break;
            default:
                c.setTime(windowStart);
                c.add(Calendar.MONTH, dir);
                c.set(Calendar.DAY_OF_MONTH, 1);
                windowStart = atDayStart(c.getTime());
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                windowEnd = atDayEnd(c.getTime());
                break;
        }
        updatePeriodLabel();
    }

    private String groupKey(Date date, Period period) {
        if (date == null) return "?";
        switch (period) {
            case WEEK:
            case MONTH:  return new SimpleDateFormat("d日",  Locale.CHINESE).format(date);
            case YEAR:   return new SimpleDateFormat("M月",  Locale.CHINESE).format(date);
            default:     return new SimpleDateFormat("M/d",  Locale.CHINESE).format(date);
        }
    }

    private void updatePeriodLabel() {
        if (windowStart == null || windowEnd == null) return;
        SimpleDateFormat sf = new SimpleDateFormat("yyyy.M.d", Locale.CHINESE);
        SimpleDateFormat s2 = new SimpleDateFormat("M.d",      Locale.CHINESE);
        String label = sf.format(windowStart) + " - " + s2.format(windowEnd);
        executors.mainThread().execute(() -> periodLabel.setValue(label));
    }

    private Date atDayStart(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private Date atDayEnd(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
    }
}