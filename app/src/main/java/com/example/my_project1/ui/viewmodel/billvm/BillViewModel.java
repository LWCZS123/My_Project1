package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
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
import com.example.my_project1.work.BillSyncWorker;

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
 * BillViewModel (重构版)
 * -------------------------------------------------------
 * ✅ 新增：将原始 Bill 列表在后台线程映射为 BillUiModel + DateHeader 混合列表
 * ✅ 新增：分页状态管理（currentPage / isLoading / isLastPage）
 * ✅ 新增：HeaderUiModel 预计算统计数据
 * ✅ 保留：原有同步、CRUD、用户切换等全部逻辑
 */
public class BillViewModel extends AndroidViewModel {

    private static final String TAG = "BillViewModel";

    // ── 分页常量 ──────────────────────────────────────
    private static final int PAGE_SIZE        = 20;  // 每页条数
    private static final int PREFETCH_OFFSET  = 5;   // 距底部 5 条时触发预加载

    // ── 后台计算线程 ───────────────────────────────────
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler        = new Handler(Looper.getMainLooper());

    // ── 依赖 ────────────────────────────────────────
    private final AccountDao accountDao;
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

    /** 所有账单（用于统计 billCount / billDays） */
    private LiveData<List<Bill>> allBills;

    /** ✅ 新增：供 HomeFragment 的 BillAdapter 使用，DateHeader + BillUiModel 混合列表 */
    private final MutableLiveData<List<Object>> _billItems = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<Object>>         billItems  = _billItems;

    /** ✅ 新增：供 HeaderAdapter 使用的统计概览数据 */
    private final MutableLiveData<HeaderUiModel> _headerData =
            new MutableLiveData<>(
                    new HeaderUiModel(
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00",
                            "¥0.00", "¥0.00", "¥0.00", "¥0.00"
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
    private Observer<List<Bill>> allBillsObserver;

    // ════════════════════════════════════════════════════
    //  构造
    // ════════════════════════════════════════════════════
    public BillViewModel(@NonNull Application application) {
        super(application);

        AppDatabase db        = AppDatabase.getInstance(application);
        accountDao            = db.accountDao();
        repository            = new BillRepository(application);
        userProfileRepository = UserProfileRepository.getInstance(application);

        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            currentUserId = user.getObjectId();
            lastUserId    = currentUserId;
        }

        initializeLiveData();
        observeAllBillsForStats();
        observeStatsForSync();
        observeMonthBillsForUiMapping();
    }

    private void observeAllBillsForStats() {
        if (allBillsObserver != null && allBills != null) {
            allBills.removeObserver(allBillsObserver);
        }

        allBillsObserver = bills -> {
            if (bills == null) return;
            bgExecutor.execute(() -> {
                int count = computeBillCount(bills);
                int days  = computeBillDays(bills);
                mainHandler.post(() -> {
                    _billCount.setValue(count);
                    _billDays.setValue(days);
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
            allBills = repository.getAllBillsByUser(currentUserId);
        } else {
            currentMonthBills = new MutableLiveData<>();
            allBills          = new MutableLiveData<>();
        }
    }

    /**
     * ✅ 核心：观察原始账单列表，在后台线程完成 UiModel 映射
     * 每当 currentMonthBills 变化时（Room 通知），重新计算整个 UiModel 列表
     */
    private void observeMonthBillsForUiMapping() {
        // 先移除旧 Observer（用户切换时重新注册）
        if (monthBillsObserver != null && currentMonthBills != null) {
            currentMonthBills.removeObserver(monthBillsObserver);
        }

        monthBillsObserver = bills -> {
            // 提交到后台线程计算，避免主线程卡顿
            bgExecutor.execute(() -> {
                // 1. 获取所有账户并构建 Map (用于显示账户名)
                List<Account> accounts = accountDao.getAllAccountsSync();
                Map<String, Account> accountMap = new HashMap<>();
                if (accounts != null) {
                    for (Account acc : accounts) {
                        accountMap.put(acc.getObjectId(), acc);
                    }
                }

                // 2. 映射 UI 模型
                List<Object> uiItems   = mapBillsToUiItems(bills, accountMap);
                HeaderUiModel header   = buildHeaderUiModel(bills);

                mainHandler.post(() -> {
                    _billItems.setValue(uiItems);
                    _headerData.setValue(header);

                    // 分页状态：首页加载完成
                    if (currentPage == 1) {
                        boolean empty   = bills == null || bills.isEmpty();
                        boolean hasMore = !empty && bills.size() >= PAGE_SIZE * currentPage;
                        _pagingState.setValue(empty || !hasMore
                                ? PagingState.NO_MORE : PagingState.IDLE);
                    }
                });
            });
        };

        if (currentMonthBills != null) {
            // observeForever，在 onCleared 中手动移除
            currentMonthBills.observeForever(monthBillsObserver);
        }
    }

    // ════════════════════════════════════════════════════
    //  ✅ UiModel 映射（后台线程执行）
    // ════════════════════════════════════════════════════
    /**
     * 将原始 Bill 列表转化为 BillAdapter 需要的 [DateHeader, BillUiModel, ...] 混合列表
     * 同时计算每条账单的时间轴连线状态（isFirstOfDay / isLastOfDay）
     */
    private List<Object> mapBillsToUiItems(List<Bill> bills, Map<String, Account> accountMap) {
        List<Object> items = new ArrayList<>();
        if (bills == null || bills.isEmpty()) return items;

        SimpleDateFormat dateKeyFmt  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat dateDispFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
        SimpleDateFormat timeFmt     = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat    amtFmt      = new DecimalFormat("#,##0.00");
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        String  prevDateKey    = null;
        double  dayExpense     = 0;
        double  dayIncome      = 0;
        int     headerIndex    = -1;    // 当前日期 Header 在 items 中的索引
        int     firstBillIndex = -1;    // 当天第一笔账单在 items 中的索引

        for (int i = 0; i < bills.size(); i++) {
            Bill   bill    = bills.get(i);
            String dateKey = dateKeyFmt.format(bill.getBillTime());

            boolean isDayChange = !dateKey.equals(prevDateKey);

            // ── 日期切换：补写上一组 Header 汇总，再插入新 Header ──
            if (isDayChange) {
                // 补写上一组 Header 的汇总金额
                if (headerIndex >= 0) {
                    BillAdapter.DateHeader oldHeader = (BillAdapter.DateHeader) items.get(headerIndex);
                    items.set(headerIndex, new BillAdapter.DateHeader(
                            oldHeader.dateKey,
                            oldHeader.dateText,
                            String.format(Locale.getDefault(), "支出 ¥%.2f", dayExpense),
                            String.format(Locale.getDefault(), "收入 ¥%.2f", dayIncome)
                    ));
                    // 标记上一天最后一笔账单 isLastOfDay=true
                    markLastBillOfDay(items, firstBillIndex);
                }

                // 重置日统计
                dayExpense = 0;
                dayIncome  = 0;

                // 构建日期显示文字
                Calendar cal = Calendar.getInstance();
                cal.setTime(bill.getBillTime());
                String weekDay  = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
                String dateDisp = dateDispFmt.format(bill.getBillTime()) + "（" + weekDay + "）";

                // 插入 DateHeader 占位（汇总金额在循环结束后补写）
                headerIndex = items.size();
                items.add(new BillAdapter.DateHeader(dateKey, dateDisp, "支出 ¥0.00", "收入 ¥0.00"));

                firstBillIndex = items.size(); // 当天第一笔账单的索引
                prevDateKey    = dateKey;
            }

            // ── 统计 ──────────────────────────────────
            if (bill.getType() == 0) dayExpense += bill.getAmount();
            else                     dayIncome  += bill.getAmount();

            // ── 构建 BillUiModel ──────────────────────
            String amountText;
            int    amountColor;
            switch (bill.getType()) {
                case 0:
                    amountText  = "-¥" + amtFmt.format(bill.getAmount());
                    amountColor = getApplication().getColor(R.color.red);
                    break;
                case 1:
                    amountText  = "+¥" + amtFmt.format(bill.getAmount());
                    amountColor = getApplication().getColor(R.color.green);
                    break;
                default:
                    amountText  = "¥" + amtFmt.format(bill.getAmount());
                    amountColor = getApplication().getColor(android.R.color.black);
            }

            boolean isFirstOfDay = (items.size() == firstBillIndex); // 第一笔

            Account account = accountMap != null ? accountMap.get(bill.getAccountId()) : null;
            String accountName = account != null ? account.getName() : "";
            String accountIcon = account != null ? account.getIconUrl() : "";

            BillUiModel uiModel = BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(timeFmt.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                    .categoryIconUrl(bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "")
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .accountName(accountName)
                    .accountIconUrl(accountIcon)
                    .remarkText(bill.getRemark())
                    .locationText(bill.getLocation())
                    .imageUrls(bill.getImageUrls())
                    .isFirstOfDay(isFirstOfDay)
                    .isLastOfDay(false)    // 先默认 false，最后一笔在下次切换或循环结束时更新
                    .build();

            items.add(uiModel);
        }

        // ── 补写最后一组 Header 汇总 + 最后一笔 isLastOfDay ──
        if (headerIndex >= 0) {
            BillAdapter.DateHeader lastHeader = (BillAdapter.DateHeader) items.get(headerIndex);
            items.set(headerIndex, new BillAdapter.DateHeader(
                    lastHeader.dateKey, lastHeader.dateText,
                    String.format(Locale.getDefault(), "支出 ¥%.2f", dayExpense),
                    String.format(Locale.getDefault(), "收入 ¥%.2f", dayIncome)
            ));
            markLastBillOfDay(items, firstBillIndex);
        }

        return items;
    }

    /** 从 startIndex 往后找到最后一条 BillUiModel，设置 isLastOfDay=true */
    private void markLastBillOfDay(List<Object> items, int startIndex) {
        for (int j = items.size() - 1; j >= startIndex; j--) {
            if (items.get(j) instanceof BillUiModel) {
                BillUiModel last = (BillUiModel) items.get(j);
                last.isLastOfDay = true;
                break;
            }
        }
    }

    /**
     * ✅ 构建 HeaderAdapter 需要的统计卡片数据
     */
    private HeaderUiModel buildHeaderUiModel(List<Bill> monthBills) {
        double monthlyExpense = 0, monthlyIncome = 0;
        double todayChange = 0;
        
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayKey = fmt.format(new Date());

        if (monthBills != null) {
            for (Bill b : monthBills) {
                if (b.getType() == 0) monthlyExpense += b.getAmount();
                else monthlyIncome += b.getAmount();
                
                // 计算今日变化
                if (b.getBillTime() != null && todayKey.equals(fmt.format(b.getBillTime()))) {
                    if (b.getType() == 0) todayChange -= b.getAmount();
                    else todayChange += b.getAmount();
                }
            }
        }

        // 计算总资产和负债 (从账户获取)
        double assets = 0, liabilities = 0;
        List<Account> accounts = accountDao.getAllAccountsSync();
        if (accounts != null) {
            for (Account acc : accounts) {
                if (acc.isCredit()) {
                    liabilities += acc.getBalance();
                } else {
                    assets += acc.getBalance();
                }
            }
        }

        // 计算总收入和总支出 (从所有账单获取)
        double totalExpense = 0, totalIncome = 0;
        List<Bill> allBillsList = repository.getAllBillsByUserSync(currentUserId);
        if (allBillsList != null) {
            for (Bill b : allBillsList) {
                if (b.getType() == 0) totalExpense += b.getAmount();
                else totalIncome += b.getAmount();
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
                "¥" + df.format(totalExpense)
        );
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

    public LiveData<List<Bill>> getBillsInTimeRange(Date start, Date end) {
        if (currentUserId == null) return new MutableLiveData<>();
        return repository.getBillsInTimeRange(currentUserId, start, end);
    }

    public LiveData<List<Bill>> getBillsByAccount(String accountId) {
        if (currentUserId == null || accountId == null) return new MutableLiveData<>();
        return repository.getBillsByAccount(currentUserId, accountId);
    }

    public void refreshData() {
        _refreshTrigger.setValue(System.currentTimeMillis());
    }

    public Bill saveBill(String objectId) {
        return repository.getBillByObjectIdSync(objectId);
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

    public void migrateBillsToAccount(String fromAccountId, String toAccountId) {
        if (fromAccountId == null || fromAccountId.isEmpty()) { _toastMessage.setValue("原账户ID为空"); return; }
        if (toAccountId   == null || toAccountId.isEmpty())   { _toastMessage.setValue("目标账户ID为空"); return; }
        _operationState.setValue(ApiResponse.loading("正在迁移账单..."));
        repository.migrateBillsToAccount(fromAccountId, toAccountId, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("迁移失败: " + r.message);
            }
        });
    }

    public void setBillsToNoAccount(String accountId) {
        if (accountId == null || accountId.isEmpty()) { _toastMessage.setValue("账户ID为空"); return; }
        _operationState.setValue(ApiResponse.loading("正在处理账单..."));
        repository.setBillsToNoAccount(accountId, r -> {
            if (r.isSuccess()) {
                _operationState.setValue(ApiResponse.success(r.message));
                _toastMessage.setValue(r.message);
                refreshData(); triggerBackgroundSync();
            } else {
                _operationState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("操作失败: " + r.message);
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

        repository.syncFromCloud(currentUserId, r -> {
            isSyncing = false;
            if (r.isSuccess()) {
                _syncState.setValue(ApiResponse.success(r.data, r.message));
                _toastMessage.setValue("同步成功");

                // ✅ 关键：同步成功后，触发本地数据库重新查询
                // 这样 HomeFragment 观察的 billItems 就会收到最新数据
                // 并且因为数据变了，HomeFragment 里的 setRefreshing(false) 会被执行
                mainHandler.post(() -> {
                    _refreshTrigger.setValue(System.currentTimeMillis());
                });
            } else {
                _syncState.setValue(ApiResponse.error(r.message));
                _toastMessage.setValue("同步失败: " + r.message);

                // 即使失败也要触发一次，防止 UI 一直在转圈
                _refreshTrigger.setValue(System.currentTimeMillis());
            }
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
            List<Bill> bills = currentMonthBills.getValue();
            if (bills == null || bills.isEmpty()) forceSyncFromCloud();
        }, 500);
    }

    public void checkAndAutoSync() {
        checkUserSwitch();
        if (currentUserId == null) { isFirstInit = false; return; }
        if (!isFirstInit) return;

        mainHandler.postDelayed(() -> {
            List<Bill> bills = currentMonthBills.getValue();
            if (bills == null || bills.isEmpty()) forceSyncFromCloud();
            isFirstInit = false;
        }, 800);
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
        super.onCleared();
        bgExecutor.shutdown();
        if (billCountObserver  != null) billCount.removeObserver(billCountObserver);
        if (billDaysObserver   != null) billDays .removeObserver(billDaysObserver);
        if (allBillsObserver   != null && allBills != null)
            allBills.removeObserver(allBillsObserver);
        if (monthBillsObserver != null && currentMonthBills != null)
            currentMonthBills.removeObserver(monthBillsObserver);
        if (statsDebounceRunnable != null)
            statsDebounceHandler.removeCallbacks(statsDebounceRunnable);
        Log.d(TAG, "ViewModel cleared");
    }
}