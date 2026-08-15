package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagingData;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.ui.adapter.bill.BillAdapter;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.work.BillSyncWorker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * BillViewModel (旗舰级秒开版)
 * -------------------------------------------------------
 * ✅ 1. 快照优先：构造时立即从 SP 恢复上一次的统计和账单，实现零等待。
 * ✅ 2. 保护机制：冷启动设置 500ms 保护期，防止数据闪烁。
 * ✅ 3. 静默刷新：后台默默更新数据，DiffUtil 平滑过渡。
 */
public class BillViewModel extends AndroidViewModel {

    private static final String TAG = "BillViewModel";
    private static final String SP_NAME = "bill_snapshot";
    private static final String KEY_HEADER_SNAPSHOT = "header_data";
    private static final String KEY_BILL_ITEMS_SNAPSHOT = "bill_items";
    private static final String KEY_CALENDAR_SNAPSHOT = "calendar_stats";

    // ── 分页常量 ──────────────────────────────────────
    private static final int PAGE_SIZE        = 20;  // 每页条数
    private static final long SNAPSHOT_PROTECT_MS = 500L; // 快照保护期

    // ── 后台计算线程 ───────────────────────────────────
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler        = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    // 🔴 性能优化相关
    private final Map<String, BillUiModel> billUiCache = new HashMap<>();
    private final Map<String, BillAdapter.DateHeader> headerCache = new HashMap<>();
    private String lastHomeFingerprint = "";
    private String lastCalendarFingerprint = "";

    // 🔴 预分配格式化工具，避免循环中重复创建
    private static final SimpleDateFormat DATE_KEY_FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DATE_DISP_FMT = new SimpleDateFormat("M月d日", Locale.getDefault());
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final DecimalFormat AMT_FMT = new DecimalFormat("#,##0.00");
    private static final String[] WEEK_DAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private volatile boolean isCleared = false;
    private boolean hasRoomBillsPublished = false;

    // ── 标志位 ────────────────────────────────────────
    private long viewModelStartTime;
    private boolean isSnapshotLoaded = false;
    private final AccountDao accountDao;
    private final com.example.my_project1.data.repository.account.AccountRepository accountRepository; // 新增
    private final BillRepository repository;
    private final UserProfileRepository userProfileRepository;

    // ── 用户 ─────────────────────────────────────────
    private String currentUserId;
    private String lastUserId;

    // ──────────────── LiveData ────────────────────────

    /** 触发器：值变化时重新查询当月账单 */
    private final MutableLiveData<Long> _refreshTrigger = new MutableLiveData<>(System.currentTimeMillis());

    /** 原始当月账单（Room → ViewModel 内部使用，不直接暴露给 UI） */
    private LiveData<List<Bill>> currentMonthBills;

    /** 所有账户（用于联动刷新 UI 和 Header） */
    private LiveData<List<Account>> allAccountsLive;

    /** 所有账单（用于统计 billCount / billDays） */
    private LiveData<List<Bill>> allBills;

    private final MediatorLiveData<PagingData<HomeBillUiModel>> _homeBillPagingData = new MediatorLiveData<>();
    public final LiveData<PagingData<HomeBillUiModel>> homeBillPagingData = _homeBillPagingData;
    private LiveData<PagingData<HomeBillUiModel>> pagerLiveData;
    private volatile HomeBillsPagingSource homeBillsPagingSource;

    /** ✅ 新增：供 HeaderAdapter 使用的统计概览数据 */
    private final MutableLiveData<HeaderUiModel> _headerData =
            new MutableLiveData<>(
                    new HeaderUiModel(
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00",
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00"
                    )
            );
    public  final LiveData<HeaderUiModel>         headerData  = _headerData;




    /** 账单总数 */
    private final MutableLiveData<Integer> _billCount = new MutableLiveData<>(0);
    public  final LiveData<Integer>         billCount  = _billCount;

    /** 记账天数 */
    private final MutableLiveData<Integer> _billDays = new MutableLiveData<>(0);
    public  final LiveData<Integer>         billDays  = _billDays;

    /** CRUD 操作状态 */
    private final MutableLiveData<ApiResponse<String>> _operationState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> operationState = _operationState;

    /** 同步状态 */
    private final MutableLiveData<ApiResponse<BillRepository.SyncResult>> _syncState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<BillRepository.SyncResult>> syncState = _syncState;

    /** Toast 消息 */
    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    /** ✅ 日历专用的每日统计映射 */
    private final MutableLiveData<Map<String, com.example.my_project1.data.model.calendar.DailyStat>> _dailyStatsMap = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, com.example.my_project1.data.model.calendar.DailyStat>> dailyStatsMap = _dailyStatsMap;

    /** ✅ 新增：选中日期的账单列表（取代全量分组映射） */
    private final MutableLiveData<Date> _selectedDate = new MutableLiveData<>();
    public LiveData<List<Bill>> selectedDateBills;

    // ── 同步节流 ──────────────────────────────────────
    private long    lastSyncTime        = 0;
    private static final long SYNC_THROTTLE_MS = 5 * 60 * 1000L;
    private boolean isSyncing           = false;
    private boolean isFirstInit         = true;

    // ── 统计防抖 ──────────────────────────────────────
    private final Handler statsDebounceHandler = new Handler(Looper.getMainLooper());
    private static final long STATS_DEBOUNCE_MS = 2000L;
    private Runnable statsDebounceRunnable;
    private int lastSyncedCount = -1;
    private int lastSyncedDays  = -1;

    // ── Observer 引用（防内存泄漏）────────────────────
    private Observer<Integer> billCountObserver;
    private Observer<Integer> billDaysObserver;
    private Observer<List<Bill>> monthBillsObserver;
    private Observer<List<Account>> accountsObserver;
    private Observer<List<Bill>> allBillsObserver;

    // ════════════════════════════════════════════════════
    //  构造
    // ════════════════════════════════════════════════════
    public BillViewModel(@NonNull Application application) {
        super(application);

        AppDatabase db        = AppDatabase.getInstance(application);
        accountDao            = db.accountDao();
        accountRepository     = new com.example.my_project1.data.repository.account.AccountRepository(application); // 初始化
        repository            = new BillRepository(application);
        userProfileRepository = UserProfileRepository.getInstance(application);

        viewModelStartTime = System.currentTimeMillis();

        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            currentUserId = user.getObjectId();
            lastUserId    = currentUserId;
        }

        // 🚀 第一时间加载快照
        loadSnapshot();

        selectedDateBills = Transformations.switchMap(_selectedDate, date -> {
            if (date == null || currentUserId == null) return new MutableLiveData<>(new ArrayList<>());
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            Date start = cal.getTime();
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
            Date end = cal.getTime();
            return repository.getBillsInTimeRange(currentUserId, start, end);
        });

        initializeLiveData();
        rebuildHomeBillsPager();
        observeAllBillsForStats();
        observeStatsForSync();
        observeMonthBillsForUiMapping();
    }

    /**
     * ✅ 快照优先：从 SharedPreferences 恢复数据
     */
    private void loadSnapshot() {
        android.content.SharedPreferences sp = getApplication().getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE);

        String headerJson = sp.getString(snapshotKey(KEY_HEADER_SNAPSHOT),
                sp.getString(KEY_HEADER_SNAPSHOT, null));
        String calendarJson = sp.getString(snapshotKey(KEY_CALENDAR_SNAPSHOT),
                sp.getString(KEY_CALENDAR_SNAPSHOT, null));
        String billItemsJson = sp.getString(snapshotKey(KEY_BILL_ITEMS_SNAPSHOT),
                sp.getString(KEY_BILL_ITEMS_SNAPSHOT, null));
        boolean billSnapshotRestored = false;

        if (headerJson != null) {
            HeaderUiModel header = gson.fromJson(headerJson, HeaderUiModel.class);
            _headerData.setValue(header);
        }

        if (calendarJson != null) {
            Type type = new TypeToken<Map<String, com.example.my_project1.data.model.calendar.DailyStat>>(){}.getType();
            Map<String, com.example.my_project1.data.model.calendar.DailyStat> stats = gson.fromJson(calendarJson, type);
            _dailyStatsMap.setValue(stats);
        }

        if (billItemsJson != null) {
            Type type = new TypeToken<List<HomeBillUiModel>>(){}.getType();
            List<HomeBillUiModel> billItems = gson.fromJson(billItemsJson, type);
            if (billItems != null) {
                ImageLoaderUtils.preloadHomeBillCategoryIcons(getApplication(), collectCategoryIconUrls(billItems));
                _homeBillPagingData.setValue(PagingData.from(billItems));
                billSnapshotRestored = true;
            }
        }

        isSnapshotLoaded = billSnapshotRestored;
        Log.d(TAG, "⚡ 快照加载完成");
    }

    /**
     * ✅ 保存快照：包含统计数据和日历统计
     */
    private String snapshotKey(String baseKey) {
        return snapshotKey(baseKey, currentUserId);
    }

    private String snapshotKey(String baseKey, String userId) {
        return userId == null ? baseKey : baseKey + "_" + userId;
    }

    private void saveSnapshot(HeaderUiModel header, Map<String, com.example.my_project1.data.model.calendar.DailyStat> calendarStats,
                              List<HomeBillUiModel> billItems) {
        if (isCleared || bgExecutor.isShutdown()) return;
        String snapshotUserId = currentUserId;
        bgExecutor.execute(() -> {
            if (isCleared) return;
            android.content.SharedPreferences sp = getApplication().getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE);

            sp.edit()
                .putString(snapshotKey(KEY_HEADER_SNAPSHOT, snapshotUserId), gson.toJson(header))
                .putString(snapshotKey(KEY_CALENDAR_SNAPSHOT, snapshotUserId), gson.toJson(calendarStats))
                .putString(snapshotKey(KEY_BILL_ITEMS_SNAPSHOT, snapshotUserId), gson.toJson(billItems))
                .apply();
            Log.d(TAG, "💾 快照已持久化");
        });
    }

    private void saveSnapshot(HeaderUiModel header, List<HomeBillUiModel> billItems) {
        saveSnapshot(header, _dailyStatsMap.getValue(), billItems);
    }

    private void observeAllBillsForStats() {
        if (allBillsObserver != null && allBills != null) {
            allBills.removeObserver(allBillsObserver);
        }

        allBillsObserver = bills -> {
            if (bills == null || isCleared) return;

            // 🚀 性能优化：日历数据指纹校验
            // 🔴 修复：同样改进日历指纹，确保所有账单的变化都能反映在日历统计中
            long billChecksum = 0;
            for (Bill b : bills) {
                billChecksum += b.getId();
                if (b.getUpdatedAt() != null) {
                    billChecksum += b.getUpdatedAt().getTime();
                }
            }

            String fingerprint = bills.size() + "_" + billChecksum;
            if (fingerprint.equals(lastCalendarFingerprint)) {
                return;
            }
            lastCalendarFingerprint = fingerprint;

            if (bgExecutor.isShutdown()) return;
            bgExecutor.execute(() -> {
                if (isCleared) return;
                int count = computeBillCount(bills);
                int days  = computeBillDays(bills);

                // 计算日历所需的映射
                Map<String, com.example.my_project1.data.model.calendar.DailyStat> statsMap = new HashMap<>();

                for (Bill b : bills) {
                    if (b.getBillTime() == null) continue;
                    String key = DATE_KEY_FMT.format(b.getBillTime());

                    // 统计
                    com.example.my_project1.data.model.calendar.DailyStat stat = statsMap.get(key);
                    if (stat == null) {
                        stat = new com.example.my_project1.data.model.calendar.DailyStat(0, 0, 0);
                        statsMap.put(key, stat);
                    }
                    stat.count++;
                    if (b.getType() == 1) stat.income += b.getAmount();
                    else if (b.getType() == 0) stat.expense += b.getAmount();
                }

                mainHandler.post(() -> {
                    _billCount.setValue(count);
                    _billDays.setValue(days);
                    _dailyStatsMap.setValue(statsMap);
                });
            });
        };

        if (allBills != null) {
            allBills.observeForever(allBillsObserver);
        }
    }

    public void setSelectedDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day);
        _selectedDate.setValue(cal.getTime());
    }

    // ════════════════════════════════════════════════════
    //  LiveData 初始化
    // ════════════════════════════════════════════════════
    private void initializeLiveData() {
        if (currentUserId != null) {
            currentMonthBills = Transformations.switchMap(_refreshTrigger, trigger -> {
                Date[] range = getCurrentMonthRange();
                Log.d(TAG, "查询当月账单: " + formatDate(range[0]) + " ~ " + formatDate(range[1]));
                return repository.getBillsInTimeRange(currentUserId, range[0], range[1]);
            });
            allAccountsLive = accountDao.getAllAccountsLive();
            allBills = repository.getAllBillsByUser(currentUserId);
        } else {
            currentMonthBills = new MutableLiveData<>();
            allAccountsLive = new MutableLiveData<>();
            allBills          = new MutableLiveData<>();
        }
    }

    private void rebuildHomeBillsPager() {
        if (pagerLiveData != null) {
            _homeBillPagingData.removeSource(pagerLiveData);
            pagerLiveData = null;
        }
        homeBillsPagingSource = null;
        if (currentUserId == null) {
            _homeBillPagingData.setValue(PagingData.from(new ArrayList<>()));
        }
    }

    private void invalidateHomeBillsPaging() {
        HomeBillsPagingSource source = homeBillsPagingSource;
        if (source != null) {
            source.invalidate();
        }
    }

    /**
     * 快速判断首页数据指纹是否变化
     * -------------------------------------------------------
     * 🔴 修复：原逻辑只比对首尾账单，导致中间账单被修改（如备注、分类）时 UI 不刷新。
     * 现在通过累加所有账单的更新时间戳来生成更可靠的指纹。
     */
    public boolean isHomeDataUnchanged(List<Bill> bills, List<Account> accounts) {
        if (bills == null) return false;

        StringBuilder sb = new StringBuilder();
        sb.append(bills.size()).append("_");

        // 🔑 核心改进：累加所有账单的 ID 和更新时间，确保中间项修改也能被感知
        long billChecksum = 0;
        for (Bill b : bills) {
            billChecksum += b.getId();
            if (b.getUpdatedAt() != null) {
                billChecksum += b.getUpdatedAt().getTime();
            }
        }
        sb.append(billChecksum).append("_");

        // 加入账户指纹 (比对所有账户的余额变化)
        if (accounts != null) {
            sb.append(accounts.size()).append("_");
            for (Account acc : accounts) {
                sb.append(acc.getObjectId()).append(acc.getName()).append(acc.getIconUrl())
                        .append(acc.getBalance()).append(acc.getSyncState()).append(",");
            }
        }

        String newFingerprint = sb.toString();
        if (newFingerprint.equals(lastHomeFingerprint)) {
            return true;
        }
        lastHomeFingerprint = newFingerprint;
        return false;
    }

    /**
     * ✅ 核心优化：联动观察账单与账户
     * 只要账单列表或账户表发生变化，立即刷新 UI Items 和 Header 统计
     */
    private void observeMonthBillsForUiMapping() {
        if (monthBillsObserver != null && currentMonthBills != null) {
            currentMonthBills.removeObserver(monthBillsObserver);
        }
        if (accountsObserver != null && allAccountsLive != null) {
            allAccountsLive.removeObserver(accountsObserver);
        }

        // 定义统一的计算逻辑
        Runnable updateAction = () -> {
            if (isCleared) return;
            List<Bill> bills = currentMonthBills.getValue();
            List<Account> accounts = allAccountsLive.getValue();

            // null 表示 Room 查询尚未返回，不等同于“没有账单”。此时保留快照内容。
            if (bills == null) return;

            // 🚀 快速指纹比对：如果数据未变，直接返回
            if (isHomeDataUnchanged(bills, accounts)) {
                Log.d(TAG, "⚡ 首页数据指纹未变，跳过映射");
                return;
            }

            // 提交到后台线程计算，避免主线程卡顿
            if (bgExecutor.isShutdown()) return;
            bgExecutor.execute(() -> {
                if (isCleared) return;
                HeaderUiModel header = buildHeaderUiModel(bills, accounts);
                Map<String, Account> accountMap = new HashMap<>();
                if (accounts != null) {
                    for (Account account : accounts) {
                        if (account.getObjectId() != null) {
                            accountMap.put(account.getObjectId(), account);
                        }
                    }
                }
                List<HomeBillUiModel> homeItems = mapBillsToFlatList(bills, accountMap);

                // ✅ 3. 计算保护期剩余时间
                long elapsed = System.currentTimeMillis() - viewModelStartTime;
                long delay = isSnapshotLoaded ? Math.max(0, SNAPSHOT_PROTECT_MS - elapsed) : 0;

                mainHandler.postDelayed(() -> {
                    _headerData.setValue(header);
                    hasRoomBillsPublished = true;
                    ImageLoaderUtils.preloadHomeBillCategoryIcons(getApplication(), collectCategoryIconUrls(homeItems));
                    _homeBillPagingData.setValue(PagingData.from(homeItems));

                    // 保存新快照
                    saveSnapshot(header, homeItems);
                    isSnapshotLoaded = false; // 保护期过后的下一次更新不再延迟

                }, delay);
            });
        };

        monthBillsObserver = bills -> updateAction.run();
        accountsObserver = accounts -> updateAction.run();

        if (currentMonthBills != null) {
            currentMonthBills.observeForever(monthBillsObserver);
        }
        if (allAccountsLive != null) {
            allAccountsLive.observeForever(accountsObserver);
        }
    }

    // ════════════════════════════════════════════════════
    //  ✅ UiModel 映射 (单层列表版) - 优化版
    // ════════════════════════════════════════════════════
    public List<BillUiModel> mapBillsToUiModels(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) return new ArrayList<>();

        List<Account> accounts = allAccountsLive.getValue();
        Map<String, Account> accountMap = new HashMap<>();
        if (accounts != null) {
            for (Account acc : accounts) {
                accountMap.put(acc.getObjectId(), acc);
            }
        }

        List<BillUiModel> uiModels = new ArrayList<>(bills.size());

        for (Bill bill : bills) {
            // 唯一标识符：ID + 更新时间
            String cacheKey = "B_" + bill.getId() + "_" + (bill.getUpdatedAt() != null ? bill.getUpdatedAt().getTime() : 0);
            BillUiModel cached = billUiCache.get(cacheKey);
            if (cached != null) {
                uiModels.add(cached);
                continue;
            }

            int billType = bill.getType();
            String prefix = "";
            int amountColor;
            String categoryIcon = bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "";

            if (billType == 0) {
                prefix = "- ¥";
                amountColor = getApplication().getColor(R.color.red);
            } else if (billType == 1) {
                prefix = "+ ¥";
                amountColor = getApplication().getColor(R.color.green);
            } else {
                prefix = "¥";
                amountColor = getApplication().getColor(R.color.orange_500);
                Uri uri = Uri.parse("android.resource://" + getApplication().getPackageName() + "/" + R.drawable.ic_transference);
                categoryIcon = uri.toString();
            }

            String amountText = prefix + AMT_FMT.format(bill.getAmount());

            Account account = accountMap.get(bill.getAccountId());
            Account toAccount = (billType == 2 || billType == 3) ? accountMap.get(bill.getToAccountId()) : null;

            BillUiModel newModel = BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(TIME_FMT.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                    .categoryIconUrl(categoryIcon)
                    .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .accountName(account != null ? account.getName() : "")
                    .accountIconUrl(account != null ? account.getIconUrl() : "")
                    .toAccountName(toAccount != null ? toAccount.getName() : "")
                    .billType(billType)
                    .remarkText(bill.getRemark())
                    .imageUrls(bill.getImageUrls())
                    .build();

            billUiCache.put(cacheKey, newModel);
            uiModels.add(newModel);
        }
        return uiModels;
    }

    // ════════════════════════════════════════════════════
    //  ✅ UiModel 映射 (聚合版) - 优化版
    // ════════════════════════════════════════════════════
    /**
     * ✅ UiModel 映射 (扁平化版) - 高性能架构
     */
    private List<HomeBillUiModel> mapBillsToFlatList(List<Bill> bills, Map<String, Account> accountMap) {
        if (bills == null || bills.isEmpty()) return new ArrayList<>();

        List<Bill> sortedBills = new ArrayList<>(bills);
        Collections.sort(sortedBills, (b1, b2) -> {
            Date t1 = b1.getBillTime();
            Date t2 = b2.getBillTime();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            int res = t2.compareTo(t1);
            if (res == 0) return Long.compare(b2.getId(), b1.getId());
            return res;
        });

        List<HomeBillUiModel> flatItems = new ArrayList<>(sortedBills.size() + 10);
        String prevDateKey = null;
        double dayExpense = 0;
        double dayIncome = 0;
        int lastHeaderIndex = -1;

        for (int i = 0; i < sortedBills.size(); i++) {
            Bill bill = sortedBills.get(i);
            Date billTime = bill.getBillTime();
            if (billTime == null) continue;

            String dateKey = DATE_KEY_FMT.format(billTime);

            if (prevDateKey == null || !dateKey.equals(prevDateKey)) {
                // 如果不是第一组，需要更新上一个 Header 的统计
                if (lastHeaderIndex != -1) {
                    HomeBillUiModel prevHeader = flatItems.get(lastHeaderIndex);
                    flatItems.set(lastHeaderIndex, HomeBillUiModel.header(new BillAdapter.DateHeader(
                            prevHeader.dateKey, prevHeader.dateText,
                            String.format(Locale.getDefault(), "支 %.2f", dayExpense),
                            String.format(Locale.getDefault(), "收 %.2f", dayIncome))));
                }

                // 新 Header 占位
                Calendar cal = Calendar.getInstance();
                cal.setTime(billTime);
                String weekDay = WEEK_DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1];
                String dateDisp = DATE_DISP_FMT.format(billTime) + "（" + weekDay + "）";
                
                lastHeaderIndex = flatItems.size();
                flatItems.add(HomeBillUiModel.header(new BillAdapter.DateHeader(dateKey, dateDisp, "", "")));
                
                dayExpense = 0;
                dayIncome = 0;
                prevDateKey = dateKey;
            }

            // 统计
            if (bill.getType() == 0) dayExpense += bill.getAmount();
            else if (bill.getType() == 1) dayIncome += bill.getAmount();

            // 映射 Item
            String billCacheKey = "B_" + bill.getId() + "_" + (bill.getUpdatedAt() != null ? bill.getUpdatedAt().getTime() : 0);
            BillUiModel billUi = billUiCache.get(billCacheKey);
            if (billUi == null) {
                billUi = buildBillUiModel(bill, accountMap);
                billUiCache.put(billCacheKey, billUi);
            }
            
            Bill nextBill = i + 1 < sortedBills.size() ? sortedBills.get(i + 1) : null;
            boolean isLastInDay = nextBill == null || nextBill.getBillTime() == null
                    || !DATE_KEY_FMT.format(nextBill.getBillTime()).equals(dateKey);
            flatItems.add(HomeBillUiModel.item(billUi, isLastInDay));
        }

        // 处理最后一个 Header
        if (lastHeaderIndex != -1) {
            HomeBillUiModel prevHeader = flatItems.get(lastHeaderIndex);
            flatItems.set(lastHeaderIndex, HomeBillUiModel.header(new BillAdapter.DateHeader(
                    prevHeader.dateKey, prevHeader.dateText,
                    String.format(Locale.getDefault(), "支 %.2f", dayExpense),
                    String.format(Locale.getDefault(), "收 %.2f", dayIncome))));
        }

        if (billUiCache.size() > 1000) billUiCache.clear();
        return flatItems;
    }

    private BillUiModel buildBillUiModel(Bill bill, Map<String, Account> accountMap) {
        int billType = bill.getType();
        String prefix = "";
        int amountColor;
        String categoryIcon = bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "";

        if (billType == 0) {
            prefix = "- ¥";
            amountColor = getApplication().getColor(R.color.red);
        } else if (billType == 1) {
            prefix = "+ ¥";
            amountColor = getApplication().getColor(R.color.green);
        } else {
            prefix = "¥";
            amountColor = getApplication().getColor(R.color.orange_500);
            Uri uri = Uri.parse("android.resource://" + getApplication().getPackageName() + "/" + R.drawable.ic_transference);
            categoryIcon = uri.toString();
        }

        String amountText = prefix + AMT_FMT.format(bill.getAmount());
        Account account = accountMap != null ? accountMap.get(bill.getAccountId()) : null;
        Account toAccount = (accountMap != null && (billType == 2 || billType == 3)) ? accountMap.get(bill.getToAccountId()) : null;

        return BillUiModel.builder()
                .localId(bill.getId())
                .objectId(bill.getObjectId())
                .timeText(TIME_FMT.format(bill.getBillTime()))
                .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                .categoryIconUrl(categoryIcon)
                .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                .amountText(amountText)
                .amountColor(amountColor)
                .accountName(account != null ? account.getName() : "")
                .accountIconUrl(account != null ? account.getIconUrl() : "")
                .toAccountName(toAccount != null ? toAccount.getName() : "")
                .billType(billType)
                .remarkText(bill.getRemark())
                .imageUrls(bill.getImageUrls())
                .build();
    }

    /**
     * ✅ 构建 HeaderAdapter 需要的统计卡片数据
     * 优化：传入已获取的账户列表，避免重复查询数据库
     */
    private HeaderUiModel buildHeaderUiModel(List<Bill> monthBills, List<Account> accounts) {
        double monthlyExpense = 0, monthlyIncome = 0;
        double todayChange = 0;

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayKey = fmt.format(new Date());

        if (monthBills != null) {
            for (Bill b : monthBills) {
                if (b.getType() == 0) monthlyExpense += b.getAmount();
                else if (b.getType() == 1) monthlyIncome += b.getAmount();

                // 计算今日变化
                if (b.getBillTime() != null && todayKey.equals(fmt.format(b.getBillTime()))) {
                    if (b.getType() == 0) todayChange -= b.getAmount();
                    else if (b.getType() == 1) todayChange += b.getAmount();
                }
            }
        }

        // 计算总资产和负债 (从传入的账户列表获取)
        double assets = 0, liabilities = 0;
        if (accounts != null) {
            for (Account acc : accounts) {
                // 排除标记删除的账户
                if (acc.getSyncState() == com.example.my_project1.data.model.SyncState.TO_DELETE) continue;

                if (acc.isCredit()) {
                    liabilities += acc.getBalance();
                } else {
                    assets += acc.getBalance();
                }
            }
        }

        // 计算总收入和总支出 (从所有账单获取) 以及周结余
        double totalExpense = 0, totalIncome = 0;
        double weeklyExpense = 0, weeklyIncome = 0;

        Date[] weekRange = getCurrentWeekRange();

        List<Bill> allBillsList = repository.getAllBillsByUserSync(currentUserId);
        if (allBillsList != null) {
            for (Bill b : allBillsList) {
                double amt = b.getAmount();
                if (b.getType() == 0) {
                    totalExpense += amt;
                    if (b.getBillTime() != null && b.getBillTime().after(weekRange[0]) && b.getBillTime().before(weekRange[1])) {
                        weeklyExpense += amt;
                    }
                } else if (b.getType() == 1) {
                    totalIncome += amt;
                    if (b.getBillTime() != null && b.getBillTime().after(weekRange[0]) && b.getBillTime().before(weekRange[1])) {
                        weeklyIncome += amt;
                    }
                }
            }
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        String changePrefix = todayChange >= 0 ? "+ ¥" : "- ¥";

        return new HeaderUiModel(
                "¥" + df.format(assets - liabilities),
                changePrefix + df.format(Math.abs(todayChange)),
                "¥" + df.format(assets),
                "¥" + df.format(liabilities),
                "¥" + df.format(monthlyIncome),
                "¥" + df.format(totalIncome),
                "¥" + df.format(monthlyExpense),
                "¥" + df.format(totalExpense),
                "¥" + df.format(weeklyIncome - weeklyExpense)
        );
    }

    private Date[] getCurrentWeekRange() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();

        c.add(Calendar.DAY_OF_WEEK, 6);
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999);
        return new Date[]{start, c.getTime()};
    }

    /**
     * 下拉刷新只检查云端增量，不能清空或重建当前已展示的本地账单。
     */
    public void refresh() {
        Log.d(TAG, "开始下拉刷新...");

        if (currentUserId != null) {
            forceSyncFromCloud();
        } else {
            refreshData();
        }
    }

    private List<String> collectCategoryIconUrls(List<HomeBillUiModel> items) {
        Set<String> urls = new java.util.LinkedHashSet<>();
        if (items == null) return new ArrayList<>();
        for (HomeBillUiModel item : items) {
            if (item != null && item.billItem != null && item.billItem.categoryIconUrl != null) {
                urls.add(item.billItem.categoryIconUrl);
                if (urls.size() == 12) break;
            }
        }
        return new ArrayList<>(urls);
    }

    // ════════════════════════════════════════════════════
    //  原有公开方法（保留，未修改逻辑）
    // ════════════════════════════════════════════════════

    /** 兼容旧代码：返回原始账单列表（首页请改用 billItems） */
    public LiveData<List<Bill>> getHomeBills() { return currentMonthBills; }
    public LiveData<List<Bill>> getAllBills()   { return allBills; }

    public LiveData<List<Account>> getAllAccountsLive() { return allAccountsLive; }

    public LiveData<List<Bill>> getBillsInTimeRange(Date start, Date end) {
        if (currentUserId == null) return new MutableLiveData<>();
        return repository.getBillsInTimeRange(currentUserId, start, end);
    }

    private final Map<String, List<Bill>> mAccountBillsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, LiveData<List<Bill>>> mActiveAccountLiveDatas = new java.util.concurrent.ConcurrentHashMap<>();

    public LiveData<List<Bill>> getBillsByAccount(String accountId) {
        return getBillsByAccount(accountId, -1);
    }

    public LiveData<List<Bill>> getBillsByAccount(String accountId, long localAccountId) {
        final String cacheKey = (accountId != null ? accountId : "") + "_" + localAccountId;

        // 1. 如果已有活跃的 LiveData，直接返回 (避免重复创建 Room 查询)
        if (mActiveAccountLiveDatas.containsKey(cacheKey)) {
            return mActiveAccountLiveDatas.get(cacheKey);
        }

        MediatorLiveData<List<Bill>> result = new MediatorLiveData<>();

        // 2. 尝试从内存缓存获取并立即返回 (瞬间展示)
        List<Bill> cached = mAccountBillsCache.get(cacheKey);
        if (cached != null) {
            result.setValue(new ArrayList<>(cached));
        }

        // 3. 观察数据库 LiveData (后台刷新)
        if (currentUserId != null) {
            LiveData<List<Bill>> dbLive = repository.getBillsByAccount(currentUserId, accountId, localAccountId);
            result.addSource(dbLive, bills -> {
                if (bills != null) {
                    List<Bill> oldBills = mAccountBillsCache.get(cacheKey);
                    if (oldBills != null && oldBills.equals(bills)) {
                        // 内容完全一致，跳过更新，防止 UI 闪烁
                        return;
                    }
                    mAccountBillsCache.put(cacheKey, new ArrayList<>(bills));
                    result.setValue(bills);
                }
            });
        }

        mActiveAccountLiveDatas.put(cacheKey, result);
        return result;
    }

    public LiveData<List<Bill>> getBillsByCategory(String categoryId) {
        if (currentUserId == null) return new MutableLiveData<>(new ArrayList<>());
        return repository.getBillsByCategory(currentUserId, categoryId);
    }

    public void refreshData() {
        _refreshTrigger.setValue(System.currentTimeMillis());

        // HomeBillsPagingSource performs synchronous Room queries, so it is not
        // invalidated by Room automatically. Refresh it whenever bill data changes.
        invalidateHomeBillsPaging();
    }

    public Bill saveBill(String objectId) {
        return repository.getBillByObjectIdSync(objectId);
    }

    public Bill saveBillLocal(long id) {
        return repository.getBillByIdSync(id);
    }

    public void insertBill(Bill bill) {
        if (currentUserId == null) { _toastMessage.setValue("请先登录"); return; }
        if (bill == null)           { _toastMessage.setValue("账单数据为空"); return; }
        bill.setUserId(currentUserId);
        _operationState.setValue(ApiResponse.loading("正在添加..."));
        repository.insertBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("添加失败: " + r.message);
            }
        });
    }

    public void insertBillWithCallback(Bill bill, ApiResponse.Callback<Long> callback) {
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            if (callback != null) callback.onComplete(ApiResponse.error("请先登录"));
            return;
        }
        if (bill == null) {
            _toastMessage.setValue("账单数据为空");
            if (callback != null) callback.onComplete(ApiResponse.error("账单数据为空"));
            return;
        }
        bill.setUserId(currentUserId);
        repository.insertBill(bill, r -> {
            if (r == null) {
                r = ApiResponse.error("插入返回为空");
            }

            if (r.isSuccess()) { refreshData(); triggerBackgroundSync(); }
            if (callback != null) callback.onComplete(r);
        });
    }

    public void insertBills(List<Bill> bills) {
        if (currentUserId == null) { _toastMessage.setValue("请先登录"); return; }
        if (bills == null || bills.isEmpty()) { _toastMessage.setValue("账单列表为空"); return; }
        for (Bill b : bills) b.setUserId(currentUserId);
        _operationState.setValue(ApiResponse.loading("正在批量添加..."));
        repository.insertBills(bills, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("批量添加失败: " + r.message);
            }
        });
    }

    public void migrateBillsToAccount(String fromAccountId, long fromLocalId, String toAccountId) {
        if ((fromAccountId == null || fromAccountId.isEmpty()) && fromLocalId <= 0) {
            _toastMessage.setValue("原账户ID为空");
            return;
        }
        if (toAccountId == null || toAccountId.isEmpty()) {
            _toastMessage.setValue("目标账户ID为空");
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在迁移账单..."));
        repository.migrateBillsToAccount(fromAccountId, fromLocalId, toAccountId, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("迁移失败: " + r.message);
            }
        });
    }

    public void setBillsToNoAccount(String accountId, long localAccountId) {
        if ((accountId == null || accountId.isEmpty()) && localAccountId <= 0) {
            _toastMessage.setValue("账户ID为空");
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在处理账单..."));
        repository.setBillsToNoAccount(accountId, localAccountId, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("操作失败: " + r.message);
            }
        });
    }

    public void deleteAllBillsByAccount(String accountId, long localAccountId) {
        if ((accountId == null || accountId.isEmpty()) && localAccountId <= 0) {
            _toastMessage.setValue("账户ID为空");
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在删除所有账单..."));
        repository.deleteBillsByAccount(currentUserId, accountId, localAccountId, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue("账户账单已全部删除");
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("删除账单失败: " + r.message);
            }
        });
    }

    public void updateBill(Bill bill) {
        if (bill == null) { _toastMessage.setValue("账单数据为空"); return; }
        _operationState.setValue(ApiResponse.loading("正在更新..."));
        repository.updateBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("更新失败: " + r.message);
            }
        });
    }

    public void deleteBill(Bill bill) {
        if (bill == null) { _toastMessage.setValue("账单数据为空"); return; }
        _operationState.setValue(ApiResponse.loading("正在删除..."));
        repository.deleteBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("删除失败: " + r.message);
            }
        });
    }

    // ── 同步 ─────────────────────────────────────────
    private void triggerBackgroundSync() {
        try { BillSyncWorker.enqueue(getApplication()); }
        catch (Exception e) { Log.e(TAG, "触发同步失败", e); }
    }

    public void smartSyncFromCloud() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTime < SYNC_THROTTLE_MS) {
            long remaining = (SYNC_THROTTLE_MS - (now - lastSyncTime)) / 1000;
            _toastMessage.setValue("请" + remaining + "秒后再试");
            return;
        }
        forceSyncFromCloud();
    }

    public void forceSyncFromCloud() {
        if (isSyncing) {
            Log.d(TAG, "正在同步中，跳过本次请求");
            return;
        }
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            return;
        }

        isSyncing     = true;
        lastSyncTime  = System.currentTimeMillis();
        _syncState.setValue(ApiResponse.loading("正在同步..."));

        // 🔴 联动同步：先同步账户，再同步账单
        accountRepository.syncFromAccountGroupCloud((success, message) -> {
            if (!success) {
                Log.w(TAG, "⚠️ 账户同步失败: " + message);
            }

            repository.syncFromCloud(currentUserId, r -> {
                isSyncing = false;
                if (r.isSuccess()) {
                    _syncState.setValue(ApiResponse.success(r.data, r.message));
                    _toastMessage.setValue("同步成功");

                    // ✅ 关键：同步成功后，触发本地数据库重新查询
                    mainHandler.post(this::refreshData);
                } else {

                    _syncState.setValue(ApiResponse.error(r.message));
                    _toastMessage.setValue("同步失败: " + r.message);
                    mainHandler.post(this::refreshData);
                }
            });
        });
    }


    public void silentSyncFromCloud() {
        if (isSyncing || currentUserId == null) return;

        isSyncing = true;
        // 注意：静默同步不更新 _syncState 为 loading，也不显示 Toast

        Log.d(TAG, "开始后台静默同步...");

        accountRepository.syncFromAccountGroupCloud((success, message) -> {
            repository.syncFromCloud(currentUserId, r -> {
                isSyncing = false;
                lastSyncTime = System.currentTimeMillis();
                if (r.isSuccess()) {
                    Log.d(TAG, "后台同步成功");
                    mainHandler.post(this::refreshData);
                } else {
                    Log.w(TAG, "后台同步失败: " + r.message);
                }
            });
        });
    }

    // ── 用户切换 ──────────────────────────────────────
    public void checkUserSwitch() {
        BmobUser user      = BmobUser.getCurrentUser();
        String   newUserId = user != null ? user.getObjectId() : null;
        if (!isUserIdEqual(lastUserId, newUserId)) {
            Log.d(TAG, "🔄 用户切换: " + lastUserId + " -> " + newUserId);
            lastUserId    = newUserId;
            currentUserId = newUserId;
            isFirstInit   = true;
            isSyncing     = false;
            lastSyncTime  = 0;
            hasRoomBillsPublished = false;
            reinitializeLiveData();
            if (currentUserId != null) Log.d(TAG, "✅ 新用户登录，将自动加载数据");
        }
    }

    private boolean isUserIdEqual(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void reinitializeLiveData() {
        if (monthBillsObserver != null && currentMonthBills != null) {
            currentMonthBills.removeObserver(monthBillsObserver);
        }
        if (accountsObserver != null && allAccountsLive != null) {
            allAccountsLive.removeObserver(accountsObserver);
        }
        if (allBillsObserver != null && allBills != null) {
            allBills.removeObserver(allBillsObserver);
        }
        if (billCountObserver != null) billCount.removeObserver(billCountObserver);
        if (billDaysObserver  != null) billDays .removeObserver(billDaysObserver);

        initializeLiveData();
        loadSnapshot();
        rebuildHomeBillsPager();
        observeAllBillsForStats();
        observeStatsForSync();
        observeMonthBillsForUiMapping();

        _refreshTrigger.setValue(System.currentTimeMillis());

        mainHandler.postDelayed(() -> {
            if (currentUserId != null) silentSyncFromCloud();
        }, 500);
    }

    public void checkAndAutoSync() {
        checkUserSwitch();
        if (currentUserId == null) return;

        // 🔑 修复：每次检查同步时，也触发一次本地数据刷新逻辑，
        // 确保从其他 Activity 返回首页时，UI 状态（如月份范围、触发器）是最新的。
        refreshData();

        long now = System.currentTimeMillis();
        // 如果是首次初始化，或者距离上次同步超过阈值，则触发静默同步
        if (isFirstInit || (now - lastSyncTime > SYNC_THROTTLE_MS)) {
            isFirstInit = false;
            silentSyncFromCloud();
        }
    }

    // ── 统计写回 UserProfile ──────────────────────────
    private void observeStatsForSync() {
        if (currentUserId == null) return;
        billCountObserver = v -> scheduleSyncStats();
        billDaysObserver  = v -> scheduleSyncStats();
        billCount.observeForever(billCountObserver);
        billDays .observeForever(billDaysObserver);
    }

    private void scheduleSyncStats() {
        if (statsDebounceRunnable != null)
            statsDebounceHandler.removeCallbacks(statsDebounceRunnable);
        statsDebounceRunnable = this::syncStatsToUserProfile;
        statsDebounceHandler.postDelayed(statsDebounceRunnable, STATS_DEBOUNCE_MS);
    }

    private void syncStatsToUserProfile() {
        Integer count = billCount.getValue();
        Integer days  = billDays .getValue();
        if (count == null || days == null) return;
        if (count == lastSyncedCount && days == lastSyncedDays) return;
        lastSyncedCount = count;
        lastSyncedDays  = days;
        userProfileRepository.updateBillStats(currentUserId, days, count);
    }

    // ── 工具 ─────────────────────────────────────────
    private int computeBillCount(List<Bill> bills) { return bills == null ? 0 : bills.size(); }

    private int computeBillDays(List<Bill> bills) {
        if (bills == null) return 0;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Set<String> days = new HashSet<>();
        for (Bill b : bills) if (b.getBillTime() != null) days.add(fmt.format(b.getBillTime()));
        return days.size();
    }

    private Date[] getCurrentMonthRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);      c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);      c.set(Calendar.MILLISECOND, 999);
        return new Date[]{start, c.getTime()};
    }

    private String formatDate(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(d);
    }

    public void resetOperationState() { _operationState.setValue(ApiResponse.idle()); }
    public void resetSyncState()       { _syncState.setValue(ApiResponse.idle()); }
    public LiveData<String> getUiMessage() { return toastMessage; }

    @Override
    protected void onCleared() {
        isCleared = true;

        // 1. 立即移除所有 Handler 任务，防止销毁后继续回调
        mainHandler.removeCallbacksAndMessages(null);
        statsDebounceHandler.removeCallbacksAndMessages(null);

        // 2. 移除所有观察者
        if (billCountObserver  != null) billCount.removeObserver(billCountObserver);
        if (billDaysObserver   != null) billDays .removeObserver(billDaysObserver);
        if (allBillsObserver   != null && allBills != null)
            allBills.removeObserver(allBillsObserver);
        if (monthBillsObserver != null && currentMonthBills != null)
            currentMonthBills.removeObserver(monthBillsObserver);
        if (accountsObserver != null && allAccountsLive != null)
            allAccountsLive.removeObserver(accountsObserver);

        // 3. 安全且快速地关闭线程池
        try {
            bgExecutor.shutdownNow();
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down executor: " + e.getMessage());
        }

        super.onCleared();
        Log.d(TAG, "ViewModel cleared - all tasks cancelled");
    }
}
