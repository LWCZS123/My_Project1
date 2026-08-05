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

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.ui.adapter.bill.BillAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.work.BillSyncWorker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

    // ── 分页常量 ──────────────────────────────────────
    private static final int PAGE_SIZE        = 20;  // 每页条数
    private static final int PREFETCH_OFFSET  = 5;   // 距底部 5 条时触发预加载
    private static final long SNAPSHOT_PROTECT_MS = 500L; // 快照保护期

    // ── 后台计算线程 ───────────────────────────────────
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler        = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private volatile boolean isCleared = false;

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

    // ── 分页状态（内部维护）────────────────────────────
    private int     currentPage = 1;
    private boolean isLoading   = false;
    private boolean isLastPage  = false;

    // ──────────────── LiveData ────────────────────────

    /** 触发器：值变化时重新查询当月账单 */
    private final MutableLiveData<Long> _refreshTrigger = new MutableLiveData<>(System.currentTimeMillis());

    /** 原始当月账单（Room → ViewModel 内部使用，不直接暴露给 UI） */
    private LiveData<List<Bill>> currentMonthBills;

    /** 所有账户（用于联动刷新 UI 和 Header） */
    private LiveData<List<Account>> allAccountsLive;

    /** 所有账单（用于统计 billCount / billDays） */
    private LiveData<List<Bill>> allBills;

    /** ✅ 新增：供 HomeFragment 的 BillAdapter 使用，按日分组的账单数据 */
    private final MutableLiveData<List<BillAdapter.BillGroup>> _billItems = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<BillAdapter.BillGroup>>         billItems  = _billItems;

    /** ✅ 新增：供 HeaderAdapter 使用的统计概览数据 */
    private final MutableLiveData<HeaderUiModel> _headerData =
            new MutableLiveData<>(
                    new HeaderUiModel(
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00",
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00", "¥0.00"
                    )
            );
    public  final LiveData<HeaderUiModel>         headerData  = _headerData;




    /** ✅ 新增：分页加载状态 */
    private final MutableLiveData<PagingState> _pagingState = new MutableLiveData<>(PagingState.IDLE);
    public  final LiveData<PagingState>         pagingState  = _pagingState;

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

    /** ✅ 日历专用的每日账单列表映射 */
    private final MutableLiveData<Map<String, List<Bill>>> _groupedBillsMap = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, List<Bill>>> groupedBillsMap = _groupedBillsMap;

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

        initializeLiveData();
        observeAllBillsForStats();
        observeStatsForSync();
        observeMonthBillsForUiMapping();
    }

    /**
     * ✅ 快照优先：从 SharedPreferences 恢复数据
     */
    private void loadSnapshot() {
        android.content.SharedPreferences sp = getApplication().getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE);
        
        String headerJson = sp.getString(KEY_HEADER_SNAPSHOT, null);
        String billsJson = sp.getString(KEY_BILL_ITEMS_SNAPSHOT, null);

        if (headerJson != null) {
            HeaderUiModel header = gson.fromJson(headerJson, HeaderUiModel.class);
            _headerData.setValue(header);
        }

        if (billsJson != null) {
            Type type = new TypeToken<List<BillAdapter.BillGroup>>(){}.getType();
            List<BillAdapter.BillGroup> groups = gson.fromJson(billsJson, type);
            _billItems.setValue(groups);
        }
        
        isSnapshotLoaded = true;
        Log.d(TAG, "⚡ 快照加载完成");
    }

    /**
     * ✅ 保存快照：仅保存前 5 天的账单以保证速度
     */
    private void saveSnapshot(HeaderUiModel header, List<BillAdapter.BillGroup> billItems) {
        if (isCleared || bgExecutor.isShutdown()) {
            return;
        }
        bgExecutor.execute(() -> {
            if (isCleared) return;
            android.content.SharedPreferences sp = getApplication().getSharedPreferences(SP_NAME, android.content.Context.MODE_PRIVATE);
            
            // 限制快照大小：只存前 5 组数据
            List<BillAdapter.BillGroup> snapshotItems = billItems;
            if (billItems != null && billItems.size() > 5) {
                snapshotItems = new ArrayList<>(billItems.subList(0, 5));
            }

            sp.edit()
                .putString(KEY_HEADER_SNAPSHOT, gson.toJson(header))
                .putString(KEY_BILL_ITEMS_SNAPSHOT, gson.toJson(snapshotItems))
                .apply();
            Log.d(TAG, "💾 快照已持久化");
        });
    }

    private void observeAllBillsForStats() {
        if (allBillsObserver != null && allBills != null) {
            allBills.removeObserver(allBillsObserver);
        }

        allBillsObserver = bills -> {
            if (bills == null || isCleared) return;
            if (bgExecutor.isShutdown()) return;
            bgExecutor.execute(() -> {
                if (isCleared) return;
                int count = computeBillCount(bills);
                int days  = computeBillDays(bills);
                
                // 计算日历所需的映射
                Map<String, com.example.my_project1.data.model.calendar.DailyStat> statsMap = new HashMap<>();
                Map<String, List<Bill>> groupedMap = new HashMap<>();
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                
                for (Bill b : bills) {
                    if (b.getBillTime() == null) continue;
                    String key = fmt.format(b.getBillTime());
                    
                    // 分组
                    List<Bill> list = groupedMap.get(key);
                    if (list == null) {
                        list = new ArrayList<>();
                        groupedMap.put(key, list);
                    }
                    list.add(b);
                    
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
                    _groupedBillsMap.setValue(groupedMap);
                });
            });
        };

        if (allBills != null) {
            allBills.observeForever(allBillsObserver);
        }
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
            
            // 提交到后台线程计算，避免主线程卡顿
            if (bgExecutor.isShutdown()) return;
            bgExecutor.execute(() -> {
                if (isCleared) return;
                Map<String, Account> accountMap = new HashMap<>();
                if (accounts != null) {
                    for (Account acc : accounts) {
                        accountMap.put(acc.getObjectId(), acc);
                    }
                }

                // 2. 映射 UI 模型 (聚合版)
                List<BillAdapter.BillGroup> uiItems = mapBillsToUiGroups(bills, accountMap);
                HeaderUiModel header = buildHeaderUiModel(bills, accounts);

                // ✅ 3. 计算保护期剩余时间
                long elapsed = System.currentTimeMillis() - viewModelStartTime;
                long delay = isSnapshotLoaded ? Math.max(0, SNAPSHOT_PROTECT_MS - elapsed) : 0;

                mainHandler.postDelayed(() -> {
                    _billItems.setValue(uiItems);
                    _headerData.setValue(header);
                    
                    // 保存新快照
                    saveSnapshot(header, uiItems);
                    isSnapshotLoaded = false; // 保护期过后的下一次更新不再延迟

                    // 分页状态：首页加载完成
                    if (currentPage == 1) {
                        boolean empty   = bills == null || bills.isEmpty();
                        boolean hasMore = !empty && bills.size() >= PAGE_SIZE * currentPage;
                        _pagingState.setValue(empty || !hasMore
                                ? PagingState.NO_MORE : PagingState.IDLE);
                    }
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
    //  ✅ UiModel 映射 (单层列表版)
    // ════════════════════════════════════════════════════
    public List<BillUiModel> mapBillsToUiModels(List<Bill> bills) {
        List<BillUiModel> uiModels = new ArrayList<>();
        if (bills == null || bills.isEmpty()) return uiModels;

        List<Account> accounts = allAccountsLive.getValue();
        Map<String, Account> accountMap = new HashMap<>();
        if (accounts != null) {
            for (Account acc : accounts) {
                accountMap.put(acc.getObjectId(), acc);
            }
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat amtFmt = new DecimalFormat("#,##0.00");

        for (Bill bill : bills) {
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

            String amountText = prefix + amtFmt.format(bill.getAmount());

            Account account = accountMap.get(bill.getAccountId());
            Account toAccount = (billType == 2 || billType == 3) ? accountMap.get(bill.getToAccountId()) : null;

            uiModels.add(BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(timeFmt.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                    .categoryIconUrl(categoryIcon)
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .accountName(account != null ? account.getName() : "")
                    .accountIconUrl(account != null ? account.getIconUrl() : "")
                    .toAccountName(toAccount != null ? toAccount.getName() : "")
                    .billType(billType)
                    .remarkText(bill.getRemark())
                    .imageUrls(bill.getImageUrls())
                    .build());
        }
        return uiModels;
    }

    // ════════════════════════════════════════════════════
    //  ✅ UiModel 映射 (聚合版)
    // ════════════════════════════════════════════════════
    private List<BillAdapter.BillGroup> mapBillsToUiGroups(List<Bill> bills, Map<String, Account> accountMap) {
        List<BillAdapter.BillGroup> groups = new ArrayList<>();
        if (bills == null || bills.isEmpty()) return groups;

        SimpleDateFormat dateKeyFmt  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat dateDispFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
        SimpleDateFormat timeFmt     = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat    amtFmt      = new DecimalFormat("#,##0.00");
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        String  prevDateKey = null;
        double  dayExpense  = 0;
        double  dayIncome   = 0;
        List<BillUiModel> currentDayBills = new ArrayList<>();
        BillAdapter.DateHeader currentHeader = null;

        for (int i = 0; i < bills.size(); i++) {
            Bill   bill    = bills.get(i);
            String dateKey = dateKeyFmt.format(bill.getBillTime());

            if (prevDateKey != null && !dateKey.equals(prevDateKey)) {
                // 保存上一组
                groups.add(new BillAdapter.BillGroup(
                        new BillAdapter.DateHeader(prevDateKey, currentHeader.dateText,
                                String.format(Locale.getDefault(), "支 %.2f", dayExpense),
                                String.format(Locale.getDefault(), "收 %.2f", dayIncome)),
                        new ArrayList<>(currentDayBills)
                ));
                dayExpense = 0;
                dayIncome  = 0;
                currentDayBills.clear();
            }

            // 统计
            if (bill.getType() == 0) dayExpense += bill.getAmount();
            else if (bill.getType() == 1) dayIncome  += bill.getAmount();

            // 构建 Header (首次或日期改变时)
            if (currentDayBills.isEmpty()) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(bill.getBillTime());
                String weekDay  = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
                String dateDisp = dateDispFmt.format(bill.getBillTime()) + "（" + weekDay + "）";
                currentHeader = new BillAdapter.DateHeader(dateKey, dateDisp, "", "");
            }

            // 构建 BillUiModel
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
                // 转账 或 还款
                prefix = "¥";
                amountColor = getApplication().getColor(R.color.orange_500); 
                // 🔑 强制使用转账图标
                Uri uri = Uri.parse("android.resource://" + getApplication().getPackageName() + "/" + R.drawable.ic_transference);
                categoryIcon =uri.toString();
            }
            
            String amountText = prefix + amtFmt.format(bill.getAmount());

            Account account = accountMap != null ? accountMap.get(bill.getAccountId()) : null;
            Account toAccount = (accountMap != null && (billType == 2 || billType == 3)) ? accountMap.get(bill.getToAccountId()) : null;

            currentDayBills.add(BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(timeFmt.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                    .categoryIconUrl(categoryIcon)
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .accountName(account != null ? account.getName() : "")
                    .accountIconUrl(account != null ? account.getIconUrl() : "")
                    .toAccountName(toAccount != null ? toAccount.getName() : "")
                    .billType(billType)
                    .remarkText(bill.getRemark())
                    .imageUrls(bill.getImageUrls())
                    .build());

            prevDateKey = dateKey;
        }

        // 最后一组
        if (!currentDayBills.isEmpty() && currentHeader != null) {
            groups.add(new BillAdapter.BillGroup(
                    new BillAdapter.DateHeader(prevDateKey, currentHeader.dateText,
                            String.format(Locale.getDefault(), "支 %.2f", dayExpense),
                            String.format(Locale.getDefault(), "收 %.2f", dayIncome)),
                    currentDayBills
            ));
        }

        return groups;
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

    // ════════════════════════════════════════════════════
    //  ✅ 分页控制
    // ════════════════════════════════════════════════════

    /**
     * 上拉加载更多
     * HomeFragment 滚动监听：距底部 PREFETCH_OFFSET 条时调用
     */
    public void loadMore() {
        if (isLoading || isLastPage) return;
        isLoading = true;
        currentPage++;
        _pagingState.setValue(PagingState.LOADING);

        // TODO: 如果后端支持分页接口，在此处请求 currentPage；
        // 当前项目直接从 Room 拿全量数据，此处模拟演示分页完成逻辑
        mainHandler.postDelayed(() -> {
            isLoading = false;
            // 若后端返回数量 < PAGE_SIZE，说明已到最后一页
            // isLastPage = (newItems.size() < PAGE_SIZE);
            // 此处暂以 NO_MORE 为示意，实际项目替换为真实分页判断
            _pagingState.setValue(PagingState.NO_MORE);
            isLastPage = true;
        }, 500);
    }

    /**
     * 下拉刷新：重置分页状态并重新拉取数据
     */
    public void refresh() {
        Log.d(TAG, "开始下拉刷新...");

        // 1. 重置分页状态
        currentPage = 1;
        isLastPage  = false;
        isLoading   = false;
        _pagingState.setValue(PagingState.IDLE);

        if (currentUserId != null) {
            forceSyncFromCloud();
        } else {
            // 如果没登录，只刷新本地触发器
            _refreshTrigger.setValue(System.currentTimeMillis());
        }
    }

    /** 加载失败后点击重试 */
    public void retryLoad() {
        if (_pagingState.getValue() != PagingState.ERROR) return;
        currentPage = Math.max(1, currentPage - 1);
        loadMore();
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

    public void refreshData() {
        _refreshTrigger.setValue(System.currentTimeMillis());
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