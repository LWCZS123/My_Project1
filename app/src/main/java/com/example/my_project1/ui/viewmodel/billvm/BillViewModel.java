package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagingData;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.data.model.calendar.DailyStat;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.account.AccountRepository;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.work.BillSyncWorker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * 账单 ViewModel
 * -------------------------------------------------------
 * 职责：
 * 1. 作为 UI 层与数据 Repository 层的桥梁，管理账单 CRUD 操作与云端同步。
 * 2. 分页处理：首页通过 Paging 3 加载账单，保证高流畅度。
 * 3. 统计计算剥离：Header 概览计算委托给 HeaderCalculator，通过 SQLite 聚合查询，避免一次性把整月账单加载到内存。
 * 4. 映射与快照剥离：UI 转换委托给 BillUiModelMapper，快照持久化委托给 BillSnapshotManager。
 * 5. 注释使用纯文字说明。
 */
public class BillViewModel extends AndroidViewModel {

    private static final String TAG = "BillViewModel";

    // 分页常量
    private static final int PAGE_SIZE = 50;
    private static final long SNAPSHOT_PROTECT_MS = 500L;

    // 辅助工具类
    private final HeaderCalculator headerCalculator = new HeaderCalculator();
    private final BillUiModelMapper uiModelMapper = new BillUiModelMapper();
    private final BillSnapshotManager snapshotManager;
    private final AppExecutors executors;

    // 数据库与仓库
    private final AccountDao accountDao;
    private final AccountRepository accountRepository;
    private final BillRepository repository;
    private final UserProfileRepository userProfileRepository;

    // 主线程 Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 性能优化与缓存控制
    private volatile Map<String, List<Bill>> dailyBillsCache = Collections.emptyMap();
    private volatile boolean dailyBillsCacheReady = false;
    private volatile boolean isCleared = false;

    private final long viewModelStartTime;
    private boolean isSnapshotLoaded = false;

    // 用户标识
    private String currentUserId;
    private String lastUserId;

    // 触发器与 LiveData 依赖
    private final MutableLiveData<Long> _refreshTrigger = new MutableLiveData<>(System.currentTimeMillis());
    private LiveData<List<Account>> allAccountsLive;
    private final LiveData<List<Bill>> allBills = new MutableLiveData<>();
    private LiveData<List<com.example.my_project1.data.model.bill.DailyStat>> dailyStatsLive;
    private LiveData<SearchSummary> billSummary;

    // 首页 Paging LiveData
    private final MutableLiveData<Integer> _pagerVersion = new MutableLiveData<>(0);
    public final LiveData<PagingData<HomeBillUiModel>> homeBillPagingData;
    private volatile HomeBillsPagingSource homeBillsPagingSource;

    // Header 数据
    private final MutableLiveData<HeaderUiModel> _headerData = new MutableLiveData<>(
            new HeaderUiModel("¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00")
    );
    public final LiveData<HeaderUiModel> headerData = _headerData;

    // 统计数据
    private final MutableLiveData<Integer> _billCount = new MutableLiveData<>(0);
    public final LiveData<Integer> billCount = _billCount;

    private final MutableLiveData<Integer> _billDays = new MutableLiveData<>(0);
    public final LiveData<Integer> billDays = _billDays;

    // 操作与同步状态
    private final MutableLiveData<ApiResponse<String>> _operationState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> operationState = _operationState;

    private final MutableLiveData<ApiResponse<BillRepository.SyncResult>> _syncState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<BillRepository.SyncResult>> syncState = _syncState;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    // 日历专用的每日统计映射
    private final MutableLiveData<Map<String, DailyStat>> _dailyStatsMap = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, DailyStat>> dailyStatsMap = _dailyStatsMap;

    // 选中日期的账单列表
    private final MutableLiveData<Date> _selectedDate = new MutableLiveData<>(new Date());
    public LiveData<List<Bill>> selectedDateBills;

    // 同步节流与防抖控制
    private long lastSyncTime = 0;
    private static final long SYNC_THROTTLE_MS = 5 * 60 * 1000L;
    private boolean isSyncing = false;
    private boolean isFirstInit = true;

    private final Handler statsDebounceHandler = new Handler(Looper.getMainLooper());
    private static final long STATS_DEBOUNCE_MS = 2000L;
    private Runnable statsDebounceRunnable;
    private int lastSyncedCount = -1;
    private int lastSyncedDays = -1;

    // Observer 引用管理，防止泄露
    private Observer<Integer> billCountObserver;
    private Observer<Integer> billDaysObserver;
    private Observer<List<Account>> accountsObserver;
    private Observer<Long> refreshTriggerObserver;
    private Observer<List<com.example.my_project1.data.model.bill.DailyStat>> dailyStatsObserver;
    private Observer<SearchSummary> billSummaryObserver;

    // 账户账单缓存映射
    private final Map<String, List<Bill>> mAccountBillsCache = new ConcurrentHashMap<>();
    private final Map<String, LiveData<List<Bill>>> mActiveAccountLiveDatas = new ConcurrentHashMap<>();

    public BillViewModel(@NonNull Application application) {
        super(application);

        executors = AppExecutors.get();
        snapshotManager = new BillSnapshotManager(application);

        AppDatabase db = AppDatabase.getInstance(application);
        accountDao = db.accountDao();
        accountRepository = new AccountRepository(application);
        repository = new BillRepository(application);
        userProfileRepository = UserProfileRepository.getInstance(application);

        viewModelStartTime = System.currentTimeMillis();

        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            currentUserId = user.getObjectId();
            lastUserId = currentUserId;
        }

        // 1. 加载本地快照数据，实现页面零等待展示
        loadSnapshot();

        // 2. 绑定选中日期的账单 LiveData
        selectedDateBills = Transformations.switchMap(_selectedDate, date -> {
            if (date == null || currentUserId == null) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0);
            Date start = cal.getTime();

            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);      cal.set(Calendar.MILLISECOND, 999);
            Date end = cal.getTime();
            return repository.getBillsInTimeRange(currentUserId, start, end);
        });

        // 3. 初始化全局 LiveData
        initializeLiveData();

        // 4. 构建首页 Paging3 分页数据流
        homeBillPagingData = Transformations.switchMap(_pagerVersion, version -> {
            Log.d(TAG, "创建新的 Pager 版本: " + version);
            if (currentUserId == null) {
                return new MutableLiveData<>(PagingData.from(new ArrayList<>()));
            }

            Date[] range = headerCalculator.getCurrentMonthRange();
            androidx.paging.Pager<Integer, HomeBillUiModel> pager = new androidx.paging.Pager<>(
                    new androidx.paging.PagingConfig(PAGE_SIZE, 10, false, PAGE_SIZE, PAGE_SIZE * 3),
                    () -> {
                        homeBillsPagingSource = new HomeBillsPagingSource(
                                getApplication(),
                                repository,
                                accountDao,
                                currentUserId,
                                range[0],
                                range[1]
                        );
                        return homeBillsPagingSource;
                    }
            );

            LiveData<PagingData<HomeBillUiModel>> pagedLiveData = androidx.paging.PagingLiveData.getLiveData(pager);
            return androidx.paging.PagingLiveData.cachedIn(pagedLiveData, androidx.lifecycle.ViewModelKt.getViewModelScope(this));
        });

        // 5. 监听统计与 Header 变更
        observeAllBillsForStats();
        observeStatsForSync();
        observeHeaderDataUpdate();
    }

    /**
     * 加载本地快照
     */
    private void loadSnapshot() {
        HeaderUiModel header = snapshotManager.loadHeaderSnapshot(currentUserId);
        if (header != null) {
            _headerData.setValue(header);
        }

        Map<String, DailyStat> calendarStats = snapshotManager.loadCalendarSnapshot(currentUserId);
        if (calendarStats != null) {
            _dailyStatsMap.setValue(calendarStats);
        }

        snapshotManager.preloadSnapshotIcons(currentUserId);
        isSnapshotLoaded = true;
    }

    /**
     * 保存快照
     */
    private void saveSnapshot(HeaderUiModel header) {
        if (isCleared) return;
        snapshotManager.saveSnapshot(currentUserId, header, _dailyStatsMap.getValue(), new ArrayList<>(), executors);
    }

    /**
     * LiveData 数据源初始化
     */
    private void initializeLiveData() {
        if (currentUserId != null) {
            allAccountsLive = accountDao.getAllAccountsLive();
            billSummary = repository.getUserBillSummary(currentUserId);
            dailyStatsLive = repository.getUserDailyStats(currentUserId);
        } else {
            allAccountsLive = new MutableLiveData<>(new ArrayList<>());
            billSummary = new MutableLiveData<>();
            dailyStatsLive = new MutableLiveData<>(new ArrayList<>());
        }
    }

    /**
     * 监听每日统计与全量聚合数据变化
     */
    private void observeAllBillsForStats() {
        if (dailyStatsObserver != null && dailyStatsLive != null) {
            dailyStatsLive.removeObserver(dailyStatsObserver);
        }

        dailyStatsObserver = rows -> {
            if (rows == null || isCleared) return;
            Map<String, DailyStat> statsMap = new HashMap<>();
            for (com.example.my_project1.data.model.bill.DailyStat row : rows) {
                if (row.day == null) continue;
                // 修复参数顺序：收入、支出、账单数
                statsMap.put(row.day, new DailyStat(row.incomeTotal, row.expenseTotal, row.billCount));
            }
            dailyBillsCache = Collections.emptyMap();
            dailyBillsCacheReady = false;
            _dailyStatsMap.setValue(statsMap);
        };
        if (dailyStatsLive != null) dailyStatsLive.observeForever(dailyStatsObserver);

        if (billSummaryObserver != null && billSummary != null) {
            billSummary.removeObserver(billSummaryObserver);
        }
        billSummaryObserver = summary -> {
            if (summary == null || isCleared) return;
            _billCount.setValue(summary.getBillCount());
            _billDays.setValue(summary.getBillDays());
        };
        if (billSummary != null) billSummary.observeForever(billSummaryObserver);
    }

    /**
     * 核心优化：更新 Header 概览统计数据
     * 说明：不再加载当月全部账单列表，而是通过 SQLite 聚合函数直接查询统计结果。
     */
    private void observeHeaderDataUpdate() {
        if (refreshTriggerObserver != null) {
            _refreshTrigger.removeObserver(refreshTriggerObserver);
        }
        if (accountsObserver != null && allAccountsLive != null) {
            allAccountsLive.removeObserver(accountsObserver);
        }

        Runnable updateAction = () -> {
            if (isCleared || currentUserId == null) return;

            executors.computation().execute(() -> {
                if (isCleared) return;

                List<Account> accounts = allAccountsLive.getValue();
                SearchSummary totalSum = repository.getUserBillSummarySync(currentUserId);
                Date[] monthRange = headerCalculator.getCurrentMonthRange();
                SearchSummary monthSum = repository.getBillSummaryInRangeSync(currentUserId, monthRange[0], monthRange[1]);

                // 检查数据指纹，避免重复刷新
                if (headerCalculator.isHeaderDataUnchanged(accounts, totalSum, monthSum)) {
                    return;
                }

                HeaderUiModel header = headerCalculator.calculateHeader(currentUserId, repository, accounts);

                long elapsed = System.currentTimeMillis() - viewModelStartTime;
                long delay = isSnapshotLoaded ? Math.max(0, SNAPSHOT_PROTECT_MS - elapsed) : 0;

                mainHandler.postDelayed(() -> {
                    if (isCleared) return;
                    _headerData.setValue(header);
                    saveSnapshot(header);
                    isSnapshotLoaded = false;
                }, delay);
            });
        };

        refreshTriggerObserver = trigger -> updateAction.run();
        accountsObserver = accounts -> updateAction.run();

        _refreshTrigger.observeForever(refreshTriggerObserver);
        if (allAccountsLive != null) {
            allAccountsLive.observeForever(accountsObserver);
        }
    }

    /**
     * 获取缓存中某天的账单列表
     */
    @Nullable
    public List<Bill> getCachedBillsForDate(int year, int month, int day) {
        if (!dailyBillsCacheReady) return null;
        List<Bill> bills = dailyBillsCache.get(dateKey(year, month, day));
        return bills == null ? Collections.emptyList() : bills;
    }

    /**
     * 获取指定日期的账单列表 LiveData
     */
    public LiveData<List<Bill>> getBillsForDate(int year, int month, int day) {
        if (currentUserId == null) {
            return new MutableLiveData<>(Collections.emptyList());
        }
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date endExclusive = cal.getTime();
        return repository.getBillsInTimeRangeExclusive(currentUserId, start, endExclusive);
    }

    private static String dateKey(int year, int month, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /**
     * 设置当前选中的日期
     */
    public void setSelectedDate(int year, int month, int day) {
        Date current = _selectedDate.getValue();
        if (current != null) {
            Calendar selected = Calendar.getInstance();
            selected.setTime(current);
            if (selected.get(Calendar.YEAR) == year
                    && selected.get(Calendar.MONTH) == month - 1
                    && selected.get(Calendar.DAY_OF_MONTH) == day) {
                return;
            }
        }
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day);
        _selectedDate.setValue(cal.getTime());
    }

    /**
     * 重建首页 Paging 数据源
     */
    public void rebuildHomeBillsPager() {
        Integer current = _pagerVersion.getValue();
        _pagerVersion.setValue(current == null ? 1 : current + 1);
    }

    private void invalidateHomeBillsPaging() {
        HomeBillsPagingSource source = homeBillsPagingSource;
        if (source != null) {
            source.invalidate();
        }
    }

    /**
     * 判断首页数据是否保持未变
     */
    public boolean isHomeDataUnchanged(List<Bill> bills, List<Account> accounts) {
        if (bills == null) return false;

        SearchSummary totalSum = repository.getUserBillSummarySync(currentUserId);
        Date[] monthRange = headerCalculator.getCurrentMonthRange();
        SearchSummary monthSum = repository.getBillSummaryInRangeSync(currentUserId, monthRange[0], monthRange[1]);

        return headerCalculator.isHeaderDataUnchanged(accounts, totalSum, monthSum);
    }

    /**
     * 将账单列表转换为 UI 展示模型（委托给 BillUiModelMapper）
     */
    public List<BillUiModel> mapBillsToUiModels(List<Bill> bills) {
        List<Account> accounts = allAccountsLive != null ? allAccountsLive.getValue() : null;
        return uiModelMapper.mapBillsToUiModels(getApplication(), bills, accounts);
    }

    /**
     * 下拉刷新数据
     */
    public void refresh() {
        Log.d(TAG, "开始下拉刷新...");
        if (currentUserId != null) {
            forceSyncFromCloud();
        } else {
            refreshData();
        }
    }

    /**
     * 兼容旧调用方
     */
    public LiveData<List<Bill>> getAllBills() {
        return allBills;
    }

    public LiveData<Bill> getBillLive(String objectId, long localId) {
        if (currentUserId == null) return new MutableLiveData<>();
        return repository.getBillLive(currentUserId, objectId, localId);
    }

    public LiveData<List<Account>> getAllAccountsLive() {
        return allAccountsLive;
    }

    public LiveData<List<Bill>> getBillsInTimeRange(Date start, Date end) {
        if (currentUserId == null) return new MutableLiveData<>();
        return repository.getBillsInTimeRange(currentUserId, start, end);
    }

    public LiveData<List<Bill>> getBillsByAccount(String accountId) {
        return getBillsByAccount(accountId, -1);
    }

    public LiveData<List<Bill>> getBillsByAccount(String accountId, long localAccountId) {
        final String cacheKey = (accountId != null ? accountId : "") + "_" + localAccountId;

        if (mActiveAccountLiveDatas.containsKey(cacheKey)) {
            return mActiveAccountLiveDatas.get(cacheKey);
        }

        MediatorLiveData<List<Bill>> result = new MediatorLiveData<>();

        List<Bill> cached = mAccountBillsCache.get(cacheKey);
        if (cached != null) {
            result.setValue(new ArrayList<>(cached));
        }

        if (currentUserId != null) {
            LiveData<List<Bill>> dbLive = repository.getBillsByAccount(currentUserId, accountId, localAccountId);
            result.addSource(dbLive, bills -> {
                if (bills != null) {
                    List<Bill> oldBills = mAccountBillsCache.get(cacheKey);
                    if (Objects.equals(oldBills, bills)) {
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

    /**
     * 刷新首页数据与通知触发器
     */
    public void refreshData() {
        Runnable refreshAction = () -> {
            if (isCleared) return;
            _refreshTrigger.setValue(System.currentTimeMillis());
            rebuildHomeBillsPager();
            invalidateHomeBillsPaging();
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshAction.run();
        } else {
            mainHandler.post(refreshAction);
        }
    }

    public Bill saveBill(String objectId) {
        return repository.getBillByObjectIdSync(objectId);
    }

    public Bill saveBillLocal(long id) {
        return repository.getBillByIdSync(id);
    }

    public void insertBill(Bill bill) {
        if (currentUserId == null) { _toastMessage.setValue("请先登录"); return; }
        if (bill == null) { _toastMessage.setValue("账单数据为空"); return; }
        bill.setUserId(currentUserId);
        _operationState.setValue(ApiResponse.loading("正在添加..."));
        repository.insertBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("添加失败: " + r.message);
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
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            return;
        }
        if (bill.getUserId() == null) {
            bill.setUserId(currentUserId);
        }
        if (!Objects.equals(currentUserId, bill.getUserId())) {
            _toastMessage.setValue("账单所属用户无效");
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在更新..."));
        repository.updateBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("更新失败: " + r.message);
            }
        });
    }

    public void deleteBill(Bill bill) {
        if (bill == null) { _toastMessage.setValue("账单数据为空"); return; }
        if (currentUserId == null) { _toastMessage.setValue("请先登录"); return; }
        if (bill.getUserId() != null && !Objects.equals(currentUserId, bill.getUserId())) {
            _toastMessage.setValue("账单所属用户无效");
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在删除..."));
        repository.deleteBill(bill, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData();
                triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("删除失败: " + r.message);
            }
        });
    }

    private void triggerBackgroundSync() {
        try {
            BillSyncWorker.enqueue(getApplication());
        } catch (Exception e) {
            Log.e(TAG, "触发同步失败", e);
        }
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

        isSyncing = true;
        lastSyncTime = System.currentTimeMillis();
        _syncState.setValue(ApiResponse.loading("正在同步..."));

        accountRepository.syncFromAccountGroupCloud((success, message) -> {
            if (!success) {
                Log.w(TAG, "账户同步失败: " + message);
            }

            repository.syncFromCloud(currentUserId, r -> {
                isSyncing = false;
                if (r.isSuccess()) {
                    _syncState.setValue(ApiResponse.success(r.data, r.message));
                    _toastMessage.setValue("同步成功");
                } else {
                    _syncState.setValue(ApiResponse.error(r.message));
                    _toastMessage.setValue("同步失败: " + (r.message != null ? r.message : "未知错误"));
                }
                mainHandler.post(this::refreshData);
            });
        });
    }

    public void silentSyncFromCloud() {
        if (isSyncing || currentUserId == null) return;

        isSyncing = true;
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

    public void checkUserSwitch() {
        BmobUser user = BmobUser.getCurrentUser();
        String newUserId = user != null ? user.getObjectId() : null;
        if (!Objects.equals(lastUserId, newUserId)) {
            Log.d(TAG, "用户切换: " + lastUserId + " -> " + newUserId);
            lastUserId = newUserId;
            currentUserId = newUserId;
            isFirstInit = true;
            isSyncing = false;
            lastSyncTime = 0;
            dailyBillsCache = Collections.emptyMap();
            dailyBillsCacheReady = false;
            uiModelMapper.clearCache();
            reinitializeLiveData();
            if (currentUserId != null) Log.d(TAG, "新用户登录，自动加载数据");
        }
    }

    private void reinitializeLiveData() {
        if (refreshTriggerObserver != null) {
            _refreshTrigger.removeObserver(refreshTriggerObserver);
        }
        if (accountsObserver != null && allAccountsLive != null) {
            allAccountsLive.removeObserver(accountsObserver);
        }
        if (dailyStatsObserver != null && dailyStatsLive != null) {
            dailyStatsLive.removeObserver(dailyStatsObserver);
        }
        if (billSummaryObserver != null && billSummary != null) {
            billSummary.removeObserver(billSummaryObserver);
        }
        if (billCountObserver != null) billCount.removeObserver(billCountObserver);
        if (billDaysObserver != null) billDays.removeObserver(billDaysObserver);

        initializeLiveData();
        loadSnapshot();
        rebuildHomeBillsPager();
        observeAllBillsForStats();
        observeStatsForSync();
        observeHeaderDataUpdate();

        _refreshTrigger.setValue(System.currentTimeMillis());

        mainHandler.postDelayed(() -> {
            if (currentUserId != null) silentSyncFromCloud();
        }, 500);
    }

    public void checkAndAutoSync() {
        checkUserSwitch();
        if (currentUserId == null) return;

        refreshData();

        long now = System.currentTimeMillis();
        if (isFirstInit || (now - lastSyncTime > SYNC_THROTTLE_MS)) {
            isFirstInit = false;
            silentSyncFromCloud();
        }
    }

    private void observeStatsForSync() {
        if (currentUserId == null) return;
        billCountObserver = v -> scheduleSyncStats();
        billDaysObserver = v -> scheduleSyncStats();
        billCount.observeForever(billCountObserver);
        billDays.observeForever(billDaysObserver);
    }

    private void scheduleSyncStats() {
        if (statsDebounceRunnable != null) {
            statsDebounceHandler.removeCallbacks(statsDebounceRunnable);
        }
        statsDebounceRunnable = this::syncStatsToUserProfile;
        statsDebounceHandler.postDelayed(statsDebounceRunnable, STATS_DEBOUNCE_MS);
    }

    private void syncStatsToUserProfile() {
        Integer count = billCount.getValue();
        Integer days = billDays.getValue();
        if (count == null || days == null) return;
        if (count == lastSyncedCount && days == lastSyncedDays) return;
        lastSyncedCount = count;
        lastSyncedDays = days;
        userProfileRepository.updateBillStats(currentUserId, days, count);
    }

    @Override
    protected void onCleared() {
        isCleared = true;

        mainHandler.removeCallbacksAndMessages(null);
        statsDebounceHandler.removeCallbacksAndMessages(null);

        if (billCountObserver != null) billCount.removeObserver(billCountObserver);
        if (billDaysObserver != null) billDays.removeObserver(billDaysObserver);
        if (refreshTriggerObserver != null) _refreshTrigger.removeObserver(refreshTriggerObserver);
        if (accountsObserver != null && allAccountsLive != null) allAccountsLive.removeObserver(accountsObserver);
        if (dailyStatsObserver != null && dailyStatsLive != null) dailyStatsLive.removeObserver(dailyStatsObserver);
        if (billSummaryObserver != null && billSummary != null) billSummary.removeObserver(billSummaryObserver);

        super.onCleared();
    }
}
