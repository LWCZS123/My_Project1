package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Build;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SubCategoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;
import com.example.my_project1.data.model.CategoryWithSubCategories;

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
    public final MutableLiveData<Integer>                     hierarchyMode = new MutableLiveData<>(0); // 0=一级, 1=全部
    public final MutableLiveData<Float>  totalExpense = new MutableLiveData<>(0f);
    public final MutableLiveData<Float>  totalIncome  = new MutableLiveData<>(0f);
    public final MutableLiveData<Float>  totalBalance = new MutableLiveData<>(0f);
    public final MutableLiveData<String> periodLabel  = new MutableLiveData<>("");

    public final MutableLiveData<Integer> pieType = new MutableLiveData<>(0);

    private final BillDao        billDao;
    private final CategoryDao    categoryDao;
    private final SubCategoryDao subCategoryDao;
    private final AppExecutors executors;
    private final String       currentUserId;

    private Date   windowStart, windowEnd;
    private Period currentPeriod  = Period.MONTH;
    private int    currentPieType = 0;   // 0=支出，1=收入

    private List<Bill> cachedBills;

    public BillStatisticsViewModel(@NonNull Application app) {
        super(app);
        AppDatabase db = AppDatabase.getInstance(app);
        billDao        = db.billDao();
        categoryDao    = db.categoryDao();
        subCategoryDao = db.subCategoryDao();
        executors      = AppExecutors.get();
        BmobUser u     = BmobUser.getCurrentUser();
        currentUserId  = (u != null) ? u.getObjectId() : null;
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

    public void setHierarchyMode(int mode) {
        if (hierarchyMode.getValue() != null && hierarchyMode.getValue() == mode) return;
        hierarchyMode.setValue(mode);
        if (cachedBills != null) buildAndPostPieData(cachedBills, currentPieType);
    }

    private List<CategoryStatItem> currentTree = new ArrayList<>();

    public void toggleExpand(String categoryId) {
        for (CategoryStatItem item : currentTree) {
            if (item.level == 1 && Objects.equals(item.categoryId, categoryId)) {
                item.isExpanded = !item.isExpanded;
                break;
            }
        }
        updateFlatList();
    }

    private void updateFlatList() {
        List<CategoryStatItem> flat = new ArrayList<>();
        Integer mode = hierarchyMode.getValue();
        if (mode == null) mode = 0;

        for (CategoryStatItem pItem : currentTree) {
            flat.add(pItem);
            if (mode == 1 || pItem.isExpanded) {
                flat.addAll(pItem.subItems);
            }
        }
        categoryItems.setValue(flat);
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
        executors.diskIO().execute(() -> {
            String typeStr = (type == 0 ? "expense" : "income");
            // 1. 获取所有分类层级关系
            List<CategoryWithSubCategories> allTree = categoryDao.getCategoriesWithSubsSync(currentUserId, typeStr);

            // 映射关系
            Map<String, Category> idToParent = new HashMap<>();
            Map<String, String> childToParentId = new HashMap<>();
            Map<String, SubCategory> idToSub = new HashMap<>();

            for (CategoryWithSubCategories node : allTree) {
                Category p = node.category;
                idToParent.put(p.cloudId, p);
                if (node.subCategories != null) {
                    for (SubCategory sub : node.subCategories) {
                        idToSub.put(sub.cloudId, sub);
                        childToParentId.put(sub.cloudId, p.cloudId);
                    }
                }
            }

            // 2. 聚合统计
            // parentId -> {totalAmount, count}
            Map<String, float[]> parentStats = new HashMap<>();
            // parentId -> { childId -> {amount, count} }
            Map<String, Map<String, float[]>> childStatsMap = new HashMap<>();

            float grandTotal = 0f;

            for (Bill b : bills) {
                if (b.getType() != type) continue;
                String bid = b.getCategoryId();
                if (bid == null) continue;

                String pid = childToParentId.get(bid);
                String cid = null;
                if (pid == null) {
                    // bid 本身是一级分类，或者未知
                    if (idToParent.containsKey(bid)) {
                        pid = bid;
                    } else {
                        // 未知分类处理为“其他”
                        pid = "other";
                    }
                } else {
                    // bid 是二级分类
                    cid = bid;
                }

                grandTotal += b.getAmount();

                // 更新父级统计
                float[] ps = parentStats.get(pid);
                if (ps == null) { ps = new float[2]; parentStats.put(pid, ps); }
                ps[0] += b.getAmount();
                ps[1]++;

                // 更新子级统计
                if (cid != null) {
                    Map<String, float[]> cMap = childStatsMap.get(pid);
                    if (cMap == null) { cMap = new HashMap<>(); childStatsMap.put(pid, cMap); }
                    float[] cs = cMap.get(cid);
                    if (cs == null) { cs = new float[2]; cMap.put(cid, cs); }
                    cs[0] += b.getAmount();
                    cs[1]++;
                }
            }

            final float fGrandTotal = (grandTotal == 0 ? 1f : grandTotal);

            // 3. 构建结果列表
            List<CategoryStatItem> resultList = new ArrayList<>();
            List<PieChartView.PieEntry> pieEntriesList = new ArrayList<>();

            // 排序父分类
            List<String> sortedParentIds = new ArrayList<>(parentStats.keySet());
            Collections.sort(sortedParentIds, (id1, id2) -> {
                float[] s1 = parentStats.get(id1);
                float[] s2 = parentStats.get(id2);
                float v1 = (s1 != null ? s1[0] : 0f);
                float v2 = (s2 != null ? s2[0] : 0f);
                return Float.compare(v2, v1);
            });

            int colorIdx = 0;
            for (String pid : sortedParentIds) {
                float[] ps = parentStats.get(pid);
                if (ps == null) ps = new float[2];
                Category p = idToParent.get(pid);
                String name = (p != null ? p.getName() : "其他");
                String icon = (p != null ? p.getIconUri() : "");
                int color = PieChartView.getPresetColor(colorIdx++);
                float pct = ps[0] / fGrandTotal * 100f;

                CategoryStatItem parentItem = new CategoryStatItem(pid, name, icon, ps[0], pct, color, (int)ps[1], 1);
                pieEntriesList.add(new PieChartView.PieEntry(name, ps[0], color, pid));

                // 处理子分类
                Map<String, float[]> cMap = childStatsMap.get(pid);
                if (cMap != null) {
                    List<String> sortedChildIds = new ArrayList<>(cMap.keySet());
                    Collections.sort(sortedChildIds, (id1, id2) -> {
                        float[] s1 = cMap.get(id1);
                        float[] s2 = cMap.get(id2);
                        float v1 = (s1 != null ? s1[0] : 0f);
                        float v2 = (s2 != null ? s2[0] : 0f);
                        return Float.compare(v2, v1);
                    });
                    
                    for (String cid : sortedChildIds) {
                        float[] cs = cMap.get(cid);
                        if (cs == null) cs = new float[2];
                        SubCategory sub = idToSub.get(cid);
                        String cName = (sub != null ? sub.getName() : "未知子类");
                        String cIcon = (sub != null ? sub.getIconUri() : "");
                        float cPct = (ps[0] == 0 ? 0 : cs[0] / ps[0] * 100f);
                        
                        parentItem.subItems.add(new CategoryStatItem(cid, cName, cIcon, cs[0], cPct, color, (int)cs[1], 2));
                    }
                }
                
                resultList.add(parentItem);
            }

            // 4. 保存树形结构并更新列表
            this.currentTree = resultList;
            executors.mainThread().execute(() -> {
                pieEntries.setValue(pieEntriesList);
                updateFlatList();
            });
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