package com.example.my_project1.ui.adapter.calendar;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.LruCache;
import android.util.SparseArray;

import com.example.my_project1.data.model.calendar.CalendarDay;
import com.example.my_project1.utils.LunarCalendar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CalendarDataEngine {

    private static final int PRELOAD_WINDOW   = 0;
    private static final int LUNAR_CACHE_SIZE = 1200;
    private static final int META_CACHE_SIZE  = 120;

    public interface DataCallback {
        void onMonthReady(int pageIndex, List<CalendarDay> days, MonthMeta meta);
    }

    public interface BillDataProvider {
        java.util.List<com.example.my_project1.data.model.bill.Bill> getBillsForDate(String dateKey);
        double getIncomeForDate(String dateKey);
        double getExpenseForDate(String dateKey);
    }

    public static final class MonthMeta {
        public final int year, month, offset, daysInMonth, rowCount, totalCells;
        MonthMeta(int year, int month, int offset, int daysInMonth) {
            this.year = year;
            this.month = month;
            this.offset = offset;
            this.daysInMonth = daysInMonth;
            int raw = offset + daysInMonth;
            this.rowCount = raw <= 35 ? 5 : 6;
            this.totalCells = rowCount * 7;
        }
    }

    private final Calendar baseCalendar;
    private final int centerIndex;

    private final HandlerThread workerThread;
    private final Handler workerHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final SparseArray<List<CalendarDay>> pageCache = new SparseArray<>();
    private final SparseArray<MonthMeta> metaCache = new SparseArray<>(META_CACHE_SIZE);
    private final SparseArray<AtomicBoolean> pendingSet = new SparseArray<>();

    private static final LruCache<String, String> sLunarTextCache =
            new LruCache<>(LUNAR_CACHE_SIZE);

    private volatile String todayKey;
    private volatile String selectedKey;
    private DataCallback callback;
    private volatile BillDataProvider billDataProvider;

    public CalendarDataEngine(Context ctx, Calendar today, int centerIndex) {
        this.baseCalendar = (Calendar) today.clone();
        this.centerIndex  = centerIndex;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.todayKey    = sdf.format(today.getTime());
        this.selectedKey = this.todayKey;

        workerThread = new HandlerThread(
                "CalendarWorker",
                android.os.Process.THREAD_PRIORITY_BACKGROUND
        );
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    public CalendarDataEngine(Calendar today, int centerIndex) {
        this(null, today, centerIndex);
    }

    public void setCallback(DataCallback callback) {
        this.callback = callback;
    }

    public void setBillDataProvider(BillDataProvider p) {
        this.billDataProvider = p;
    }

    public void invalidateAll(int focusPage) {
        invalidateVisibleRange(focusPage);
    }

    public void invalidateVisibleRange(int focusPage) {
        requestMonth(focusPage);
    }

    public void requestMonth(int pageIndex) {

        List<CalendarDay> cached = pageCache.get(pageIndex);
        MonthMeta meta = metaCache.get(pageIndex);

        if (cached != null && meta != null) {
            fireCallback(pageIndex, cached, meta);
            return;
        }

        scheduleGenerate(pageIndex);

        for (int d = 1; d <= PRELOAD_WINDOW; d++) {
            schedulePreload(pageIndex + d);
            schedulePreload(pageIndex - d);
        }
    }

    public void updateSelectedKey(String newKey, int pageIndex) {
        this.selectedKey = newKey;
        pageCache.remove(pageIndex);
        requestMonth(pageIndex);
    }

    public void refreshTodayKey(Calendar today, String newTodayKey,
                                String newSelectedKey, int centerPage) {

        this.todayKey    = newTodayKey;
        this.selectedKey = newSelectedKey;

        synchronized (pageCache) {
            pageCache.clear();
        }

        synchronized (pendingSet) {
            pendingSet.clear();
        }

        requestMonth(centerPage);
    }

    public void release() {
        workerHandler.removeCallbacksAndMessages(null);
        workerThread.quitSafely();
        synchronized (pageCache) {
            pageCache.clear();
        }
    }

    // =========================
    // schedule
    // =========================

    private void scheduleGenerate(int pageIndex) {

        synchronized (pendingSet) {
            if (pendingSet.get(pageIndex) != null) return;

            AtomicBoolean token = new AtomicBoolean(true);
            pendingSet.put(pageIndex, token);

            workerHandler.post(() -> {
                if (!token.get()) return;

                generateAndCache(pageIndex);

                synchronized (pendingSet) {
                    pendingSet.remove(pageIndex);
                }
            });
        }
    }

    private void schedulePreload(int pageIndex) {
        if (pageIndex < 0) return;

        synchronized (pageCache) {
            if (pageCache.get(pageIndex) != null) return;
        }

        scheduleGenerate(pageIndex);
    }

    // =========================
    // core generate
    // =========================

    private void generateAndCache(int pageIndex) {

        Calendar cal = calendarForIndex(pageIndex);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        final int year  = cal.get(Calendar.YEAR);
        final int month = cal.get(Calendar.MONTH) + 1;

        int firstDow    = cal.get(Calendar.DAY_OF_WEEK);
        int offset      = firstDow - 1;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        final MonthMeta meta =
                new MonthMeta(year, month, offset, daysInMonth);

        List<CalendarDay> days =
                new ArrayList<>(meta.totalCells);

        // 上月补位
        Calendar prevCal = (Calendar) cal.clone();
        prevCal.add(Calendar.MONTH, -1);

        int prevMax   = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int prevYear  = prevCal.get(Calendar.YEAR);
        int prevMonth = prevCal.get(Calendar.MONTH) + 1;

        for (int i = offset; i > 0; i--) {
            CalendarDay cd =
                    new CalendarDay(prevYear, prevMonth, prevMax - i + 1);
            cd.setCurrentMonth(false);
            days.add(cd);
        }

        SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        String todayKey    = this.todayKey;
        String selectedKey = this.selectedKey;

        // 当月
        for (int d = 1; d <= daysInMonth; d++) {

            cal.set(Calendar.DAY_OF_MONTH, d);

            CalendarDay cd = new CalendarDay(year, month, d);
            cd.setDate(cal.getTime());

            String dayKey = sdf.format(cal.getTime());

            cd.setToday(dayKey.equals(todayKey));
            cd.setSelected(dayKey.equals(selectedKey));

            // 农历
            String key = year + "-" + month + "-" + d;

            String lunarText = sLunarTextCache.get(key);
            if (lunarText == null) {
                lunarText = LunarCalendar.getDisplayText(year, month, d);
                if (lunarText == null) lunarText = "";
                sLunarTextCache.put(key, lunarText);
            }
            cd.setLunarText(lunarText);

            String festival =
                    LunarCalendar.getSolarFestival(month, d);
            cd.setLunarFestival(festival != null && !festival.isEmpty());

            String solarTerm =
                    LunarCalendar.getSolarTerm(year, month, d);
            cd.setSolarTerm(solarTerm != null && !solarTerm.isEmpty());

            // ⚠️ 已删除节假日逻辑

            BillDataProvider provider = this.billDataProvider;
            if (provider != null) {
                java.util.List<com.example.my_project1.data.model.bill.Bill> bills =
                        provider.getBillsForDate(dayKey);

                if (bills != null && !bills.isEmpty()) {
                    cd.setBillCount(bills.size());
                }

                cd.setTotalIncome(provider.getIncomeForDate(dayKey));
                cd.setTotalExpense(provider.getExpenseForDate(dayKey));
            }

            days.add(cd);
        }

        // 下月补位
        int remaining = meta.totalCells - days.size();

        Calendar nextCal = (Calendar) cal.clone();
        nextCal.add(Calendar.MONTH, 1);

        int nextYear  = nextCal.get(Calendar.YEAR);
        int nextMonth = nextCal.get(Calendar.MONTH) + 1;

        for (int d = 1; d <= remaining; d++) {
            CalendarDay cd = new CalendarDay(nextYear, nextMonth, d);
            cd.setCurrentMonth(false);
            days.add(cd);
        }

        synchronized (pageCache) {
            pageCache.put(pageIndex, days);
        }

        synchronized (metaCache) {
            metaCache.put(pageIndex, meta);
        }

        fireCallback(pageIndex, days, meta);
    }

    // =========================
    // utils
    // =========================

    private void fireCallback(int pageIndex,
                              List<CalendarDay> days,
                              MonthMeta meta) {

        if (callback == null) return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onMonthReady(pageIndex, days, meta);
        } else {
            mainHandler.post(() ->
                    callback.onMonthReady(pageIndex, days, meta));
        }
    }

    private Calendar calendarForIndex(int index) {
        Calendar c = (Calendar) baseCalendar.clone();
        c.add(Calendar.MONTH, index - centerIndex);
        return c;
    }

    public MonthMeta getMetaSync(int pageIndex) {

        MonthMeta cached = metaCache.get(pageIndex);
        if (cached != null) return cached;

        Calendar cal = calendarForIndex(pageIndex);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int offset = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int dim    = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        MonthMeta meta =
                new MonthMeta(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        offset,
                        dim
                );

        synchronized (metaCache) {
            metaCache.put(pageIndex, meta);
        }

        return meta;
    }
}